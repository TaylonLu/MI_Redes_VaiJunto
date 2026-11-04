
import argparse
import concurrent.futures
import json
import random
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

from collections import Counter
from datetime import date
from pathlib import Path

from datetime import date, timedelta

DATA_CARONAS = (date.today() + timedelta(days=1)).isoformat()


BASE = Path(__file__).resolve().parent

CONFIG = json.loads(
    (BASE / "config.json").read_text(encoding="utf-8")
)

SERVIDORES = CONFIG["servidores"]
TIMEOUT = CONFIG.get("timeout", 15)

# Acesso direto à rede, sem utilizar proxies do ambiente.
OPENER = urllib.request.build_opener(
    urllib.request.ProxyHandler({})
)

ESTATISTICAS = Counter()
LOCK = threading.Lock()

# Mapeamento global: nome da cidade -> ID numérico.
CIDADES = {}


def incrementar(chave, quantidade=1):
    with LOCK:
        ESTATISTICAS[chave] += quantidade


def requisicao(url, metodo="GET", dados=None, token=None):
    headers = {
        "Accept": "application/json"
    }

    corpo = None

    if dados is not None:
        headers["Content-Type"] = "application/json"
        corpo = json.dumps(dados).encode("utf-8")

    if token:
        headers["Authorization"] = f"Bearer {token}"

    req = urllib.request.Request(
        url,
        data=corpo,
        headers=headers,
        method=metodo
    )

    try:
        with OPENER.open(req, timeout=TIMEOUT) as resposta:
            texto = resposta.read().decode("utf-8")
            resultado = json.loads(texto) if texto else None
            return resposta.status, resultado

    except urllib.error.HTTPError as erro:
        texto = erro.read().decode("utf-8", errors="replace")

        try:
            resultado = json.loads(texto)
        except json.JSONDecodeError:
            resultado = texto

        return erro.code, resultado

    except Exception as erro:
        return 0, {"erro": str(erro)}


def carregar_cidades():
    """Obtém os IDs das cidades disponibilizados pelos servidores."""

    for servidor in SERVIDORES:
        url = servidor["url"].rstrip("/") + "/api/servidor/cidades"

        status, resposta = requisicao(url)

        if status != 200 or not isinstance(resposta, dict):
            raise RuntimeError(
                f"Não foi possível carregar as cidades de "
                f"{servidor['id']}: HTTP {status}"
            )

        for id_cidade, nome in resposta.items():
            chave = nome.strip().casefold()
            CIDADES[chave] = int(id_cidade)

    print(f"[OK] {len(CIDADES)} cidades carregadas.")


def id_cidade(nome):
    resultado = CIDADES.get(nome.strip().casefold())

    if resultado is None:
        raise ValueError(f"Cidade não encontrada: {nome}")

    return resultado


def criar_passageiro(servidor, numero):
    identificador = uuid.uuid4().hex

    cadastro = {
        "nome": f"Passageiro {numero}",
        "email": f"teste_{identificador}@exemplo.com",
        "senha": f"Teste_{identificador}"
    }

    status, resposta = requisicao(
        servidor["url"].rstrip("/") + "/api/cliente/cadastro",
        "POST",
        cadastro
    )

    if status != 200 or not isinstance(resposta, dict):
        incrementar("cadastros_com_erro")
        return None

    usuario = resposta.get("usuario") or {}
    token = resposta.get("token")
    id_usuario = usuario.get("id")

    if not token or not id_usuario:
        incrementar("cadastros_com_erro")
        return None

    incrementar("cadastros")

    return {
        "token": token,
        "id": id_usuario
    }


def buscar(servidor, token, origem, destino, data, vagas):
    parametros = urllib.parse.urlencode({
        "origem": origem,
        "destino": destino,
        "data": data,
        "vagas": vagas
    })

    url = (
            servidor["url"].rstrip("/")
            + "/api/cliente/itinerarios?"
            + parametros
    )

    status, resposta = requisicao(url, token=token)

    if status != 200 or not isinstance(resposta, dict):
        incrementar("buscas_com_erro")
        return []

    incrementar("buscas")

    itinerarios = resposta.get("itinerarios") or []

    if itinerarios:
        incrementar("buscas_com_itinerario")
        incrementar("itinerarios_encontrados", len(itinerarios))
    else:
        incrementar("buscas_sem_itinerario")

    return itinerarios


