package org.UEFS.vaijunto.client.service;

import org.UEFS.shared.dto.SelfUserDTO;

public class SessionManager {
    private static SessionManager instance;

    private String userToken;

    private SelfUserDTO currentUser;

    private SessionManager() {}

    public synchronized static SessionManager getInstance() {
        if (instance == null)
            instance = new SessionManager();

        return instance;
    }

    public void setUserToken(String userToken) {
        this.userToken = userToken;
    }
    public String getUserToken() {
        return userToken;
    }

    public SelfUserDTO getCurrentUser() {
        return currentUser;
    }
    public void setCurrentUser(SelfUserDTO currentUser) {
        this.currentUser = currentUser;
    }

    public boolean isMotorista() {
        return this.currentUser != null && this.currentUser.perfilMotorista() != null;
    }

    public void limparSessao() {
        userToken = null;
        currentUser = null;
    }
}
