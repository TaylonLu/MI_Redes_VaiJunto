import argparse
import json
import subprocess
import time
import re
from pathlib import Path



BASE = Path(__file__).resolve().parent
CONFIG = json.loads((BASE / "config.json").read_text(encoding="utf-8"))
SERVIDORES = CONFIG["servidores"]
DIRETORIO_DADOS = (BASE.parent / CONFIG.get("diretorio_dados", "data")).resolve()


def carregar_json(caminho):
    if not caminho.exists():
        return {}

    try:
        return json.loads(caminho.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError) as erro:
        print(f"[ERRO] Não foi possível ler {caminho}: {erro}")
        return None


def id_base_carona(id_carona):
    # O formato produzido pela BuscaDistribuida é:
    # carona_UUID.origem-destino
    return re.sub(r"_\d+_\d+$", "", id_carona)


def obter_porta(servidor):
    return servidor["url"].rstrip("/").rsplit(":", 1)[-1]


def carregar_servidor(servidor):
    pasta = DIRETORIO_DADOS / obter_porta(servidor)

    transacoes = carregar_json(pasta / "transacoes.json")
    caronas = carregar_json(pasta / "caronas.json")

    if transacoes is None or caronas is None:
        return None

    return {
        "id": servidor["id"],
        "transacoes": transacoes,
        "caronas": caronas
    }


def auditar_transacoes(dados):
    problemas = []

    for servidor in dados:
        for id_tx, tx in servidor["transacoes"].items():
            estado = tx.get("estado")

            if estado == "PREPARADO":
                problemas.append(
                    f"Transação {id_tx} permanece PREPARADO "
                    f"em {servidor['id']}"
                )

    return problemas


def auditar_assentos(dados):
    problemas = []

    for servidor in dados:
        for id_tx, tx in servidor["transacoes"].items():
            if tx.get("estado") != "COMITADO":
                continue

            passageiro = tx.get("idPassageiro")
            locais = tx.get("locais") or []

            for trecho in locais:
                id_carona = trecho.get("id_carona")
                assento = trecho.get("assento")

                if id_carona is None or assento is None:
                    problemas.append(
                        f"Transação {id_tx} possui trecho sem carona/assento "
                        f"em {servidor['id']}"
                    )

    # Um mesmo assento não pode aparecer em duas transações
    # confirmadas da mesma carona/perna no mesmo servidor.
    ocupantes = {}

    for servidor in dados:
        for id_tx, tx in servidor["transacoes"].items():
            if tx.get("estado") != "COMITADO":
                continue

            for trecho in tx.get("locais") or []:
                chave = (
                    servidor["id"],
                    trecho.get("id_carona"),
                    trecho.get("assento")
                )

                passageiro = tx.get("idPassageiro")

                if chave in ocupantes and ocupantes[chave] != passageiro:
                    problemas.append(
                        f"Assento duplicado: {chave}, "
                        f"passageiros {ocupantes[chave]} e {passageiro}"
                    )
                else:
                    ocupantes[chave] = passageiro

    return problemas


def auditar_ocupacoes(dados):
    problemas = []

    for servidor in dados:
        transacoes_confirmadas = [
            tx for tx in servidor["transacoes"].values()
            if tx.get("estado") == "COMITADO"
        ]

        for id_carona, carona in servidor["caronas"].items():
            ocupacao = carona.get("ocupacaoPorTrecho") or {}

            for id_trecho, passageiros in ocupacao.items():
                for passageiro in passageiros:
                    possui_reserva = any(
                        tx.get("idPassageiro") == passageiro
                        and any(
                            id_base_carona(t.get("id_carona", "")) == id_carona
                            for t in tx.get("locais", [])
                        )
                        for tx in transacoes_confirmadas
                    )

                    if not possui_reserva:
                        problemas.append(
                            f"Ocupação órfã: servidor={servidor['id']}, "
                            f"carona={id_carona}, trecho={id_trecho}, "
                            f"passageiro={passageiro}"
                        )

    return problemas


def injetar_falha(servidor, tempo_parado):
    container = servidor.get("container")

    if not container:
        raise RuntimeError("Container não configurado para esse servidor.")

    print(f"\n[DERRUBANDO] {container}")

    subprocess.run(
        ["docker", "kill", "--signal", "KILL", container],
        check=True
    )

    print(f"[FALHA] Servidor parado por {tempo_parado} segundos.")
    time.sleep(tempo_parado)

    print(f"[REINICIANDO] {container}")

    subprocess.run(
        ["docker", "start", container],
        check=True
    )


def esperar_recuperacao(tempo):
    print(f"\nAguardando {tempo} segundos para recuperação...")
    time.sleep(tempo)


def main():
    parser = argparse.ArgumentParser(
        description="Auditoria de consistência do VaiJunto"
    )

    parser.add_argument(
        "--falha",
        action="store_true",
        help="Derruba e reinicia um servidor"
    )

    parser.add_argument(
        "--servidor",
        type=int,
        default=1,
        help="Índice do servidor a derrubar (começa em zero)"
    )

    parser.add_argument(
        "--tempo-parado",
        type=int,
        default=5
    )

    parser.add_argument(
        "--espera",
        type=int,
        default=CONFIG.get("intervalo_recuperacao", 30)
    )

    args = parser.parse_args()

    if args.falha:
        if not 0 <= args.servidor < len(SERVIDORES):
            parser.error("Índice de servidor inválido")

        injetar_falha(
            SERVIDORES[args.servidor],
            args.tempo_parado
        )

        esperar_recuperacao(args.espera)

    dados = []

    for servidor in SERVIDORES:
        resultado = carregar_servidor(servidor)

        if resultado is not None:
            dados.append(resultado)

    if len(dados) != len(SERVIDORES):
        print("[ERRO] Não foi possível carregar os dados de todos os servidores.")
        return

    problemas = []

    problemas.extend(auditar_transacoes(dados))
    problemas.extend(auditar_assentos(dados))
    problemas.extend(auditar_ocupacoes(dados))

    print("\n========== AUDITORIA ==========")

    if problemas:
        print(f"[FALHA] {len(problemas)} problema(s) encontrado(s):")

        for problema in problemas:
            print(" -", problema)
    else:
        print("[OK] Nenhuma inconsistência detectada.")

    print("\nTransações por servidor:")

    for servidor in dados:
        contagem = {}

        for tx in servidor["transacoes"].values():
            estado = tx.get("estado", "DESCONHECIDO")
            contagem[estado] = contagem.get(estado, 0) + 1

        print(f"{servidor['id']}: {contagem}")


if __name__ == "__main__":
    main()