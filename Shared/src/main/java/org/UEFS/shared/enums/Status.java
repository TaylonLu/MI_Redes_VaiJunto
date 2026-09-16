package org.UEFS.shared.enums;

public enum Status {

    // ==========================================
    // 2xx: SUCESSO E OPERAÇÕES CONCLUÍDAS
    // ==========================================
    SUCESSO(200, "SUCESSO", "Operação realizada com sucesso."),
    LOGIN_EFETUADO(201, "LOGIN_EFETUADO", "Login realizado com sucesso."),
    CADASTRO_COMPLETO(202, "CADASTRO_COMPLETO", "Cadastro efetuado com sucesso."),
    CADASTRO_MOTORISTA_COMPLETO(207, "CADASTRO_MOTORISTA_COMPLETO", "Usuário cadastrado como motorista."),
    RESERVA_CONFIRMADA(203, "RESERVA_CONFIRMADA", "Reserva efetuada com sucesso."),
    RESERVA_CANCELADA(204, "RESERVA_CANCELADA", "Reserva cancelada com sucesso."),
    CARONA_CRIADA(205, "CARONA_CRIADA", "Carona publicada com sucesso."),
    CARONA_CANCELADA(206, "CARONA_CANCELADA", "Carona cancelada com sucesso."),

    // ==========================================
    // 3xx: RESPOSTAS GENÉRICAS
    // ==========================================
    SEM_ALTERACAO(304, "SEM_ALTERACAO", "A operação foi processada, mas nenhuma alteração foi realizada."),

    // ==========================================
    // 4xx: ERROS DE REQUISIÇÃO DO CLIENTE / SESSÃO
    // ==========================================
    BAD_REQUEST(400, "BAD_REQUEST", "A requisição possui formato ou parâmetros inválidos."),
    NAO_AUTORIZADO(401, "NAO_AUTORIZADO", "Acesso não autorizado. Faça login primeiro."),
    TOKEN_INVALIDO(402, "TOKEN_INVALIDO", "Sessão expirada ou token de acesso inválido."),
    FORBIDDEN(403, "FORBIDDEN", "Você não tem permissão para realizar esta ação."),
    RECURSO_NAO_ENCONTRADO(404, "NOT_FOUND", "O recurso solicitado (carona, usuário ou trecho) não foi encontrado."),


    // ==========================================
    // 7xx: ERROS DE REGRA DE NEGÓCIO DA APLICAÇÃO
    // ==========================================
    DADOS_INCORRETOS(701, "DADOS_INCORRETOS", "Dados de credenciais ou formulário incorretos."),
    USUARIO_NAO_CADASTRADO(702, "USUARIO_NAO_CADASTRADO", "Usuário não encontrado no sistema."),
    CARONA_LOTADA(703, "FALHA_RESERVA", "Vagas esgotadas para um ou mais trechos selecionados."),
    CONFLITO_RESERVA(704, "CONFLITO_RESERVA", "O passageiro já possui reserva ativa neste mesmo trecho."),
    VEICULO_NAO_CADASTRADO(705, "VEICULO_NAO_CADASTRADO", "Operação restrita a motoristas com veículo cadastrado."),
    ACAO_INVALIDA(706, "ACAO_INVALIDA", "A operação não pode ser concluída no estado atual do sistema."),
    RESERVA_NAO_ENCONTRADA(707, "RESERVA_NAO_ENCONTRADA", "Usuário não possui reserva vinculada a esta carona."),
    EMAIL_JA_CADASTRADO(708, "EMAIL_JA_CADASTRADO", "O e-mail informado já está em uso por outra conta."),

    // ==========================================
    // 8xx / 5xx: ERROS INTERNOS OU NÃO IMPLEMENTADOS
    // ==========================================
    ERRO_INTERNO(500, "ERRO_INTERNO", "Ocorreu um erro interno inesperado no servidor."),
    NAO_IMPLEMENTADO(800, "NAO_IMPLEMENTADO", "Comando de rede não reconhecido ou não suportado.");

    private final int codigo;
    private final String tipo;
    private final String mensagemPadrao;

    Status(int codigo, String tipo, String mensagemPadrao) {
        this.codigo = codigo;
        this.tipo = tipo;
        this.mensagemPadrao = mensagemPadrao;
    }

    public int getCodigo() {
        return codigo;
    }

    public String getTipo() {
        return tipo;
    }

    public String getMensagemPadrao() {
        return mensagemPadrao;
    }

    /**
     * Busca o Enum correspondente a partir do código numérico.
     */
    public static Status fromCodigo(int codigo) {
        for (Status s : values()) {
            if (s.codigo == codigo) {
                return s;
            }
        }
        return ERRO_INTERNO;
    }
}