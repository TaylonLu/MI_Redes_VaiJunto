import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Busca de itinerários de carona em vários servidores (versão distribuída do seu BFS).
 * <p>
 * Cada servidor guarda apenas as caronas que ele criou. Uma mensagem EXPANDIR carrega o caminho
 * parcial inteiro (path-vector): o servidor que recebe expande com as SUAS caronas e repassa o
 * caminho para quem pode continuar dali. Término por Dijkstra-Scholten, uma árvore por consulta.
 * <p>
 * Rodar (Java 17+): java ItinerariosDistribuidos.java
 */
public class Simulacao {

    static final int MAX_ITINERARIOS = 20;
    static final int MAX_TRECHOS = 6;

    // ===================== Domínio (espelha o seu código) =====================
    record Trecho(int inicio, int fim) implements Serializable {}

    record BuscaCaronaRequest(int origemDesejada, int destinoDesejado, int vagasRequeridas,
                              LocalDateTime data_desejada) implements Serializable {}

    /** Parâmetros de uma consulta (viajam com cada mensagem). */
    record Consulta(BuscaCaronaRequest busca, int maxItinerarios, int maxTrechos) implements Serializable {}

    static class Carona {
        final int id, servidorDono, motorista, capacidade;
        final LocalDateTime data;
        final double valor;
        final List<Trecho> rota;
        private boolean aberta = true;
        private final Map<Trecho, Integer> ocupadas = new HashMap<>();

        Carona(int id, int servidorDono, int motorista, int capacidade, LocalDateTime data, double valor, List<Trecho> rota) {
            this.id = id; this.servidorDono = servidorDono; this.motorista = motorista;
            this.capacidade = capacidade; this.data = data; this.valor = valor; this.rota = rota;
        }
        // Lidas pelas threads dos servidores e escritas por quem reserva/fecha: sincronizadas.
        synchronized boolean emAberto() { return aberta; }
        synchronized void fechar() { aberta = false; }
        synchronized int vagas(Trecho t) { return capacidade - ocupadas.getOrDefault(t, 0); }
        synchronized boolean ocupar(Trecho t, int n) {
            if (vagas(t) < n) return false;
            ocupadas.merge(t, n, Integer::sum);
            return true;
        }
    }

    record ArestaTrecho(int destino, Carona carona, Trecho trecho) {}

    /** O que trafega na rede: só referências leves, nunca a Carona inteira. */
    record PassoRef(int caronaId, int servidorDono, int de, int para, LocalDateTime dataCarona) implements Serializable {}

    static PassoRef ref(ArestaTrecho a) {
        return new PassoRef(a.carona().id, a.carona().servidorDono, a.trecho().inicio(), a.trecho().fim(), a.carona().data);
    }

    /**
     * Grafo de referência: público, imutável, igual em todos os servidores.
     */
        record GrafoRef(Map<Integer, Set<Integer>> adj) {
            boolean adjacente(int u, int v) {
                return adj.getOrDefault(u, Set.of()).contains(v);
            }
        }

    // ===================== As duas funções do seu código, sem mudar a lógica =====================
    static void construirGrafoInto(Map<Integer, List<ArestaTrecho>> grafo, Collection<Carona> caronas, BuscaCaronaRequest busca) {
        for (Carona carona : caronas) {
            if (!carona.emAberto()) continue;
            if (!carona.data.toLocalDate().equals(busca.data_desejada().toLocalDate())) continue;
            for (Trecho T : carona.rota) {
                if (carona.vagas(T) < busca.vagasRequeridas()) continue;
                grafo.computeIfAbsent(T.inicio(), k -> new ArrayList<>()).add(new ArestaTrecho(T.fim(), carona, T));
            }
        }
    }

    static Set<Integer> cidadesDoCaminho(List<PassoRef> caminho, int origem) {
        Set<Integer> s = new HashSet<>();
        s.add(origem);
        for (PassoRef p : caminho) s.add(p.para());
        return s;
    }

