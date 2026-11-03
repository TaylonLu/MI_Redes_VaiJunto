Consultei a implementação atual da branch `parte_2` e comparei o fluxo com o `transacao.c`.

Há uma distinção importante: a reserva Java já implementa boa parte do 2PC encadeado, com `PREPARADO`, `COMITADO`, `ABORTADO`, `prepare`, `commit` e `rollback`. O que ainda precisa mudar não é reescrever tudo, mas corrigir alguns pontos para garantir que a decisão fique persistida e que as propagações possam ser retomadas após falhas.

Os principais problemas estão nos cancelamentos:

- O cancelamento do passageiro hoje libera as vagas e altera o estado antes de confirmar a propagação. Isso não segue a ordem do C.
- O cancelamento do motorista usa notificações assíncronas, mas não registra quais avisos falharam.
- O recuperador atual ignora todos os estados de cancelamento.
- O endpoint de cancelamento não diferencia claramente uma solicitação do passageiro de um aviso originado pelo motorista.

Vou separar as mudanças por arquivo. Não estou alterando o repositório.

## 1. Reserva: `PREPARE → COMMIT/ROLLBACK`

Arquivos: `TransacaoService.java`, `ReservaCoordenador.java`, `ReservaParticipante.java` e `RecuperadorTransacoes.java`.

O fluxo atual já está próximo do C:

1. O coordenador cria uma transação em `PREPARADO`.
2. Reserva os assentos locais.
3. Encaminha `prepare` ao próximo servidor.
4. Cada participante reserva os assentos locais e encaminha o restante.
5. Se todos responderem positivamente, o coordenador persiste `COMITADO` e envia `commit` pela cadeia.
6. Se algum servidor recusar, o coordenador envia `rollback`.

Eu manteria essa estrutura. Há, entretanto, três ajustes importantes.

### 1.1. Não ignorar falhas de rollback

Em `ReservaParticipante.rollback()`, atualmente a propagação pode falhar sem que isso seja registrado como uma pendência recuperável.

A alteração é fazer o rollback local ser idempotente e deixar a recuperação reenviar a decisão. O método `RecuperadorTransacoes` já tenta novamente os estados `ABORTADO`, então podemos preservar essa estrutura.

O método `desfazer()` de `TransacaoService` já tem a regra correta para impedir rollback de uma transação confirmada:

```
if (tx.getEstado() != EstadoTransacao.PREPARADO)
    return Resultado.CONFLITO;
```

Essa proteção deve continuar.

### 1.2. Não tratar ausência do próximo vizinho como propagação concluída

No `ReservaParticipante.commit()`, hoje:

```
boolean propagacaoPendente = false;

if (!isVazio(existente.getProximo())) {
    Vizinho proximo = buscarVizinho(existente.getProximo());
    if (proximo != null) {
        boolean entregue = repasse.commit(proximo, idTransacao);
        propagacaoPendente = !entregue;
    }
}
```

Se `buscarVizinho()` retornar `null`, o código informa que não há propagação pendente. Isso está incorreto: existe um próximo servidor, mas não foi possível localizá-lo.

Substitua por:

```
boolean propagacaoPendente = false;

if (!isVazio(existente.getProximo())) {
    Vizinho proximo = buscarVizinho(existente.getProximo());

    propagacaoPendente = proximo == null
            || !repasse.commit(proximo, idTransacao);
}
```

Faça a mesma correção na propagação de rollback.

### 1.3. Preservar a decisão antes de propagar

O `ReservaCoordenador.reservar()` já persiste o commit antes de enviá-lo ao próximo servidor. Essa ordem deve ser mantida.

O mesmo vale para o aborto: primeiro persistir `ABORTADO`, depois propagar.

## 2. Cancelamento pelo passageiro: propagação sequencial

Aqui está a mudança mais importante.

Atualmente, `TransacaoService.cancelarReserva()`:

- altera o estado para `CANCELADO_PELO_PASSAGEIRO`;
- libera os assentos;
- persiste;
- inicia uma propagação assíncrona.

Isso permite que um servidor apague sua reserva antes de saber se o próximo servidor conseguiu apagá-la.

No C, o coordenador registra `CANCELANDO`, envia o cancelamento ao próximo e só conclui a remoção local depois que a cadeia confirma.

### 2.1. Fluxo que vamos implementar

Coordenador A

