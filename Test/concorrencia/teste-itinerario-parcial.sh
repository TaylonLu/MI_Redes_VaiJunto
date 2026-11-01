#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../comum.sh"

SUFIXO="$(date +%s)-$$"

echo
info "=============================================="
info " TESTE 2 - ATOMICIDADE DO ITINERÁRIO (2PC)"
info "=============================================="

for porta in 2602 2603 2604; do
    aguardar_servidor "Servidor $porta" "$porta"
done

# -----------------------------------------------
# Cadastro de usuário
# -----------------------------------------------

cadastrar_usuario() {
    local porta="$1"
    local identificador="$2"

    local payload
    payload=$(jq -n \
        --arg email "$identificador-$SUFIXO@teste.local" \
        --arg nome "$identificador" \
        '{
            email:$email,
            senha:"Teste123!",
            nome:$nome
        }')

    api_json "$porta" POST "/api/cliente/cadastro" "$payload"
    exigir_status 200 "Cadastro de $identificador"

    TOKEN_NOVO=$(jq -r '.token // empty' <<< "$HTTP_BODY")
    ID_NOVO=$(jq -r '.usuario.id // empty' <<< "$HTTP_BODY")

    [[ -n "$TOKEN_NOVO" ]] || falhar "Token ausente para $identificador."
}

# -----------------------------------------------
# Criação de motorista e carona
# -----------------------------------------------

criar_carona() {
    local porta="$1"
    local nome="$2"
    local origem="$3"
    local destino="$4"

    info "Criando motorista em $nome..."

    cadastrar_usuario "$porta" "motorista-$nome"

    local token_motorista="$TOKEN_NOVO"
    local id_motorista="$ID_NOVO"

    local cnh
    cnh=$(printf '%011d' "$(( $(date +%s) % 10000000000 + $$ + porta ))")

    local payload
    payload=$(jq -n \
        --arg cnh "$cnh" \
        '{
            cnh:$cnh,
            placaCarro:"ABC1D23",
            modeloCarro:"Carro de Teste",
            corCarro:"Azul"
        }')

    api_json "$porta" POST \
        "/api/cliente/cadastro/motorista" \
        "$payload" "$token_motorista"

    exigir_status 200 "Perfil de motorista em $nome"

    local data
    data=$(date -d "+2 days" "+%Y-%m-%dT%H:%M:%S")

    payload=$(jq -n \
        --arg id "$id_motorista" \
        --arg data "$data" \
        --argjson origem "$origem" \
        --argjson destino "$destino" \
        '{
            idMotorista:$id,
            vagas:1,
            valorPorTrecho:10.0,
            dataPartida:$data,
            trechos:[
                {origem:$origem,destino:$destino}
            ]
        }')

    api_json "$porta" POST \
        "/api/cliente/caronas" "$payload" "$token_motorista"

    exigir_status 201 "Criação de carona em $nome"

    CARONA_NOVA=$(jq -r '.id // empty' <<< "$HTTP_BODY")

    [[ -n "$CARONA_NOVA" ]] ||
        falhar "ID da carona ausente em $nome."

    sucesso "$nome: $CARONA_NOVA"
}

# -----------------------------------------------
# Reserva de um trecho local
# -----------------------------------------------

reservar_local() {
    local porta="$1"
    local servidor="$2"
    local carona="$3"
    local origem="$4"
    local destino="$5"
    local token="$6"

    local payload
    payload=$(jq -n \
        --arg servidor "$servidor" \
        --arg carona "$carona" \
        --arg origem "$origem" \
        --arg destino "$destino" \
        '{
            trechos_solicitados:[
                {
                    servidor:$servidor,
                    id_carona:($carona + "_" + $origem + "_" + $destino)
                }
            ]
        }')

    api_json "$porta" POST \
        "/api/cliente/reservas" "$payload" "$token"
}

# -----------------------------------------------
# 1. Criar as três caronas
# -----------------------------------------------

info "Criando caronas com uma vaga em cada servidor..."

criar_carona 2602 "Unova" 101 102
CARONA_UNOVA="$CARONA_NOVA"

criar_carona 2603 "Kalos" 108 211
CARONA_KALOS="$CARONA_NOVA"

criar_carona 2604 "Johto" 208 310
CARONA_JOHTO="$CARONA_NOVA"

# -----------------------------------------------
# 2. Ocupar previamente a vaga de Johto
# -----------------------------------------------

info "Ocupando a única vaga de Johto..."

cadastrar_usuario 2604 "passageiro-bloqueio"
TOKEN_BLOQUEIO="$TOKEN_NOVO"

