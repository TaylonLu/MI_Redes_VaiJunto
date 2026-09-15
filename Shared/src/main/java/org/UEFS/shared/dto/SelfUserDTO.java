package org.UEFS.shared.dto;

public record SelfUserDTO(
        String id, String email,
        String nome, PerfilMotoristaDTO perfilMotorista
) {}
