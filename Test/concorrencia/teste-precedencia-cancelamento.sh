
#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

source "$SCRIPT_DIR/../comum.sh"

COMPOSE=(docker compose -f "$ROOT_DIR/compose_1.yaml")

SUFIXO="$(date +%s)-$$"

KALOS_PARADO=0
JOHTO_PARADO=0

# Garante que os servidores sejam iniciados mesmo se o teste falhar.
limpar() {
    if (( KALOS_PARADO )); then
        info "Restaurando Kalos..."
        "${COMPOSE[@]}" start kalos >/dev/null 2>&1 || true
    fi

    if (( JOHTO_PARADO )); then
        info "Restaurando Johto..."
        "${COMPOSE[@]}" start johto >/dev/null 2>&1 || true
    fi
}

trap limpar EXIT

echo
info "=============================================="
info " TESTE - PRECEDÊNCIA DO CANCELAMENTO MOTORISTA"
info "=============================================="

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
            email: $email,
            senha: "Teste123!",
            nome: $nome
        }')

    api_json "$porta" POST "/api/cliente/cadastro" "$payload"
    exigir_status 200 "Cadastro de $identificador"

    TOKEN_NOVO=$(jq -r '.token // empty' <<< "$HTTP_BODY")
    ID_NOVO=$(jq -r '.usuario.id // empty' <<< "$HTTP_BODY")

    [[ -n "$TOKEN_NOVO" ]] ||
        falhar "Token ausente para $identificador."

    [[ -n "$ID_NOVO" ]] ||
        falhar "ID ausente para $identificador."
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
    cnh=$(printf '%011d' "$(( ($(date +%s) + porta) % 100000000000 ))")

    local payload
    payload=$(jq -n \
        --arg cnh "$cnh" \
        '{
            cnh: $cnh,
            placaCarro: "ABC1D23",
            modeloCarro: "Carro de Teste",
            corCarro: "Azul"
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
            idMotorista: $id,
            vagas: 1,
            valorPorTrecho: 10.0,
            dataPartida: $data,
            trechos: [
                {origem: $origem, destino: $destino}
            ]
        }')

    api_json "$porta" POST \
        "/api/cliente/caronas" "$payload" "$token_motorista"

    exigir_status 201 "Criação da carona em $nome"

    CARONA_NOVA=$(jq -r '.id // empty' <<< "$HTTP_BODY")

    [[ -n "$CARONA_NOVA" ]] ||
        falhar "ID da carona ausente em $nome."

    sucesso "$nome: carona $CARONA_NOVA"

    TOKEN_MOTORISTA_NOVO="$token_motorista"
}

# -----------------------------------------------
# Consulta e espera por estado de transação
# -----------------------------------------------

estado_transacao() {
    local porta="$1"
    local id="$2"

    local resposta
    resposta=$(curl --silent \
        --connect-timeout 2 \
        "$BASE_URL:$porta/api/consorcio/reservas/$id/status" \
        2>/dev/null || true)

    jq -r '.status // empty' <<< "$resposta" 2>/dev/null || true
}

aguardar_estado() {
    local porta="$1"
    local id="$2"
    local esperado="$3"
    local tentativas="${4:-30}"

    info "Aguardando transação em $esperado no servidor $porta..."

    for ((i = 1; i <= tentativas; i++)); do
        local atual
        atual=$(estado_transacao "$porta" "$id")

        if [[ "$atual" == "$esperado" ]]; then
            sucesso "Transação em $esperado no servidor $porta."
            return 0
        fi

        sleep 1
    done

    falhar "Transação não chegou a $esperado no servidor $porta. Estado atual: $(estado_transacao "$porta" "$id")"
}

# -----------------------------------------------
# Reserva local para verificar liberação de vaga
# -----------------------------------------------

reservar_local() {
    local porta="$1"
    local carona="$2"
    local origem="$3"
    local destino="$4"
    local token="$5"

    local payload
    payload=$(jq -n \
        --arg servidor "localhost:$porta" \
        --arg carona "$carona" \
        --argjson origem "$origem" \
        --argjson destino "$destino" \
        '{
            trechos: [
                {
                    servidor: $servidor,
                    id_carona: $carona,
                    origem: $origem,
                    destino: $destino
                }
            ]
        }')

    api_json "$porta" POST \
        "/api/cliente/reservas" "$payload" "$token"

    exigir_status 201 "Nova reserva local em $porta"

    exigir_json \
        '.status == "CONFIRMADO"' \
        "Nova reserva confirmada em $porta"
}

# -----------------------------------------------
# 1. Verificar os servidores
# -----------------------------------------------

for porta in 2602 2603 2604; do
    aguardar_servidor "Servidor $porta" "$porta"
done

# -----------------------------------------------
# 2. Criar as caronas
# -----------------------------------------------

info "Criando uma carona em cada servidor..."

criar_carona 2602 "Unova" 101 102
CARONA_UNOVA="$CARONA_NOVA"
TOKEN_MOTORISTA_UNOVA="$TOKEN_MOTORISTA_NOVO"

criar_carona 2603 "Kalos" 108 211
CARONA_KALOS="$CARONA_NOVA"

criar_carona 2604 "Johto" 208 310
CARONA_JOHTO="$CARONA_NOVA"

# -----------------------------------------------
# 3. Cadastrar passageiro e fazer reserva
# -----------------------------------------------

info "Cadastrando passageiro em Unova..."

cadastrar_usuario 2602 "passageiro-distribuido"
TOKEN_PASSAGEIRO="$TOKEN_NOVO"

