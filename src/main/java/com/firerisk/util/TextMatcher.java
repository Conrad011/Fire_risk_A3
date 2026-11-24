package com.firerisk.util;

import java.text.Normalizer;
import java.util.Locale;

/** Comparacao de nomes tolerante a acentos, maiusculas e erros de digitacao. */
public final class TextMatcher {

    private TextMatcher() {
    }

    /** {@code "São João del-Rei"} e {@code "sao joao del rei"} ficam iguais. */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[-'’]", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    /** Similaridade de 0 a 1 pela distancia de edicao de Levenshtein. */
    public static double similarity(String first, String second) {
        int longest = Math.max(first.length(), second.length());
        return longest == 0 ? 1.0 : 1.0 - (double) editDistance(first, second) / longest;
    }

    private static int editDistance(String first, String second) {
        int[] previous = new int[second.length() + 1];
        for (int j = 0; j < previous.length; j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= first.length(); i++) {
            int[] current = new int[previous.length];
            current[0] = i;
            for (int j = 1; j < current.length; j++) {
                int cost = first.charAt(i - 1) == second.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            previous = current;
        }
        return previous[second.length()];
    }
}
