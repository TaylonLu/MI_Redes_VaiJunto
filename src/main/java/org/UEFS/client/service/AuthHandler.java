package org.UEFS.client.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.UEFS.shared.JsonUtils;
import org.UEFS.shared.ServerGrammar;
import org.UEFS.shared.dto.UserDTO;

import java.util.zip.DataFormatException;

public class AuthHandler {
    /**
     * Processa a resposta de login do servidor, extrai o JSON e salva na sessão.
     * @param rawData A string crua recebida do Socket
     */
    public static void guardarLogin(String rawData) throws DataFormatException, JsonProcessingException {
        String[] dadosSplit = ServerGrammar.extrairEnvelope(rawData);
        
        if (dadosSplit.length != 4) {
            throw new DataFormatException("Dados formatados incorretamente no envelope da rede.");
        }

        String token = dadosSplit[1]; 
        String jsonPayload = dadosSplit[2];

        UserDTO usuarioLogado = JsonUtils.fromJson(jsonPayload, UserDTO.class);

        SessionManager session = SessionManager.getInstance();
        session.setUserToken(token);
        session.setCurrentUser(usuarioLogado);
        
        System.out.println("Login efetuado com sucesso para: " + usuarioLogado.nome());
    }
}