package org.UEFS.vaijunto.Util;

import org.UEFS.vaijunto.Server.Request;
import org.UEFS.vaijunto.Exceptions.IncorrectRequestException;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.UnknownHostException;

public class DmppParser {

    public static Request parceRequest(String mensagem) throws IOException {
        String[] parametros = mensagem.split("\\|");

        if (parametros.length != 4) {
            throw new IncorrectRequestException("ERRO: Número de parâmetros incorreto.");
        }

        String tipo = parametros[0];
        String dados = parametros[1];
        String token = parametros[2];
        int tamanho = -1;
        try {
            tamanho = Integer.parseInt(parametros[3]);
        } catch (NumberFormatException e) {
            throw new IncorrectRequestException("ERRO: Tamanho dos parâmetros não é um valor válido.");
        }

        return new Request(tipo, dados, token, tamanho);
    }

    public static String getServerIP() {
        try {
            return Inet4Address.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
}
