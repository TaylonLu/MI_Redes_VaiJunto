package org.UEFS.client.Controller;

import org.UEFS.client.Service.SessionManager;
import org.UEFS.shared.ServerGrammar;

import java.util.zip.DataFormatException;

public class UserController {
    private final SessionManager sessionManager = SessionManager.getInstance();

    public void guardarLogin(String rawData) throws DataFormatException {
        String[] dadosSplit = ServerGrammar.extrairEnvelope(rawData);
        if (dadosSplit.length != 4) throw new DataFormatException("Dados formatados incorretamente.");

        String dados = dadosSplit[2];

        sessionManager.setUserToken(dados);
    }
}
