package org.UEFS.Server.domain.cidades;

import com.fasterxml.jackson.core.type.TypeReference;
import org.UEFS.Server.http.relogio.ServerConstant;
import org.UEFS.shared.JsonUtils;
import org.UEFS.shared.model.Regiao;

import java.io.InputStream;
import java.util.*;

public class CidadeMapper {
    private final Map<String, Integer> nomeParaId = new HashMap<>();
    private final Map<Integer, String> idParaNome = new HashMap<>();

    private final Set<Integer> cidadesDoMeuServidor = new HashSet<>();

    public CidadeMapper(String caminhoArquivoJson) {
        carregarCidades(caminhoArquivoJson);

        Regiao eu = ServerConstant.getMe().orElse(null);
        if (eu == null) return;

        if (eu.cidadesBase() != null) {
            cidadesDoMeuServidor.addAll(eu.cidadesBase().stream()
                    .map(this::traduzirParaId)
                    .filter(id -> id != -1)
                    .toList());
        }

        if (eu.conexoesExternas() != null) {
            cidadesDoMeuServidor.addAll(eu.conexoesExternas().stream()
                    .map(this::traduzirParaId)
                    .filter(id -> id != -1)
                    .toList());
        }
    }

    private void carregarCidades(String caminhoArquivoJson) {
        try (InputStream is = getClass().getResourceAsStream(caminhoArquivoJson)) {
            if (is == null) throw new IllegalArgumentException("Arquivo não encontrado: " + caminhoArquivoJson);

            List<CidadeJSON> lista = JsonUtils.fromJson(is, new TypeReference<List<CidadeJSON>>() {});

            for (CidadeJSON c : lista) {
                nomeParaId.put(c.nome().trim().toLowerCase(), c.id());
                idParaNome.put(c.id(), c.nome());
            }
        } catch (Exception e) {
            throw new RuntimeException("Falha ao carregar " + caminhoArquivoJson, e);
        }
    }

    public int traduzirParaId(String nome) {
        return nomeParaId.getOrDefault(nome.toLowerCase(), -1);
    }

    public String getNome(int id) {
        return idParaNome.getOrDefault(id, "Desconhecida");
    }

    public boolean ehCidadeDesteServidor(int idCidade) {
        return cidadesDoMeuServidor.contains(idCidade);
    }
}
