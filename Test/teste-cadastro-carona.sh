
#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/comum.sh"

PORTA=2602

echo
info "======================================"
info " TESTE 2 - CADASTRO E CRIAÇÃO DE CARONA"
info "======================================"

# Identificador único para evitar colisões entre execuções.
SUFIXO="$(date +%s)-$$"

EMAIL_MOTORISTA="motorista-${SUFIXO}@teste.local"
EMAIL_PASSAGEIRO="passageiro-${SUFIXO}@teste.local"

# ---------- Cadastro do motorista ----------

info "Cadastrando motorista..."

payload=$(jq -n \
    --arg email "$EMAIL_MOTORISTA" \
    --arg senha "Teste123!" \
    --arg nome "Motorista Teste" \
    '{email: $email, senha: $senha, nome: $nome}')

api_json "$PORTA" POST "/api/cliente/cadastro" "$payload"
exigir_status 200 "Cadastro do motorista"

TOKEN_MOTORISTA=$(jq -r '.token // empty' <<< "$HTTP_BODY")
ID_MOTORISTA=$(jq -r '.usuario.id // empty' <<< "$HTTP_BODY")

[[ -n "$TOKEN_MOTORISTA" ]] ||
    falhar "Cadastro não retornou token do motorista."

[[ -n "$ID_MOTORISTA" ]] ||
    falhar "Cadastro não retornou ID do motorista."

# ---------- Cadastro do perfil de motorista ----------

info "Cadastrando perfil de motorista..."

payload=$(jq -n \
    --arg cnh "12345678901" \
    --arg placa "ABC1D23" \
    --arg modelo "Carro de Teste" \
    --arg cor "Azul" \
    '{
        cnh: $cnh,
        placaCarro: $placa,
        modeloCarro: $modelo,
        corCarro: $cor
    }')

api_json "$PORTA" POST \
    "/api/cliente/cadastro/motorista" \
    "$payload" "$TOKEN_MOTORISTA"

exigir_status 200 "Cadastro do perfil de motorista"

exigir_json '.perfilMotorista != null' \
    "Perfil de motorista retornado"

# ---------- Criação da carona ----------

info "Criando carona em Unova..."

DATA_PARTIDA=$(date -d "+2 days" "+%Y-%m-%dT%H:%M:%S")

payload=$(jq -n \
    --arg id "$ID_MOTORISTA" \
    --arg data "$DATA_PARTIDA" \
    '{
        idMotorista: $id,
        vagas: 3,
        valorPorTrecho: 15.50,
        dataPartida: $data,
        trechos: [
            {origem: 101, destino: 102},
            {origem: 102, destino: 103}
        ]
    }')

api_json "$PORTA" POST "/api/cliente/caronas" \
    "$payload" "$TOKEN_MOTORISTA"

exigir_status 201 "Criação da carona"

ID_CARONA=$(jq -r '.id // empty' <<< "$HTTP_BODY")

[[ -n "$ID_CARONA" ]] ||
    falhar "A resposta da criação não contém o ID da carona."

sucesso "Carona criada: $ID_CARONA"

# ---------- Consulta das caronas do motorista ----------

info "Consultando caronas do motorista..."

api_json "$PORTA" GET "/api/cliente/caronas" "" "$TOKEN_MOTORISTA"

exigir_status 200 "Consulta das caronas"

if jq -e --arg id "$ID_CARONA" \
    'any(.[]; .id == $id)' >/dev/null <<< "$HTTP_BODY"; then
    sucesso "Carona encontrada na lista do motorista."
else
    falhar "Carona não encontrada na lista do motorista."
fi

# ---------- Cadastro do passageiro ----------

info "Cadastrando passageiro..."

payload=$(jq -n \
    --arg email "$EMAIL_PASSAGEIRO" \
    --arg senha "Teste123!" \
    --arg nome "Passageiro Teste" \
    '{email: $email, senha: $senha, nome: $nome}')

api_json "$PORTA" POST "/api/cliente/cadastro" "$payload"

exigir_status 200 "Cadastro do passageiro"

TOKEN_PASSAGEIRO=$(jq -r '.token // empty' <<< "$HTTP_BODY")

[[ -n "$TOKEN_PASSAGEIRO" ]] ||
    falhar "Cadastro não retornou token do passageiro."

# ---------- Consulta inicial das reservas ----------

info "Consultando reservas do passageiro..."

api_json "$PORTA" GET "/api/cliente/reservas" "" "$TOKEN_PASSAGEIRO"

exigir_status 200 "Consulta das reservas"

exigir_json 'type == "array" and length == 0' \
    "Passageiro ainda não possui reservas"

echo
sucesso "TESTE 2 CONCLUÍDO."
echo
info "Dados criados:"
echo "  Motorista:  $EMAIL_MOTORISTA"
echo "  Passageiro: $EMAIL_PASSAGEIRO"
echo "  Carona:     $ID_CARONA"

# ---------- Reserva local (2PC) ----------

info "Testando reserva local em Unova..."

# Descobre o identificador real do servidor.
api_json "$PORTA" GET "/api/consorcio/health"

exigir_status 200 "Consulta de identificação do servidor"

SERVIDOR=$(jq -r '.status | split(" ")[0]' <<< "$HTTP_BODY")

# Solicita a carona inteira: cidade 101 até cidade 103.
payload=$(jq -n \
    --arg servidor "$SERVIDOR" \
    --arg id "$ID_CARONA" \
    '{
        trechos_solicitados: [
            {
                servidor: $servidor,
                id_carona: ($id + "_101_103")
            }
        ]
    }')

api_json "$PORTA" POST "/api/cliente/reservas" \
    "$payload" "$TOKEN_PASSAGEIRO"

exigir_status 201 "Reserva local"

exigir_json \
    '.status == "CONFIRMADO" and (.id_transacao | length > 0)' \
    "Transação confirmada pelo coordenador"

ID_TRANSACAO=$(jq -r '.id_transacao' <<< "$HTTP_BODY")

# ---------- Verificação da reserva ----------

info "Consultando reservas após o commit..."

api_json "$PORTA" GET "/api/cliente/reservas" "" "$TOKEN_PASSAGEIRO"

exigir_status 200 "Consulta das reservas após o commit"

exigir_json \
    'length == 1 and .[0].status == "ATIVA"' \
    "Reserva aparece como ativa"

sucesso "Transação confirmada: $ID_TRANSACAO"