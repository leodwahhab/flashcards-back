package com.example.flashcards_api.card;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilitários puros para texto com lacunas (cloze) no formato {@code {{c1::resposta}}} ou
 * {@code {{c2::resposta::dica}}}. Os números válidos vão de 1 a 99; vários clozes com o mesmo número são permitidos
 * (viram o mesmo card). Chaves aninhadas não são suportadas.
 */
public final class Cloze {

    /** Menor número de cloze válido. */
    public static final int MIN_NUMBER = 1;
    /** Maior número de cloze válido. */
    public static final int MAX_NUMBER = 99;

    // Resposta e dica são preguiçosas para que "}}" feche o primeiro cloze possível.
    private static final Pattern PATTERN =
            Pattern.compile("\\{\\{c([1-9][0-9]?)::(.+?)(?:::(.*?))?\\}\\}", Pattern.DOTALL);

    private Cloze() {
    }

    /**
     * Uma lacuna encontrada no texto.
     *
     * @param number número do cloze (1..99)
     * @param answer resposta oculta
     * @param hint   dica ou {@code null} se não houver (dica em branco também vira nula)
     * @param start  posição inicial (inclusive) no texto original
     * @param end    posição final (exclusiva) no texto original
     */
    public record Match(int number, String answer, String hint, int start, int end) {
    }

    /**
     * Extrai todas as lacunas, na ordem em que aparecem.
     *
     * @param text texto com lacunas (aceita nulo)
     * @return lacunas encontradas; lista vazia se não houver
     */
    public static List<Match> parse(String text) {
        List<Match> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        Matcher m = PATTERN.matcher(text);
        while (m.find()) {
            String hint = m.group(3);
            result.add(new Match(Integer.parseInt(m.group(1)), m.group(2),
                    hint == null || hint.isBlank() ? null : hint, m.start(), m.end()));
        }
        return result;
    }

    /**
     * Números de cloze distintos, em ordem crescente.
     *
     * @param text texto com lacunas (aceita nulo)
     * @return números distintos; vazio se não houver lacunas
     */
    public static SortedSet<Integer> clozeNumbers(String text) {
        SortedSet<Integer> numbers = new TreeSet<>();
        for (Match match : parse(text)) {
            numbers.add(match.number());
        }
        return numbers;
    }

    /**
     * Indica se o texto tem ao menos uma lacuna válida.
     *
     * @param text texto (aceita nulo)
     * @return {@code true} se houver lacuna
     */
    public static boolean hasCloze(String text) {
        return !parse(text).isEmpty();
    }

    /**
     * Texto da pergunta do cloze {@code n}: as lacunas {@code n} viram {@code [...]} (ou {@code [dica]}) e as demais
     * mostram a própria resposta.
     *
     * @param text texto com lacunas (aceita nulo)
     * @param n    número do cloze ativo
     * @return texto renderizado
     */
    public static String renderQuestion(String text, int n) {
        return render(text, n, false);
    }

    /**
     * Texto da resposta do cloze {@code n}: todas as lacunas mostram a resposta e a ativa vem entre
     * {@code <b></b>}.
     *
     * @param text texto com lacunas (aceita nulo)
     * @param n    número do cloze ativo
     * @return texto renderizado
     */
    public static String renderAnswer(String text, int n) {
        return render(text, n, true);
    }

    private static String render(String text, int n, boolean reveal) {
        if (text == null) {
            return "";
        }
        Matcher m = PATTERN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            int number = Integer.parseInt(m.group(1));
            String answer = m.group(2);
            String hint = m.group(3);
            String replacement;
            if (number != n) {
                replacement = answer;
            } else if (reveal) {
                replacement = "<b>" + answer + "</b>";
            } else {
                replacement = hint == null || hint.isBlank() ? "[...]" : "[" + hint + "]";
            }
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }
}
