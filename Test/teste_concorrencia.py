#!/usr/bin/env python3
"""Executa somente o teste de disputa concorrente por assentos."""
import argparse
from suporte import TestFailure, log, require, run_audit, run_concurrency, start_environment, wait_all

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--passageiros", type=int, default=20)
    parser.add_argument("--sem-build", action="store_true")
    args = parser.parse_args()
    try:
        require(args.passageiros >= 2, "Use pelo menos dois passageiros.")
        wait_all() if args.sem_build else start_environment()
        run_concurrency(args.passageiros, args.passageiros)
        log("TESTE DE CONCORRÊNCIA CONCLUÍDO.")
        return 0
    except (TestFailure, OSError, ValueError, KeyError) as exc:
        log(f"FALHA: {exc}")
        return 1

if __name__ == "__main__":
    raise SystemExit(main())
