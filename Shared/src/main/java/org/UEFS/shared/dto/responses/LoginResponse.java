package org.UEFS.shared.dto.responses;

import org.UEFS.shared.dto.SelfUserDTO;

public record LoginResponse(
    String token,
    SelfUserDTO usuario
) {}