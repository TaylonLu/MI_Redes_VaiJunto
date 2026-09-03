package org.UEFS.vaijunto.Util;

import java.util.regex.Pattern;

public final class Constants {
    private Constants() {}

    // Códigos de Estilo e Cor ANSI
    // Reset
    public static final String ANSI_RESET = "\u001B[0m";

    // Cores de Texto (Normais)
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m"; // Magenta
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE = "\u001B[37m";

    // Cores de Texto (Brilhantes/Claras)
    public static final String ANSI_BRIGHT_BLACK = "\u001B[90m";
    public static final String ANSI_BRIGHT_RED = "\u001B[91m";
    public static final String ANSI_BRIGHT_GREEN = "\u001B[92m";
    public static final String ANSI_BRIGHT_YELLOW = "\u001B[93m";
    public static final String ANSI_BRIGHT_BLUE = "\u001B[94m";
    public static final String ANSI_BRIGHT_PURPLE = "\u001B[95m";
    public static final String ANSI_BRIGHT_CYAN = "\u001B[96m";
    public static final String ANSI_BRIGHT_WHITE = "\u001B[97m";

    // Cores de Fundo
    public static final String ANSI_BG_BLACK = "\u001B[40m";
    public static final String ANSI_BG_RED = "\u001B[41m";
    public static final String ANSI_BG_GREEN = "\u001B[42m";
    public static final String ANSI_BG_YELLOW = "\u001B[43m";
    public static final String ANSI_BG_BLUE = "\u001B[44m";
    public static final String ANSI_BG_PURPLE = "\u001B[45m";
    public static final String ANSI_BG_CYAN = "\u001B[46m";
    public static final String ANSI_BG_WHITE = "\u001B[47m";

    // Estilos de Texto
    public static final String ANSI_BOLD = "\u001B[1m";
    public static final String ANSI_UNDERLINE = "\u001B[4m";
    public static final String ANSI_BLINK = "\u001B[5m";

    // Regex para as 256 cores
    public static final Pattern FG_256 = Pattern.compile("\\[:(\\d{1,3})]");
    public static final Pattern BG_256 = Pattern.compile("\\[:bg_(\\d{1,3})]");
}