CANCELANDO persistido

Envia cancelamento para B e aguarda a resposta.

Participante B

Envia cancelamento para C e aguarda a resposta.

Participante C

Se for o último, libera os assentos e apaga sua cópia.

Retorno da confirmação

C → B → A

Cada servidor libera os assentos e apaga sua cópia somente após a confirmação do próximo.

### 2.2. `TransacaoService.java`

Adicione um método para finalizar a remoção local. Ele deve ser chamado somente depois da confirmação do próximo servidor:

```
public Resultado concluirCancelamentoPassageiro(String idTransacao) {
    synchronized (caronaService) {
        Transacao tx = repo.getByID(idTransacao);

        if (tx == null) return Resultado.OK;

        List<Perna> pernas = pernasDe(tx);

        liberar(pernas, tx.getIdPassageiro());

        if (!caronaRepo.salvarNoDisco()) {
            reocupar(pernas, tx.getIdPassageiro());
            return Resultado.ERRO;
        }

        repo.remover(idTransacao);

        if (!repo.salvarNoDisco()) {
            repo.salvar(tx);
            reocupar(pernas, tx.getIdPassageiro());

            caronaRepo.salvarNoDisco();
            return Resultado.ERRO;
        }

        return Resultado.OK;
    }
}
```

Agora substitua `cancelarReserva()` por uma versão que persiste `CANCELANDO` antes de iniciar a propagação:

```
public boolean cancelarReserva(String idTransacao, String userID) {
    Transacao tx;

    synchronized (caronaService) {
        tx = repo.getByID(idTransacao);

        if (tx == null)
            throw new RecursoNaoEncontradoException("Reserva não encontrada.");

        if (!tx.getIdPassageiro().equalsIgnoreCase(userID))
            throw new ForbiddenException("Reserva não pertence a esse usuário.");

        if (tx.getEstado() == EstadoTransacao.CANCELANDO) {
            // Uma tentativa anterior já iniciou o cancelamento.
        } else if (tx.getEstado() != EstadoTransacao.COMITADO) {
            return false;
        } else {
            if (!tx.souDono(meuId))
                throw new IllegalStateException(
                        "O cancelamento deve ser iniciado pelo coordenador."
                );

            tx.setEstado(EstadoTransacao.CANCELANDO);
            repo.salvar(tx);

            if (!repo.salvarNoDisco()) {
                tx.setEstado(EstadoTransacao.COMITADO);
                repo.salvarNoDisco();
                return false;
            }
        }
    }

    // A comunicação acontece fora do synchronized.
    if (!isVazio(tx.getProximo())) {
        Vizinho proximo = ServerConstant.getVizinho(tx.getProximo());

        if (proximo == null || !client.cancelPassageiro(proximo, idTransacao))
            return false;
    }

    return concluirCancelamentoPassageiro(idTransacao) == Resultado.OK;
}
```

Observação: esse método pressupõe que adicionaremos `cancelPassageiro()` ao `PeerClient`, mostrado adiante. Também será necessário ajustar `CaronaController.cancelarReserva()` para responder com HTTP 202 quando o cancelamento permanecer pendente, em vez de informar sucesso definitivo.

### 2.3. `ReservaParticipante.java`

O cancelamento recebido pela rede precisa ser separado do cancelamento iniciado pelo passageiro.

Adicione:

```
public RespostaConsorcio cancelPassageiro(String idTransacao) {
    if (!idValido(idTransacao))
        throw new ServerException(400, "id da transação inválido");

    Transacao tx = transacoes.buscar(idTransacao);

    // Mensagem duplicada: a cópia já foi removida.
    if (tx == null) {
        return builder()
                .put("id_transacao", idTransacao)
                .put("mensagem", "Cancelamento já processado")
                .build(200);
    }

    if (tx.getEstado() != EstadoTransacao.COMITADO)
        throw new ServerException(409, "Transação não está disponível para cancelamento.");

    // Primeiro, encaminha ao próximo.
    if (!isVazio(tx.getProximo())) {
        Vizinho proximo = ServerConstant.getVizinho(tx.getProximo());

        if (proximo == null || !repasse.cancelPassageiro(proximo, idTransacao)) {
            throw new ServerException(503, "Próximo servidor indisponível.");
        }
    }

    // Somente depois da confirmação, apaga a cópia local.
    Resultado resultado = transacoes.concluirCancelamentoPassageiro(idTransacao);

    if (resultado != Resultado.OK)
        throw new ServerException(500, "Falha ao concluir cancelamento local.");

    return builder()
            .put("id_transacao", idTransacao)
            .put("mensagem", "Cancelamento confirmado")
            .build(200);
}
```

