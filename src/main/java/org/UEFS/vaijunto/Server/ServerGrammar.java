package org.UEFS.vaijunto.Server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.UEFS.vaijunto.Exceptions.IncorrectDataException;
import org.UEFS.vaijunto.Exceptions.IncorrectRequestException;

import java.util.List;
import java.util.Map;

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
            String dados,
            String token
    ) {
        dados = dados != null ? dados : "";

        return String.join(
                ENV_JOIN,
                String.valueOf(codigo),
                tipo,
                dados,
                token,
                String.valueOf(dados.length())
        );
    }
    public static String construirAtributos(String... atributos) {
        return String.join(ATTR_SEP, atributos);
    }
    public static <U> String toJson(Map<String, U> objeto) {
        try {
            return mapper.writeValueAsString(objeto);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Erro ao converter Objeto para JSON.", e);
        }
    }
    public static <U> String toJson(List<U> objeto) {
        try {
            return mapper.writeValueAsString(objeto);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Erro ao converter Objeto para JSON.", e);
        }
    }

    public static <U> U fromJson(String dados, Class<U> tipo) throws IncorrectDataException{
        try {
            return mapper.readValue(dados, tipo);
        } catch (JsonProcessingException _) {
            throw new IncorrectDataException("Dados da requisição incorretos");
        }
    }

    public static Request parseRequest(String mensagem) throws IncorrectRequestException {
        // O limite -1 impede que o Java descarte campos vazios no final da string.
        // Sem isso, uma requisição terminada em "|" causaria um ArrayIndexOutOfBounds.
        String[] parametros = mensagem.split("\\|", -1);

        if (parametros.length != 4) {
            throw new IncorrectRequestException("ERRO: Formato da requisição inválido. Esperado: TIPO|DADOS|TOKEN|TAMANHO");
        }

        // O trim() remove quebras de linha (\n, \r) ou espaços que vêm do buffer do Socket
        String tipo = parametros[0].trim();
        String dados = parametros[1]; // Não usamos trim() aqui para não corromper o formato do JSON
        String token = parametros[2].trim();

        int tamanho;
        try {
            tamanho = Integer.parseInt(parametros[3].trim());
        } catch (NumberFormatException e) {
            throw new IncorrectRequestException("ERRO: O parâmetro 'tamanho' não é um número inteiro válido.");
        }

        // Dica: Como você tem o 'tamanho' enviado pelo cliente, você pode usá-lo para
        // garantir que a mensagem não chegou cortada pela metade na rede:
        if (dados.length() != tamanho && tamanho != 0) {
            // Dependendo da sua regra de negócio, você pode lançar erro aqui
            // throw new IncorrectRequestException("ERRO: O tamanho dos dados não bate com o informado.");
        }

        return new Request(tipo, dados, token, tamanho);
    }
}