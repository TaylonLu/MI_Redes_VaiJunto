#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/comum.sh"

echo
info "======================================"
info " TESTE 1 - DISPONIBILIDADE DOS SERVIDORES"
info "======================================"

for i in "${!PORTAS[@]}"; do
    nome="${SERVIDORES[$i]}"
    porta="${PORTAS[$i]}"

    resposta=$(health "$porta") || falhar "$nome não respondeu."

    status=$(jq -r '.status' <<< "$resposta")
    relogio=$(jq -r '.relogio' <<< "$resposta")

    sucesso "$nome respondeu."
    echo "  Status:  $status"
    echo "  Relógio: $relogio"
done

echo
sucesso "Todos os servidores estão funcionando."
