
package org.UEFS.Server.http.service;

import java.security.InvalidParameterException;
import java.util.Objects;

public class Vizinho {
    private final String id;
    private final String ip;
    private final String host;
    private final int porta;
    private final String url;

    public Vizinho(String id, String ip, String host, int porta) {
        this.id = id;
        this.ip = ip;
        this.host = host;
        this.porta = porta;
        this.url = "http://" + host + ":" + porta;
    }

    /**
     * Construtor de compatibilidade com o formato antigo.
     * O ID será um endereço IP:porta, portanto não deve ser usado
     * para criar novos vizinhos na configuração.
     */
    public Vizinho(String ip, String host, int porta) {
        this(ip + ":" + porta, ip, host, porta);
    }

    /**
     * Construtor para endereços antigos armazenados em transações.
     */
    public Vizinho(String endereco) {
        String[] dados = endereco.split(":");

        if (dados.length != 2 || !dados[1].matches("\\d{1,5}"))
            throw new InvalidParameterException("Formato de endereço inválido.");

        this.id = endereco;
        this.ip = dados[0];
        this.host = dados[0];
        this.porta = Integer.parseInt(dados[1]);
        this.url = "http://" + endereco;
    }

    public String id() {
        return id;
    }
    public String ip() {
        return ip;
    }
    public String host() {
        return host;
    }
    public int porta() {
        return porta;
    }
    public String endereco() {
        return ip + ":" + porta;
    }
    public String url() {
        return url;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Vizinho other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
