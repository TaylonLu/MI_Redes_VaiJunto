#!/usr/bin/env python3

import hmac
import json
import os
import subprocess

from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


BASE = Path(__file__).resolve().parent

CONFIG = json.loads(
    (BASE / "agente-config.json").read_text(encoding="utf-8")
)

TOKEN = os.environ.get("VAIJUNTO_AGENT_TOKEN", "")

HOST = os.environ.get("VAIJUNTO_AGENT_HOST", "127.0.0.1")
PORT = int(os.environ.get("VAIJUNTO_AGENT_PORT", "8765"))

ACTIONS = {"start", "stop", "restart"}


class Handler(BaseHTTPRequestHandler):

    def responder(self, status, dados):
        corpo = json.dumps(
            dados,
            ensure_ascii=False
        ).encode("utf-8")

        self.send_response(status)
        self.send_header(
            "Content-Type",
            "application/json; charset=utf-8"
        )
        self.send_header("Content-Length", str(len(corpo)))
        self.end_headers()
        self.wfile.write(corpo)

    def do_GET(self):

        if self.path != "/health":
            return self.responder(
                404,
                {"erro": "rota inexistente"}
            )

        if not TOKEN:
            return self.responder(
                503,
                {"erro": "token não configurado"}
            )

        self.responder(200, {
            "status": "ok",
            "servicos": sorted(CONFIG["servidores"])
        })

    def do_POST(self):

        if self.path != "/v1/container":
            return self.responder(
                404,
                {"erro": "rota inexistente"}
            )

        recebido = self.headers.get("Authorization", "")

        if not TOKEN or not hmac.compare_digest(
            recebido,
            "Bearer " + TOKEN
        ):
            return self.responder(
                401,
                {"erro": "não autorizado"}
            )

        try:
            tamanho = int(
                self.headers.get("Content-Length", "0")
            )

            if tamanho < 1 or tamanho > 4096:
                return self.responder(
                    400,
                    {"erro": "tamanho inválido"}
                )

            pedido = json.loads(
                self.rfile.read(tamanho)
            )

            regiao = pedido.get("servidor")
            acao = pedido.get("acao")

            container = CONFIG["servidores"].get(regiao)

            if not container or acao not in ACTIONS | {"status"}:
                return self.responder(
                    400,
                    {"erro": "servidor ou ação inválida"}
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
                text=True,
                timeout=30
            )

            if resultado.returncode:
                return self.responder(
                    502,
                    {
                        "erro": resultado.stderr.strip()
                        or "Docker falhou"
                    }
                )

            self.responder(200, {
                "servidor": regiao,
                "acao": acao,
                "resultado": resultado.stdout.strip() or "ok"
            })

        except (ValueError, json.JSONDecodeError):
            self.responder(400, {"erro": "JSON inválido"})

        except subprocess.TimeoutExpired:
            self.responder(
                504,
                {"erro": "Docker excedeu o tempo limite"}
            )

        except Exception as erro:
            self.responder(
                500,
                {"erro": str(erro)}
            )

    def log_message(self, formato, *args):
        print("[AGENTE]", formato % args)


if __name__ == "__main__":

    if not TOKEN:
        raise SystemExit(
            "Defina VAIJUNTO_AGENT_TOKEN antes de iniciar."
        )

    print(f"Agente VaiJunto ouvindo em {HOST}:{PORT}")

    ThreadingHTTPServer(
        (HOST, PORT),
        Handler
    ).serve_forever()
