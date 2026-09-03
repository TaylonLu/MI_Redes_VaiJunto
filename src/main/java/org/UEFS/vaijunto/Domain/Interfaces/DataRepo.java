package org.UEFS.vaijunto.Domain.Interfaces;

import org.UEFS.vaijunto.Controller.ControllerService;
import org.UEFS.vaijunto.Domain.FileManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public abstract class DataRepo<T extends Identificavel> {
    protected final Map<String, T> dados = new ConcurrentHashMap<>();

    public DataRepo() {}
    public DataRepo(BaseMapper<T> mapper, String caminho) {
        try {
            this.dados.putAll(ControllerService.getInstance().get(FileManager.class).carregar(caminho, mapper));
        } catch (Exception e) {
            System.err.println("Deu merda." + e);
        }
    }

    public void salvar(T dado) {
        dados.put(dado.getID(), dado);
    }
    public HashMap<String, T> copy() {
        return new HashMap<>(dados);
    }
    public void remover(String id) {
        dados.remove(id);
    }
    public T getByID(String id) {
        return dados.get(id);
    }
    public List<T> getByIDs(Set<String> IDs) {
        return IDs.stream()
                  .map(dados::get)
                  .toList();
    }
}
