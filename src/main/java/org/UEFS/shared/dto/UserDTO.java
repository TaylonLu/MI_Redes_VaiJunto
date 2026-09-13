package org.UEFS.shared.dto;

public record UserDTO(
        String id, String email,
        String nome, boolean eMotorista
) {}
