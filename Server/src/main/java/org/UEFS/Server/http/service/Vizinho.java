package org.UEFS.Server.http.service;
import java.security.InvalidParameterException;
import java.util.Objects;

public class Vizinho {
    private final String ip;
    private final int porta;
    private final String host;

    private final String id;
    private final String url;

    public Vizinho(String ip, String host, int porta) {
        this.ip = ip;
        this.porta = porta;
        this.host = host;

        this.id = ip + ":" + porta;
        this.url = "http://" + this.id;
    }

    public Vizinho(String id) {
        String[] dados = id.split(":");
        if (dados.length != 2 || !dados[1].matches("\\d{1,5}"))
            throw new InvalidParameterException("Formado de ID inválido.");

        this.id = id;
        this.url = "http://" + this.id;
        this.ip = dados[0];
        this.porta = Integer.parseInt(dados[1]);

        this.host = ip;
    }

    // Getters no formato que você mencionou
    public String id() { return this.id; }
    public String url() { return this.url; }
    public String ip() { return this.ip; }
    public int porta() { return this.porta; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;

        if (obj instanceof Vizinho other)
            return Objects.equals(id,other.id);
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}