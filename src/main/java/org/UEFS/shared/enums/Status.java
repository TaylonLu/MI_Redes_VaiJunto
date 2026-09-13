package org.UEFS.shared.enums;

public enum Status {
    SUCESSO(200, "Operação realizada com sucesso."),
    CONFLITO(444, "Conflito detectado."),
    BAD_REQUEST(700, "A requisição possui formato inválido."),
    DADOS_INCORRETOS(701, "Os dados enviados não são válidos."),
    TOKEN_INVALIDO(702, "Sessão expirada ou inválida."),
    USUARIO_NAO_CADASTRADO(703, "Usuário não encontrado"),
    ACAO_INVALIDA(704, "Não é possível completar a operação."),
    NAO_IMPLEMENTADO(800, "Comando não conhecido ou implementado.");

    private final int codigo;
    private final String mensagemPadrao;

    Status(int codigo, String mensagemPadrao) {
        this.codigo = codigo;
        this.mensagemPadrao = mensagemPadrao;
    }

    public int getCodigo() { return codigo; }
    public String getMensagemPadrao() { return mensagemPadrao; }
}