#!/usr/bin/env python3
"""Orquestrador local de carga e falhas 2PC do VaiJunto.

Execute na raiz do repositório:
    python3 Test/executar_testes.py
"""
from __future__ import annotations

import argparse
import concurrent.futures
import json
import os
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import date, datetime, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TEST = ROOT / "Test"
CONFIG = json.loads((TEST / "config.json").read_text(encoding="utf-8"))
SERVERS = {s["id"]: s for s in CONFIG["servidores"]}
COMPOSE = ["docker", "compose", "-f", "compose+3.yaml"]
DATA = ROOT / CONFIG.get("diretorio_dados", "data")
MARKERS = TEST / ".falhas-kalos"
TIMEOUT = int(CONFIG.get("timeout", 15))
PORTS = {"Unova": 2602, "Kalos": 2603, "Johto": 2604}
# Caminho real do mapa utilizado pelo CidadeMapper.
CITIES_FILE = ROOT / "Server/src/main/resources/public/data/cidades.json"


class TestFailure(RuntimeError):
    pass


def log(msg: str) -> None:
    print(f"[{datetime.now().strftime('%H:%M:%S')}] {msg}", flush=True)


def http(server: str, method: str, path: str, payload=None, token=None, timeout=TIMEOUT):
    url = SERVERS[server]["url"].rstrip("/") + path
    headers = {"Accept": "application/json"}
    body = None
    if payload is not None:
        headers["Content-Type"] = "application/json"
        body = json.dumps(payload).encode()
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            raw = response.read().decode()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode(errors="replace")
        try:
            data = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            data = raw
        return exc.code, data
    except Exception as exc:
        return 0, {"erro": str(exc)}


def require(ok: bool, message: str) -> None:
    if not ok:
        raise TestFailure(message)


def docker(*args: str, check=True, capture=True):
    result = subprocess.run(
        ["docker", *args], cwd=ROOT, text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None
    )
    if check and result.returncode:
        raise TestFailure((result.stderr or result.stdout or "Comando Docker falhou").strip())
    return result.stdout.strip() if capture else ""


def compose(*args: str):
    result = subprocess.run(
        COMPOSE + list(args), cwd=ROOT, text=True,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT
    )
    if result.returncode:
        raise TestFailure(result.stdout.strip() or "Docker Compose falhou")
    return result.stdout.strip()


def wait_health(server: str, seconds=60):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        status, data = http(server, "GET", "/api/consorcio/health", timeout=2)
        if status == 200 and isinstance(data, dict):
            return
        time.sleep(1)
    raise TestFailure(f"{server} não ficou saudável em {seconds}s.")


def wait_all():
    for server in SERVERS:
        wait_health(server)


def start_environment():
    log("Subindo Unova, Kalos e Johto com Docker Compose...")
    compose("up", "-d", "--build", "--force-recreate", "unova", "kalos", "johto")
    wait_all()
    log("Os três servidores estão saudáveis.")


def create_user(server: str, label: str):
    suffix = uuid.uuid4().hex
    payload = {
        "nome": f"{label}-{suffix[:8]}",
        "email": f"{label}-{suffix}@teste.local",
        "senha": f"Teste_{suffix}"
    }
    status, data = http(server, "POST", "/api/cliente/cadastro", payload)
    require(status in (200, 201) and isinstance(data, dict),
            f"Cadastro de {label} falhou em {server}: HTTP {status} {data}")
    token = data.get("token")
    user_id = (data.get("usuario") or {}).get("id")
    require(token and user_id, f"Cadastro em {server} não retornou token/ID.")
    return {"token": token, "id": str(user_id)}


def create_ride(server: str, label: str, path: list[int], seats: int, departure: str):
    driver = create_user(server, f"motorista-{label}")
    suffix = uuid.uuid4().hex
    profile = {
        "cnh": str(int(suffix[:11], 16) % 10**11).zfill(11),
        "placaCarro": f"T{suffix[:6].upper()}",
        "modeloCarro": "Veículo de teste",
        "corCarro": "Azul"
    }
    status, data = http(server, "POST", "/api/cliente/cadastro/motorista",
                        profile, driver["token"])
    require(status in (200, 201), f"Perfil motorista {server}: HTTP {status} {data}")

    payload = {
        "idMotorista": driver["id"],
        "vagas": seats,
        "valorPorTrecho": 5.0,
        "dataPartida": departure,
        "trechos": [{"origem": a, "destino": b} for a, b in zip(path, path[1:])]
    }
    status, data = http(server, "POST", "/api/cliente/caronas", payload, driver["token"])
    require(status == 201 and isinstance(data, dict) and data.get("id"),
            f"Criação de carona em {server} falhou: HTTP {status} {data}")
    return data["id"]


