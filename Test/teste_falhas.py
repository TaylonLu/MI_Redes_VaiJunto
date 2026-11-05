#!/usr/bin/env python3
"""Executa somente os testes de falha e recuperação do 2PC."""
from suporte import TestFailure, log, run_audit, run_failure_commit, run_failure_prepare, start_environment, wait_all
import argparse

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sem-build", action="store_true")
    args = parser.parse_args()
    try:
        wait_all() if args.sem_build else start_environment()
        run_failure_prepare()
        run_failure_commit()
        run_audit()
        log("TESTE DE FALHAS CONCLUÍDO.")
        return 0
    except (TestFailure, OSError, ValueError, KeyError) as exc:
        log(f"FALHA: {exc}")
        return 1

if __name__ == "__main__":
    raise SystemExit(main())
