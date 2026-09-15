package org.UEFS.Server.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.UEFS.Server.exceptions.IncorrectRequestException;
import org.UEFS.shared.model.Request;

public final class ServerGrammar {
    private static final ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.registerModule(new JavaTimeModule()); // Essencial para o LocalDateTime!
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private static final String ENV_SPLIT = "\\|";

    public static final String ENV_JOIN = "|";
    public static final String ATTR_SEP = ";";
    public static final String LIST_SEP = ",";
    public static final String SUB_SEP = ":";

    private ServerGrammar() {}

    public static String sanitizar(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        return input.replaceAll("[|;,:]", " ")
                    .trim();
    }

    public static String[] extrairEnvelope(String mensagem) {
        return mensagem.split(ENV_SPLIT, -1);
    }
    public static String[] extrairAtributo(String attr) {
        return attr.split(ATTR_SEP, -1);
    }
    public static String[] extrairLista(String lista) {
        return lista.split(LIST_SEP, -1);
    }
    public static String[] extrairSubAtributos(String subAtributos) {
        return subAtributos.split(SUB_SEP, -1);
    }

    public static String empacotarEnvelope(
            int codigo,
            String tipo,
            String dados
    ) {
        dados = dados != null ? dados : "";

        return String.join(
                ENV_JOIN,
                String.valueOf(codigo),
                tipo,
                dados,
                String.valueOf(dados.length())
        );
    }
    public static String construirAtributos(String... atributos) {
        return String.join(ATTR_SEP, atributos);
    }

    public static Request parseRequest(String mensagem) throws IncorrectRequestException {
        String[] parametros = mensagem.split("\\|", -1);

        if (parametros.length != 4) {
            throw new IncorrectRequestException("ERRO: Formato da requisição inválido. Esperado: TIPO|DADOS|TOKEN|TAMANHO");
        }

        String tipo = parametros[0].trim();
        String dados = parametros[1];
        String token = parametros[2].trim();

        int tamanho;
        try {
            tamanho = Integer.parseInt(parametros[3].trim());
        } catch (NumberFormatException e) {
            throw new IncorrectRequestException("ERRO: O parâmetro 'tamanho' não é um número inteiro válido.");
        }

        if (dados.length() != tamanho && tamanho != 0) {
            // throw new IncorrectRequestException("ERRO: O tamanho dos dados não bate com o informado.");
        }

        return new Request(tipo, dados, token, tamanho);
    }

}