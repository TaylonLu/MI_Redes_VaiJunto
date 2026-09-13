package org.UEFS.vaijunto.controller;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class SessionsController {
    private final Map<String, String> sessoesAtivas = new ConcurrentHashMap<>();
    private final Map<String, String> idToToken = new ConcurrentHashMap<>();

    private final ReentrantLock trava = new ReentrantLock();

    public String criarSessao(String userID) {
        trava.lock();
        String token = UUID.randomUUID().toString();
        try {
            String tokenAntigo = idToToken.get(userID);
            if (tokenAntigo != null) {
                sessoesAtivas.remove(tokenAntigo);
            }

            sessoesAtivas.put(token, userID);
            idToToken.put(userID, token);

        } finally {
            trava.unlock();
        }

        return token;
    }

    /**
     * Verifica se o token recebido é de um usuário ativo.
     * @param token Token a ser verificado.
     * @return ID do usuário ativo ou {@code null} caso não esteja logado.
     */
    public String validarToken(String token) {
        if (token == null || token.isBlank()) return null;

        return sessoesAtivas.get(token);
    }

    public boolean encerrarSessao(String token) {
        if (token == null || token.isBlank()) return false;

        String idUsuario = sessoesAtivas.remove(token);
        if (idUsuario != null) {
            idToToken.remove(idUsuario);
            return true;
        }
        return false;

    }
}
