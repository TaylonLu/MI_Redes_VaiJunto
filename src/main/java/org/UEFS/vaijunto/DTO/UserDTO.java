package org.UEFS.vaijunto.DTO;

public record UserDTO(
        String id, String email,
        String nome, boolean eMotorista
) implements  DataRecord {
    public String toText() {
        return String.join(";", id, nome, email, String.valueOf(eMotorista));
    }
}
