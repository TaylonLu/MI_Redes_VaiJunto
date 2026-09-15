package org.UEFS.shared.dto.requests;

public record LoginRequest(
    String email, String senha
){}