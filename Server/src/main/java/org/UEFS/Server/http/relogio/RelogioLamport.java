package org.UEFS.Server.http.relogio;

import java.util.concurrent.atomic.AtomicInteger;

public class RelogioLamport {
    private final AtomicInteger tempo = new AtomicInteger(0);

    public int tique() {
        return tempo.incrementAndGet();
    }

    public int atualizar(int tempoRecebido) {
        return tempo.updateAndGet(T -> Math.max(T, tempoRecebido) + 1);
    }

    public int getTempo() {
        return tempo.get();
    }
}
