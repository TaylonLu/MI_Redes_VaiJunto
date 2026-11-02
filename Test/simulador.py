import argparse
import concurrent.futures
import json
import random
import threading
import time
import urllib.error
import urllib.request
import uuid
from collections import Counter
from pathlib import Path


CONFIG = json.loads(Path("config.json").read_text(encoding="utf-8"))
SERVIDORES = CONFIG["servidores"]
TIMEOUT = CONFIG.get("timeout", 5)

ORIGENS_DESTINOS = [
    # Substitua pelos nomes de cidades existentes no seu cidades.json.
    ("Mistralton City", "Anistar city")
]

estatisticas = Counter()
lock = threading.Lock()


def requisicao(url, metodo="GET", dados=None, token=None):
    corpo = None
    headers = {"Content-Type": "application/json"}

    if dados is not None:
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
        with urllib.request.urlopen(req, timeout=TIMEOUT) as resposta:
            texto = resposta.read().decode("utf-8")
            try:
                resultado = json.loads(texto) if texto else None
            except json.JSONDecodeError:
                resultado = texto

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


def incrementar(chave, quantidade=1):
    with lock:
        estatisticas[chave] += quantidade

def criar_passageiro(servidor, numero):
    identificador = uuid.uuid4().hex[:12]

    cadastro = {
        "nome": f"Passageiro Teste {numero}",
        "email": f"teste_{identificador}@exemplo.com",
        "senha": f"Teste_{identificador}"
    }

    status, resposta = requisicao(
        servidor["url"] + "/api/cliente/cadastro",
        "POST",
        cadastro
    )

    if status not in (200, 201) or not isinstance(resposta, dict):
        incrementar("cadastros_com_erro")
        return None

    token = resposta.get("token")
    usuario = resposta.get("usuario") or {}
    id_usuario = usuario.get("id")

    if not token or not id_usuario:
        incrementar("cadastros_com_erro")
        return None

    incrementar("cadastros")
    return {"token": token, "id": id_usuario}


def extrair_trechos(itinerario):
    """
    Converte um itinerário retornado por BuscaDistribuida
    para o formato esperado por ReservaRequest.
    """
    trechos = itinerario.get("trechos", [])

    resultado = []

    for trecho in trechos:
        servidor = trecho.get("servidor")
        id_carona = trecho.get("idCarona", trecho.get("id_carona"))

        if not servidor or not id_carona:
            return []

        resultado.append({
            "servidor": servidor,
            "id_carona": id_carona
        })

    return resultado

def normalizar_servidor(servidor):
    """
    Normaliza a identificação do servidor para permitir
    comparações entre endereços em formatos equivalentes.
    """
    if not servidor:
        return ""

    servidor = servidor.strip().lower()
    servidor = servidor.removeprefix("http://")
    servidor = servidor.removeprefix("https://")
    return servidor.rstrip("/")


def ordenar_trechos_por_servidor(trechos, coordenador_id):
    """
    Agrupa os trechos por servidor, preservando a ordem
    em que os servidores aparecem no itinerário.

    O servidor coordenador sempre fica primeiro, pois
    o protocolo de reserva exige isso.

    Não permite que um servidor reapareça depois de
    outro servidor já ter começado.
    """
    grupos = {}
    servidores_encerrados = set()
    servidor_anterior = None

    for trecho in trechos:
        servidor = normalizar_servidor(trecho["servidor"])

        if not servidor:
            raise ValueError("Trecho sem identificação de servidor.")

        if servidor != servidor_anterior:
            if servidor in servidores_encerrados:
                raise ValueError(
                    f"O itinerário retorna a um servidor já visitado: {servidor}"
                )

            if servidor_anterior is not None:
                servidores_encerrados.add(servidor_anterior)

            servidor_anterior = servidor

        grupos.setdefault(servidor, []).append(trecho)

    coordenador = normalizar_servidor(coordenador_id)
    trechos_locais = grupos.pop(coordenador, None)

    if not trechos_locais:
        raise ValueError(
            f"O itinerário não possui trechos do coordenador {coordenador}"
        )

    resultado = list(trechos_locais)

    for trechos_servidor in grupos.values():
        resultado.extend(trechos_servidor)

    return resultado

