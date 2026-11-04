#!/usr/bin/env python3

import json
import sys
import urllib.error
import urllib.request
from pathlib import Path


BASE = Path(__file__).resolve().parent

CONFIG = json.loads(
    (BASE / "config.json").read_text(encoding="utf-8")
)

TIMEOUT = CONFIG.get("timeout", 15)


def verificar_servidor(servidor):
    print("Verificando servidor: " + servidor["id"])
    nome = servidor["id"]
    url = servidor["url"].rstrip("/") + "/api/consorcio/health"

    requisicao = urllib.request.Request(
        url,
        headers={"Accept": "application/json"}
    )

    try:
        with urllib.request.urlopen(
                requisicao,
                timeout=TIMEOUT
        ) as resposta:
            dados = json.loads(
                resposta.read().decode("utf-8")
            )

        if not isinstance(dados, dict):
            raise ValueError("Resposta não é um objeto JSON.")

        if not dados.get("status") or dados.get("relogio") is None:
            raise ValueError(
                "Resposta não contém status ou relogio válido."
            )

        print(f"[OK] {nome} respondeu.")
        print(f"     Status:  {dados['status']}")
        print(f"     Relógio: {dados['relogio']}")

        return True

    except urllib.error.HTTPError as erro:
        print(f"[ERRO] {nome}: HTTP {erro.code}")
    except urllib.error.URLError as erro:
        print(f"[ERRO] {nome}: falha de conexão: {erro.reason}")
    except (TimeoutError, ValueError, json.JSONDecodeError) as erro:
        print(f"[ERRO] {nome}: {erro}")
    except OSError as erro:
        print(f"[ERRO] {nome}: {erro}")

    return False


def main():
    print("========== TESTE DE DISPONIBILIDADE ==========")

    servidores = CONFIG["servidores"]
    resultados = [
        verificar_servidor(servidor)
        for servidor in servidores
    ]

    total = len(resultados)
    aprovados = sum(resultados)

    print("\n========== RESULTADO ==========")
    print(f"Servidores disponíveis: {aprovados}/{total}")

    if aprovados != total:
        print("[FALHA] Nem todos os servidores responderam.")
        return 1

    print("[OK] Todos os servidores estão funcionando.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
