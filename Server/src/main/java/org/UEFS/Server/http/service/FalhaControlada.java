package org.UEFS.Server.http.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Injeção determinística de falhas para testes locais.
 * Cada etapa só é interrompida uma vez, quando seu marcador existe.
 */
public final class FalhaControlada {
    private static final Logger log = LoggerFactory.getLogger(FalhaControlada.class);

    private FalhaControlada() {}

    public static void executar(String etapa) {
        String etapas = System.getProperty("vaijunto.teste.falha.etapas", "");
        String diretorio = System.getProperty("vaijunto.teste.falha.diretorio", "");

        boolean habilitada = Arrays.stream(etapas.split(","))
                .map(String::trim)
                .anyMatch(etapa::equalsIgnoreCase);

        if (!habilitada || diretorio.isBlank()) return;

        Path marcador = Path.of(diretorio, etapa.toLowerCase());
        try {
            if (!Files.deleteIfExists(marcador)) return;

            log.error("[TESTE] Falha controlada na etapa '{}'. Encerrando o processo.", etapa);
            System.err.flush();
            System.out.flush();
            Runtime.getRuntime().halt(86);
        } catch (IOException erro) {
            log.error("[TESTE] Não foi possível injetar a falha '{}': {}", etapa, erro.getMessage(), erro);
        }
    }
}
