package org.UEFS.Server.http.cluster;

import org.UEFS.Server.http.relogio.ServerConstant;
import org.UEFS.shared.dto.consorcio.TrechoReserva;
import org.UEFS.shared.dto.requests.ReservaRequest;

import java.util.List;

public record ResultadoRepasse(boolean ok, List<TrechoReserva> assentos, int http, String motivo, boolean falhaDeRede) {
    public static ResultadoRepasse sucesso(List<TrechoReserva> a) { return new ResultadoRepasse(true, a, 200, null, false); }
    public static ResultadoRepasse recusa(int http, String m)     { return new ResultadoRepasse(false, List.of(), http, m, false); }
    public static ResultadoRepasse rede(String m)                 { return new ResultadoRepasse(false, List.of(), 502, m, true); }
}

