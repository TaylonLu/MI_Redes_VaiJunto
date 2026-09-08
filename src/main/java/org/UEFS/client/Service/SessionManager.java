package org.UEFS.client.Service;

public class SessionManager {
    private static SessionManager instance;

    private String userToken;
    private String username;

    private SessionManager() {}

    public synchronized static SessionManager getInstance() {
        if (instance == null)
            instance = new SessionManager();

        return instance;
    }

    public void setUserToken(String userToken) {
        this.userToken = userToken;
    }

    public String getUsername() {
        return username;
    }

    public String getUserToken() {
        return userToken;
    }

    public void limparSessao() {
        userToken = null;
        username = null;
    }
}
