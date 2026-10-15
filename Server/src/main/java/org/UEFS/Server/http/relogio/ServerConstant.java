package org.UEFS.Server.http.relogio;

import org.UEFS.shared.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ServerConstant {
    private static final Logger log = LoggerFactory.getLogger(ServerConstant.class);
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

    public static void loadServersIps() {
        try (InputStream input = ServerConstant.class.getResourceAsStream("/servidores.json")) {
            if (input == null) {
                throw new FileNotFoundException("Arquivo de servidores não encontrado...");
            }

            List<String> servers = JsonUtils.fromJsonList(input, String.class);

            for (String S : servers) {
                System.out.println("Servidor: " + S);
            }

            servidores.addAll(servers);

        } catch (Exception e) {
            log.error("Erro ao carregar servidores", e);
        }
    }
}
