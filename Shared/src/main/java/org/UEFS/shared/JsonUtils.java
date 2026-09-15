package org.UEFS.shared;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.UEFS.shared.model.ServerException;

import java.util.List;
import java.util.Map;

public class JsonUtils {
    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        mapper.registerModule(new JavaTimeModule());
    }

    public static ObjectMapper getMapper() {
        return mapper;
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

    public static <U> String toJson(U dados) {
        try {
            return mapper.writeValueAsString(dados);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Erro ao converter Objeto para JSON.", e);
        }
    }

    public static <U> U fromJson(String dados, Class<U> tipo) throws ServerException {
        try {
            return mapper.readValue(dados, tipo);
        } catch (JsonProcessingException _) {
            throw new ServerException(701, "DADOS_INCORRETOS", "Dados da requisição incorretos: \n" + dados);
        }
    }

    public static <T> List<T> fromJsonList(String dados, Class<T> tipoElemento) throws ServerException {
        try {
            CollectionType tipoLista = mapper.getTypeFactory().constructCollectionType(List.class, tipoElemento);

            return mapper.readValue(dados, tipoLista);

        } catch (JsonProcessingException e) {
            throw new ServerException(701, "DADOS_INCORRETOS", "Dados da requisição incorretos.");

        }
    }
}
