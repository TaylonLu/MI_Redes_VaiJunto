#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../comum.sh"

PORTA=2602
PASSAGEIROS="${PASSAGEIROS:-20}"
SUFIXO="$(date +%s)-$$"

echo
info "=============================================="
info " TESTE DE CONCORRÊNCIA - ASSENTO DUPLICADO"
info "=============================================="

(( PASSAGEIROS >= 2 )) || falhar "Use pelo menos 2 passageiros."

aguardar_servidor "Unova" "$PORTA"

DIR_TEMP=$(mktemp -d)
GO="$DIR_TEMP/iniciar"

trap 'rm -rf "$DIR_TEMP"' EXIT

declare -a TOKENS=()

# -----------------------------------------------
# 1. Cadastro do motorista
# -----------------------------------------------

info "Cadastrando motorista..."

payload=$(jq -n \
    --arg email "motorista-$SUFIXO@teste.local" \
    '{email:$email, senha:"Teste123!", nome:"Motorista Concorrencia"}')

api_json "$PORTA" POST "/api/cliente/cadastro" "$payload"
exigir_status 200 "Cadastro do motorista"

TOKEN_MOTORISTA=$(jq -r '.token // empty' <<< "$HTTP_BODY")
ID_MOTORISTA=$(jq -r '.usuario.id // empty' <<< "$HTTP_BODY")

[[ -n "$TOKEN_MOTORISTA" ]] || falhar "Token do motorista ausente."
[[ -n "$ID_MOTORISTA" ]] || falhar "ID do motorista ausente."

# -----------------------------------------------
# 2. Cadastro do perfil de motorista
# -----------------------------------------------

info "Cadastrando perfil de motorista..."

payload=$(jq -n \
    --arg cnh "12345678901" \
    --arg placa "ABC1D23" \
    '{
        cnh:$cnh,
        placaCarro:$placa,
        modeloCarro:"Carro de Teste",
        corCarro:"Azul"
    }')

api_json "$PORTA" POST \
    "/api/cliente/cadastro/motorista" \
    "$payload" "$TOKEN_MOTORISTA"

exigir_status 200 "Perfil de motorista"

# -----------------------------------------------
# 3. Criação de carona com uma vaga
# -----------------------------------------------

info "Criando carona com apenas uma vaga..."

DATA_PARTIDA=$(date -d "+2 days" "+%Y-%m-%dT%H:%M:%S")

payload=$(jq -n \
    --arg id "$ID_MOTORISTA" \
    --arg data "$DATA_PARTIDA" \
    '{
        idMotorista:$id,
        vagas:1,
        valorPorTrecho:10.0,
        dataPartida:$data,
        trechos:[
            {origem:101, destino:102}
        ]
    }')

api_json "$PORTA" POST \
    "/api/cliente/caronas" \
    "$payload" "$TOKEN_MOTORISTA"

exigir_status 201 "Criação da carona"

ID_CARONA=$(jq -r '.id // empty' <<< "$HTTP_BODY")

[[ -n "$ID_CARONA" ]] || falhar "ID da carona ausente."

sucesso "Carona criada: $ID_CARONA"

# -----------------------------------------------
# 4. Cadastro dos passageiros
# -----------------------------------------------

info "Cadastrando $PASSAGEIROS passageiros..."

for ((i=0; i<PASSAGEIROS; i++)); do
    payload=$(jq -n \
        --arg email "passageiro-$i-$SUFIXO@teste.local" \
        --arg nome "Passageiro $i" \
        '{
            email:$email,
            senha:"Teste123!",
            nome:$nome
        }')

    api_json "$PORTA" POST "/api/cliente/cadastro" "$payload"

    [[ "$HTTP_STATUS" == "200" ]] ||
        falhar "Falha ao cadastrar passageiro $i."

    token=$(jq -r '.token // empty' <<< "$HTTP_BODY")

    [[ -n "$token" ]] ||
        falhar "Passageiro $i não recebeu token."

    TOKENS+=("$token")
