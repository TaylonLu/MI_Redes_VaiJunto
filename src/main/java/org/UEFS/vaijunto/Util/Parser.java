package org.UEFS.vaijunto.Util;

import java.net.Inet4Address;
import java.net.UnknownHostException;

public class Parser {
    public static String getServerIP() {
        try {
            return Inet4Address.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            // É melhor lançar um erro claro do que apenas um RuntimeException genérico
            throw new RuntimeException("Não foi possível determinar o IP do servidor.", e);
        }
    }
}
