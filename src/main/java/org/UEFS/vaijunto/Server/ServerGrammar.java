package org.UEFS.vaijunto.Server;

public final class ServerGrammar {

    private static final String ENV_SPLIT = "\\|";

    public static final String ENV_JOIN = "|";
    public static final String ATTR_SEP = ";";
    public static final String LIST_SEP = ",";
    public static final String SUB_SEP = ":";

    private ServerGrammar() {}

    public static String sanitizar(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        return input.replaceAll("[|;,:]", " ")
                    .trim();
    }

    public static String[] extrairEnvelope(String mensagem) {
        return mensagem.split(ENV_SPLIT, -1);
    }
    public static String[] extrairAtributo(String attr) {
        return attr.split(ATTR_SEP, -1);
    }
    public static String[] extrairLista(String lista) {
        return lista.split(LIST_SEP, -1);
    }
    public static String[] extrairSubAtributos(String subAtributos) {
        return subAtributos.split(SUB_SEP, -1);
    }

    public static String empacotarEnvelope(
            String tipo,
            String token,
            String dados
    ) {
        dados = dados != null ? dados : "";

        return String.join(
                ENV_JOIN,
                tipo,
                dados,
                token,
                String.valueOf(dados.length())
        );
    }

    public static String construirAtributos(String... atributos) {
        return String.join(ATTR_SEP, atributos);
    }
}