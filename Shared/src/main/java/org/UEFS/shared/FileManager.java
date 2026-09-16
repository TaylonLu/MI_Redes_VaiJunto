package org.UEFS.shared;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.UEFS.shared.model.Cidade;

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

    /**
     * Tenta encontrar o arquivo no diretório atual ou um nível acima (caso execute de uma subpasta).
     */
    private static File resolverArquivo(String caminhoArquivo) {
        File arquivoDireto = new File(caminhoArquivo);
        if (arquivoDireto.exists()) {
            return arquivoDireto;
        }

        // Tenta subir um nível (ex: se estiver rodando de dentro de /Client ou /Server)
        File arquivoPai = new File("..", caminhoArquivo);
        if (arquivoPai.exists()) {
            return arquivoPai;
        }

        // Se não achar em nenhum dos dois, retorna o direto para gerar o erro amigável de não encontrado
        return arquivoDireto;
    }

    public static List<Cidade> carregarDeJson(String caminhoArquivo) {
        File arquivo = resolverArquivo(caminhoArquivo);

        if (!arquivo.exists()) {
            System.out.println("Arquivo não encontrado: " + arquivo.getPath());
            return new ArrayList<>();
        }

        try {
            return mapper.readValue(arquivo, new TypeReference<>(){});
        } catch (IOException e) {
            System.err.println("Erro ao ler o arquivo JSON: " + e.getMessage());
            return null;
        }
    }

    public <T> void salvarObjetos(String arquivoDestino, Map<String, T> mapa) throws IOException {
        File arquivo = resolverArquivo(arquivoDestino);
        mapper.writerWithDefaultPrettyPrinter().writeValue(arquivo, mapa);
    }

    public void salvarObjetos(String arquivoDestino, Object dados) throws IOException {
        File arquivo = resolverArquivo(arquivoDestino);
        mapper.writerWithDefaultPrettyPrinter().writeValue(arquivo, dados);
    }

    public <U> U carregarObjetos(String arquivoOrigem, TypeReference<U> tipo) throws IOException {
        File arquivo = resolverArquivo(arquivoOrigem);

        if (!arquivo.exists() || arquivo.length() == 0) {
            throw new FileNotFoundException("Arquivo " + arquivoOrigem + " não encontrado ou vazio.");
        }

        return mapper.readValue(arquivo, tipo);
    }
}