done

sucesso "$PASSAGEIROS passageiros cadastrados."

# -----------------------------------------------
# 5. Preparação das requisições concorrentes
# -----------------------------------------------

info "Preparando as requisições..."

for ((i=0; i<PASSAGEIROS; i++)); do
    payload=$(jq -n \
        --arg servidor "localhost:$PORTA" \
        --arg id "$ID_CARONA" \
        '{
            trechos_solicitados:[
                {
                    servidor:$servidor,
                    id_carona:($id + "_101_102")
                }
            ]
        }')

    (
        # Todos aguardam o sinal de início.
        while [[ ! -e "$GO" ]]; do
            sleep 0.01
        done

        status=$(curl --silent --show-error \
            --connect-timeout 5 \
            -X POST \
            -H "Accept: application/json" \
            -H "Content-Type: application/json" \
            -H "Authorization: Bearer ${TOKENS[$i]}" \
            --data "$payload" \
            -o "$DIR_TEMP/body-$i.json" \
            -w '%{http_code}' \
            "$BASE_URL:$PORTA/api/cliente/reservas" || true)

        printf '%s\n' "${status:-000}" > "$DIR_TEMP/status-$i.txt"
    ) &

done

# Libera todos os processos simultaneamente.
touch "$GO"

# Aguarda todas as requisições.
wait

# -----------------------------------------------
# 6. Contabilização dos resultados
# -----------------------------------------------

confirmadas=0
rejeitadas=0
erros=0

echo
info "Resultados das requisições:"

for ((i=0; i<PASSAGEIROS; i++)); do
    status=$(cat "$DIR_TEMP/status-$i.txt")

    case "$status" in
        201)
            ((confirmadas+=1))
            echo "  Passageiro $i: CONFIRMADA"
            ;;
        409)
            ((rejeitadas+=1))
            echo "  Passageiro $i: REJEITADA (sem vaga)"
            ;;
        *)
            ((erros+=1))
            echo "  Passageiro $i: ERRO HTTP $status"
            cat "$DIR_TEMP/body-$i.json" 2>/dev/null || true
            ;;
    esac
done

# -----------------------------------------------
# 7. Verificação das reservas persistidas
# -----------------------------------------------

info "Verificando as reservas dos passageiros..."

for ((i=0; i<PASSAGEIROS; i++)); do
    api_json "$PORTA" GET \
        "/api/cliente/reservas" "" "${TOKENS[$i]}"

    exigir_status 200 "Consulta das reservas do passageiro $i"

    quantidade=$(jq 'length' <<< "$HTTP_BODY")

    status=$(cat "$DIR_TEMP/status-$i.txt")

    if [[ "$status" == "201" ]]; then
        [[ "$quantidade" -eq 1 ]] ||
            falhar "Passageiro vencedor não possui exatamente uma reserva."
    else
        [[ "$quantidade" -eq 0 ]] ||
            falhar "Passageiro rejeitado possui uma reserva inesperada."
    fi
done

# -----------------------------------------------
# 8. Relatório
# -----------------------------------------------

echo
echo "=============================================="
echo " RELATÓRIO - CONCORRÊNCIA"
echo "=============================================="

echo "Passageiros:             $PASSAGEIROS"
echo "Vagas disponíveis:       1"
echo "Reservas confirmadas:    $confirmadas"
echo "Reservas rejeitadas:     $rejeitadas"
echo "Erros HTTP/execução:     $erros"

echo

if [[ "$confirmadas" -eq 1 &&
      "$rejeitadas" -eq $((PASSAGEIROS - 1)) &&
      "$erros" -eq 0 ]]; then
    sucesso "TESTE APROVADO: nenhum assento foi reservado duas vezes."
else
    erro "TESTE REPROVADO: resultado diferente do esperado."
    exit 1
fi

echo
echo "Carona utilizada: $ID_CARONA"
