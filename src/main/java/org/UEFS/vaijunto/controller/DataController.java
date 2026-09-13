package org.UEFS.vaijunto.controller;

import org.UEFS.vaijunto.domain.interfaces.Identificavel;
import org.UEFS.vaijunto.domain.interfaces.DataRepo;
import org.UEFS.vaijunto.server.Request;
import org.UEFS.vaijunto.server.Response;

public abstract class DataController<T extends Identificavel, RP extends DataRepo<T>> {
    protected final ControllerService serviceProvider = ControllerService.getInstance();
    protected final RP repo;

    public DataController(RP repositorio) {
        this.repo = repositorio;
    }

    public abstract Response cadastrar(Request RQ) throws Exception;
    public abstract Response atualizar(Request RQ) throws Exception;

    public boolean remover(String ID) {
        if (ID == null || ID.isBlank()) return false;

        try {
            repo.remover(ID);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    T getByID(String ID) {
        if (ID == null || ID.isBlank()) return null;

        return repo.getByID(ID);
    }
}
