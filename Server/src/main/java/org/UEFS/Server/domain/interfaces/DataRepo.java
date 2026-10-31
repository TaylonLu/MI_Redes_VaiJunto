package org.UEFS.Server.domain.interfaces;

import com.fasterxml.jackson.core.type.TypeReference;
import org.UEFS.Server.controller.ControllerService;
import org.UEFS.Server.domain.caronas.Carona;
import org.UEFS.shared.FileManager;
import org.UEFS.Server.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public abstract class DataRepo<T extends Identificavel> {
    private static final Logger log = LoggerFactory.getLogger(DataRepo.class);
    protected final Map<String, T> dados = new ConcurrentHashMap<>();
    protected final String caminho_arquivo;

    private static final Object DISK_LOCK = new Object();

    public DataRepo(String caminho, TypeReference<Map<String, T>> typeReference) {
        this.caminho_arquivo = caminho;
        try {
            Map<String, T> dados = ControllerService.getInstance()
                    .get(FileManager.class)
                    .carregarObjetos(caminho, typeReference);
            if (dados != null && !dados.isEmpty()) this.dados.putAll(dados);
        } catch (FileNotFoundException e){
            log.info("[DATA] Arquivo '{}' ainda não existe. Será criado ao salvar algum dado.", caminho_arquivo);
        } catch (IOException e) {
            log.info("[DATA] Nenhum dado foi carregado.", e);
        }
    }

    public HashMap<String, T> copy() {
        return new HashMap<>(dados);
    }
    public T getByID(String id) {
        return dados.get(id);
    }

    public Collection<T> getData() {
        return dados.values();
    }
    public List<T> getByIDs(Set<String> IDs) {
        return IDs.stream()
                .map(dados::get)
                .toList();
    }


    public void salvar(T dado) {
        dados.put(dado.getId(), dado);
    }
    public void remover(String id) {
        dados.remove(id);
    }
    public List<T> getDadosList() {
        return new ArrayList<>(dados.values());
    }

    public boolean salvarNoDisco() {
        synchronized (DISK_LOCK) {
            try {
                ControllerService.getInstance()
                        .get(FileManager.class)
                        .salvarObjetos(caminho_arquivo, dados);
                return true;
            } catch (IOException e) {
                log.error("[DATA] !! Erro ao persistir dados no arquivo: {}", caminho_arquivo, e);
                return false;
            }
        }
    }
}
