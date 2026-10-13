package org.UEFS.Server.controller.relogio;

import java.util.HashSet;
import java.util.Set;

public class ServerConstant {
    /** IP do servidor atual. */
    private static String MEU_ID;
    /** IP dos outros servidores. */
    private static final Set<String> servidores = new HashSet<>();
    /** Número de servidores. */
    private static int N;

    public static int getN() {
        return N;
    }
    public static Set<String> getServidores() {
        return servidores;
    }
    public static String getMEU_ID() {
        return MEU_ID;
    }

    public static void setMeuId(String meuId) {
        MEU_ID = meuId;
    }
    public static void setN(int n) {
        N = n;
    }

    public static void addServidor(String idServidor) {
        servidores.add(idServidor);
    }
}