Esse método deve ser idempotente: receber a mesma mensagem duas vezes não pode gerar erro se a primeira já apagou a transação.

## 3. Cancelamento pelo motorista: propagação bidirecional

Aqui precisamos abandonar a lógica atual de `notificarCancelamento()`.

Hoje ela usa `CompletableFuture` e tenta avisar os servidores com base no itinerário ou no coordenador. O protocolo do C utiliza explicitamente `anterior` e `proximo`.

A propagação deve ser:

- O servidor que cancelou a carona envia para `anterior` e `proximo`.
- Um servidor que recebe a mensagem envia para ambos, exceto para quem acabou de enviá-la.
- Cada servidor persiste o estado antes de tentar propagar.
- Mensagens duplicadas não podem liberar assentos novamente nem interromper uma propagação pendente.

### 3.1. Alteração em `Transacao.java`

Precisamos persistir se os avisos para os dois vizinhos ainda estão pendentes.

Adicione estes campos:

```
private volatile boolean avisoAnteriorPendente;
private volatile boolean avisoProximoPendente;
```

Adicione getters e setters:

```
public boolean isAvisoAnteriorPendente() {
    return avisoAnteriorPendente;
}

public void setAvisoAnteriorPendente(boolean valor) {
    this.avisoAnteriorPendente = valor;
}

public boolean isAvisoProximoPendente() {
    return avisoProximoPendente;
}

public void setAvisoProximoPendente(boolean valor) {
    this.avisoProximoPendente = valor;
}
```

Importante: esses campos precisam entrar também no construtor `@JsonCreator`, com `@JsonProperty`, e nos parâmetros usados para reconstruir uma `Transacao`. Caso contrário, as pendências desaparecem após reiniciar o servidor.

### 3.2. `TransacaoService.java`: registrar o cancelamento

Adicione um método que registra o estado e as pendências:

```
public boolean registrarCancelamentoMotorista(
        String idTransacao,
        String remetente
) {
    synchronized (caronaService) {
        Transacao tx = repo.getByID(idTransacao);

        if (tx == null) return false;

        if (tx.getEstado() == EstadoTransacao.CANCELANDO)
            return false;

        if (tx.getEstado() != EstadoTransacao.COMITADO
                && tx.getEstado() != EstadoTransacao.CANCELADO_PELO_MOTORISTA) {
            return false;
        }

        if (tx.getEstado() == EstadoTransacao.COMITADO) {
            tx.setEstado(EstadoTransacao.CANCELADO_PELO_MOTORISTA);

            tx.setAvisoAnteriorPendente(
                    !isVazio(tx.getAnterior())
                            && !tx.getAnterior().equals(remetente)
            );

            tx.setAvisoProximoPendente(
                    !isVazio(tx.getProximo())
                            && !tx.getProximo().equals(remetente)
            );

            liberar(pernasDe(tx), tx.getIdPassageiro());

            if (!caronaRepo.salvarNoDisco()) {
                reocupar(pernasDe(tx), tx.getIdPassageiro());
                tx.setEstado(EstadoTransacao.COMITADO);
                return false;
            }
        }

        repo.salvar(tx);

        return repo.salvarNoDisco();
    }
}
```

Esse é o núcleo da alteração, mas ainda precisamos de um método para enviar os avisos e atualizar as pendências após cada confirmação.

### 3.3. `PeerClient.java`

Adicione métodos distintos para os dois tipos de cancelamento:

```
public boolean cancelPassageiro(Vizinho proximo, String idTransacao) {
    Resposta r = enviar(
            proximo,
            "PUT",
            "/api/consorcio/reservas/" + idTransacao + "/cancel/passageiro",
            null,
            Duration.ofSeconds(DURATION_S)
    );

    return !r.redeFalhou() && r.status() == 200;
}

public boolean cancelMotorista(Vizinho vizinho, String idTransacao) {
    Resposta r = enviar(
            vizinho,
            "PUT",
            "/api/consorcio/reservas/" + idTransacao + "/cancel/motorista",
            null,
            Duration.ofSeconds(DURATION_S)
    );

    return !r.redeFalhou() && r.status() == 200;
}
```