reservar_local 2604 "localhost:2604" \
    "$CARONA_JOHTO" 208 310 "$TOKEN_BLOQUEIO"

exigir_status 201 "Reserva bloqueadora em Johto"

sucesso "Johto agora está sem vagas."

# -----------------------------------------------
# 3. Criar o passageiro da reserva distribuída
# -----------------------------------------------

info "Cadastrando passageiro da reserva distribuída..."

cadastrar_usuario 2602 "passageiro-distribuido"
TOKEN_DISTRIBUIDO="$TOKEN_NOVO"

# -----------------------------------------------
# 4. Solicitar o itinerário de três servidores
# -----------------------------------------------

info "Solicitando reserva distribuída..."

payload=$(jq -n \
    --arg unova "$CARONA_UNOVA" \
    --arg kalos "$CARONA_KALOS" \
    --arg johto "$CARONA_JOHTO" \
    '{
        trechos_solicitados:[
            {
                servidor:"localhost:2602",
                id_carona:($unova + "_101_102")
            },
            {
                servidor:"localhost:2603",
                id_carona:($kalos + "_108_211")
            },
            {
                servidor:"localhost:2604",
                id_carona:($johto + "_208_310")
            }
        ]
    }')

api_json 2602 POST \
    "/api/cliente/reservas" \
    "$payload" "$TOKEN_DISTRIBUIDO"

echo
info "Resposta da reserva distribuída:"
jq . <<< "$HTTP_BODY" || echo "$HTTP_BODY"

if [[ "$HTTP_STATUS" != "409" ]]; then
    falhar "Esperado HTTP 409 pela falta de vaga em Johto; recebido HTTP $HTTP_STATUS."
fi

exigir_json \
    '.status == "RECUSADO"' \
    "Reserva distribuída foi recusada"

exigir_json \
    '(.motivo // "") | test("sem assento"; "i")' \
    "Recusa ocorreu por falta de vaga em Johto"

# -----------------------------------------------
# 5. Verificar se o passageiro não recebeu reserva
# -----------------------------------------------

info "Verificando reservas do passageiro distribuído..."

api_json 2602 GET \
    "/api/cliente/reservas" "" "$TOKEN_DISTRIBUIDO"

exigir_status 200 "Consulta das reservas"

exigir_json \
    'length == 0' \
    "Nenhuma reserva parcial foi confirmada"

# -----------------------------------------------
# 6. Verificar se Unova liberou a vaga
# -----------------------------------------------

info "Testando novamente a vaga de Unova..."

cadastrar_usuario 2602 "passageiro-verificacao-unova"
TOKEN_VERIFICACAO_UNOVA="$TOKEN_NOVO"

reservar_local 2602 "localhost:2602" \
    "$CARONA_UNOVA" 101 102 "$TOKEN_VERIFICACAO_UNOVA"

exigir_status 201 "Vaga de Unova liberada após rollback"

# -----------------------------------------------
# 7. Verificar se Kalos liberou a vaga
# -----------------------------------------------

info "Testando novamente a vaga de Kalos..."

cadastrar_usuario 2603 "passageiro-verificacao-kalos"
TOKEN_VERIFICACAO_KALOS="$TOKEN_NOVO"

reservar_local 2603 "localhost:2603" \
    "$CARONA_KALOS" 108 211 "$TOKEN_VERIFICACAO_KALOS"

exigir_status 201 "Vaga de Kalos liberada após rollback"

# -----------------------------------------------
# 8. Verificar que a reserva bloqueadora continua ativa
# -----------------------------------------------

info "Verificando reserva original de Johto..."

api_json 2604 GET \
    "/api/cliente/reservas" "" "$TOKEN_BLOQUEIO"

exigir_status 200 "Consulta das reservas do passageiro bloqueador"

exigir_json \
    'length == 1 and .[0].status == "ATIVA"' \
    "Reserva original de Johto continua ativa"

# -----------------------------------------------
# 9. Relatório
# -----------------------------------------------

echo
echo "=============================================="
echo " RELATÓRIO - ATOMICIDADE 2PC"
echo "=============================================="

echo "Servidores envolvidos:       3"
echo "Trechos solicitados:         3"
echo "Vagas iniciais:              3"
echo "Vagas ocupadas inicialmente: 1 (Johto)"
echo "Reserva distribuída:        RECUSADA"
echo "Reserva parcial:             NÃO"
echo "Vaga Unova liberada:         SIM"
echo "Vaga Kalos liberada:         SIM"
echo "Reserva de Johto preservada: SIM"

echo
sucesso "TESTE APROVADO: rollback distribuído executado corretamente."
