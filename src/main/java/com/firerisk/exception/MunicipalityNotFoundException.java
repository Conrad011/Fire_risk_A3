package com.firerisk.exception;

import java.util.List;

/** O nome informado nao corresponde a nenhum municipio da UF. */
public class MunicipalityNotFoundException extends RuntimeException {

    private final List<String> suggestions;

    public MunicipalityNotFoundException(String city, String stateName, List<String> suggestions) {
        super("Não encontramos \"" + city + "\" em " + stateName + ".");
        this.suggestions = List.copyOf(suggestions);
    }

    public List<String> suggestions() {
        return suggestions;
    }
}
