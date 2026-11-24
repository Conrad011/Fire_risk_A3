package com.firerisk.model;

import com.firerisk.util.TextMatcher;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Malha municipal indexada por UF e nome normalizado.
 */
public final class GeoData {

    private static final double MIN_SIMILARITY = 0.6;

    private final Map<String, Map<String, GeoFeature>> byState = new HashMap<>();

    public GeoData(List<GeoFeature> features) {
        features.stream()
                .filter(f -> f.nome() != null && f.uf() != null)
                .sorted(Comparator.comparing(f -> TextMatcher.normalize(f.nome())))
                .forEach(f -> byState.computeIfAbsent(f.uf(), uf -> new LinkedHashMap<>())
                        .put(TextMatcher.normalize(f.nome()), f));
    }

    /** Busca ignorando acentos, maiusculas e hifens. */
    public Optional<GeoFeature> findByName(String name, String uf) {
        return Optional.ofNullable(cities(uf).get(TextMatcher.normalize(name)));
    }

    /** Nomes oficiais da UF em ordem alfabetica. */
    public List<String> namesIn(String uf) {
        return cities(uf).values().stream().map(GeoFeature::nome).toList();
    }

    /** Municipios da UF com nome parecido, do mais ao menos similar. */
    public List<String> suggest(String name, String uf, int limit) {
        String target = TextMatcher.normalize(name);
        return cities(uf).entrySet().stream()
                .filter(e -> TextMatcher.similarity(target, e.getKey()) >= MIN_SIMILARITY)
                .sorted(Comparator.comparingDouble(
                        (Map.Entry<String, GeoFeature> e) -> -TextMatcher.similarity(target, e.getKey())))
                .limit(limit)
                .map(e -> e.getValue().nome())
                .toList();
    }

    private Map<String, GeoFeature> cities(String uf) {
        return byState.getOrDefault(uf, Map.of());
    }
}
