package com.firerisk.repository;

import com.firerisk.model.HistoryEntry;
import com.firerisk.model.Municipality;
import com.firerisk.model.RiskLevel;
import com.firerisk.model.RiskSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Acesso as tabelas {@code municipality} e {@code risk_analysis} (ver {@code schema.sql}).
 */
@Repository
public class AnalysisRepository {

    /** Repetir a mesma consulta em pouco tempo nao gera uma nova linha. */
    private static final Duration DUPLICATE_WINDOW = Duration.ofMinutes(10);

    private static final RowMapper<HistoryEntry> ROW = (rs, i) -> new HistoryEntry(
            OffsetDateTime.parse(rs.getString("analyzed_at")),
            LocalDate.parse(rs.getString("reference_date")),
            rs.getInt("days"),
            rs.getInt("radius_km"),
            rs.getDouble("risk_index"),
            RiskLevel.valueOf(rs.getString("risk_level")),
            rs.getInt("fires_counted"),
            rs.getInt("fires_low_confidence"),
            rs.getInt("fires_in_municipality"),
            rs.getObject("avg_temp", Double.class),
            rs.getObject("avg_rain", Double.class),
            rs.getInt("dry_days"),
            rs.getString("satellite"),
            rs.getString("data_source"));

    private final JdbcTemplate jdbc;

    public AnalysisRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Grava uma analise e atualiza o cadastro do municipio.
     *
     * @return {@code false} se a mesma analise ja foi gravada ha menos de 10 minutos
     */
    @Transactional
    public boolean save(Municipality city, int days, int radiusKm, RiskSummary summary, String satellite,
                        String dataSource, LocalDate referenceDate, OffsetDateTime analyzedAt) {
        if (isRecentDuplicate(city.codarea(), days, radiusKm, dataSource, referenceDate, summary.riskIndex(), analyzedAt)) {
            return false;
        }
        jdbc.update("""
                INSERT INTO municipality (codarea, nome, uf, latitude, longitude) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (codarea) DO UPDATE SET
                    nome = excluded.nome, uf = excluded.uf,
                    latitude = excluded.latitude, longitude = excluded.longitude
                """, city.codarea(), city.nome(), city.uf(), city.lat(), city.lon());
        jdbc.update("""
                INSERT INTO risk_analysis (
                    codarea, analyzed_at, reference_date, days, radius_km, risk_index, risk_level,
                    fires_counted, fires_low_confidence, fires_in_municipality,
                    avg_temp, avg_rain, dry_days, satellite, data_source)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                city.codarea(), analyzedAt.truncatedTo(ChronoUnit.SECONDS).toString(), referenceDate.toString(),
                days, radiusKm, summary.riskIndex(), summary.level().name(),
                summary.totalFires(), summary.lowConfidenceFires(), summary.firesInMunicipality(),
                finite(summary.avgTemp()), finite(summary.avgRain()), summary.dryDays(), satellite, dataSource);
        return true;
    }

    /** Ultimas analises do municipio, da mais recente para a mais antiga. */
    public List<HistoryEntry> findByMunicipality(String codarea, int limit) {
        return jdbc.query("""
                SELECT * FROM risk_analysis WHERE codarea = ?
                ORDER BY reference_date DESC, analyzed_at DESC LIMIT ?
                """, ROW, codarea, limit);
    }

    public boolean exists(String codarea, LocalDate referenceDate, int days, int radiusKm, String dataSource) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM risk_analysis
                WHERE codarea = ? AND reference_date = ? AND days = ? AND radius_km = ? AND data_source = ?
                """, Integer.class, codarea, referenceDate.toString(), days, radiusKm, dataSource);
        return count != null && count > 0;
    }

    private boolean isRecentDuplicate(String codarea, int days, int radiusKm, String dataSource,
                                      LocalDate referenceDate, double riskIndex, OffsetDateTime analyzedAt) {
        List<HistoryEntry> last = jdbc.query("""
                SELECT * FROM risk_analysis
                WHERE codarea = ? AND days = ? AND radius_km = ? AND data_source = ? AND reference_date = ?
                ORDER BY id DESC LIMIT 1
                """, ROW, codarea, days, radiusKm, dataSource, referenceDate.toString());
        return !last.isEmpty()
                && last.get(0).riskIndex() == riskIndex
                && Duration.between(last.get(0).analyzedAt(), analyzedAt).compareTo(DUPLICATE_WINDOW) < 0;
    }

    private static Double finite(double value) {
        return Double.isFinite(value) ? value : null;
    }
}