A identificação do remetente será feita pelo header `X-Server-Id`, que o método `enviar()` já envia.

### 3.4. `ReservaParticipante.java`

Adicione o tratamento da notificação do motorista:

```
public RespostaConsorcio cancelMotorista(
        String idTransacao,
        String remetente
) {
    if (!idValido(idTransacao))
        throw new ServerException(400, "id da transação inválido");

    Transacao tx = transacoes.buscar(idTransacao);

    if (tx == null) {
        return builder()
                .put("id_transacao", idTransacao)
                .put("mensagem", "Transação inexistente")
                .build(200);
    }

    transacoes.registrarCancelamentoMotorista(idTransacao, remetente);

    return builder()
            .put("id_transacao", idTransacao)
            .put("mensagem", "Cancelamento registrado")
            .build(200);
}
```

O método acima registra a mensagem. A propagação para os dois vizinhos será executada pelo recuperador, que também tratará as falhas.

### 3.5. `HttpServer.java` e `ServerManager.java`

Atualmente existe apenas:

```
put("/{id}/cancel", manager::cancel, Acesso.PUBLIC);
```

Substitua por duas rotas:

```
put("/{id}/cancel/passageiro", manager::cancelPassageiro, Acesso.PUBLIC);
put("/{id}/cancel/motorista", manager::cancelMotorista, Acesso.PUBLIC);
```

No `ServerManager`, os métodos precisam encaminhar o ID e o remetente:

```
public void cancelPassageiro(@NotNull Context ctx) {
    RespostaConsorcio resposta =
            participante.cancelPassageiro(ctx.pathParam("id"));

    ctx.status(resposta.status()).json(resposta.corpo());
}

public void cancelMotorista(@NotNull Context ctx) {
    RespostaConsorcio resposta =
            participante.cancelMotorista(
                    ctx.pathParam("id"),
                    ctx.header("X-Server-Id")
            );

    ctx.status(resposta.status()).json(resposta.corpo());
}
```

A implementação do envio bidirecional e da atualização individual dos flags de pendência ficará centralizada no recuperador.

## 4. Recuperação após falhas e reinicialização

O recuperador atual já contempla:

| Estado                     | Comportamento atual                                  |
| -------------------------- | ---------------------------------------------------- |
| `PREPARADO`                | Coordenador aborta; participante consulta o anterior |
| `COMITADO`                 | Reenvia commit ao próximo                            |
| `ABORTADO`                 | Reenvia rollback ao próximo                          |
| `CANCELANDO`               | Ignorado                                             |
| `CANCELADO_PELO_MOTORISTA` | Ignorado                                             |

Precisamos acrescentar os dois estados de cancelamento.

### 4.1. `RecuperadorTransacoes.java`

Altere o `switch`:

```
switch (tx.getEstado()) {
    case COMITADO -> propagarCommit(tx);
    case ABORTADO -> propagarRollback(tx);
    case PREPARADO -> recuperarPreparada(tx);
    case CANCELANDO -> recuperarCancelamentoPassageiro(tx);
    case CANCELADO_PELO_MOTORISTA -> recuperarCancelamentoMotorista(tx);
    default -> {
    }
}
```

O cancelamento do passageiro deve ser retomado somente pelo coordenador:

```
private void recuperarCancelamentoPassageiro(Transacao tx) {
    if (!tx.souDono(meuId)) return;

    if (transacoes.retentarCancelamentoPassageiro(tx.getId())) {
        log.info("[RECUPERAÇÃO] Cancelamento '{}' concluído.", tx.getId());
    }
}
```

O método `retentarCancelamentoPassageiro()` deve executar a mesma lógica do cancelamento inicial: enviar ao próximo, aguardar a resposta e, somente depois, apagar a cópia local.

Para o motorista, o recuperador deve tentar cada direção separadamente:

```
private void recuperarCancelamentoMotorista(Transacao tx) {
    if (tx.isAvisoAnteriorPendente()) {
        Vizinho anterior = ServerConstant.getVizinho(tx.getAnterior());

        if (anterior != null
                && peerClient.cancelMotorista(anterior, tx.getId())) {
            transacoes.confirmarAvisoMotorista(tx.getId(), true);
        }
    }

    if (tx.isAvisoProximoPendente()) {
        Vizinho proximo = ServerConstant.getVizinho(tx.getProximo());

        if (proximo != null
                && peerClient.cancelMotorista(proximo, tx.getId())) {
            transacoes.confirmarAvisoMotorista(tx.getId(), false);
        }
    }
}
```

