package org.UEFS.shared.dto.requests;

public record GeneralRequest(
        String pedido,
        String dados,
        String token,
        int tamanho
) {
    String toMessage() {
        return String.join("|", pedido, dados, token, String.valueOf(tamanho));
    }

    @Override
    public String toString() {
        return toMessage();
    }

    public static String toMessage(String pedido, String dados, String token, int tamanho) {
        return String.join("|", pedido, dados, token, String.valueOf(tamanho));
    }
}
