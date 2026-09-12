package org.UEFS.vaijunto.DTO;

public record PassoItinerarioDTO(
    int inicio, int fim,
    String idCarona,
    String idMotorista
) {}

