package org.UEFS.Server.http.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ponto de injeção de falhas usado exclusivamente pela suíte automatizada.
 *
 * A falha só é disparada quando:
 * 1. a propriedade vaijunto.teste.falha.etapa corresponde à etapa atual; e
 * 2. o arquivo-marcador configurado existe.
 *
 * O marcador é removido antes de encerrar o processo, evitando repetir a falha
 * quando o contêiner for iniciado novamente.
 */
public final class FalhaControlada {
    private static final Logger log = LoggerFactory.getLogger(FalhaControlada.class);

    private FalhaControlada() {}

    public static void executar(String etapa) {
        String etapaConfigurada = System.getProperty("vaijunto.teste.falha.etapa", "");
        String caminhoMarcador = System.getProperty("vaijunto.teste.falha.marcador", "");

        if (!etapa.equalsIgnoreCase(etapaConfigurada) || caminhoMarcador.isBlank()) {
            return;
        }

        try {
            if (!Files.deleteIfExists(Path.of(caminhoMarcador))) {
                return;
            }

            log.error("[TESTE] Falha controlada na etapa '{}'. Encerrando o processo.", etapa);
            System.err.flush();
            System.out.flush();

            // Encerra o processo Java e, consequentemente, o contêiner de teste.
            Runtime.getRuntime().halt(86);
        } catch (IOException erro) {
            log.error("[TESTE] Não foi possível injetar a falha controlada: {}",
                    erro.getMessage(), erro);
        }
    }
}
