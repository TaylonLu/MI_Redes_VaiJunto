#!/usr/bin/env python3
"""Executa somente o teste de carga distribuída."""
import argparse
from suporte import TestFailure, log, require, run_audit, run_load, start_environment, wait_all

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--passageiros", type=int, default=50)
    parser.add_argument("--threads", type=int, default=30)
    parser.add_argument("--sem-build", action="store_true")
    args = parser.parse_args()
    try:
        require(args.passageiros > 0 and args.threads > 0, "Passageiros e threads devem ser positivos.")
        wait_all() if args.sem_build else start_environment()
        run_load(args.passageiros, args.threads)
        run_audit()
        log("TESTE DE CARGA CONCLUÍDO.")
        return 0
    except (TestFailure, OSError, ValueError, KeyError) as exc:
        log(f"FALHA: {exc}")
        return 1

if __name__ == "__main__":
    raise SystemExit(main())
