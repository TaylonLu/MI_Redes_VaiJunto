package org.UEFS.shared.dto.requests;

import org.UEFS.shared.dto.TrechoReservaDTO;

import java.util.List;

public record ReservaRequest(List<TrechoReservaDTO> passos) {}
