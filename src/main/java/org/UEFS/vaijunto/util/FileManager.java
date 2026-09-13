package org.UEFS.vaijunto.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.UEFS.shared.Cidade;
import org.UEFS.shared.JsonUtils;
import org.UEFS.vaijunto.domain.interfaces.Identificavel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FileManager {
    private static final ObjectMapper mapper = JsonUtils.getMapper();

    public static List<Cidade> carregarDeJson(String caminhoArquivo) {
        Path arquivoC = Path.of(caminhoArquivo);
        if (!Files.exists(arquivoC)) {
            System.out.println("Arquivo não encontrado.");
            return new ArrayList<>();
        }

        ObjectMapper mapper = new ObjectMapper();

        File arquivo = new File(arquivoC.toUri());

        try {
            return mapper.readValue(arquivo, new TypeReference<>(){});
        } catch (IOException e) {
            System.err.println("Erro ao ler o arquivo JSON: " + e.getMessage());
            return null;
        }
    }

    public <T extends Identificavel>
    void salvarObjetos(String arquivo, Map<String, T> mapa) throws IOException {
        mapper.writeValue(new File(arquivo), mapa);
    }

    public void salvarObjetos(String arquivo, Object dados) throws IOException {
        mapper.writeValue(new File(arquivo), dados);
    }

    public <U> U carregarObjetos(String arquivo, TypeReference<U> tipo) throws IOException {
        File file = new File(arquivo);

        if (!file.exists() || file.length() == 0) {
            throw new FileNotFoundException("Arquivo " + arquivo + " não encontrado ou vazio.");
        }

        return mapper.readValue(file, tipo);
    }
}
