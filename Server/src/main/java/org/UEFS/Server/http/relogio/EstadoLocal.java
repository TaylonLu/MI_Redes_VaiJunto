package org.UEFS.Server.http.relogio;

import io.javalin.http.LockedResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class EstadoLocal {
    private static final int MAX_TENTATIVAS = 3;

    private final AtomicLong CLOCK = new AtomicLong(0);
    private boolean requesting = false;
    private boolean inCS = false;
    private boolean falhou = false;

    private long requestTimeStamp;

    private final List<String> pendingReplies = new ArrayList<>();
    private final List<String> deferredReplies = new ArrayList<>();

    private final ReentrantLock trava = new ReentrantLock();
    private final Condition respostasRecebidas = trava.newCondition();

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public void solicitarRegiaoCritica() {
        trava.lock();
        if (requesting) {
            throw new LockedResponse("Já existe uma solicitação em andamento.");
        }

        try {
            requestTimeStamp = CLOCK.addAndGet(1);
            requesting = true;
            pendingReplies.addAll(ServerConstant.getServidores());

            long meuTimeStamp = requestTimeStamp;

            trava.unlock();

            CMessage mensagem = new CMessage(MessageType.REQUEST, ServerConstant.getMEU_ID(), meuTimeStamp);

            for (String id : ServerConstant.getServidores()) {
                send(mensagem, id, 1);
            }

            trava.lock();

            long nanos = TimeUnit.SECONDS.toNanos(10);
            while (!pendingReplies.isEmpty()) {
                if (nanos <= 0L) {
                    falhou = true;
                    break;
                }

                nanos = respostasRecebidas.awaitNanos(nanos);
            }

            if (falhou) {
                abortar();
                throw new IllegalStateException("Servidor indisponível");
            }

            inCS = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            abortar();
            throw new RuntimeException(e);
        } finally {
            if (trava.isHeldByCurrentThread()) {
                trava.unlock();
            }
        }
    }

    public void ReceberRequest(CMessage mensagem) {
        trava.lock();
        try {
            // Atualiza o relógio
            CLOCK.set(Long.max(CLOCK.get(), mensagem.timeStamp()) + 1);

            switch (mensagem.tipo()) {
                case REPLY -> {
                    // Se recebemos permissão, removemos da lista de pendentes
                    pendingReplies.remove(mensagem.remetente());

                    // Se todos responderam, acorda a thread bloqueada no await()
                    if (pendingReplies.isEmpty()) respostasRecebidas.signalAll();
                }

                case REQUEST -> {
                    boolean deveAdiar = false;

                    if (inCS) deveAdiar = true; // Adiar resposta se já estou na região crítica
                    else if (requesting) {
                        if (requestTimeStamp < mensagem.timeStamp())
                            deveAdiar = true;
                        else // Verifica com desempate
                            deveAdiar = requestTimeStamp == mensagem.timeStamp() &&
                                        ServerConstant.getMEU_ID().compareTo(mensagem.remetente()) < 0;
                    }

                    if (deveAdiar)
                        deferredReplies.add(mensagem.remetente());
                    else enviarReply(mensagem.remetente());
                }
            }

        } finally {
            trava.unlock();
        }
    }

    public void liberarRegiaoCritica() {
        List<String> paraResponder;

        try {
            trava.lock();

            inCS = false;
            requesting = false;

            paraResponder = new ArrayList<>(deferredReplies);
            deferredReplies.clear();
        } finally {
            trava.unlock();
        }

        long meuTimeStamp = CLOCK.addAndGet(1);
        CMessage replyMSG = new CMessage(MessageType.REPLY, ServerConstant.getMEU_ID(), meuTimeStamp);

        for (String id : paraResponder) {
            try {
                send(replyMSG, id, 1);
            } catch (Exception e) {
                System.err.println("Falha ao responder servidor: " + id);
            }
        }
    }

    private void enviarReply(String destino) {
        send(new CMessage(MessageType.REPLY, ServerConstant.getMEU_ID(), CLOCK.get()), destino, 1);
    }

    private void send(CMessage mensagem, String id, int tentativa) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + id + "/api/server"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mensagem.json()))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete((res, ex) -> {
                    boolean ok = (ex == null) && res.statusCode() >= 200 && res.statusCode() < 300;

                    if (ok) return;

                    String motivo = (ex != null) ? ex.getMessage()
                                                 : "HTTP " + res.statusCode();

                    System.err.println("Falha ao enviar para " + id + " (tentativa " + tentativa + "): " + motivo);

                    if (tentativa < MAX_TENTATIVAS) {
                        CompletableFuture.delayedExecutor(500L * tentativa, TimeUnit.MILLISECONDS)
                                         .execute(() -> send(mensagem, id, tentativa + 1));
                    } else {
                        falhaDefinitiva(mensagem, id, motivo);
                    }
                });
    }

    private void falhaDefinitiva(CMessage mensagem, String id, String motivo) {
        if (mensagem.tipo() != MessageType.REQUEST) return;

        trava.lock();

        try {
            if (requesting && pendingReplies.contains(id)) {
                falhou = true;
                respostasRecebidas.signalAll();
            }
        } finally {
            trava.unlock();
        }
    }

    private void abortar() {
        List<String> paraResponder;

        trava.lock();

        try {
            requesting = false;
            inCS = false;
            falhou = false;
            pendingReplies.clear();

            paraResponder = new ArrayList<>(deferredReplies);
            deferredReplies.clear();
        } finally {
            trava.unlock();
        }

        long ts = CLOCK.addAndGet(1);
        CMessage reply = new CMessage(MessageType.REPLY, ServerConstant.getMEU_ID(), ts);
        for (String id : paraResponder)
            send(reply, id, 1);
    }

    public long getClock() {
        return CLOCK.get();
    }
}
