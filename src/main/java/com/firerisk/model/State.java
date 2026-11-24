package com.firerisk.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.util.Arrays;
import java.util.Optional;

/**
 * Unidades federativas e o bioma predominante de cada uma.
 *
 * <p>Usar um unico bioma por UF e uma simplificacao do modelo.</p>
 */
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum State {
    AC("Acre", "Amazônia"), AL("Alagoas", "Caatinga"), AM("Amazonas", "Amazônia"), AP("Amapá", "Amazônia"),
    BA("Bahia", "Caatinga"), CE("Ceará", "Caatinga"), DF("Distrito Federal", "Cerrado"),
    ES("Espírito Santo", "Mata Atlântica"), GO("Goiás", "Cerrado"), MA("Maranhão", "Amazônia"),
    MG("Minas Gerais", "Cerrado"), MS("Mato Grosso do Sul", "Pantanal"), MT("Mato Grosso", "Amazônia"),
    PA("Pará", "Amazônia"), PB("Paraíba", "Caatinga"), PE("Pernambuco", "Caatinga"), PI("Piauí", "Caatinga"),
    PR("Paraná", "Mata Atlântica"), RJ("Rio de Janeiro", "Mata Atlântica"), RN("Rio Grande do Norte", "Caatinga"),
    RO("Rondônia", "Amazônia"), RR("Roraima", "Amazônia"), RS("Rio Grande do Sul", "Pampa"),
    SC("Santa Catarina", "Mata Atlântica"), SE("Sergipe", "Caatinga"), SP("São Paulo", "Mata Atlântica"),
    TO("Tocantins", "Cerrado");

    private final String label;
    private final String biome;

    State(String label, String biome) {
        this.label = label;
        this.biome = biome;
    }

    public String getCode() {
        return name();
    }

    public String getLabel() {
        return label;
    }

    public String getBiome() {
        return biome;
    }

    public static Optional<State> fromCode(String code) {
        return Arrays.stream(values()).filter(s -> s.name().equalsIgnoreCase(code)).findFirst();
    }
}
