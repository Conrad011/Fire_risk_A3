package com.firerisk.repository;

import com.firerisk.model.HistoryEntry;
import com.firerisk.model.Municipality;
import com.firerisk.model.RiskLevel;
import com.firerisk.model.RiskSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Usa um arquivo SQLite real, criado com o mesmo schema.sql da aplicacao. */
class AnalysisRepositoryTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 11, 26, 14, 0, 0, 0, ZoneOffset.ofHours(-3));
    private static final LocalDate TODAY = NOW.toLocalDate();
    private static final Municipality CITY = new Municipality("3167202", "Sete Lagoas", "MG", -19.4658, -44.2467, true);

    @TempDir
    Path dir;

    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbc;
    private AnalysisRepository repository;

    @BeforeEach
    void setUp() {
        dataSource = new SingleConnectionDataSource(
                "jdbc:sqlite:" + dir.resolve("teste.db") + "?foreign_keys=on", true);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        repository = new AnalysisRepository(jdbc);
    }

    /** No Windows o arquivo so pode ser apagado depois que a conexao fecha. */
    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void gravaELeAnaliseNoHorarioDeBrasilia() {
        assertTrue(repository.save(CITY, 5, 100, summary(2.81), "VIIRS NOAA-20", "live", TODAY, NOW));

        List<HistoryEntry> history = repository.findByMunicipality("3167202", 10);

        assertEquals(1, history.size());
        assertEquals(2.81, history.get(0).riskIndex());
        assertEquals(RiskLevel.LOW, history.get(0).level());
        assertEquals("2026-11-26T14:00-03:00", history.get(0).analyzedAt().toString());
        assertEquals("Sete Lagoas", jdbc.queryForObject("SELECT nome FROM municipality", String.class));
    }

    @Test
    void mesmaConsultaEmMenosDeDezMinutosNaoDuplica() {
        repository.save(CITY, 5, 100, summary(2.81), "VIIRS NOAA-20", "live", TODAY, NOW);

        assertFalse(repository.save(CITY, 5, 100, summary(2.81), "VIIRS NOAA-20", "live", TODAY, NOW.plusMinutes(5)));
        assertTrue(repository.save(CITY, 5, 100, summary(2.81), "VIIRS NOAA-20", "live", TODAY, NOW.plusMinutes(11)));
        assertTrue(repository.save(CITY, 5, 200, summary(3.28), "VIIRS NOAA-20", "live", TODAY, NOW.plusMinutes(1)));
        assertEquals(3, repository.findByMunicipality("3167202", 10).size());
    }

    @Test
    void ordenaDoDiaMaisRecenteParaOMaisAntigo() {
        repository.save(CITY, 5, 100, summary(1.0), null, "backfill", TODAY.minusDays(2), NOW);
        repository.save(CITY, 5, 100, summary(2.0), null, "backfill", TODAY.minusDays(1), NOW);

        List<HistoryEntry> history = repository.findByMunicipality("3167202", 10);

        assertEquals(TODAY.minusDays(1), history.get(0).referenceDate());
        assertTrue(repository.exists("3167202", TODAY.minusDays(2), 5, 100, "backfill"));
        assertFalse(repository.exists("3167202", TODAY.minusDays(3), 5, 100, "backfill"));
    }

    @Test
    void bancoRejeitaValoresInvalidos() {
        // O Spring nao tem a tabela de codigos de erro do SQLite; a mensagem identifica a restricao
        DataAccessException check = assertThrows(DataAccessException.class,
                () -> repository.save(CITY, 9, 100, summary(2.0), null, "live", TODAY, NOW));
        assertTrue(check.getMessage().contains("CHECK constraint failed"));

        DataAccessException foreignKey = assertThrows(DataAccessException.class,
                () -> jdbc.update("INSERT INTO risk_analysis (codarea, analyzed_at, reference_date, days, radius_km,"
                        + " risk_index, risk_level, fires_counted, fires_low_confidence, fires_in_municipality,"
                        + " dry_days, data_source) VALUES ('999', 'x', 'x', 5, 100, 1, 'LOW', 0, 0, 0, 0, 'live')"));
        assertTrue(foreignKey.getMessage().contains("FOREIGN KEY constraint failed"));
    }

    private static RiskSummary summary(double index) {
        return new RiskSummary("Cerrado", 1.2, 3, 1, 0, 31.5, 27.0, 7.8, 22.6, 44.0, 0,
                0.03, 0.22, index, RiskLevel.of(index));
    }
}
