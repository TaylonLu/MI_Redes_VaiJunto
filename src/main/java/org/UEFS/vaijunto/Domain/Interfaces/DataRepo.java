package org.UEFS.vaijunto.Domain.Interfaces;

import com.fasterxml.jackson.core.type.TypeReference;
import org.UEFS.vaijunto.Controller.ControllerService;
import org.UEFS.vaijunto.Domain.FileManager;
import org.UEFS.vaijunto.Util.IOUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public abstract class DataRepo<T extends Identificavel> {
    protected final Map<String, T> dados = new ConcurrentHashMap<>();
    protected final String caminho_arquivo;

    public DataRepo(String caminho, TypeReference<Map<String, T>> typeReference) {
        this.caminho_arquivo = caminho;
        try {
            Map<String, T> dados = ControllerService.getInstance()
                    .get(FileManager.class)
                    .carregarObjetos(caminho, typeReference);
            if (dados != null && !dados.isEmpty()) this.dados.putAll(dados);
        } catch (FileNotFoundException e){
            IOUtils.fprintf("[:red]ERRO: arquivo %s não encontrado.\n",  caminho);
        } catch (IOException e) {
            System.err.println("Nenhum dado foi carregado.\n"+ e);
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

    public synchronized void salvarNoDisco() {
        try {
            ControllerService.getInstance()
                    .get(FileManager.class)
                    .salvarObjetos(caminho_arquivo, dados);
        } catch (IOException e) {
            System.err.println("Erro ao persistir dados no arquivo:" + e.getMessage());
        }
    }
}
