package org.UEFS.Server.http.relogio;

import org.UEFS.Server.http.service.Vizinho;
import org.UEFS.shared.JsonUtils;
import org.UEFS.shared.model.Regiao;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ServerConstant {
    private static final List<Regiao> REGIOES = new ArrayList<>();

    private static String MEU_ID = "";
    private static Regiao EU;
    private static int porta;
    private static List<Vizinho> vizinhos = List.of();

    private static final Logger log = LoggerFactory.getLogger(ServerConstant.class);

    private ServerConstant() { }

    public static String getMeuId() {
        return MEU_ID;
    }

    public static int getPorta() {
        return porta;
    }

    public static Regiao getRegiao() {
        return EU;
    }

    public static void setMeuId(String meuId) {
        MEU_ID = meuId;
    }

    public static List<Vizinho> getVizinhos() {
        return vizinhos;
    }
    public static Vizinho getVizinho(String dado) {
        if (dado == null || dado.isBlank()) return null;

        String alvo = normalizar(dado);

        return vizinhos.stream()
                .filter(v -> normalizar(v.id()).equals(alvo)
                        || normalizar(v.endereco()).equals(alvo)
                        || normalizar(v.ip()).equals(alvo))
                .findFirst()
                .orElse(null);
    }
    public static Optional<Regiao> getMe() {
        return buscar(MEU_ID);
    }

    public static void carregarRegioes() {
        try (InputStream IS = ServerConstant.class.getResourceAsStream("/regioes.json")) {
            if (IS == null) {
                log.error("[DADOS] arquivo '/regioes.json' não foi encontrado no classpath.");
                return;
            }

            List<Regiao> regioes = JsonUtils.fromJsonList(IS,Regiao.class);

            if (regioes != null) {
                REGIOES.clear();
                REGIOES.addAll(regioes);
                log.info("[DADOS] {} regiões carregadas com sucesso.", REGIOES.size());
            }
        } catch (IOException e) {
            log.error("[DADOS] Erro ao carregar arquivo de servidores.");
        }
    }

    public static void carregar(String regiao) {
        Regiao eu = buscar(regiao).orElseThrow(() -> new IllegalArgumentException(
                "Região desconhecida: '" + regiao + "'. Válidas: "
                + REGIOES.stream().map(Regiao::nome).toList()
        ));

        EU = eu;
        MEU_ID = eu.regiaoBase();
        porta = eu.porta();

        vizinhos = eu.vizinhos().stream().map(id -> {
            Regiao R = buscar(id).orElseThrow();
            String nomeLimpo = R.regiaoBase()
                    .replaceAll("[^a-zA-Z0-9]", "")
                    .toUpperCase(Locale.ROOT);
            String hostEnv = System.getenv("HOST_" + nomeLimpo);
            String isReal = (hostEnv != null && !hostEnv.isEmpty()) ? hostEnv : R.ip();
            return new Vizinho(R.regiaoBase(), isReal, R.porta());
        }).toList();
    }

    /** Busca uma região pelo identificador lógico, nome de apresentação
     * ou endereço antigo IP:porta.
     */
    public static Optional<Regiao> buscar(String nome) {
        if (nome == null || nome.isBlank()) return Optional.empty();

        return REGIOES.stream()
                .filter(r -> r.regiaoBase().equalsIgnoreCase(nome)
                        || r.nome().equalsIgnoreCase(nome)
                        || r.endereco().equalsIgnoreCase(nome))
                .findFirst();
    }

    public static String normalizar(String id) {
        return id.toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }
}
