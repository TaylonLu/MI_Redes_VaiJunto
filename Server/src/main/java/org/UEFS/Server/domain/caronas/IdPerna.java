package org.UEFS.Server.domain.caronas;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record IdPerna(String idCarona, int inicio, int fim) {
    private static final Pattern PADRAO_C = Pattern.compile("^(.*)_(\\d+)_(\\d+)$");

    public static Optional<IdPerna> decodificar(String idComposto) {
        if (idComposto == null) return Optional.empty();

        Matcher M = PADRAO_C.matcher(idComposto);

        if (M.matches()) {
            int inicio = Integer.parseInt(M.group(2));
            int fim = Integer.parseInt(M.group(3));
            return Optional.of(new IdPerna(M.group(1), inicio, fim));
        }

        return Optional.empty();
    }

    /** O id da carona, sem o sufixo da perna. */
    public static String idBase(String id) {
        if (id == null) return null;

        Matcher M = PADRAO_C.matcher(id);

        return M.matches()
                ? M.group(1)
                : id;
    }
}