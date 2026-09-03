package org.UEFS.vaijunto.Util;

import java.util.Scanner;
import java.util.function.Predicate;
import java.util.regex.Matcher;

import static org.UEFS.vaijunto.Util.Constants.*;

public final class IOUtils {
    private static final Scanner scanner = new Scanner(System.in);

    public static void closeScanner() {
        scanner.close();
    }

    /**
     * Simplesmente imprime uma mensagem e pega uma string do usuário.
     * @param mensagem Mensagem a ser exibida.
     * @return Texto digitado pelo usuário.
     */
    public static String ask(String mensagem) {
        try {
            fprintf(mensagem);
            return scanner.nextLine().trim();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Pede uma entrada ao usuário, mas permite que ele pressione Enter para manter um valor existente.
     * @param mensagem A mensagem a ser exibida.
     * @param valorAtual O valor atual que será mantido se a entrada for vazia.
     * @return A nova entrada do usuário ou o valorAtual se a entrada for vazia.
     */
    public static String askOrKeep(String mensagem, String valorAtual) {
        fprintln("[:240][:underline]Digite \"-\" para cancelar[::]");
        fprintf("%s (Atual: %s): ", mensagem, valorAtual);
        String entrada = scanner.nextLine().trim();

        if (entrada.equals("-") || entrada.isEmpty()) {
            return valorAtual; // Mantém o valor atual
        }

        System.out.println();
        return entrada;
    }

    /**
     * Pede uma entrada ao usuário, mas permite que ele pressione Enter para manter um valor existente.
     * @param mensagem A mensagem a ser exibida.
     * @param valorAtual O valor atual que será mantido se a entrada for vazia.
     * @return A nova entrada do usuário ou o valorAtual se a entrada for vazia.
     */
    public static String askOrKeep(String mensagem, String valorAtual, Predicate<String> validator) {
        return askOrKeep(mensagem, valorAtual, validator, "[:red]:: Erro: Entrada inválida. Mantendo valor atual.[::]");
    }

    /**
     * Pede uma entrada ao usuário, mas permite que ele pressione Enter para manter um valor existente.
     * @param mensagem A mensagem a ser exibida.
     * @param valorAtual O valor atual que será mantido se a entrada for vazia.
     * @param mensagemErro Mensagem a ser exibida quando um valor inválido for inserido.
     * @return A nova entrada do usuário ou o valorAtual se a entrada for vazia.
     */
    public static String askOrKeep(String mensagem, String valorAtual, Predicate<String> validator, String mensagemErro) {
        fprintln("[:240][:underline]Digite \"-\" para cancelar[::]");
        fprintf("%s (Atual: %s): ", mensagem, valorAtual);
        String entrada = scanner.nextLine().trim();

        if (entrada.equals("-")) {
            return null; // Sinaliza cancelamento
        }
        if (entrada.isEmpty()) {
            return valorAtual; // Mantém o valor atual
        }

        try {
            if (validator.test(entrada))
                return entrada;
            fprintln(mensagemErro);
            return valorAtual;
        } catch (Exception e) {
            fprintf("[:cyan][:bold]::Entrada Inválida, usando valor padrão...[::]");
            return valorAtual;
        }
    }

    /**
     * Pede por um inteiro ao usuário
     * @param mensagem Pergunta de contexto
     * @param valorAtual Valor de retorno caso a entrada não seja inválida.
     * @param validation Lambda de validação do inteiro digitado.
     * @return Inteiro padrão ou inteiro válido digitado pelo usuário.
     */
    public static Integer askForIntOrKeep(String mensagem, int valorAtual, Predicate<Integer> validation) {
        return askForIntOrKeep(mensagem, valorAtual, validation, "[:red]:: Erro: Entrada inválida. Mantendo valor atual.[::]");
    }

    /**
     * Pede por um inteiro ao usuário
     * @param mensagem Pergunta de contexto
     * @param valorAtual Valor de retorno caso a entrada não seja inválida.
     * @param validation Lambda de validação do inteiro digitado.
     * @param mensagemErro Mensagem a ser exibida quando um valor inválido for inserido.
     * @return Inteiro padrão ou inteiro válido digitado pelo usuário.
     */
    public static Integer askForIntOrKeep(String mensagem, int valorAtual, Predicate<Integer> validation, String mensagemErro) {
        fprintln("[:240][:underline]Digite \"-\" para cancelar[::]");
        fprintf("%s (Atual: %d): ", mensagem, valorAtual);
        String entrada = scanner.nextLine().trim();

        if (entrada.equals("-") || entrada.isEmpty()) {
            return valorAtual;
        }

        try {
            Integer val = Integer.parseInt(entrada);
            if (validation.test(val))
                return val;
            fprintln(mensagemErro);
        } catch (NumberFormatException e) {
            fprintln("[:red]:: Erro: Entrada inválida.[::]");
            return valorAtual;
        }

        return valorAtual;
    }

    /**
     * Pede por ume entrada do usuário e continua pedindo até um texto
     * válido ser inserido ou "-" para cancelar.
     * @param mensagem Pergunta de contexto para o usuário.
     * @param validator Validação para confirmar a validez da entrada.
     * @return Texto válido digitado ou {@code null}.
     */
    public static String askWhile(String mensagem, Predicate<String> validator) {
        return askWhile(mensagem, validator, "[:bold][:red]::Valor Inválido! Tente novamente.[::]");
    }

    /**
     * Pede por ume entrada do usuário e continua pedindo até um texto
     * válido ser inserido ou "-" para cancelar.
     * @param mensagem Pergunta de contexto para o usuário.
     * @param validator Validação para confirmar a validez da entrada.
     * @param mensagemErro Mensagem a ser exibida quando um valor inválido for inserido.
     * @return Texto válido digitado ou {@code null}.
     */
    public static String askWhile(String mensagem, Predicate<String> validator, String mensagemErro) {
        while (true) {
            fprintln("[:240][:underline]Digite \"-\" para cancelar[::]");
            // CORREÇÃO AQUI: Passa a mensagem diretamente para ser formatada
            fprintf(mensagem + ": ");
            String entrada = scanner.nextLine().trim();

            if (entrada.equals("-")) return null;

            try {
                if (validator.test(entrada)) return entrada;
                else fprintln(mensagemErro);
            } catch (Exception e) {
                fprintln("[:bold][:red]Erro na validação: [::]" + e.getMessage());
            }
        }
    }

    /**
     * Pede um input do usuário em um loop que acaba quando o usuário inserir algo válido,
     * uma string não vazia, ou desistir inserindo apenas "-".
     * @param mensagem Mensagem a ser exibida antes de pedir o input.
     * @return {@code String} válida digitada pelo usuário ou {@code null} caso tenha desistido.
     */
    public static String askWhile(String mensagem) {
        while (true) {
            fprintln("[:240][:underline]Digite \"-\" para cancelar[::]");
            System.out.printf(format(mensagem));
            String entrada = scanner.nextLine().trim();

            if (entrada.equals("-")) {
                return null;
            }

            if (entrada.isBlank()) {
                fprintln("[:bold][:red]|| Erro: Entrada não pode ser vazia.[::]");
            } else {
                return entrada;
            }
        }
    }

    /**
     * Pede por um número inteiro ao usuário, enquanto ele não digitar um valor
     * válido ou "-" para cancelar, o loop continua.
     * @param message Pergunta de contexto.
     * @return {@code Integer} se ele digitou um número válido, senão {@code null}.
     */
    public static Integer askForInt(String message) {
        while (true) {
            String numero = askWhile(message);

            try {
                if (numero == null) return null;
                return Integer.parseInt(numero);
            } catch (NumberFormatException e) {
                fprintln("[:bold][:red]:: Erro: Digite apenas um número.[::]");
            }
        }
    }

    public static Integer askForInt(String mensagem, Predicate<Integer> validator) {
        return askForInt(mensagem, validator, "[:red]Erro: valor inválido...[::]");
    }

    /**
     * Pede por um número inteiro ao usuário, enquanto ele não digitar um valor
     * válido ou "-" para cancelar, o loop continua.
     * @param mensagem Pergunta de contexto
     * @param validator Lambda pára validar o número digitado.
     * @return {@code Integer} se o usuário digitou um número válido.
     * {@code null} se ele quis parar.
     */
    public static Integer askForInt(String mensagem, Predicate<Integer> validator, String mensagemErro) {
        while (true) {
            fprintln("[:240][:underline]Digite \"-\" para cancelar[::]");
            System.out.print(format(mensagem)); // Também corrigido aqui para garantir
            String entrada = scanner.nextLine().trim();

            if (entrada.equals("-")) return null;

            try {
                Integer numero = Integer.parseInt(entrada);
                if (validator.test(numero)) {
                    return numero;
                } else {
                    fprintln(mensagemErro);
                }
            } catch (Exception e) {
                fprintln("[:bold][:red]:: Erro na validação: [::]" + e.getMessage());
            }
        }
    }

    /**
     * Pede, sem mensagem, por uma confirmação do usuário, que deve digitar "S" ou "s" para confirmar.
     * @return {@code true} ou {@code false} dependendo da resposta do usuário.
     */
    public static boolean askForConfirmation() {
        String resposta = ask("S/N =: ");
        return resposta.equalsIgnoreCase("s");
    }

    /**
     * Pede por uma confirmação do usuário, que deve digitar "S" ou "s" para confirmar.
     * @param mensagem Texto da pergunta de confirmação
     * @return {@code true} ou {@code false} dependendo da resposta do usuário.
     */
    public static boolean askForConfirmation(String mensagem) {
        String resposta = ask(mensagem + " S/N =: ");
        return resposta.equalsIgnoreCase("s");
    }

    /**
     * Utiliza caracteres de escape ASCII para limpar o terminal
     */
    public static void limparTela() {
        try {
            String os = System.getProperty("os.name");

            if (os.contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                new ProcessBuilder("clear").inheritIO().start().waitFor();
            }
        } catch (final Exception e) {
            //  Lida com o erro (por exemplo, se o comando não for encontrado)
            //  Como fallback, pode-se apenas pular algumas linhas.
            for (int i = 0; i < 50; ++i) System.out.println();
        }
    }

    /**
     * Imprime uma linha no console, aplicando a formatação de cor personalizada.
     *
     * @param text O texto contendo as tags de cor a ser formatado e impresso.
     */
    public static void fprintln(String text) {
        System.out.println(format(text));
    }

    /**
     * Imprime uma linha no console, aplicando a formatação de cor personalizada,
     * e formatado com variáveis, como no {}.
     * @param text O texto contendo as tags de cor a ser formatado e impresso.
     * @param args Argumentos para formatação de texto.
     */
    public static void fprintf(String text, Object... args) {
        String msg = format(text);
        System.out.printf(msg, args);
    }

    /**
     * Formata uma string com valores de escape ANSI pars cores.
     * @param text String para formatação.
     * @return String formatada.
     */
    public static String format(String text) {
        Matcher fgMatcher = FG_256.matcher(text);
        StringBuilder SB = new StringBuilder();
        while (fgMatcher.find()) {
            String colorCode = fgMatcher.group(1);
            String ansiScape = "\u001B[38;5;"+ colorCode +"m";
            fgMatcher.appendReplacement(SB, Matcher.quoteReplacement(ansiScape));
        }
        fgMatcher.appendTail(SB);
        text = SB.toString();

        Matcher bgMatcher = BG_256.matcher(text);
        SB = new StringBuilder();
        while (bgMatcher.find()) {
            String colorCode = bgMatcher.group(1);
            String ansiEscape = "\u001B[48;5;" + colorCode + "m";
            bgMatcher.appendReplacement(SB, Matcher.quoteReplacement(ansiEscape));
        }
        bgMatcher.appendTail(SB);
        text = SB.toString();

        return text
                // Cores
                .replace("[:black]", ANSI_BLACK)
                .replace("[:red]", ANSI_RED)
                .replace("[:green]", ANSI_GREEN)
                .replace("[:yellow]", ANSI_YELLOW)
                .replace("[:blue]", ANSI_BLUE)
                .replace("[:purple]", ANSI_PURPLE)
                .replace("[:cyan]", ANSI_CYAN)
                .replace("[:white]", ANSI_WHITE)
                // Cores Brilhantes
                .replace("[:br_black]", ANSI_BRIGHT_BLACK)
                .replace("[:br_red]", ANSI_BRIGHT_RED)
                .replace("[:br_green]", ANSI_BRIGHT_GREEN)
                .replace("[:br_yellow]", ANSI_BRIGHT_YELLOW)
                .replace("[:br_blue]", ANSI_BRIGHT_BLUE)
                .replace("[:br_purple]", ANSI_BRIGHT_PURPLE)
                .replace("[:br_cyan]", ANSI_BRIGHT_CYAN)
                .replace("[:br_white]", ANSI_BRIGHT_WHITE)
                // Fundos
                .replace("[:bg_black]", ANSI_BG_BLACK)
                .replace("[:bg_red]", ANSI_BG_RED)
                .replace("[:bg_green]", ANSI_BG_GREEN)
                .replace("[:bg_yellow]", ANSI_BG_YELLOW)
                .replace("[:bg_blue]", ANSI_BG_BLUE)
                .replace("[:bg_purple]", ANSI_BG_PURPLE)
                .replace("[:bg_cyan]", ANSI_BG_CYAN)
                .replace("[:bg_white]", ANSI_BG_WHITE)
                // Estilos
                .replace("[:bold]", ANSI_BOLD)
                .replace("[:underline]", ANSI_UNDERLINE)
                .replace("[:blink]", ANSI_BLINK)
                // Reset
                .replace("[::]", ANSI_RESET);
    }
}