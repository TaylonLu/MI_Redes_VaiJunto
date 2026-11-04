
#!/usr/bin/env python3

import argparse
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

from pathlib import Path


BASE = Path(__file__).resolve().parent

CONFIG = json.loads(
    (BASE / "config.json").read_text(encoding="utf-8")
)


def executar(servidor, acao):

    controle = servidor.get(
        "controle",
        {"tipo": "docker"}
    )

    tipo = controle.get("tipo", "docker")

    # Controle Docker local
    if tipo == "docker":

        container = servidor.get("container")

        if not container:
            raise RuntimeError(
                f"Container não configurado para {servidor['id']}"
            )

        if acao == "status":
            comando = [
                "docker",
                "inspect",
                "--format",
                "{{.State.Status}}",
                container
            ]
        else:
            comando = [
                "docker",
                acao,
                container
            ]

        resultado = subprocess.run(
            comando,
            capture_output=True,
            text=True
        )

        if resultado.returncode:
            raise RuntimeError(
                resultado.stderr.strip()
                or "Comando Docker falhou"
            )

        return resultado.stdout.strip() or "ok"

    # Controle por agente remoto
    if tipo == "agente":

        url = (
                controle.get("url", "").rstrip("/")
                + "/v1/container"
        )

        token_env = controle.get(
            "token_env",
            "VAIJUNTO_AGENT_TOKEN"
        )

        token = os.environ.get(token_env)

        if not url.startswith(("http://", "https://")) or not token:
            raise RuntimeError(
                f"Configure URL do agente e variável {token_env}"
            )

        corpo = json.dumps({
            "servidor": servidor["id"],
            "acao": acao
        }).encode("utf-8")

        req = urllib.request.Request(
            url,
            data=corpo,
            method="POST",
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {token}"
            }
        )

        try:
            with urllib.request.urlopen(
                    req,
                    timeout=35
            ) as resposta:

                dados = json.loads(
                    resposta.read().decode("utf-8")
                )

                return dados.get("resultado", "ok")

        except urllib.error.HTTPError as erro:

            detalhe = erro.read().decode(
                "utf-8",
                errors="replace"
            )

            raise RuntimeError(
                f"Agente HTTP {erro.code}: {detalhe}"
            ) from erro

    raise RuntimeError(
        f"Tipo de controle desconhecido: {tipo}"
    )


def main():

    parser = argparse.ArgumentParser(
        description=__doc__
    )

    parser.add_argument(
        "servidor",
        help="Unova, Kalos ou Johto"
    )

    parser.add_argument(
        "acao",
        choices=("start", "stop", "restart", "status")
    )

    args = parser.parse_args()

    servidor = next(
        (
            s for s in CONFIG["servidores"]
            if s["id"].casefold() == args.servidor.casefold()
        ),
        None
    )

    if servidor is None:
        parser.error(
            f"Servidor desconhecido: {args.servidor}"
        )

    try:

        resultado = executar(
            servidor,
            args.acao
        )

        print(
            f"[OK] {servidor['id']} — "
            f"{args.acao}: {resultado}"
        )

    except (OSError, RuntimeError) as erro:

        print(
            f"[ERRO] {erro}",
            file=sys.stderr
        )

        return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
