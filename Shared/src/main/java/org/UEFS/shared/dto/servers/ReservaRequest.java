package org.UEFS.shared.dto.servers;

public record ReservaRequest(
        String id_transacao, String coordenador_id, String id_carona, long relogio_logico
) { }
