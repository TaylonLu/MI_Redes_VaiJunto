package org.UEFS.shared.dto.requests;

public record CadastroRequest(
        String email, String senha,
        String nome
) {}
