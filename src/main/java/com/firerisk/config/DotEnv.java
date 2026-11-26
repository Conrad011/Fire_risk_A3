package com.firerisk.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Carrega um arquivo {@code .env} para as propriedades de sistema.
 *
 * <p>Mantem a chave da NASA FIRMS fora do codigo-fonte. Valores ja
 * definidos no ambiente tem precedencia e nao sao sobrescritos.</p>
 */
public final class DotEnv {

    private DotEnv() {
    }

    /** Le {@code .env} do diretorio de trabalho, se existir. */
    public static void load() {
        load(Path.of(".env"));
    }

    static void load(Path path) {
        if (!Files.isRegularFile(path)) {
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Aviso: nao foi possivel ler " + path + ": " + e.getMessage());
            return;
        }

        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = line.substring(0, separator).strip();
            String value = unquote(line.substring(separator + 1).strip());

            if (System.getenv(key) == null && System.getProperty(key) == null) {
                System.setProperty(key, value);
            }
        }
    }

    private static String unquote(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