def city_paths():
    cities = json.loads(CITIES_FILE.read_text(encoding="utf-8"))
    by_id = {int(c["id"]): c for c in cities}

    def shortest(start: int, end: int):
        queue = [[start]]
        seen = {start}
        while queue:
            path = queue.pop(0)
            current = path[-1]
            if current == end:
                return path
            for neighbor in by_id[current].get("conexoes", []):
                neighbor = int(neighbor)
                if neighbor not in seen:
                    seen.add(neighbor)
                    queue.append(path + [neighbor])
        raise TestFailure(f"Não existe caminho no mapa entre {start} e {end}.")

    return by_id, shortest


def create_trip_set(seats: int, label: str):
    """Cria três caronas que reproduzem Mistralton -> Snowbelle -> Ecruteak -> Goldenrod."""
    by_id, shortest = city_paths()
    endpoints = [(108, 211, "Unova"), (211, 304, "Kalos"), (304, 305, "Johto")]
    departure = (datetime.now() + timedelta(days=2)).replace(
        hour=10, minute=0, second=0, microsecond=0
    ).isoformat(timespec="seconds")
    rides = []
    for start, end, server in endpoints:
        path = shortest(start, end)
        require(by_id[path[0]]["regiao"].casefold() == server.casefold(),
                f"A origem {start} não pertence a {server}.")
        ride_id = create_ride(server, f"{label}-{server}", path, seats, departure)
        rides.append({
            "servidor": server,
            "id_carona": f"{ride_id}_{start}_{end}",
            "origem": start,
            "destino": end
        })
        log(f"Carona criada em {server}: {' -> '.join(str(x) for x in path)}")
    return rides, departure[:10]


def reserve_payload(rides):
    return {"trechos": rides}


def search_itinerary(passenger, origin="Mistralton City", destination="Goldenrod city", day=None, server="Unova"):
    query = urllib.parse.urlencode({
        "origem": origin, "destino": destination,
        "data": day, "vagas": 1
    })
    status, data = http(server, "GET", "/api/cliente/itinerarios?" + query,
                        token=passenger["token"])
    require(status == 200 and isinstance(data, dict),
            f"Busca de itinerário falhou: HTTP {status} {data}")
    itineraries = data.get("itinerarios") or []
    require(itineraries, f"Nenhum itinerário encontrado para {origin} -> {destination}.")
    return itineraries


def find_container_state(container: str):
    return docker("inspect", "--format", "{{.State.Status}}", container)


def arm_failure(stage: str):
    MARKERS.mkdir(parents=True, exist_ok=True)
    (MARKERS / stage).touch()


def run_reservation_with_failure(passenger, rides, stage: str, wait_crash=35):
    """Envia a reserva em uma thread e reinicia Kalos assim que o processo cai."""
    container = SERVERS["Kalos"]["container"]
    marker = MARKERS / stage
    require(marker.exists(), f"Marcador de falha não foi criado: {marker}")

    result = {}
    finished = threading.Event()

    def send():
        try:
            result["response"] = http(
                "Unova", "POST", "/api/cliente/reservas",
                reserve_payload(rides), passenger["token"], timeout=35
            )
        finally:
            finished.set()

    worker = threading.Thread(target=send, daemon=True)
    worker.start()

    deadline = time.monotonic() + wait_crash
    crashed = False
    while time.monotonic() < deadline:
        if not marker.exists():
            state = find_container_state(container)
            if state in ("exited", "dead"):
                crashed = True
                break
        if finished.is_set() and marker.exists():
            break
        time.sleep(0.05)

    if not crashed:
        # Não deixa um marcador armado afetar uma requisição futura.
        marker.unlink(missing_ok=True)
        worker.join(timeout=2)
        raise TestFailure(
            f"Kalos não caiu na etapa '{stage}'. Verifique se o build incluiu FalhaControlada."
        )

    log(f"Kalos caiu deterministicamente na etapa '{stage}'. Reiniciando...")
    docker("start", container)
    wait_health("Kalos", 60)
    worker.join(timeout=40)
    require(not worker.is_alive(), "A requisição de reserva não terminou após a recuperação.")
    return result.get("response")


