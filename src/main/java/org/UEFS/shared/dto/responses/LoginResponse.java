package org.UEFS.shared.dto.responses;

import org.UEFS.shared.dto.UserDTO;

public record LoginResponse(
    String token,
    UserDTO usuario
) {}