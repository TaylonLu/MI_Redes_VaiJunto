#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/comum.sh"

echo
info "======================================"
info " TESTES AUTOMATIZADOS - VAIJUNTO"
info "======================================"

verificar_rede

bash "$SCRIPT_DIR/teste-health.sh"
bash "$SCRIPT_DIR/teste-cadastro-carona.sh"

echo
sucesso "Execução finalizada."