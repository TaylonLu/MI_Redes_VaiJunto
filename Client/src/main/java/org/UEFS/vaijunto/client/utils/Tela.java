package org.UEFS.vaijunto.client.utils;

public enum Tela {
    TELA_CADASTRO("/FXML/Cadastro.fxml", "Cadastro"),
    TELA_LOGIN("/FXML/Login.fxml", "Login"),
    TELA_INICIO("/FXML/Inicio.fxml", "Tela Inicial"),
    TELA_MAPA("/FXML/MapaCarona.fxml", "Mapa"),
    TELA_PRINCIPAL("/FXML/TelaPrincipal.fxml", "Tela Inicial");


    private final String caminho;
    private final String nome;

    Tela(String caminho, String nome) {
        this.caminho = caminho;
        this.nome = nome;
    }

    public String getCaminho() {
        return caminho;
    }

    public String getNome() {
        return nome;
    }
}