`confirmarAvisoMotorista(id, true)` deve limpar e persistir `avisoAnteriorPendente`; com `false`, deve fazer o mesmo para `avisoProximoPendente`.

Assim, se o envio para `anterior` funcionar e o envio para `proximo` falhar, o servidor não precisará repetir o primeiro aviso.

### 4.2. Recuperação de assentos

Também é necessário corrigir `reaplicarOcupacao()`.

Atualmente ele reaplica ocupações para `PREPARADO` e `COMITADO`. Isso está correto, desde que transações canceladas pelo motorista e em `CANCELANDO` não voltem a ocupar assentos.

Mantenha a condição restrita a:

```
if (t.getEstado() == EstadoTransacao.PREPARADO
        || t.getEstado() == EstadoTransacao.COMITADO) {
    reocupar(pernasDe(t), t.getIdPassageiro());
}
```

## 5. Testes que precisam ser feitos

Eu não vou afirmar que esses testes passaram: consultei o código remoto, mas não executei a aplicação nem os testes do projeto. Estes são os cenários que devem entrar na suíte de testes.

| Cenário                             | Ação                                            | Resultado esperado                              |
| ----------------------------------- | ----------------------------------------------- | ----------------------------------------------- |
| Falha no prepare                    | Desligar um participante durante o `PREPARE`    | Coordenador aborta e propaga rollback           |
| Falha no commit                     | Desligar um participante depois do commit local | Recuperador reenvia commit                      |
| Falha no cancelamento do passageiro | Desligar o próximo servidor                     | Coordenador permanece em `CANCELANDO`           |
| Retorno do servidor                 | Religar o participante                          | Recuperador conclui o cancelamento              |
| Falha no aviso do motorista         | Desligar somente o servidor anterior            | Aviso anterior permanece pendente               |
| Falha parcial                       | Um vizinho confirma e o outro falha             | Apenas o aviso pendente é repetido              |
| Mensagem duplicada                  | Enviar duas vezes o mesmo cancelamento          | Nenhuma liberação duplicada ou inconsistência   |
| Reinicialização                     | Reiniciar com pendências persistidas            | Recuperador retoma o trabalho                   |
| Cancelamentos concorrentes          | Passageiro e motorista cancelam simultaneamente | Estado final consistente, sem reocupar assentos |
| Rollback após commit                | Enviar rollback para transação confirmada       | Servidor responde conflito                      |

### Atenção especial: cancelamentos concorrentes

Eu adotaria a seguinte regra de precedência, compatível com o comportamento do C:

- Se o cancelamento do passageiro já chegou a `CANCELANDO`, uma notificação do motorista não deve sobrescrever essa decisão.
- Se o cancelamento do motorista já foi persistido, o cancelamento do passageiro não deve apagar a transação, pois ela precisa continuar visível ao passageiro.
- Uma transação `ABORTADO` nunca pode voltar a ser confirmada.
- Uma transação cancelada nunca pode voltar a ocupar assentos.

## Ordem de aplicação

Há um detalhe importante antes de você copiar os trechos: os códigos acima mostram as mudanças centrais, mas ainda não formam um patch compilável completo. Precisamos completar as assinaturas de `Repasse`, `TransacaoService` e `CaronaController`, além dos construtores JSON de `Transacao`, em conjunto. Também é necessário definir o tratamento de falha na persistência dos dois arquivos (`transacoes.json` e `caronas.json`), porque eles não são gravados atomicamente.

Minha sugestão é aplicar em duas etapas:

1. Primeiro, concluir e testar o cancelamento sequencial do passageiro, incluindo o endpoint e a resposta HTTP 202.
2. Depois, concluir o cancelamento bidirecional do motorista, os flags persistentes e a recuperação automática.

Isso evita introduzir os dois protocolos de cancelamento ao mesmo tempo e dificulta menos a identificação de erros.

O ponto mais importante é não copiar os métodos isoladamente ainda: há dependências entre eles, e prefiro entregar o conjunto completo de cada etapa com as assinaturas compatíveis com o projeto, em vez de deixar você com código que não compila.