def read_json(path: Path):
    if not path.exists():
        return {}
    return json.loads(path.read_text(encoding="utf-8"))


def transactions_for(passenger_id: str):
    found = {}
    for server, port in PORTS.items():
        db = read_json(DATA / str(port) / "transacoes.json")
        found[server] = {
            txid: tx for txid, tx in db.items()
            if str(tx.get("idPassageiro")) == passenger_id
        }
    return found


def occupancy_contains(server: str, ride_id: str, passenger_id: str):
    port = PORTS[server]
    db = read_json(DATA / str(port) / "caronas.json")
    ride = db.get(ride_id)
    if not ride:
        return False
    occupancy = ride.get("ocupacaoPorTrecho") or {}
    return any(
        passenger_id in (occupants or [])
        for occupants in occupancy.values()
    )


def wait_transaction_states(passenger_id: str, expected: dict[str, str], seconds=35):
    deadline = time.monotonic() + seconds
    last = {}
    while time.monotonic() < deadline:
        last = transactions_for(passenger_id)
        ok = True
        for server, state in expected.items():
            entries = last.get(server, {})
            if not entries or any(tx.get("estado") != state for tx in entries.values()):
                ok = False
                break
        if ok:
            return last
        time.sleep(1)
    raise TestFailure(f"Estados não convergiram. Esperado={expected}; encontrado={last}")


def run_failure_prepare():
    log("TESTE DE FALHA 1/2: queda de Kalos depois de persistir PREPARADO.")
    rides, day = create_trip_set(1, "falha-prepare")
    passenger = create_user("Unova", "passageiro-falha-prepare")
    # A busca é feita antes da falha para validar o fluxo de descoberta.
    search_itinerary(passenger, day=day)
    arm_failure("prepare")
    response = run_reservation_with_failure(passenger, rides, "prepare")
    log(f"Resposta HTTP da tentativa PREPARE: {response}")

    wait_transaction_states(passenger["id"], {"Unova": "ABORTADO", "Kalos": "ABORTADO"})
    for ride, server in zip(rides, ("Unova", "Kalos", "Johto")):
        require(not occupancy_contains(server, ride["id_carona"].rsplit("_", 2)[0], passenger["id"]),
                f"Assento permaneceu ocupado em {server} após rollback.")
    log("APROVADO: PREPARE interrompido, transação abortada e assentos liberados.")


def run_failure_commit():
    log("TESTE DE FALHA 2/2: queda de Kalos depois de persistir COMITADO.")
    rides, day = create_trip_set(1, "falha-commit")
    passenger = create_user("Unova", "passageiro-falha-commit")
    search_itinerary(passenger, day=day)
    arm_failure("commit")
    response = run_reservation_with_failure(passenger, rides, "commit")
    log(f"Resposta HTTP da tentativa COMMIT: {response}")

    states = wait_transaction_states(
        passenger["id"], {"Unova": "COMITADO", "Kalos": "COMITADO", "Johto": "COMITADO"}
    )
    for ride, server in zip(rides, ("Unova", "Kalos", "Johto")):
        base_id = ride["id_carona"].rsplit("_", 2)[0]
        require(occupancy_contains(server, base_id, passenger["id"]),
                f"Reserva confirmada não ocupa o assento em {server}.")
    log("APROVADO: COMMIT recuperado nos três servidores e assentos confirmados.")


