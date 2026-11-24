package com.firerisk.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.firerisk.model.GeoFeature;
import com.firerisk.model.MunicipalityName;
import com.firerisk.model.Snapshot;
import com.firerisk.util.TextMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Arquivos JSON com dados salvos para quando as APIs externas nao respondem.
 *
 * <p>Um arquivo por municipio ({@code sete-lagoas-mg.json}) e um com os nomes de todos os
 * municipios do IBGE, que a malha precisa para funcionar.</p>
 */
@Component
public class SnapshotStore {

    private static final Logger log = LoggerFactory.getLogger(SnapshotStore.class);
    private static final String NAMES_FILE = "municipios-nomes.json";

    private final Path dir;
    private final ObjectMapper mapper;

    public SnapshotStore(@Value("${firerisk.snapshot-dir:data/snapshot}") String dir, ObjectMapper mapper) {
        this.dir = Path.of(dir);
        // Sem o ajuste, o Jackson converte o horario de gravacao para UTC ao ler o arquivo
        this.mapper = mapper.copy()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
    }

    public Optional<Snapshot> load(GeoFeature feature) {
        return read(fileFor(feature.nome(), feature.uf()), new TypeReference<>() {
        });
    }

    public Path save(Snapshot snapshot) {
        Path file = fileFor(snapshot.municipality().nome(), snapshot.municipality().uf());
        write(file, snapshot);
        return file;
    }

    public Optional<Map<String, MunicipalityName>> loadNames() {
        return read(dir.resolve(NAMES_FILE), new TypeReference<>() {
        });
    }

    public void saveNames(Map<String, MunicipalityName> names) {
        write(dir.resolve(NAMES_FILE), names);
    }

    private Path fileFor(String nome, String uf) {
        String slug = TextMatcher.normalize(nome).replaceAll("[^a-z0-9]+", "-");
        return dir.resolve(slug + "-" + uf.toLowerCase(Locale.ROOT) + ".json");
    }

    private <T> Optional<T> read(Path file, TypeReference<T> type) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(file.toFile(), type));
        } catch (IOException e) {
            log.warn("Arquivo de dados salvos ilegivel, ignorado: {}", file);
            return Optional.empty();
        }
    }

    private void write(Path file, Object value) {
        try {
            Files.createDirectories(dir);
            mapper.writeValue(file.toFile(), value);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao gravar " + file, e);
        }
    }
}
