#!/usr/bin/env python3
"""Aplica a configuração de execução distribuída do VaiJunto.

Execute na raiz do repositório, na branch parte_2:
    python aplicar_config_rede.py

O script valida os trechos esperados antes de gravar cada arquivo e cria
.bak ao lado dos arquivos que serão alterados.
"""
from pathlib import Path
import re
import shutil
import sys

ROOT = Path.cwd()
MAIN_PATH = ROOT / "Server/src/main/java/org/UEFS/Server/Main.java"
COMPOSE_PATH = ROOT / "compose.yaml"
IGNORE_PATH = ROOT / ".gitignore"
README_PATH = ROOT / "README.md"
ENV_PATH = ROOT / ".env.example"

required = [MAIN_PATH, COMPOSE_PATH, IGNORE_PATH, README_PATH]
missing = [str(p.relative_to(ROOT)) for p in required if not p.is_file()]
if missing:
    print("Execute este script na raiz do repositório. Arquivos ausentes:")
    print("\n".join(missing))
    sys.exit(1)

main = MAIN_PATH.read_text(encoding="utf-8")
compose = COMPOSE_PATH.read_text(encoding="utf-8")
gitignore = IGNORE_PATH.read_text(encoding="utf-8")
readme = README_PATH.read_text(encoding="utf-8")

# 1. Main.java: usar o nome da região na variável HOST_* e não o ID ip:porta.
old = '''        System.out.println("  em outras máquinas, aponte este servidor com: HOST_"
                + ServerConstant.getMeuId().toUpperCase(java.util.Locale.ROOT) + "=" + ipLocal());'''
new = '''        String regiao = ServerConstant.getRegiao().regiaoBase()
                .replaceAll("[^a-zA-Z0-9]", "")
                .toUpperCase(java.util.Locale.ROOT);

        System.out.println("  Para que outros nós alcancem este servidor, configure: HOST_"
                + regiao + "=<IP_DA_MAQUINA>");

        if (Files.exists(Path.of("/.dockerenv")) || Files.exists(Path.of("/run/.containerenv"))) {
            System.out.println("  Execução em contêiner: use o IPv4 da máquina hospedeira, não o IP interno do contêiner.");
        } else {
            System.out.println("  IPv4 detectado nesta máquina: " + ipLocal()
                    + " (confirme que é acessível pelos outros nós).");
        }'''
if old not in main:
    raise SystemExit("Não encontrei o trecho esperado de mostrarResumo() em Main.java; nenhum arquivo foi alterado.")
main = main.replace(old, new, 1)
if "import java.nio.file.Files;" not in main:
    anchor = "import java.net.SocketException;"
    if anchor not in main:
        raise SystemExit("Não encontrei a área de imports em Main.java; nenhum arquivo foi alterado.")
    main = main.replace(anchor, anchor + "\nimport java.nio.file.Files;\nimport java.nio.file.Path;", 1)

# 2. compose.yaml: converter environment para mapa YAML e permitir .env.
# O padrão mantém a resolução por DNS do Compose na execução local, em um único host.
for region in ("UNOVA", "KALOS", "JOHTO", "KANTO"):
    found = re.findall(rf"^      - HOST_{region}=([a-z]+)$", compose, flags=re.MULTILINE)
    if len(found) != 4:
        raise SystemExit(f"Esperava encontrar 4 entradas HOST_{region} no Compose; encontrei {len(found)}. Nenhum arquivo foi alterado.")
    compose = re.sub(
        rf"^      - HOST_{region}=([a-z]+)$",
        lambda match: f"      HOST_{region}: ${{HOST_{region}:-{match.group(1)}}}",
        compose,
        flags=re.MULTILINE,
    )
# MAVEN_OPTS também precisa ser convertido para a forma mapa, pois YAML não pode
# misturar itens de lista e chaves dentro do mesmo bloco environment.
compose, n_maven = re.subn(
    r"^      - MAVEN_OPTS=(.*)$",
    lambda match: f'      MAVEN_OPTS: "{match.group(1)}"',
    compose,
    flags=re.MULTILINE,
)
if n_maven != 4:
    raise SystemExit(f"Esperava encontrar 4 entradas MAVEN_OPTS no Compose; encontrei {n_maven}. Nenhum arquivo foi alterado.")
if re.search(r"^      - (?:HOST_|MAVEN_OPTS=)", compose, flags=re.MULTILINE):
    raise SystemExit("O bloco environment ainda mistura formato de lista e mapa. Nenhum arquivo foi alterado.")

