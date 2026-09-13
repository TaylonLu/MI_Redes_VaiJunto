package org.UEFS.client.service;

import org.UEFS.shared.dto.UserDTO;

public class SessionManager {
    private static SessionManager instance;

    private String userToken;

    private UserDTO currentUser;

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

    public UserDTO getCurrentUser() {
        return currentUser;
    }
    public void setCurrentUser(UserDTO currentUser) {
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