    /** Oráculo: o seu BFS original, rodando sobre TODAS as caronas num lugar só. Usado nos testes. */
    static List<List<PassoRef>> oraculo(Collection<Carona> todas, Consulta c) {
        BuscaCaronaRequest busca = c.busca();
        Map<Integer, List<ArestaTrecho>> grafo = new HashMap<>();
        construirGrafoInto(grafo, todas, busca);

        List<List<PassoRef>> itinerarios = new ArrayList<>();
        Queue<List<PassoRef>> fila = new ArrayDeque<>();
        for (ArestaTrecho inicial : grafo.getOrDefault(busca.origemDesejada(), List.of()))
            fila.add(List.of(ref(inicial)));

        while (!fila.isEmpty()) {
            List<PassoRef> caminho = fila.poll();
            PassoRef ultima = caminho.get(caminho.size() - 1);
            int cidadeAtual = ultima.para();
            if (cidadeAtual == busca.destinoDesejado()) {
                itinerarios.add(caminho);
                if (itinerarios.size() >= c.maxItinerarios()) break;
                continue;
            }
            if (caminho.size() >= c.maxTrechos()) continue;
            Set<Integer> visitadas = cidadesDoCaminho(caminho, busca.origemDesejada());
            for (ArestaTrecho proxima : grafo.getOrDefault(cidadeAtual, List.of())) {
                if (visitadas.contains(proxima.destino())) continue;
                if (proxima.carona().data.isBefore(ultima.dataCarona())) continue;
                List<PassoRef> novo = new ArrayList<>(caminho);
                novo.add(ref(proxima));
                fila.add(novo);
            }
        }
        return itinerarios;
    }

    // ===================== Mensagens =====================
    interface Msg extends Serializable {}
    record Iniciar(long qid, Consulta consulta, CompletableFuture<List<List<PassoRef>>> resposta) implements Msg {}   // local
    record Expandir(long qid, Consulta consulta, List<PassoRef> caminho, int remetente) implements Msg {}
    record Encontrado(long qid, List<PassoRef> caminho, int remetente) implements Msg {}
    record Ack(long qid) implements Msg {}
    record Cancelar(long qid) implements Msg {}   // limite de itinerários atingido (não participa do término)
    record Fim(long qid) implements Msg {}

    // ===================== Rede =====================
    interface Rede {
        void enviar(int destino, Msg m);
        Collection<Integer> todos();
    }

    static class RedeLocal implements Rede {
        final Map<Integer, Servidor> servidores = new HashMap<>();
        public void enviar(int destino, Msg m) { servidores.get(destino).caixa.add(m); }
        public Collection<Integer> todos() { return servidores.keySet(); }
    }