# 3. .gitignore: não versionar os endereços específicos de cada ambiente.
if ".env" not in gitignore.splitlines():
    gitignore = gitignore.rstrip() + "\n\n# Configuração local de endereços (não versionar)\n.env\n"

# 4. README: explicar ID lógico vs host real.
old_id = "O identificador utilizado nas mensagens entre servidores é formado pelo endereço IP e pela porta (`ip:porta`). Portanto, em uma execução local, os identificadores são `localhost:2602`, `localhost:2603` e `localhost:2604`; eles não são simplesmente os nomes das regiões."
new_id = "O identificador lógico utilizado nas mensagens entre servidores é formado pelos campos `ip` e `porta` do `regioes.json`. Na configuração atual, os identificadores são `localhost:2602`, `localhost:2603` e `localhost:2604`. **Mantenha esses valores iguais em todas as máquinas**: eles identificam os nós de forma estável e não precisam ser os endereços físicos usados para comunicação. O endereço de rede efetivo de cada vizinho é substituído pelas variáveis `HOST_UNOVA`, `HOST_KALOS`, `HOST_JOHTO` ou `HOST_KANTO`."
if old_id not in readme:
    raise SystemExit("Não encontrei o parágrafo de identificadores no README; nenhum arquivo foi alterado.")
readme = readme.replace(old_id, new_id, 1)
old_cfg = "A classe `ServerConstant` carrega esse arquivo e determina a região atual, o identificador do servidor e os vizinhos. As variáveis de ambiente `HOST_UNOVA`, `HOST_KALOS`, `HOST_JOHTO` e `HOST_KANTO` podem substituir os endereços IP utilizados na comunicação entre processos — por exemplo, pelos nomes dos serviços Docker."
new_cfg = "A classe `ServerConstant` carrega esse arquivo e determina a região atual, o identificador lógico do servidor e os vizinhos. Para cada vizinho, ela consulta `HOST_<REGIAO>` e usa esse valor como host HTTP; quando a variável não está definida, utiliza o campo `ip` do JSON. Assim, `localhost:2602` pode continuar sendo o identificador lógico de Unova, enquanto `HOST_UNOVA=192.168.1.10` define o endereço alcançável pelos outros computadores. No Docker Compose, os valores padrão são os nomes dos serviços (`unova`, `kalos`, `johto` e `kanto`)."
if old_cfg not in readme:
    raise SystemExit("Não encontrei o parágrafo de configuração regional no README; nenhum arquivo foi alterado.")
readme = readme.replace(old_cfg, new_cfg, 1)
readme = readme.replace("├── compose_1.yaml\n", "├── compose.yaml\n├── .env.example\n", 1)

start = readme.find("### 6.1. Execução local, sem Docker")
end = readme.find("\n---\n\n## 7. Interface Web", start)
if start < 0 or end < 0:
    raise SystemExit("Não encontrei a seção 6 do README; nenhum arquivo foi alterado.")