def preparar_trechos(itinerario):
    """
    Converte os trechos encontrados para o formato
    esperado pela ReservaClienteRequest.

    Mantém a ordem original do itinerário.
    """

    trechos = itinerario.get("trechos") or []

    if not trechos:
        raise ValueError("Itinerário sem trechos.")

    resultado = []

    for trecho in trechos:
        resultado.append({
            "servidor": trecho["servidor"],
            "id_carona": trecho["id_carona"],
            "origem": id_cidade(trecho["origem"]),
            "destino": id_cidade(trecho["destino"])
        })

    return resultado


def reservar(servidor, token, itinerario):
    try:
        trechos = preparar_trechos(itinerario)

    except (ValueError, KeyError, TypeError) as erro:
        incrementar("itinerarios_invalidos")
        print(f"[ITINERÁRIO INVÁLIDO] {erro}")
        return

    # A busca começa no servidor coordenador.
    # A API exige que seus trechos apareçam primeiro.
    if trechos[0]["servidor"].casefold() != servidor["id"].casefold():
        incrementar("itinerarios_invalidos")
        print("[ITINERÁRIO INVÁLIDO] Coordenador não é o primeiro servidor.")
        return

    pedido = {
        "trechos": trechos
    }

    status, resposta = requisicao(
        servidor["url"].rstrip("/") + "/api/cliente/reservas",
        "POST",
        pedido,
        token
    )

    if status == 201 and isinstance(resposta, dict):
        if resposta.get("status") == "CONFIRMADO":
            incrementar("reservas_confirmadas")
            return

    if status == 409:
        incrementar("reservas_recusadas_conflito")
    elif status == 0:
        incrementar("reservas_com_erro_rede")
    else:
        incrementar("reservas_recusadas")

    print(f"[RESERVA RECUSADA] HTTP {status}: {resposta}")


def trabalhador(numero, servidor, args):
    passageiro = criar_passageiro(servidor, numero)

    if passageiro is None:
        return

    for _ in range(args.iteracoes):
        itinerarios = buscar(
            servidor,
            passageiro["token"],
            args.origem,
            args.destino,
            args.data,
            args.vagas
        )

        if not itinerarios:
            continue

        itinerario = random.choice(itinerarios)

        reservar(
            servidor,
            passageiro["token"],
            itinerario
        )


def main():
    parser = argparse.ArgumentParser(
        description="Simulador de carga do VaiJunto"
    )

    parser.add_argument("--passageiros", type=int, default=30)
    parser.add_argument("--iteracoes", type=int, default=10)
    parser.add_argument("--threads", type=int, default=30)
    parser.add_argument("--vagas", type=int, default=1)

    parser.add_argument("--origem", required=True)
    parser.add_argument("--destino", required=True)
    parser.add_argument("--data", required=True)

    args = parser.parse_args()

    if min(args.passageiros, args.iteracoes, args.threads, args.vagas) < 1:
        parser.error("Os valores numéricos devem ser positivos.")

    try:
        data = date.fromisoformat(args.data)
    except ValueError:
        parser.error("Data inválida. Utilize AAAA-MM-DD.")

    if not SERVIDORES:
        parser.error("Nenhum servidor configurado.")

    print("========== SIMULADOR VAIJUNTO ==========")
    print(f"Passageiros: {args.passageiros}")
    print(f"Iterações por passageiro: {args.iteracoes}")
    print(f"Concorrência: {args.threads} threads")
    print(f"Rota: {args.origem} -> {args.destino}")
    print(f"Data: {data}")
    print(f"Vagas solicitadas: {args.vagas}")

    carregar_cidades()

    inicio = time.perf_counter()

    with concurrent.futures.ThreadPoolExecutor(
            max_workers=args.threads
    ) as executor:

        tarefas = []

        for numero in range(args.passageiros):
            servidor = SERVIDORES[numero % len(SERVIDORES)]

            tarefas.append(
                executor.submit(
                    trabalhador,
                    numero,
                    servidor,
                    args
                )
            )

        for tarefa in concurrent.futures.as_completed(tarefas):
            try:
                tarefa.result()
            except Exception as erro:
                incrementar("erros_internos")
                print(f"[ERRO INTERNO] {erro}")

    duracao = time.perf_counter() - inicio

    print("\n========== RESULTADO ==========")

    for chave, valor in sorted(ESTATISTICAS.items()):
        print(f"{chave}: {valor}")

    print(f"Tempo total: {duracao:.2f} segundos")

    total_buscas = ESTATISTICAS["buscas"]
    total_reservas = ESTATISTICAS["reservas_confirmadas"]

    if total_buscas:
        print(f"Buscas por segundo: {total_buscas / duracao:.2f}")

    if total_reservas:
        print(f"Reservas por segundo: {total_reservas / duracao:.2f}")


if __name__ == "__main__":
    sys.exit(main())
