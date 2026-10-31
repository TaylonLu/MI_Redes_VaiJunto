#!/usr/bin/env bash

# Configurações comuns dos testes
readonly PORTAS=(2602 2603 2604 2605)
readonly SERVIDORES=(unova kalos johto kanto)

BASE_URL="${BASE_URL:-http://localhost}"

# Cores para o terminal
VERDE='\033[0;32m'
VERMELHO='\033[0;31m'
AMARELO='\033[0;33m'
NORMAL='\033[0m'

info() {
    echo -e "${AMARELO}[INFO]${NORMAL} $*"
}

sucesso() {
    echo -e "${VERDE}[OK]${NORMAL} $*"
}

erro() {
    echo -e "${VERMELHO}[ERRO]${NORMAL} $*" >&2
}

falhar() {
    erro "$*"
    exit 1
}

# Consulta o endpoint de saúde de um servidor.
health() {
    local porta="$1"

    curl --silent --show-error --fail \
        --connect-timeout 2 \
        "$BASE_URL:$porta/api/consorcio/health"
}

# Aguarda um servidor ficar disponível.
aguardar_servidor() {
    local nome="$1"
    local porta="$2"
    local tentativas="${3:-30}"

    info "Aguardando $nome na porta $porta..."

    for ((i = 1; i <= tentativas; i++)); do
        if resposta=$(health "$porta" 2>/dev/null); then
            if jq -e '.status and (.relogio != null)' \
                >/dev/null <<< "$resposta"; then
                sucesso "$nome está disponível."
                return 0
            fi
        fi

        sleep 1
    done

    falhar "$nome não respondeu após $tentativas segundos."
}

# Verifica se todos os servidores estão disponíveis.
verificar_rede() {
    for i in "${!PORTAS[@]}"; do
        aguardar_servidor "${SERVIDORES[$i]}" "${PORTAS[$i]}"
    done
}

# Consulta o estado de uma transação.
consultar_transacao() {
    local porta="$1"
    local id="$2"

    curl --silent --show-error \
        --connect-timeout 2 \
        "$BASE_URL:$porta/api/consorcio/reservas/$id/status"
}