section = r'''### 6.1. Execução local, sem Docker

Compile o projeto:

```bash
make -f Makefile.local compile
```

Em uma única máquina, abra um terminal para cada região:

```bash
make -f Makefile.local run-only REGIAO=Unova
make -f Makefile.local run-only REGIAO=Kalos
make -f Makefile.local run-only REGIAO=Johto
```

Quando os processos estiverem em computadores diferentes, defina em cada computador as variáveis referentes aos **servidores vizinhos** antes de iniciar o processo. Exemplo no PowerShell, considerando Unova em `192.168.1.10`, Kalos em `192.168.1.11` e Johto em `192.168.1.12`:

**Computador de Unova:**

```powershell
$env:HOST_KALOS = "192.168.1.11"
make -f Makefile.local run-only REGIAO=Unova
```

**Computador de Kalos:**

```powershell
$env:HOST_UNOVA = "192.168.1.10"
$env:HOST_JOHTO = "192.168.1.12"
make -f Makefile.local run-only REGIAO=Kalos
```

**Computador de Johto:**

```powershell
$env:HOST_KALOS = "192.168.1.11"
make -f Makefile.local run-only REGIAO=Johto
```

No Linux/WSL, use `export HOST_KALOS=192.168.1.11` (e as demais variáveis necessárias) no terminal que iniciará o processo. Os endereços são exemplos: substitua-os pelos IPv4 alcançáveis na rede onde os computadores estão conectados.

Cada máquina precisa aceitar conexões TCP na porta de seu servidor: 2602 para Unova, 2603 para Kalos e 2604 para Johto. No Windows, o IPv4 pode ser consultado com `ipconfig`; no Linux, com `ip -4 addr`. Confirme também que o firewall permite a porta e que os computadores não estão em redes Wi-Fi com isolamento entre clientes.

Para conferir a conectividade de outro computador no PowerShell:

```powershell
Test-NetConnection 192.168.1.11 -Port 2603
```

O resultado esperado é `TcpTestSucceeded : True`.

O alvo `run-only` inicia sem recompilar. Quando o código for alterado, execute `make -f Makefile.local compile` novamente antes de iniciar os processos.

A porta efetiva vem do registro da região em `regioes.json`; alterar apenas `PORT=` no Makefile não muda a porta utilizada pelo servidor.

### 6.2. Execução com Docker Compose

O Compose lê automaticamente um arquivo `.env` na raiz do projeto para substituir as variáveis declaradas em `compose.yaml`. Comece criando sua configuração local:

**PowerShell:**

```powershell
Copy-Item .env.example .env
```

**Linux/WSL:**

```bash
cp .env.example .env
```

Para executar todos os servidores na mesma máquina e no mesmo projeto Compose, mantenha os nomes de serviço do exemplo (`unova`, `kalos`, `johto` e `kanto`). Os próprios valores padrão do Compose também permitem iniciar sem um arquivo `.env`:

```bash
docker compose up --build unova kalos johto
```

Para distribuir os servidores entre computadores diferentes, edite o `.env` **em cada computador** e substitua os nomes dos serviços pelos IPv4 das máquinas que hospedam os vizinhos. Por exemplo, no computador que hospedará somente Unova:

```dotenv
HOST_UNOVA=unova
HOST_KALOS=192.168.1.11
HOST_JOHTO=192.168.1.12
HOST_KANTO=kanto
```

Nesse computador, inicie somente o serviço correspondente:

```bash
docker compose up -d --build unova
docker compose logs -f unova
```

No computador de Kalos, configure `HOST_UNOVA` e `HOST_JOHTO` com os IPs das máquinas correspondentes e inicie `kalos`. No computador de Johto, configure `HOST_KALOS` e inicie `johto`. Cada computador deve ter seu próprio `.env`; não versione esse arquivo, pois ele contém endereços específicos do ambiente.

As portas publicadas são 2602 (Unova), 2603 (Kalos) e 2604 (Johto). Libere a porta correspondente no firewall do computador que hospeda cada servidor. Para testar, acesse, a partir de outro computador, por exemplo, `http://192.168.1.10:2602/api/consorcio/health`.

**Atenção ao Docker:** dentro de um contêiner, o IP detectado pelo próprio Java pode ser um endereço interno da rede Docker. Para configurar os outros computadores, utilize o IPv4 da máquina hospedeira (por exemplo, obtido com `ipconfig` no Windows), e não o IP interno do contêiner.

O volume `./data:/app/data` mantém os dados fora do ciclo de vida dos contêineres. Cada servidor separa seus arquivos de acordo com sua porta. Para encerrar os serviços iniciados naquele computador:

```bash
docker compose down
```

O serviço Kanto continua declarado no Compose, mas a região Kanto não está cadastrada no `regioes.json` atual; portanto, não o inicie até adicionar sua configuração regional.
'''
readme = readme[:start] + section + readme[end:]

env_example = '''# Endereços usados pelo Docker Compose para alcançar os servidores vizinhos.
# Em uma única máquina/projeto Compose, mantenha os nomes dos serviços.
# Para distribuir os servidores entre computadores, substitua os nomes pelos
# IPv4 das máquinas hospedeiras correspondentes (um .env por computador).
HOST_UNOVA=unova
HOST_KALOS=kalos
HOST_JOHTO=johto
HOST_KANTO=kanto
'''

# Backup and write. All validations above happen before this block.
changes = {
    MAIN_PATH: main,
    COMPOSE_PATH: compose,
    IGNORE_PATH: gitignore,
    README_PATH: readme,
}
for path, content in changes.items():
    backup = path.with_suffix(path.suffix + ".bak")
    if not backup.exists():
        shutil.copy2(path, backup)
    path.write_text(content, encoding="utf-8", newline="\n")
ENV_PATH.write_text(env_example, encoding="utf-8", newline="\n")

print("Configuração aplicada com sucesso:")
for path in [*changes.keys(), ENV_PATH]:
    print(" -", path.relative_to(ROOT))
print("Backups .bak criados para os arquivos preexistentes (quando não havia backup).")
print("Revise o diff com: git diff")
