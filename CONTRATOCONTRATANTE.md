# Contrato de interoperabilidade: Consórcio VaiJunto

Documento para dois alunos que implementam **servidores independentes** e querem que eles conversem entre si.
O que está aqui é o que **precisa ser idêntico** nas duas implementações. Todo o resto (linguagem, bibliotecas,
estrutura interna, formato do arquivo em disco, menu de testes) é livre.

> Regra de ouro: se um servidor manda algo que o outro não entende, a transação aborta.
> Em caso de dúvida, o que está escrito aqui vale. Mudanças devem ser combinadas e atualizadas nos dois lados.

## Sumário

1. [Decisões a fechar juntos](#1-decisões-a-fechar-juntos)
2. [Rede: quem roda o quê e como se conectar](#2-rede-quem-roda-o-quê-e-como-se-conectar)
3. [Regiões, portas e fronteiras](#3-regiões-portas-e-fronteiras)
4. [Regras gerais do protocolo](#4-regras-gerais-do-protocolo)
5. [Relógio lógico de Lamport](#5-relógio-lógico-de-lamport)
6. [Endpoints](#6-endpoints)
7. [Fluxo encadeado (2PC) e falhas](#7-fluxo-encadeado-2pc-e-falhas)
8. [Critério de precedência](#8-critério-de-precedência)
9. [Checklist de testes entre os dois servidores](#9-checklist-de-testes-entre-os-dois-servidores)

---

## 1. Decisões a fechar juntos

Marquem cada item quando os dois concordarem. A proposta é o que está descrito neste documento.

- [ ] **Tabela de quem hospeda cada região** (seção 2).
- [ ] **Portas** de cada região (seção 3). Proposta: as da tabela.
- [ ] **Cidades de cada região** e **nome dos pontos de fronteira** (seção 6.2). Sem isso a busca de itinerário não casa.
- [ ] **Formato da resposta de `/trechos`** (lista `itinerarios`, seção 6.2).
- [ ] **Critério de precedência** (seção 8). O formato da chave tem que ser igual; a política de conflito também.
- [ ] **Timeouts** (seção 7.3).
- [ ] **Como cada participante guarda `anterior` e `proximo`** para repassar commit/rollback e consultar status (seção 7.2).

---

## 2. Rede: quem roda o quê e como se conectar

### 2.1 Divisão das regiões

Cada região deve rodar em **um único** servidor (nunca duas instâncias da mesma região).
Preencham, por exemplo, assim (cada um hospeda algumas regiões e acessa duas ou três do outro):

| Região | Dono | Endereço (IP ou host) | Porta |
|---|---|---|---|
| skyrim | | | 3001 |
| morrowind | | | 3002 |
| cyrodiil | | | 3003 |
| high_rock | | | 3004 |
| hammerfell | | | 3005 |
| black_marsh | | | 3006 |
| elsweyr | | | 3007 |
| valenwood | | | 3008 |
| summerset_islands | | | 3009 |

Para testar o consórcio de verdade, escolham a divisão de modo que existam **fronteiras entre os dois donos**
(por exemplo, um fica com `morrowind` e `skyrim` e o outro com `high_rock`; assim a cadeia Morrowind → Skyrim → High Rock cruza as máquinas).

### 2.2 Cada servidor precisa saber onde estão os vizinhos

Cada implementação precisa de uma configuração região → endereço:porta (arquivo, variável de ambiente, o que preferirem).
O servidor só deve falar com vizinhos de fronteira (seção 3), nunca em broadcast.

No servidor em C do autor desta versão, o host de cada vizinho vem de variáveis `HOST_<REGIAO>`
(a porta vem da tabela da seção 3):

```bash
HOST_HIGH_ROCK=192.168.0.25 ./servidor skyrim
```

### 2.3 Como as máquinas se enxergam

Escolham uma opção:

| Opção | Quando usar | Como |
|---|---|---|
| **Mesma rede (Wi-Fi/LAN do laboratório)** | estão na mesma rede e sem isolamento de clientes | usar o IP local (`hostname -I` no Linux, `ipconfig` no Windows) |
| **VPN mesh (Tailscale ou ZeroTier)** | redes diferentes, ou rede que bloqueia conexões entre clientes | instalar nos dois, usar o IP da VPN (ex.: `100.x.y.z`); é a opção mais simples e estável |
| **Túnel (ngrok, cloudflared)** | último caso | um túnel por servidor; mais trabalhoso por causa das várias portas |

**Atenção para quem usa WSL2 (Windows):** o WSL2 fica atrás de um NAT, e o outro computador não alcança as portas do WSL por padrão. Soluções:

1. Usar a **VPN (Tailscale) instalada dentro do WSL**, que resolve sem mexer em nada do Windows; ou
2. Ativar o *mirrored networking* (Windows 11 recente): no arquivo `%UserProfile%\.wslconfig`, colocar
   ```
   [wsl2]
   networkingMode=mirrored
   ```
   e rodar `wsl --shutdown`; ou
3. Encaminhar as portas do Windows para o WSL (como administrador, no PowerShell):
   ```powershell
   netsh interface portproxy add v4tov4 listenport=3001 listenaddress=0.0.0.0 connectport=3001 connectaddress=<IP do WSL>
   ```
   (o IP do WSL muda a cada reinício, então esta opção é a mais chata).

Além disso, liberar as portas no firewall do Windows (regra de entrada TCP para 3001 a 3009).

Se o servidor rodar em Docker, publicar as portas (`-p 3001:3001`) e ouvir em todas as interfaces (`0.0.0.0`), não só em `localhost`.

### 2.4 Teste de conexão antes de tudo

De cada máquina, para cada servidor do outro lado:

```bash
curl -i http://<endereco>:<porta>/api/consorcio/health
```

Se der `200`, a rede está resolvida e o resto é protocolo.

---

## 3. Regiões, portas e fronteiras

IDs em **minúsculas, com `_` no lugar de espaço**. Esse ID é usado em `X-Server-Id`, `servidor`, `coordenador_id` e `visitados`.

| ID | Nome de exibição | Porta | Vizinhos (fronteira) |
|---|---|---|---|
| `skyrim` | Skyrim | 3001 | high_rock, hammerfell, cyrodiil, morrowind |
| `morrowind` | Morrowind | 3002 | skyrim, cyrodiil, black_marsh |
| `cyrodiil` | Cyrodiil | 3003 | skyrim, hammerfell, morrowind, black_marsh, elsweyr, valenwood, summerset_islands |
| `high_rock` | High Rock | 3004 | skyrim, hammerfell |
| `hammerfell` | Hammerfell | 3005 | high_rock, skyrim, cyrodiil, valenwood, summerset_islands |
| `black_marsh` | Black Marsh | 3006 | morrowind, cyrodiil, elsweyr |
| `elsweyr` | Elsweyr | 3007 | valenwood, cyrodiil, black_marsh |
| `valenwood` | Valenwood | 3008 | elsweyr, cyrodiil, hammerfell, summerset_islands |
| `summerset_islands` | Summerset Islands | 3009 | hammerfell, cyrodiil, valenwood |

O grafo é **simétrico** (se A é vizinho de B, B é vizinho de A) e **tem ciclos** (por isso existe o parâmetro `visitados`).
Um servidor só envia requisições para os seus vizinhos. Se receber um id que não é vizinho, deve recusar.

---

## 4. Regras gerais do protocolo

- Transporte: **HTTP/1.1**, sem TLS.
- Prefixo de **todas** as rotas entre servidores: `/api/consorcio`.
- Corpo e respostas em **JSON UTF-8**, com `Content-Type: application/json`.
- Chaves JSON em `snake_case`, exatamente como escritas neste documento.
- **Campos desconhecidos devem ser ignorados** (permite um lado acrescentar algo sem quebrar o outro).
- `id_transacao`: UUID v4 em texto minúsculo (ex.: `9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d`), gerado pelo coordenador. Tratar como texto opaco.
- Inteiros (`relogio_logico`, `relogio`) são números JSON, não texto.
- Erros: o corpo é `{"erro": "mensagem"}`, a menos que o endpoint defina outro formato.
- Todas as requisições e respostas entre servidores levam os cabeçalhos:

| Cabeçalho | Valor | Exemplo |
|---|---|---|
| `X-Server-Id` | ID da região de quem **envia** (não do coordenador) | `skyrim` |
| `X-Lamport-Clock` | relógio lógico de quem envia, inteiro | `42` |

Nomes de cabeçalho HTTP não diferenciam maiúsculas de minúsculas; a implementação deve ler assim.

---

## 5. Relógio lógico de Lamport

Cada servidor mantém **um** contador inteiro, começando em 0, protegido contra acesso concorrente.

| Evento | Regra |
|---|---|
| Vai **enviar** uma requisição (inclusive repasse a outro vizinho) | `relogio = relogio + 1`, e envia esse valor em `X-Lamport-Clock` |
| **Recebe** uma requisição | `relogio = max(relogio, recebido) + 1` (se o cabeçalho faltar, só `+1`) |
| **Responde** | devolve o relógio atual em `X-Lamport-Clock` |
| **Recebe** uma resposta | `relogio = max(relogio, recebido_na_resposta) + 1` |

Ao reiniciar, o relógio **não pode voltar para trás**: recomenda-se recomeçar do maior `relogio_logico` guardado em disco.

Importante: o campo `relogio_logico` **no corpo do `prepare`** é o *timestamp da transação*. O coordenador o define **uma vez**
(o valor do seu relógio ao criar a transação) e **todos os repasses o mantêm inalterado**. O cabeçalho `X-Lamport-Clock`, ao contrário, muda a cada salto.

---

## 6. Endpoints

Todos os servidores expõem exatamente a mesma API.

| Método e rota | Função |
|---|---|
| `GET /health` | detecção de falha |
| `GET /trechos` | busca de trechos disponíveis |
| `POST /reservas/prepare` | fase 1 do 2PC |
| `PUT /reservas/{id}/commit` | fase 2, confirmar |
| `DELETE /reservas/{id}` | fase 2, desfazer (rollback) |
| `GET /reservas/{id}/status` | recuperação |
| `GET /reservas` | *(opcional, depuração)* lista as reservas do servidor |

### 6.1 `GET /health`

`200`:

```json
{"status": "skyrim ta on", "relogio": 12}
```

Só o código `200` é obrigatório para a detecção de falha. Sem resposta dentro do timeout = servidor considerado fora do ar.

### 6.2 `GET /trechos?origem=&destino=&visitados=`

Busca recursiva: o servidor procura trechos locais e repassa a busca aos vizinhos que **não estão** em `visitados`.

- Parâmetros em query string, com URL-encoding (`Daggerfall`, `Fronteira%20Skyrim`).
- `visitados`: IDs separados por vírgula, **incluindo quem enviou e todos os anteriores** (`morrowind,skyrim`).
  O servidor que recebe deve se acrescentar à lista antes de repassar, e **nunca** repassar para quem já está nela.
- `origem` e `destino` são nomes de cidades ou de pontos de fronteira (abaixo).

Resposta `200`:

```json
{
  "itinerarios": [
    {
      "itinerario_id": "rota-777",
      "trechos": [
        {"servidor": "skyrim", "id_carona": "sk-45", "origem": "Fronteira Morrowind", "destino": "Fronteira High Rock", "preco": 80.0},
        {"servidor": "high_rock", "id_carona": "hr-02", "origem": "Fronteira Skyrim", "destino": "Daggerfall", "preco": 40.0}
      ]
    }
  ]
}
```

- `trechos` está **em ordem de viagem**, começando no servidor que respondeu e indo até o destino.
- Campo `preco` é número. Pode haver o campo opcional `assentos_livres` (inteiro).
- `404` com `{"erro": "..."}` se não houver rota por esse ramo.

**Pontos de fronteira.** Formato combinado: `Fronteira <Nome de exibição da região vizinha>`, por exemplo
`Fronteira Skyrim`, `Fronteira High Rock`, `Fronteira Summerset Islands`. Um trecho que sai de Morrowind em direção a Skyrim
termina em `Fronteira Skyrim`, e o trecho seguinte, de Skyrim, começa em `Fronteira Morrowind`.

**Cidades.** Os dois lados precisam usar os mesmos nomes de cidade e saber a qual região cada uma pertence.
Preencham a tabela (sem acentos, para evitar problemas de encoding):

| Região | Cidades |
|---|---|
| morrowind | Mournhold, ... |
| high_rock | Daggerfall, ... |
| skyrim | |
| cyrodiil | |
| hammerfell | |
| black_marsh | |
| elsweyr | |
| valenwood | |
| summerset_islands | |

Comparação de nomes: ignorar maiúsculas/minúsculas e espaços nas pontas.

### 6.3 `POST /reservas/prepare`

Fase 1: trancar o assento temporariamente. Corpo:

```json
{
  "id_transacao": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "coordenador_id": "morrowind",
  "relogio_logico": 14,
  "trechos_solicitados": [
    {"servidor": "morrowind", "id_carona": "mw-10"},
    {"servidor": "skyrim", "id_carona": "sk-45"},
    {"servidor": "high_rock", "id_carona": "hr-02"}
  ]
}
```

- `trechos_solicitados` é a **lista completa e ordenada** do itinerário (inclusive o trecho do coordenador). Cada servidor trata
  as entradas em que `servidor` é ele mesmo e **repassa o mesmo corpo, sem alterar nada**, ao próximo servidor da lista,
  que obrigatoriamente é seu vizinho. O último da cadeia não repassa.
- Um servidor só responde `READY` depois de (1) trancar o seu assento e **gravar em disco** e (2) receber `READY` do próximo, se houver.

Respostas:

| Código | Corpo | Significado |
|---|---|---|
| `200` | `{"id_transacao": "...", "status": "READY"}` | assento trancado, e o resto da cadeia também |
| `409` | `{"id_transacao": "...", "status": "ABORT", "motivo": "..."}` | assento indisponível ou perdeu a disputa |
| `503` | `{"id_transacao": "...", "status": "ABORT", "motivo": "..."}` | o próximo da cadeia não respondeu (timeout/queda) |
| `400` | `{"erro": "..."}` | corpo inválido |

Para o coordenador, **qualquer resposta diferente de 200, ou ausência de resposta no timeout, significa ABORT**.
Ao devolver `409` ou `503`, o servidor já deve ter **liberado o seu próprio assento** (não deixar bloqueado).

`prepare` repetido com o mesmo `id_transacao` é **idempotente**: devolve `READY` de novo sem trancar outro assento e sem
rebaixar uma transação já `COMITADO`.

### 6.4 `PUT /reservas/{id_transacao}/commit`

Fase 2 (sucesso). Sem corpo. O servidor marca `COMITADO` (gravando em disco), repassa o commit ao próximo da cadeia e responde:

| Código | Corpo | Significado |
|---|---|---|
| `200` | `{"id_transacao": "...", "mensagem": "Transacao COMMITTED com sucesso."}` | ACK (o resto da cadeia também confirmou) |
| `404` | `{"erro": "..."}` | transação desconhecida (nunca houve prepare, ou já foi desfeita) |
| `503` | `{"erro": "..."}` | commit feito localmente, mas o próximo não respondeu |

- **Idempotente**: repetir o commit de uma transação já `COMITADO` devolve `200`.
- No `503`, a decisão local **continua valendo** (não desfazer). O coordenador deve reenviar o commit até receber ACK.

### 6.5 `DELETE /reservas/{id_transacao}`

Fase 2 (falha/rollback). Sem corpo. O servidor **libera o assento**, apaga/marca a transação como abortada em disco,
repassa o DELETE ao próximo da cadeia e responde:

| Código | Corpo | Significado |
|---|---|---|
| `200` | `{"id_transacao": "...", "mensagem": "Transacao ABORTED com sucesso."}` | assento liberado (ou não existia) |
| `409` | `{"erro": "transacao ja comitada"}` | **não se desfaz** uma transação já `COMITADO` |

- **Idempotente**: apagar algo que não existe devolve `200`.
- Mesmo que o servidor não tenha trancado nada (caso do `409` no prepare), deve repassar o DELETE ao próximo, para que ninguém fique com assento preso.

### 6.6 `GET /reservas/{id_transacao}/status`

Usado na recuperação. `200`:

```json
{"id_transacao": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d", "status": "PREPARADO"}
```

`status` ∈ `PREPARADO`, `COMITADO`, `ABORTADO`. Transação desconhecida: `404` com `{"erro": "..."}`.
**Para quem consulta, `404` e `ABORTADO` significam a mesma coisa: liberar o assento.** (Quem apaga a transação ao abortar devolve `404`; quem mantém o registro devolve `ABORTADO`. Os dois estão certos.)

O coordenador só pode responder `COMITADO` depois de **gravar a decisão em disco**.

### 6.7 `GET /reservas` (opcional)

Lista o que o servidor tem guardado. Não é necessário para interoperar, mas ajuda a depurar o servidor do colega:

```json
{"servidor": "skyrim", "total": 1, "reservas": [
  {"id_transacao": "tx-A", "estado": "COMITADO", "coordenador_id": "morrowind", "id_carona": "sk-45", "relogio_logico": 14}
]}
```

---

## 7. Fluxo encadeado (2PC) e falhas

### 7.1 Sucesso (passageiro em Morrowind, destino em High Rock)

```
P  -> M : POST /api/reservas                         (API de cliente, não faz parte deste contrato)
M       : cria id_transacao, tranca o seu assento, grava PREPARADO
M  -> S : POST /api/consorcio/reservas/prepare       (corpo completo)
S       : tranca o seu assento, grava PREPARADO
S  -> H : POST /api/consorcio/reservas/prepare       (mesmo corpo)
H       : tranca o seu assento, grava PREPARADO
H  -> S -> M : 200 READY
M       : todos READY -> grava COMITADO (decisão)
M  -> S : PUT /api/consorcio/reservas/{id}/commit
S  -> H : PUT /api/consorcio/reservas/{id}/commit
H  -> S -> M : 200 ACK
M  -> P : 201 Created
```

Conflito: se H responde `409`, S desfaz o seu assento e repassa o `409` para M; M desfaz o seu e envia
`DELETE /reservas/{id}` pela cadeia (M → S → H, idempotente) e responde `409` ao passageiro.

### 7.2 O que cada servidor grava em disco

Para o commit, o rollback e a recuperação funcionarem, **cada servidor grava, por transação**, no mínimo:

| Campo | Para que serve |
|---|---|
| `id_transacao`, `estado` | estado atual (`PREPARADO`/`COMITADO`/...) |
| `coordenador_id`, `relogio_logico` | precedência e recuperação |
| `id_carona` (e o assento trancado) | liberar no rollback |
| `proximo` | para quem repassar commit/DELETE (é o próximo servidor da lista de trechos) |
| `anterior` | quem enviou o prepare; é a quem se pergunta o `/status` na recuperação |

Como um servidor só fala com vizinhos, um participante no meio da cadeia (ex.: High Rock) **não** consulta o coordenador diretamente:
ele pergunta ao `anterior`, que repassa a pergunta até o coordenador.
O formato do arquivo em si é livre, desde que o servidor consiga reconstruir isso depois de reiniciar.

### 7.3 Falhas e timeouts

| Situação | Comportamento esperado |
|---|---|
| Vizinho não responde ao `prepare` | abortar: liberar o assento local, devolver `503` (`ABORT`) para trás |
| Participante em `PREPARADO` não recebe commit/rollback | após timeout, consulta `GET /status` no `anterior`; `COMITADO` → comita, `ABORTADO`/`404` → libera |
| Coordenador cai **depois** de decidir `COMITADO` | ao voltar, lê o disco e reenvia o commit |
| Coordenador cai **antes** de decidir | ao voltar, aborta e envia `DELETE` pela cadeia |
| Servidor cai e volta | recarrega o disco; transações `PREPARADO` seguem o fluxo de recuperação acima |
| Servidor fora do ar | os outros continuam atendendo rotas que não passam por ele; **nunca** tratar como erro fatal |

Timeouts sugeridos: **1,5 s para conectar** e **3 s por salto** esperando resposta. Como o `prepare` é encadeado
(o servidor do meio espera o próximo antes de responder), quem está mais perto do coordenador deve esperar mais:
`3 s × número de saltos restantes`. Senão o coordenador aborta enquanto o participante ainda está esperando o próximo.

---

## 8. Critério de precedência

Dois servidores podem tentar trancar o **último assento** do mesmo trecho ao mesmo tempo, e os relógios físicos não são
confiáveis. Todos precisam usar a **mesma chave de ordenação**:

```
chave = (relogio_logico, coordenador_id)
```

- Vence a transação com **menor `relogio_logico`**.
- Em empate, vence a de **menor `coordenador_id`** em ordem alfabética (comparação byte a byte do ID em minúsculas;
  por exemplo, `cyrodiil` vence `morrowind`).
- Quem perde recebe `409` com `"status": "ABORT"` e `"motivo"` explicando.

**Política proposta (sem espera, evita deadlock):** o assento é concedido a quem o tranca primeiro; se ele já está
`PREPARADO`/`COMITADO` por outra transação, a nova recebe `409`. A chave acima decide **requisições que chegam juntas**
(enquanto o servidor ainda não trancou: processar na ordem da chave). Os dois lados precisam implementar a mesma política.

> Se preferirem a ordem fixa de prioridade por região (Elsweyr > Black Marsh > Cyrodiil > ... > Summerset Islands, das anotações),
> basta trocar a chave. O importante é que os dois usem a mesma.

---

## 9. Checklist de testes entre os dois servidores

Troquem `<host>` e `<porta>` pelo endereço do servidor do colega (e vice-versa). Marquem quando passar nos dois sentidos.

| # | Teste | Como | Esperado |
|---|---|---|---|
| 1 | Conectividade | `curl -i http://<host>:<porta>/api/consorcio/health` | `200` |
| 2 | Cabeçalhos de Lamport | `curl -i -H "X-Server-Id: teste" -H "X-Lamport-Clock: 50" .../health` | resposta com `X-Lamport-Clock` ≥ `51` |
| 3 | Busca | `curl ".../trechos?origem=Mournhold&destino=Daggerfall&visitados=morrowind"` | `200` com `itinerarios`, ou `404` se não houver rota |
| 4 | Prepare | `POST .../reservas/prepare` com o corpo da seção 6.3 | `200` com `READY` |
| 5 | Prepare repetido | repetir o teste 4 | `200` `READY`, sem duplicar |
| 6 | Status | `GET .../reservas/<id>/status` | `PREPARADO` |
| 7 | Commit | `PUT .../reservas/<id>/commit` | `200`; depois o status é `COMITADO` |
| 8 | Commit repetido | repetir o 7 | `200` |
| 9 | Rollback de comitada | `DELETE .../reservas/<id>` após commit | `409` |
| 10 | Rollback | prepare novo, depois `DELETE` | `200`; depois o status é `404` ou `ABORTADO` |
| 11 | Rollback repetido | repetir o 10 | `200` |
| 12 | Transação desconhecida | `PUT .../reservas/inexistente/commit` | `404` |
| 13 | Corpo inválido | `POST .../reservas/prepare` sem corpo | `400` |
| 14 | Persistência | reiniciar o servidor e consultar o status do teste 7 | continua `COMITADO` |
| 15 | Queda | derrubar um servidor e chamar `/health` dele | o outro dá timeout sem travar nem cair |
| 16 | Cadeia cruzando as máquinas | transação com 3 trechos, em 3 servidores de donos alternados | `READY` em todos, commit propagado, estados iguais ao final |
| 17 | Conflito | dois coordenadores disputando o último assento | exatamente **um** `READY`; o outro recebe `409` |
| 18 | Queda no meio | derrubar o servidor do meio durante o `prepare` | coordenador aborta e não sobra assento trancado |

Quando os 18 passarem nos dois sentidos, os servidores são interoperáveis.
