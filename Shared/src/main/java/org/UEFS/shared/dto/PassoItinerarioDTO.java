package org.UEFS.shared.dto;

public record PassoItinerarioDTO(
    int inicio, int fim,
    String idCarona,
    String idMotorista
) {}

