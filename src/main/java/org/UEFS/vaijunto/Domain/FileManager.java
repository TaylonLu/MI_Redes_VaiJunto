package org.UEFS.vaijunto.Domain;

import org.UEFS.vaijunto.Domain.Interfaces.BaseMapper;
import org.UEFS.vaijunto.Domain.Interfaces.Identificavel;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class FileManager {

    public <T extends Identificavel, MP extends BaseMapper<T>>
    int salvar(Map<String, T> dados, String caminho, MP mapper) {
        if (dados == null || caminho == null) return -1;
        else if (dados.isEmpty()) return -2;
        else if (caminho.isBlank()) return -3;

        try (BufferedWriter writer = Files.newBufferedWriter(Path.of(caminho))) {
            for (T dado : dados.values()) {
                writer.write(mapper.toString(dado));
                writer.newLine();
            }
         } catch (Exception e) {
            return -4;
        }

        return 0;
    }

    public <T extends Identificavel, MP extends BaseMapper<T>>
    Map<String, T> carregar(String caminho, MP mapper) {
        if (caminho == null || caminho.isBlank()) return new HashMap<>();

        Path arquivo = Path.of(caminho);
        if (!Files.exists(arquivo)) return new HashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(arquivo)) {
            Map<String, T> dados = new HashMap<>();
            String linha;

            while ((linha = reader.readLine()) != null) {
                if (linha.isBlank()) continue;

                T dado = mapper.fromString(linha);
                dados.put(dado.getID(), dado);
            }

            return dados;

        } catch (FileNotFoundException e) {
            throw new RuntimeException("Erro ao carregar o arquivo: " + caminho, e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
