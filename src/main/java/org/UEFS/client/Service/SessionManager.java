package org.UEFS.client.Service;

public class SessionManager {
    private static SessionManager instance;

    private String userToken;
    private String username;
    private String userID;

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

    public void setUsername(String username) {
        this.username = username;
    }
    public String getUsername() {
        return username;
    }

    public void setUserID(String userID) {
        this.userID = userID;
    }
    public String getUserID() {
        return userID;
    }

    public void limparSessao() {
        userToken = null;
        username = null;
    }
}