    /** Atrasa cada mensagem de forma aleatória (reordena a entrega). Semente fixa = teste reproduzível. */
    static class RedeComAtraso implements Rede {
        final Rede interna;
        final Random rnd;
        final int maxMs;
        final ScheduledExecutorService agenda = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r); t.setDaemon(true); return t; });

        RedeComAtraso(Rede interna, long semente, int maxMs) { this.interna = interna; this.rnd = new Random(semente); this.maxMs = maxMs; }

        public void enviar(int destino, Msg m) {
            long ms;
            synchronized (rnd) { ms = rnd.nextInt(maxMs + 1); }
            agenda.schedule(() -> interna.enviar(destino, m), ms, TimeUnit.MILLISECONDS);
        }
        public Collection<Integer> todos() { return interna.todos(); }
    }

    // ===================== Servidor =====================
    static class Servidor implements Runnable {
        final int id;
        final Set<Integer> meus;                      // cidades que este servidor possui
        final GrafoRef ref;
        final Map<Integer, List<Integer>> donos;      // cidade -> servidores donos (público)
        final Rede rede;
        final long ttlNanos;
        final BlockingQueue<Msg> caixa = new LinkedBlockingQueue<>();
        final List<Carona> caronas = new CopyOnWriteArrayList<>();
        final AtomicInteger seqCarona = new AtomicInteger();
        final AtomicLong contador = new AtomicLong();
        final Map<Long, Estado> estados = new ConcurrentHashMap<>();   // só o ator escreve; leitura segura nos testes
        Thread thread;

        static class Estado {
            final Map<Integer, List<ArestaTrecho>> grafo;     // grafo LOCAL desta consulta (snapshot)
            final Consulta consulta;
            final long criadoEm = System.nanoTime();
            boolean cancelado;
            // Dijkstra-Scholten
            boolean engajado;
            int paiDs = -1;
            int pendentes;
            // só no iniciador
            boolean iniciador, respondido;
            CompletableFuture<List<List<PassoRef>>> resposta;
            final List<List<PassoRef>> achados = new ArrayList<>();

            Estado(Map<Integer, List<ArestaTrecho>> grafo, Consulta consulta) { this.grafo = grafo; this.consulta = consulta; }
        }

        Servidor(int id, Set<Integer> meus, GrafoRef ref, Map<Integer, List<Integer>> donos, Rede rede, long ttlMs) {
            this.id = id; this.meus = meus; this.ref = ref; this.donos = donos; this.rede = rede;
            this.ttlNanos = ttlMs * 1_000_000L;
        }

        void iniciarThread() { thread = new Thread(this, "servidor-" + id); thread.setDaemon(true); thread.start(); }
        void parar() { if (thread != null) thread.interrupt(); }

        // ---------- Criar carona (substitui a criação de arestas) ----------
        Carona criarCarona(int motorista, LocalDateTime data, int capacidade, double valor, List<Trecho> rota) {
            if (rota.isEmpty() || capacidade <= 0 || !(valor >= 0.0)) throw new IllegalArgumentException("carona inválida");
            for (int i = 0; i < rota.size(); i++) {
                Trecho t = rota.get(i);
                if (!meus.contains(t.inicio()) || !meus.contains(t.fim()))
                    throw new IllegalArgumentException("servidor " + id + " não possui os dois vértices de " + t);
                if (!ref.adjacente(t.inicio(), t.fim()))
                    throw new IllegalArgumentException("trecho fora do grafo de referência: " + t);
                if (i > 0 && rota.get(i - 1).fim() != t.inicio())
                    throw new IllegalArgumentException("trechos não encadeados");
            }
            Carona c = new Carona(id * 1_000_000 + seqCarona.incrementAndGet(), id, motorista, capacidade, data, valor, List.copyOf(rota));
            caronas.add(c);
            return c;
        }

        // ---------- API para clientes (qualquer thread) ----------
        CompletableFuture<List<List<PassoRef>>> buscarItinerarios(BuscaCaronaRequest busca) {
            return buscarItinerarios(new Consulta(busca, MAX_ITINERARIOS, MAX_TRECHOS));
        }

        CompletableFuture<List<List<PassoRef>>> buscarItinerarios(Consulta c) {
            CompletableFuture<List<List<PassoRef>>> f = new CompletableFuture<>();
            BuscaCaronaRequest b = c.busca();
            if (!donos.containsKey(b.origemDesejada()) || !donos.containsKey(b.destinoDesejado())) {
                f.completeExceptionally(new IllegalArgumentException("cidade inexistente"));
                return f;
            }
            long qid = ((long) id << 40) | contador.incrementAndGet();   // único sem coordenação
            caixa.add(new Iniciar(qid, c, f));
            return f;
        }

        static int iniciadorDe(long qid) { return (int) (qid >>> 40); }

        // ---------- Loop do ator ----------
        public void run() {
            long ultimaVarredura = System.nanoTime();
            try {
                for(;;) {
                    Msg m = caixa.poll(250, TimeUnit.MILLISECONDS);
                    if (m != null) tratar(m);
                    long agora = System.nanoTime();
                    if (agora - ultimaVarredura > 250_000_000L) { varrerExpirados(); ultimaVarredura = agora; }
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        void tratar(Msg m) {
            if (m instanceof Iniciar x)         aoIniciar(x);
            else if (m instanceof Expandir x)   aoExpandir(x);
            else if (m instanceof Encontrado x) aoEncontrado(x);
            else if (m instanceof Ack x)        aoAck(x);
            else if (m instanceof Cancelar(long qid1))   { Estado st = estados.get(qid1); if (st != null) st.cancelado = true; }
            else if (m instanceof Fim(long qid))        estados.remove(qid);
        }

        /** TTL: libera estado órfão (iniciador caiu, servidor parou) e avisa o cliente. */
        void varrerExpirados() {
            long agora = System.nanoTime();
            for (Iterator<Estado> it = estados.values().iterator(); it.hasNext(); ) {
                Estado st = it.next();
                if (agora - st.criadoEm < ttlNanos) continue;
                if (st.iniciador && !st.respondido) {
                    st.respondido = true;
                    st.resposta.completeExceptionally(new TimeoutException("consulta expirou (TTL)"));
                }
                it.remove();
            }
        }

        // ---------- Mensagens que participam do término: toda enviada incrementa 'pendentes' ----------
        void enviarContado(Estado st, int destino, Msg m) {
            rede.enviar(destino, m);
            st.pendentes++;
        }

        void aoIniciar(Iniciar m) {
            Map<Integer, List<ArestaTrecho>> grafo = new HashMap<>();
            construirGrafoInto(grafo, caronas, m.consulta().busca());       // snapshot local desta consulta
            Estado st = new Estado(grafo, m.consulta());
            st.iniciador = true; st.engajado = true; st.resposta = m.resposta();
            estados.put(m.qid(), st);
            for (int t : donos.get(m.consulta().busca().origemDesejada()))
                enviarContado(st, t, new Expandir(m.qid(), m.consulta(), List.of(), id));
        }

        void aoExpandir(Expandir m) {
            Estado st = estados.get(m.qid());
            if (st == null) {
                Map<Integer, List<ArestaTrecho>> grafo = new HashMap<>();
                construirGrafoInto(grafo, caronas, m.consulta().busca());
                st = new Estado(grafo, m.consulta());
                estados.put(m.qid(), st);
            }
            boolean engajaAgora = !st.engajado;
            if (engajaAgora) { st.engajado = true; st.paiDs = m.remetente(); }

            if (!st.cancelado) expandir(st, m);

            // Só a mensagem que engajou o nó tem o ack adiado; as demais são confirmadas na hora.
            if (!engajaAgora) rede.enviar(m.remetente(), new Ack(m.qid()));
            verificarTermino(m.qid(), st);
        }

        /** O corpo do seu while do BFS, para UM caminho parcial. */
        void expandir(Estado st, Expandir m) {
            Consulta c = m.consulta();
            BuscaCaronaRequest busca = c.busca();
            List<PassoRef> caminho = m.caminho();
            PassoRef ultima = caminho.isEmpty() ? null : caminho.getLast();
            int cidadeAtual = ultima == null ? busca.origemDesejada() : ultima.para();

            if (ultima != null && cidadeAtual == busca.destinoDesejado()) {
                // Todos os donos do destino recebem o mesmo caminho: só o de menor id reporta.
                if (donos.get(cidadeAtual).getFirst() == id)
                    enviarContado(st, Servidor.iniciadorDe(m.qid()), new Encontrado(m.qid(), caminho, id));
                return;
            }
            if (caminho.size() >= c.maxTrechos()) return;

            Set<Integer> visitadas = cidadesDoCaminho(caminho, busca.origemDesejada());
            for (ArestaTrecho proxima : st.grafo.getOrDefault(cidadeAtual, List.of())) {
                if (visitadas.contains(proxima.destino())) continue;
                if (ultima != null && proxima.carona().data.isBefore(ultima.dataCarona())) continue;

                List<PassoRef> novo = new ArrayList<>(caminho);
                novo.add(ref(proxima));
                // Quem pode continuar dali: os donos da cidade de destino do trecho.
                for (int t : donos.get(proxima.destino()))
                    enviarContado(st, t, new Expandir(m.qid(), c, novo, id));
            }
        }

        void aoEncontrado(Encontrado m) {           // só chega ao iniciador
            Estado st = estados.get(m.qid());
            if (st != null && st.iniciador && !st.respondido) {
                st.achados.add(m.caminho());
                if (st.achados.size() >= st.consulta.maxItinerarios()) {
                    responder(st);                  // o cliente já recebe a resposta
                    st.cancelado = true;            // e o resto da consulta é interrompido
                    for (int t : rede.todos()) rede.enviar(t, new Cancelar(m.qid()));
                }
            }
            rede.enviar(m.remetente(), new Ack(m.qid()));
        }

        void aoAck(Ack m) {
            Estado st = estados.get(m.qid());
            if (st == null) return;
            st.pendentes--;
            verificarTermino(m.qid(), st);
        }

        void verificarTermino(long qid, Estado st) {
            if (!st.engajado || st.pendentes > 0) return;
            st.engajado = false;
            if (st.iniciador) {                     // nenhuma mensagem desta consulta em trânsito
                responder(st);
                for (int t : rede.todos()) rede.enviar(t, new Fim(qid));
            } else {
                rede.enviar(st.paiDs, new Ack(qid));
            }
        }

        void responder(Estado st) {
            if (st.respondido) return;
            st.respondido = true;
            List<List<PassoRef>> r = new ArrayList<>(st.achados);
            r.sort((a, b) -> Integer.compare(a.size(), b.size()));    // menos trechos primeiro (estável)
            st.resposta.complete(r);
        }
    }

    // ===================== Montagem de clusters (testes) =====================
    static class Cluster implements AutoCloseable {
        final List<Servidor> servidores = new ArrayList<>();
        final RedeLocal local = new RedeLocal();
        RedeComAtraso atraso;

        Cluster(GrafoRef ref, List<Set<Integer>> vertices, Long sementeAtraso, long ttlMs) {
            Map<Integer, List<Integer>> donos = new HashMap<>();
            for (int s = 0; s < vertices.size(); s++)
                for (int v : vertices.get(s)) donos.computeIfAbsent(v, k -> new ArrayList<>()).add(s);
            Rede rede = local;
            if (sementeAtraso != null) { atraso = new RedeComAtraso(local, sementeAtraso, 5); rede = atraso; }
            for (int s = 0; s < vertices.size(); s++) {
                Servidor sv = new Servidor(s, vertices.get(s), ref, donos, rede, ttlMs);
                servidores.add(sv);
                local.servidores.put(s, sv);
            }
            servidores.forEach(Servidor::iniciarThread);
        }

        List<Carona> todasCaronas() {
            List<Carona> l = new ArrayList<>();
            servidores.forEach(s -> l.addAll(s.caronas));
            return l;
        }

        Carona achar(int caronaId) {
            for (Carona c : servidores.get(caronaId / 1_000_000).caronas) if (c.id == caronaId) return c;
            return null;
        }

        int estadosResiduais() { return servidores.stream().mapToInt(s -> s.estados.size()).sum(); }

        boolean aguardarSemEstado(long ms) throws InterruptedException {
            long fim = System.currentTimeMillis() + ms;
            while (System.currentTimeMillis() < fim) { if (estadosResiduais() == 0) return true; Thread.sleep(20); }
            return estadosResiduais() == 0;
        }

        public void close() {
            servidores.forEach(Servidor::parar);
            if (atraso != null) atraso.agenda.shutdownNow();
        }
    }

    // ===================== Cenário de exemplo (cidades 1..6, servidores A=0, B=1, C=2) =====================
    static final LocalDate DIA = LocalDate.of(2026, 10, 5);
    static LocalDateTime h(int hora, int min) { return DIA.atTime(hora, min); }

    static GrafoRef grafoExemplo() {
        int[][] l = {{1, 2, 3, 4}, {2, 1, 5, 4, 6}, {3, 1, 4}, {4, 1, 2, 3, 5, 6}, {5, 2, 4, 6}, {6, 2, 4, 5}};
        Map<Integer, Set<Integer>> adj = new HashMap<>();
        for (int[] linha : l) for (int i = 1; i < linha.length; i++) adj.computeIfAbsent(linha[0], k -> new HashSet<>()).add(linha[i]);
        return new GrafoRef(adj);
    }

    static List<Set<Integer>> verticesExemplo() { return List.of(Set.of(1, 2, 3), Set.of(4, 5, 6), Set.of(2, 3, 4, 5)); }

    static Cluster clusterExemplo(Long atraso, long ttlMs) { return new Cluster(grafoExemplo(), verticesExemplo(), atraso, ttlMs); }

    static Map<String, Carona> caronasExemplo(Cluster c) {
        Servidor A = c.servidores.get(0), B = c.servidores.get(1), C = c.servidores.get(2);
        Map<String, Carona> m = new LinkedHashMap<>();
        m.put("A1", A.criarCarona(10, h(8, 0), 2, 10.0, List.of(new Trecho(1, 2))));
        m.put("A2", A.criarCarona(11, h(8, 30), 2, 8.0, List.of(new Trecho(1, 3))));
        m.put("C1", C.criarCarona(12, h(9, 0), 2, 6.0, List.of(new Trecho(2, 4))));
        m.put("C2", C.criarCarona(13, h(7, 0), 2, 5.0, List.of(new Trecho(3, 4))));   // sai ANTES de A2: nunca encadeia
        m.put("C3", C.criarCarona(14, h(9, 30), 2, 7.0, List.of(new Trecho(2, 5))));
        m.put("B1", B.criarCarona(15, h(10, 0), 2, 4.0, List.of(new Trecho(4, 6))));
        m.put("B2", B.criarCarona(16, h(11, 0), 2, 3.0, List.of(new Trecho(5, 6))));
        m.put("B3", B.criarCarona(17, h(10, 30), 2, 9.0, List.of(new Trecho(4, 5), new Trecho(5, 6))));
        return m;
    }

    static String fmt(List<PassoRef> cam, Map<Integer, String> nomes) {
        StringJoiner j = new StringJoiner(" -> ");
        for (PassoRef p : cam) j.add(nomes.getOrDefault(p.caronaId(), "?" + p.caronaId()) + "(" + p.de() + ">" + p.para() + ")");
        return j.toString();
    }

    // ===================== Mini framework de teste =====================
    static int falhas = 0, total = 0;

    static void check(String nome, boolean ok) {
        total++;
        if (!ok) falhas++;
        System.out.println((ok ? "  ok     " : "  FALHA  ") + nome);
    }

    static BuscaCaronaRequest busca(int a, int b, int vagas) { return new BuscaCaronaRequest(a, b, vagas, h(0, 0)); }

    static List<List<PassoRef>> rodar(Cluster c, int entrada, Consulta q) throws Exception {
        return c.servidores.get(entrada).buscarItinerarios(q).get(15, TimeUnit.SECONDS);
    }

    static Consulta tudo(BuscaCaronaRequest b) { return new Consulta(b, Integer.MAX_VALUE, MAX_TRECHOS); }

    static boolean igualAoOraculo(Cluster c, int entrada, BuscaCaronaRequest b) throws Exception {
        // Compara como multiconjunto: uma carona cuja rota repete o mesmo trecho gera o mesmo
        // itinerário duas vezes, tanto no oráculo quanto na versão distribuída.
        List<List<PassoRef>> esperado = oraculo(c.todasCaronas(), tudo(b));
        List<List<PassoRef>> obtido = rodar(c, entrada, tudo(b));
        return obtido.size() == esperado.size() && new HashSet<>(obtido).equals(new HashSet<>(esperado));
    }

    /** Propriedade que vale mesmo com caronas mudando: o itinerário é válido segundo as regras. */
    static boolean valido(List<PassoRef> cam, BuscaCaronaRequest b, Cluster c) {
        if (cam.isEmpty()) return false;
        if (cam.getFirst().de() != b.origemDesejada() || cam.getLast().para() != b.destinoDesejado()) return false;
        Set<Integer> vis = new HashSet<>();
        vis.add(b.origemDesejada());
        for (int i = 0; i < cam.size(); i++) {
            PassoRef p = cam.get(i);
            if (i > 0 && cam.get(i - 1).para() != p.de()) return false;
            if (!vis.add(p.para())) return false;                                   // sem repetir cidade
            if (i > 0 && p.dataCarona().isBefore(cam.get(i - 1).dataCarona())) return false;
            if (!p.dataCarona().toLocalDate().equals(b.data_desejada().toLocalDate())) return false;
            Carona car = c.achar(p.caronaId());
            if (car == null || !car.rota.contains(new Trecho(p.de(), p.para()))) return false;
        }
        return true;
    }

    // ===================== Mundo aleatório =====================
    static Cluster mundoAleatorio(long semente, Long atraso) {
        Random r = new Random(semente);
        int n = 4 + r.nextInt(4);                                    // cidades 1..n
        Map<Integer, Set<Integer>> adj = new HashMap<>();
        for (int u = 1; u <= n; u++) adj.put(u, new HashSet<>());
        for (int u = 1; u <= n; u++)
            for (int v = u + 1; v <= n; v++)
                if (r.nextDouble() < 0.55) { adj.get(u).add(v); adj.get(v).add(u); }

        List<Set<Integer>> vertices = List.of(new HashSet<>(), new HashSet<>(), new HashSet<>());
        for (int v = 1; v <= n; v++) {
            int s0 = r.nextInt(3);
            vertices.get(s0).add(v);
            if (r.nextDouble() < 0.4) vertices.get((s0 + 1 + r.nextInt(2)) % 3).add(v);   // vértice compartilhado
        }
        Cluster c = new Cluster(new GrafoRef(adj), vertices, atraso, 30_000);

        for (Servidor s : c.servidores) {
            List<Integer> meus = new ArrayList<>(s.meus);
            if (meus.isEmpty()) continue;
            int k = 2 + r.nextInt(5);
            for (int i = 0; i < k; i++) {
                int atual = meus.get(r.nextInt(meus.size()));
                List<Trecho> rota = new ArrayList<>();
                int len = 1 + r.nextInt(3);
                for (int j = 0; j < len; j++) {
                    List<Integer> opcoes = new ArrayList<>();
                    for (int v : adj.get(atual)) if (s.meus.contains(v)) opcoes.add(v);
                    if (opcoes.isEmpty()) break;
                    int prox = opcoes.get(r.nextInt(opcoes.size()));
                    rota.add(new Trecho(atual, prox));
                    atual = prox;
                }
                if (rota.isEmpty()) continue;
                LocalDateTime data = (r.nextDouble() < 0.1 ? DIA.plusDays(1) : DIA).atTime(6 + r.nextInt(8), 0);
                int cap = 1 + r.nextInt(3);
                Carona car = s.criarCarona(100 + i, data, cap, r.nextInt(20), rota);
                if (r.nextDouble() < 0.3) car.ocupar(rota.getFirst(), 1 + r.nextInt(cap));
                if (r.nextDouble() < 0.1) car.fechar();
            }
        }
        return c;
    }

    // ===================== Main: testes =====================
    public static void main(String[] args) throws Exception {
        System.out.println("== Cenários à mão (exemplo A/B/C) ==");
        try (Cluster c = clusterExemplo(null, 30_000)) {
            Map<String, Carona> cs = caronasExemplo(c);
            Map<Integer, String> nomes = new HashMap<>();
            cs.forEach((nome, car) -> nomes.put(car.id, nome));

            List<List<PassoRef>> r = rodar(c, 0, tudo(busca(1, 6, 1)));
            System.out.println("  itinerários 1 -> 6:");
            r.forEach(it -> System.out.println("    " + fmt(it, nomes)));
            check("1->6 devolve 5 itinerários (baldeação entre 3 servidores)", r.size() == 5);
            check("nenhum itinerário usa C2 (sairia antes de A2)", r.stream().flatMap(List::stream).noneMatch(p -> p.caronaId() == cs.get("C2").id));
            check("1->6 igual ao oráculo de um servidor", igualAoOraculo(c, 1, busca(1, 6, 1)));
            check("1->4: só A1+C1 (data impede A2+C2)", rodar(c, 2, tudo(busca(1, 4, 1))).size() == 1);
            check("6->1: sem caminho, e a consulta termina", rodar(c, 1, tudo(busca(6, 1, 1))).isEmpty());
            check("outro dia: nada", rodar(c, 0, tudo(new BuscaCaronaRequest(1, 6, 1, DIA.plusDays(1).atTime(0, 0)))).isEmpty());

            List<List<PassoRef>> lim = rodar(c, 0, new Consulta(busca(1, 6, 1), 2, MAX_TRECHOS));
            Set<List<PassoRef>> esperado = new HashSet<>(oraculo(c.todasCaronas(), tudo(busca(1, 6, 1))));
            check("limite 2: devolve 2 itinerários válidos", lim.size() == 2 && esperado.containsAll(lim));
            check("limite 2: nenhum estado residual depois do cancelamento", c.aguardarSemEstado(3000));

            check("MAX_TRECHOS=2 poda caminhos longos", rodar(c, 0, new Consulta(busca(1, 6, 1), 99, 2)).isEmpty());

            // Cancelar tardio para uma consulta que não existe mais não pode criar estado
            c.local.enviar(0, new Cancelar(123456789L));
            Thread.sleep(150);
            check("Cancelar tardio é ignorado (sem estado órfão)", c.estadosResiduais() == 0);

            // vagas
            cs.get("B1").ocupar(new Trecho(4, 6), 1);
            check("vagas: B1 com 1 vaga some quando pedimos 2", rodar(c, 0, tudo(busca(1, 6, 2))).size() == 4);
            check("vagas: com 1 vaga pedida B1 continua", rodar(c, 0, tudo(busca(1, 6, 1))).size() == 5);
            check("vagas: igual ao oráculo", igualAoOraculo(c, 2, busca(1, 6, 2)));

            // sem ciclo
            c.servidores.get(2).criarCarona(20, h(9, 45), 2, 1.0, List.of(new Trecho(4, 2)));
            c.servidores.get(0).criarCarona(21, h(8, 45), 2, 1.0, List.of(new Trecho(2, 1)));
            List<List<PassoRef>> semCiclo = rodar(c, 1, tudo(busca(1, 6, 1)));
            check("sem ciclo: caronas de volta (4->2, 2->1) não geram itinerário",
                    semCiclo.size() == 5 && semCiclo.stream().allMatch(it -> valido(it, busca(1, 6, 1), c)));

            // carona fechada
            cs.get("A1").fechar();
            check("carona fechada: A1 fechada, 1->6 fica sem caminho", rodar(c, 0, tudo(busca(1, 6, 1))).isEmpty());

            // regras de criação
            try { c.servidores.getFirst().criarCarona(1, h(8, 0), 2, 1.0, List.of(new Trecho(1, 4))); check("A não cria 1->4", false); }
            catch (IllegalArgumentException e) { check("A não cria 1->4 (não possui o 4)", true); }
            try { c.servidores.get(2).criarCarona(1, h(8, 0), 2, 1.0, List.of(new Trecho(2, 3))); check("C não cria 2->3", false); }
            catch (IllegalArgumentException e) { check("C não cria 2->3 (fora do grafo de referência)", true); }
        }

        System.out.println("\n== Todos os pares, rede com atraso e reordenação ==");
        for (long semente = 1; semente <= 20; semente++) {
            try (Cluster c = clusterExemplo(semente, 30_000)) {
                caronasExemplo(c);
                boolean ok = true;
                for (int a = 1; a <= 6; a++)
                    for (int b = 1; b <= 6; b++)
                        ok &= igualAoOraculo(c, (a + b) % 3, busca(a, b, 1));
                check("semente " + semente + ": 36 pares iguais ao oráculo", ok);
                check("semente " + semente + ": sem estado residual", c.aguardarSemEstado(3000));
            }
        }

        System.out.println("\n== Mundos aleatórios vs oráculo (com atraso) ==");
        int divergentes = 0;
        for (long semente = 1; semente <= 150; semente++) {
            try (Cluster c = mundoAleatorio(semente, semente)) {
                Random r = new Random(semente * 31);
                int n = c.servidores.get(0).ref.adj.size();
                boolean ok = true;
                for (int q = 0; q < 3; q++) {
                    BuscaCaronaRequest b = busca(1 + r.nextInt(n), 1 + r.nextInt(n), 1 + r.nextInt(2));
                    boolean igual = igualAoOraculo(c, r.nextInt(3), b);
                    if (!igual) {
                        System.out.println("  DIVERGÊNCIA semente=" + semente + " busca=" + b);
                        Set<List<PassoRef>> esp = new HashSet<>(oraculo(c.todasCaronas(), tudo(b)));
                        List<List<PassoRef>> obt = rodar(c, 0, tudo(b));
                        System.out.println("    oraculo=" + esp.size() + " obtido=" + obt.size() + " distintos=" + new HashSet<>(obt).size());
                        for (List<PassoRef> x : esp) if (!obt.contains(x)) System.out.println("    faltou: " + x);
                        for (List<PassoRef> x : obt) if (!esp.contains(x)) System.out.println("    sobrou: " + x);
                    }
                    ok &= igual;
                }
                ok &= c.aguardarSemEstado(3000);
                if (!ok) divergentes++;
            }
        }
        check("150 mundos aleatórios, sem divergência nem estado residual (divergentes=" + divergentes + ")", divergentes == 0);

        System.out.println("\n== Consultas simultâneas (16 clientes) ==");
        try (Cluster c = clusterExemplo(null, 30_000)) {
            caronasExemplo(c);
            ExecutorService clientes = Executors.newFixedThreadPool(16);
            List<Future<Boolean>> fs = new ArrayList<>();
            for (int i = 0; i < 600; i++) {
                final int a = 1 + i % 6, b = 1 + (i / 6) % 6, entrada = i % 3;
                fs.add(clientes.submit(() -> igualAoOraculo(c, entrada, busca(a, b, 1))));
            }
            int erros = 0;
            for (Future<Boolean> f : fs) if (!f.get()) erros++;
            clientes.shutdown();
            check("600 consultas simultâneas iguais ao oráculo (erros=" + erros + ")", erros == 0);
            check("sem estado residual", c.aguardarSemEstado(3000));
        }

        System.out.println("\n== Consultas enquanto caronas são criadas e fechadas ==");
        try (Cluster c = clusterExemplo(null, 30_000)) {
            caronasExemplo(c);
            AtomicInteger invalidos = new AtomicInteger();
            Thread editor = new Thread(() -> {
                Random r = new Random(7);
                for (int i = 0; i < 300; i++) {
                    try {
                        Servidor s = c.servidores.get(2);                       // C: cidades 2,3,4,5
                        Carona car = s.criarCarona(200 + i, h(7 + r.nextInt(5), 0), 2, 1.0, List.of(new Trecho(2, 4)));
                        if (i % 3 == 0) car.fechar();
                        Thread.sleep(2);
                    } catch (Exception e) { throw new RuntimeException(e); }
                }
            });
            editor.start();
            ExecutorService clientes = Executors.newFixedThreadPool(8);
            List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < 300; i++) {
                final int entrada = i % 3;
                fs.add(clientes.submit(() -> {
                    try {
                        BuscaCaronaRequest b = busca(1, 6, 1);
                        for (List<PassoRef> it : rodar(c, entrada, tudo(b))) if (!valido(it, b, c)) invalidos.incrementAndGet();
                    } catch (Exception e) { invalidos.incrementAndGet(); }
                }));
            }
            for (Future<?> f : fs) f.get();
            editor.join();
            clientes.shutdown();
            check("todo itinerário devolvido é válido (inválidos=" + invalidos.get() + ")", invalidos.get() == 0);
            check("sem estado residual", c.aguardarSemEstado(3000));
        }

        System.out.println("\n== Falha: servidor B parado ==");
        try (Cluster c = clusterExemplo(null, 600)) {
            caronasExemplo(c);
            c.servidores.get(1).parar();
            Thread.sleep(100);
            long ini = System.currentTimeMillis();
            try {
                c.servidores.get(0).buscarItinerarios(tudo(busca(1, 6, 1))).get(10, TimeUnit.SECONDS);
                check("consulta com servidor parado falha por TTL", false);
            } catch (ExecutionException e) {
                check("consulta com servidor parado falha por TTL, sem travar o cliente (" + (System.currentTimeMillis() - ini) + " ms)",
                        e.getCause() instanceof TimeoutException && System.currentTimeMillis() - ini < 4000);
            }
        }

        System.out.println("\n" + (total - falhas) + "/" + total + " verificações passaram.");
        System.exit(falhas == 0 ? 0 : 1);
    }
}