def run_load(passengers_count: int, threads: int):
    log(f"TESTE DE CARGA: {passengers_count} passageiros, {threads} threads.")
    rides, day = create_trip_set(passengers_count, "carga")
    passageiros_unova = [create_user("Unova", f"passageiro-carga-unova-{i+1}")
                           for i in range(passengers_count)]
    passageiros_kalos = [create_user("Kalos", f"passageiro-carga-kalos-{i+1}")
                         for i in range(passengers_count)]
    passageiros_johto = [create_user("Johto", f"passageiro-carga-johto-{i+1}")
                         for i in range(passengers_count)]

    latencies = []
    lock = threading.Lock()
    counts = {"buscas_unova": 0, "buscas_kalos": 0, "buscas_johto": 0,
              "reservas_confirmadas": 0, "reservas_recusadas": 0}

    def work(passengers):
        passageiro_unova, passageiro_kalos, passageiro_johto = passengers
        start = time.perf_counter()
        search_itinerary(passageiro_unova, day=day, server="Unova")
        search_itinerary(passageiro_kalos, origin="Snowbelle City",
                         destination="Ecruteak City", day=day, server="Kalos")
        search_itinerary(passageiro_johto, origin="Ecruteak City",
                         destination="Goldenrod city", day=day, server="Johto")
        with lock:
            counts["buscas_unova"] += 1
            counts["buscas_kalos"] += 1
            counts["buscas_johto"] += 1
        status, data = http("Unova", "POST", "/api/cliente/reservas",
                            reserve_payload(rides), passageiro_unova["token"], timeout=35)
        elapsed = time.perf_counter() - start
        with lock:
            latencies.append(elapsed)
            if status == 201 and isinstance(data, dict) and data.get("status") == "CONFIRMADO":
                counts["reservas_confirmadas"] += 1
            else:
                counts["reservas_recusadas"] += 1
        return status, data

    start = time.perf_counter()
    tarefas = zip(passageiros_unova, passageiros_kalos, passageiros_johto)
    with concurrent.futures.ThreadPoolExecutor(max_workers=threads) as pool:
        results = list(pool.map(work, tarefas))
    duration = time.perf_counter() - start
    ordered = sorted(latencies)
    percentile = lambda p: ordered[min(len(ordered)-1, int((len(ordered)-1)*p))] if ordered else 0
    require(counts["reservas_confirmadas"] == passengers_count,
            f"Carga: esperadas {passengers_count} reservas confirmadas; "
            f"recebidas {counts['reservas_confirmadas']}.")
    total_buscas = counts["buscas_unova"] + counts["buscas_kalos"] + counts["buscas_johto"]
    log(f"Buscas: {total_buscas} (Unova={counts['buscas_unova']}, "
        f"Kalos={counts['buscas_kalos']}, Johto={counts['buscas_johto']}); "
        f"reservas confirmadas: {counts['reservas_confirmadas']}; "
        f"recusadas: {counts['reservas_recusadas']}")
    log(f"Duração: {duration:.2f}s; latência p50={percentile(.50):.3f}s, "
        f"p95={percentile(.95):.3f}s, p99={percentile(.99):.3f}s")
    log("APROVADO: carga concorrente sem reservas perdidas ou duplicadas.")


def run_audit():
    log("Executando auditoria global existente...")
    result = subprocess.run(
        [sys.executable, str(TEST / "auditoria.py")],
        cwd=TEST, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT
    )
    print(result.stdout, flush=True)
    if result.returncode or "[FALHA]" in result.stdout or "[ERRO]" in result.stdout:
        raise TestFailure("A auditoria encontrou inconsistências ou terminou com erro.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--passageiros", type=int, default=15)
    parser.add_argument("--threads", type=int, default=10)
    parser.add_argument("--somente-carga", action="store_true")
    parser.add_argument("--somente-falhas", action="store_true")
    parser.add_argument("--sem-build", action="store_true")
    args = parser.parse_args()
    require(args.passageiros > 0 and args.threads > 0, "Passageiros e threads devem ser positivos.")

    try:
        if args.sem_build:
            wait_all()
        else:
            start_environment()

        if not args.somente_falhas:
            run_load(args.passageiros, args.threads)
        if not args.somente_carga:
            run_failure_prepare()
            run_failure_commit()

        run_audit()
        log("TODOS OS TESTES AUTOMATIZADOS TERMINARAM.")
        return 0
    except (TestFailure, OSError, ValueError, KeyError, json.JSONDecodeError) as exc:
        log(f"FALHA: {exc}")
        # Recupera Kalos se um teste tiver terminado durante a injeção.
        try:
            if find_container_state(SERVERS["Kalos"]["container"]) in ("exited", "dead"):
                docker("start", SERVERS["Kalos"]["container"], check=False)
        except Exception:
            pass
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
