package org.UEFS.shared.dto.responses;

public record GeneralResponse(
        int codigo,
        String tipo,
        String dados,
        int tamanho
) {
    public static GeneralResponse fromString(String rawMessage) {
        String[] partes = rawMessage.split("\\|", 4);

        if (partes.length != 4) {
            System.out.println(rawMessage);
            return new GeneralResponse(0, "", "", 0);
        }

        int codigo = Integer.parseInt(partes[0]);
        String tipo = partes[1];
        String dados = partes[2];
        int tamanho = Integer.parseInt(partes[3]);

        return new GeneralResponse(codigo, tipo, dados, tamanho);
    }
}