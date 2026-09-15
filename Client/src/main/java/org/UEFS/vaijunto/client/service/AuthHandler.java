package org.UEFS.vaijunto.client.service;

import org.UEFS.shared.JsonUtils;
import org.UEFS.shared.dto.responses.GeneralResponse;
import org.UEFS.shared.dto.responses.LoginResponse;
import org.UEFS.shared.model.ServerException;

public class AuthHandler {
    public static void guardarLogin(GeneralResponse response) throws ServerException {
        LoginResponse login = JsonUtils.fromJson(response.dados(), LoginResponse.class);

        SessionManager session = SessionManager.getInstance();
        session.setUserToken(login.token());
        session.setCurrentUser(login.usuario());
        
        System.out.println("Login efetuado com sucesso para: " + login.usuario().nome());
    }
}