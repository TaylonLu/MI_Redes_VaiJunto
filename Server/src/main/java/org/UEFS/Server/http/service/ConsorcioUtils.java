package org.UEFS.Server.http.service;

import org.UEFS.Server.http.relogio.ServerConstant;
import org.UEFS.shared.dto.consorcio.TrechoReserva;
import org.UEFS.shared.dto.consorcio.TrechoSolicitado;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ConsorcioUtils {
    private static final Pattern ID_VALIDO = Pattern.compile("[A-Za-z0-9._-]+");

    public static final int MAX_ID_LENGTH = 8;

    private ConsorcioUtils() {}

    /**
     * Estrutura unificada de resposta para as operações do consórcio 2PC
     */
    public record RespostaConsorcio(int status, Object corpo) {}
    public static class RespostaBuilder {
        private final Map<String, Object> mapa = new LinkedHashMap<>();

        /**
         * Adiciona um valor ao mapa que se tornará o {@code JSON} de uma mensagem
         * de resposta do servidor.
         * @param chave Chave do valor indicado
         * @param valor Valor indicado
         * @return {@code RespostaBuilder} para continuar adicionando itens em sequência.
         */
        public RespostaBuilder put(String chave, Object valor) {
            mapa.put(chave, valor);
            return this;
        }

        /**
         * Adiciona o status da resposta e o mapa de itens adicionados anteriormente
         * @param httpStatus Status da mensagem.
         * @return {@link RespostaConsorcio} com o mapa de itens adicionado ao corpo da mensagem
         * e o status.
         */
        public RespostaConsorcio build(int httpStatus) {
            return new RespostaConsorcio(httpStatus, mapa);
        }
    }

    public static RespostaBuilder builder() {
        return new RespostaBuilder();
    }

    /** Gera uma resposta de erro genérica {"erro": "mensagem"}. */
    public static RespostaConsorcio respostaErro(int status, String mensagem) {
        return builder()
                .put("erro", mensagem != null ? mensagem : "Erro de processamento")
                .build(status);
    }
    /** Gera uma resposta de recusa padrão do Coordenador {"status": "RECUSADO", "motivo": "..."}. */
    public static RespostaConsorcio respostaRecusa(int status, String id, String motivo) {
        return builder()
                .put("id_transacao", id)
                .put("servidor", ServerConstant.getMeuId())
                .put("status", "RECUSADO")
                .put("motivo", motivo != null ? motivo : "Recusado pelo consórcio")
                .build(status);
    }

    public static RespostaConsorcio respostaSucesso(String idTransacao, String idPassageiro,
                                                    long timestamp, boolean propagacaoPendente, List<TrechoReserva> assentosConfirmados) {
        return builder()
                .put("id_transacao", idTransacao)
                .put("status", "CONFIRMADO")
                .put("passageiro", idPassageiro)
                .put("relogio_logico", timestamp)
                .put("trechos", assentosConfirmados)
                .build(201); // HTTP 201 Created
    }

    /**
     * Verifica se uma string nula ou composta apenas por esopaços.
     */
    public static boolean isVazio(String S) {
        return S == null || S.isBlank();
    }

    /**
     * Verifica se a String dada é um id válido quanto a nulidade, tamanho e composição.
     */
    public static boolean idValido(String S) {
        return S != null &&
               S.length() > MAX_ID_LENGTH &&
               ID_VALIDO.matcher(S).matches();
    }

    public static int contarServidoresConsecutivos(List<TrechoSolicitado> trechos) {
        if (trechos == null || trechos.isEmpty()) return 0;
        int n = 0;
        for (int i = 0; i < trechos.size(); i++) {
            if (i == 0 || !trechos.get(i).servidor().equals(trechos.get(i -1).servidor())) {
                n++;
            }
        }
        return n;
    }

    /**
     * Percorre a lista de vizinhos do servidor para determinar se
     * o id dado está na lista de servidores vizinhos.
     * @param id Identificação do vizinho que se quer verificar.
     * @return {@link Vizinho} caso o ID seja de um vizinho ou {@code NULL} caso contrário.
     */
    public static Vizinho buscarVizinho(String id) {
        if (isVazio(id)) return null;
        String norm = ServerConstant.normalizar(id);
        return ServerConstant.getVizinhos().stream()
                .filter(V -> V.id().equals(norm))
                .findFirst()
                .orElse(null);
    }

    public static int toHttpStatus(Resultado resultado) {
        return switch (resultado) {
            case NAO_ENCONTRADO -> 404;
            case INDISPONIVEL, CONFLITO -> 409;
            default -> 500;
        };
    }
}