info "Criando reserva distribuída..."

payload=$(jq -n \
    --arg unova "$CARONA_UNOVA" \
    --arg kalos "$CARONA_KALOS" \
    --arg johto "$CARONA_JOHTO" \
    '{
        trechos: [
            {
                servidor: "localhost:2602",
                id_carona: $unova,
                origem: 101,
                destino: 102
            },
            {
                servidor: "localhost:2603",
                id_carona: $kalos,
                origem: 108,
                destino: 211
            },
            {
                servidor: "localhost:2604",
                id_carona: $johto,
                origem: 208,
                destino: 310
            }
        ]
    }')

api_json 2602 POST \
    "/api/cliente/reservas" \
    "$payload" "$TOKEN_PASSAGEIRO"

exigir_status 201 "Reserva distribuída"

exigir_json \
    '.status == "CONFIRMADO" and (.id_transacao | length > 0)' \
    "Reserva distribuída confirmada"

ID_TRANSACAO=$(jq -r '.id_transacao' <<< "$HTTP_BODY")

sucesso "Transação criada: $ID_TRANSACAO"

aguardar_estado 2603 "$ID_TRANSACAO" COMITADO
aguardar_estado 2604 "$ID_TRANSACAO" COMITADO

# -----------------------------------------------
# 4. Interromper Johto
# -----------------------------------------------

info "Interrompendo Johto para simular falha de comunicação..."

"${COMPOSE[@]}" stop johto
JOHTO_PARADO=1

# -----------------------------------------------
# 5. Injetar CANCELANDO em Kalos
# -----------------------------------------------

info "Interrompendo Kalos para preparar o estado de teste..."

"${COMPOSE[@]}" stop kalos
KALOS_PARADO=1

ARQUIVO="$ROOT_DIR/data/2603/transacoes.json"

[[ -f "$ARQUIVO" ]] ||
    falhar "Arquivo de transações de Kalos não encontrado: $ARQUIVO"

jq -e --arg id "$ID_TRANSACAO" \
    '.[$id] != null and .[$id].estado == "COMITADO"' \
    "$ARQUIVO" >/dev/null ||
    falhar "Transação não encontrada ou não está COMITADO no arquivo de Kalos."

TEMP=$(mktemp "${ARQUIVO}.XXXXXX")

jq --arg id "$ID_TRANSACAO" \
    '.[$id].estado = "CANCELANDO"' \
    "$ARQUIVO" > "$TEMP"

mv "$TEMP" "$ARQUIVO"

sucesso "Estado CANCELANDO gravado em Kalos."

# Reinicia Kalos. Johto continua indisponível.
"${COMPOSE[@]}" start kalos
KALOS_PARADO=0

aguardar_servidor "Kalos" 2603

# Aguarda pelo menos uma tentativa do recuperador.
sleep 7

estado=$(estado_transacao 2603 "$ID_TRANSACAO")

[[ "$estado" == "CANCELANDO" ]] ||
    falhar "Kalos deveria continuar em CANCELANDO, mas está em '$estado'."

sucesso "Kalos permaneceu em CANCELANDO enquanto Johto estava indisponível."

# -----------------------------------------------
# 6. Cancelar a carona pelo motorista
# -----------------------------------------------

info "Solicitando cancelamento da carona pelo motorista..."

api_json 2602 DELETE \
    "/api/cliente/caronas/$CARONA_UNOVA" \
    "" "$TOKEN_MOTORISTA_UNOVA"

exigir_status 200 "Cancelamento da carona em Unova"

# A notificação é assíncrona.
aguardar_estado 2603 "$ID_TRANSACAO" CANCELADO_PELO_MOTORISTA

# -----------------------------------------------
# 7. Restaurar Johto e aguardar propagação
# -----------------------------------------------

info "Restaurando Johto..."

"${COMPOSE[@]}" start johto
JOHTO_PARADO=0

aguardar_servidor "Johto" 2604

aguardar_estado 2604 "$ID_TRANSACAO" CANCELADO_PELO_MOTORISTA 40

# -----------------------------------------------
# 8. Verificar liberação dos assentos remotos
# -----------------------------------------------

info "Verificando se Kalos liberou sua vaga..."

cadastrar_usuario 2603 "passageiro-verificacao-kalos"
TOKEN_VERIFICACAO_KALOS="$TOKEN_NOVO"

reservar_local 2603 "$CARONA_KALOS" 108 211 \
    "$TOKEN_VERIFICACAO_KALOS"

info "Verificando se Johto liberou sua vaga..."

cadastrar_usuario 2604 "passageiro-verificacao-johto"
TOKEN_VERIFICACAO_JOHTO="$TOKEN_NOVO"

reservar_local 2604 "$CARONA_JOHTO" 208 310 \
    "$TOKEN_VERIFICACAO_JOHTO"

# -----------------------------------------------
# 9. Relatório
# -----------------------------------------------

echo
echo "=============================================="
echo " RELATÓRIO - CANCELAMENTO DISTRIBUÍDO"
echo "=============================================="

echo "Transação:                   $ID_TRANSACAO"
echo "Estado intermediário Kalos:  CANCELANDO"
echo "Johto indisponível:          SIM"
echo "Estado final Kalos:          CANCELADO_PELO_MOTORISTA"
echo "Estado final Johto:          CANCELADO_PELO_MOTORISTA"
echo "Vaga liberada em Kalos:      SIM"
echo "Vaga liberada em Johto:      SIM"

echo
sucesso "TESTE APROVADO: cancelamento do motorista prevaleceu sobre CANCELANDO."