def buscar(servidor, token, origem, destino):
    url = (
        servidor["url"]
        + "/api/cliente/itinerarios"
        + f"?origem={urllib.parse.quote(origem)}"
        + f"&destino={urllib.parse.quote(destino)}"
    )

    status, resposta = requisicao(url, token=token)

    if status != 200 or not isinstance(resposta, dict):
        incrementar("buscas_com_erro")
        return []

    incrementar("buscas")

    itinerarios = resposta.get("itinerarios", [])

    if not itinerarios:
        incrementar("buscas_sem_itinerario")
    else:
        incrementar("buscas_com_itinerario")

    incrementar("itinerarios_encontrados", len(itinerarios))
    return itinerarios


def reservar(servidor, token, itinerario):
    trechos = extrair_trechos(itinerario)

    if not trechos:
        incrementar("itinerarios_invalidos")
        return

    try:
        trechos = ordenar_trechos_por_servidor(
            trechos,
            servidor["id"]
        )

    except ValueError as erro:
        incrementar("itinerarios_invalidos")
        print(f"[ITINERÁRIO INVÁLIDO] {erro}")
        return

    pedido = {
        "id_transacao": str(uuid.uuid4()),
        "coordenador_id": servidor["id"],
        "passageiro": "",
        "relogio_logico": 0,
        "trechos_solicitados": trechos
    }

    status, resposta = requisicao(
        servidor["url"] + "/api/cliente/reservas",
        "POST",
        pedido,
        token
    )

    if status == 201 and isinstance(resposta, dict):
        if resposta.get("status") == "CONFIRMADO":
            incrementar("reservas_confirmadas")

            print(
                f"[RESERVA CONFIRMADA] "
                f"Transação: {resposta.get('id_transacao')}"
            )
            return

    if status == 0:
        incrementar("reservas_com_erro_rede")

    elif status == 409:
        incrementar("reservas_recusadas_conflito")

    else:
        incrementar("reservas_recusadas")

    print(
        f"[RESERVA RECUSADA] HTTP {status}: {resposta}"
    )


def trabalhador(numero, servidor, iteracoes):
    passageiro = criar_passageiro(servidor, numero)

    if passageiro is None:
        return

    for _ in range(iteracoes):
        origem, destino = random.choice(ORIGENS_DESTINOS)

        itinerarios = buscar(
            servidor,
            passageiro["token"],
            origem,
            destino
        )

        if not itinerarios:
            continue

        # Escolhe aleatoriamente um itinerário encontrado.
        itinerario = random.choice(itinerarios)

        reservar(
            servidor,
            passageiro["token"],
            itinerario
        )


def main():
    parser = argparse.ArgumentParser(
        description="Simulador de passageiros do VaiJunto"
    )

    parser.add_argument("--passageiros", type=int, default=30)
    parser.add_argument("--iteracoes", type=int, default=10)
    parser.add_argument("--threads", type=int, default=30)

    args = parser.parse_args()

    if args.passageiros < 1 or args.iteracoes < 1:
        parser.error("passageiros e iteracoes devem ser positivos")

    print("Iniciando simulação...")
    print(f"Passageiros: {args.passageiros}")
    print(f"Iterações por passageiro: {args.iteracoes}")
    print(f"Servidores: {len(SERVIDORES)}")

    inicio = time.time()

    tarefas = []

    with concurrent.futures.ThreadPoolExecutor(
        max_workers=args.threads
    ) as executor:

        for numero in range(args.passageiros):
            servidor = SERVIDORES[numero % len(SERVIDORES)]

            tarefas.append(
                executor.submit(
                    trabalhador,
                    numero,
                    servidor,
                    args.iteracoes
                )
            )

        for tarefa in concurrent.futures.as_completed(tarefas):
            try:
                tarefa.result()
            except Exception as erro:
                incrementar("erros_internos")
                print(f"Erro em trabalhador: {erro}")

    duracao = time.time() - inicio

    print("\n========== RESULTADO ==========")

    for chave, valor in sorted(estatisticas.items()):
        print(f"{chave}: {valor}")

    print(f"Tempo total: {duracao:.2f}s")


if __name__ == "__main__":
    import urllib.parse
    main()