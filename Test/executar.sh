#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/comum.sh"

echo
info "======================================"
info " TESTES AUTOMATIZADOS - VAIJUNTO"
info "======================================"

# Confere se a rede está disponível.
verificar_rede

# Executa os testes.
bash "$SCRIPT_DIR/teste-health.sh"

echo
sucesso "Execução finalizada."
