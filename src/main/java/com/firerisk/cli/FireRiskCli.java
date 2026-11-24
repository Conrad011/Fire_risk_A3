package com.firerisk.cli;

import com.firerisk.exception.InvalidRequestException;
import com.firerisk.exception.MunicipalityNotFoundException;
import com.firerisk.model.Analysis;
import com.firerisk.model.AnalysisRequest;
import com.firerisk.model.RiskSummary;
import com.firerisk.model.Snapshot;
import com.firerisk.service.FireRiskService;
import com.firerisk.service.SnapshotService;
import com.firerisk.util.Brasilia;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Modo terminal: uma analise unica.
 *
 * <p>Ativado por {@code --cli}. Aceita {@code --city}, {@code --uf}, {@code --days} e
 * {@code --radius}; o padrao e Sete Lagoas/MG, 5 dias e 100 km. Com {@code --snapshot}
 * grava os dados de contingencia em vez de analisar.</p>
 */
@Component
@ConditionalOnProperty(name = "firerisk.cli", havingValue = "true")
public class FireRiskCli implements ApplicationRunner {

    private static final String DEFAULT_CITY = "Sete Lagoas";
    private static final String DEFAULT_UF = "MG";
    private static final int DEFAULT_DAYS = 5;
    private static final int DEFAULT_RADIUS_KM = 100;

    private final FireRiskService service;
    private final SnapshotService snapshots;
    private final ConfigurableApplicationContext context;

    public FireRiskCli(FireRiskService service, SnapshotService snapshots, ConfigurableApplicationContext context) {
        this.service = service;
        this.snapshots = snapshots;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (args.containsOption("snapshot")) {
            saveSnapshot(args);
            return;
        }
        Analysis analysis;
        try {
            AnalysisRequest request = new AnalysisRequest(
                    textArg(args, "city", DEFAULT_CITY),
                    textArg(args, "uf", DEFAULT_UF),
                    intArg(args, "days", DEFAULT_DAYS),
                    intArg(args, "radius", DEFAULT_RADIUS_KM));
            System.out.println("Analisando " + request.city() + " / " + request.uf() + "...");
            analysis = service.analyze(request);
        } catch (MunicipalityNotFoundException e) {
            String hint = e.suggestions().isEmpty() ? "" : " Você quis dizer: " + String.join(", ", e.suggestions()) + "?";
            fail(e.getMessage() + hint);
            return;
        } catch (RuntimeException e) {
            fail(e.getMessage());
            return;
        }
        print(analysis);
        context.close();
    }

    /** {@code --snapshot [--city] [--uf] [--date=AAAA-MM-DD]}; a data padrao e ontem. */
    private void saveSnapshot(ApplicationArguments args) {
        SnapshotService.Saved saved;
        try {
            String date = textArg(args, "date", Brasilia.today().minusDays(1).toString());
            LocalDate reference;
            try {
                reference = LocalDate.parse(date);
            } catch (DateTimeParseException e) {
                throw new InvalidRequestException("Data inválida para --date: " + date + ". Use AAAA-MM-DD.");
            }
            saved = snapshots.save(textArg(args, "city", DEFAULT_CITY), textArg(args, "uf", DEFAULT_UF), reference);
        } catch (RuntimeException e) {
            fail(e.getMessage());
            return;
        }
        Snapshot snapshot = saved.snapshot();
        System.out.println();
        System.out.println("--- DADOS SALVOS ---");
        System.out.printf(Locale.ROOT, "Municipio    : %s / %s%n", snapshot.municipality().nome(), snapshot.municipality().uf());
        System.out.printf(Locale.ROOT, "Ate o dia    : %s (horario de Brasilia)%n", snapshot.referenceDate());
        System.out.printf(Locale.ROOT, "Satelite     : %s%n", snapshot.satellite());
        System.out.printf(Locale.ROOT, "Focos        : %d (%d km, %d dias)%n", snapshot.fires().size(),
                snapshot.radiusKm(), snapshot.days());
        System.out.printf(Locale.ROOT, "Dias de clima: %d%n", snapshot.climate().size());
        System.out.printf(Locale.ROOT, "Arquivo      : %s%n", saved.file().toAbsolutePath());
        context.close();
    }

    private void print(Analysis analysis) {
        RiskSummary summary = analysis.summary();
        System.out.println();
        System.out.println("--- RESULTADO ---");
        System.out.printf(Locale.ROOT, "Municipio    : %s / %s%n", analysis.municipality().nome(), analysis.municipality().uf());
        System.out.printf(Locale.ROOT, "Coordenada   : %s, %s%n", analysis.municipality().lat(), analysis.municipality().lon());
        System.out.printf(Locale.ROOT, "Bioma        : %s%n", summary.biome());
        System.out.printf(Locale.ROOT, "Satelite     : %s%n", analysis.satellite());
        if (analysis.dataSource().referenceDate() != null) {
            System.out.printf(Locale.ROOT, "ATENCAO      : APIs indisponiveis, dados salvos ate %s%n",
                    analysis.dataSource().referenceDate());
        }
        System.out.printf(Locale.ROOT, "Focos no raio: %d (%d km, %d dias)%n", summary.totalFires(),
                analysis.request().radiusKm(), analysis.request().days());
        if (summary.lowConfidenceFires() > 0) {
            System.out.printf(Locale.ROOT, "Fora do indice: %d de baixa confianca%n", summary.lowConfidenceFires());
        }
        System.out.printf(Locale.ROOT, "No municipio : %d%n", summary.firesInMunicipality());
        System.out.printf(Locale.ROOT, "Temp media   : %s C%n", summary.avgTemp());
        System.out.printf(Locale.ROOT, "Chuva media  : %s mm%n", summary.avgRain());
        System.out.printf(Locale.ROOT, "Dias s/ chuva: %d%n", summary.dryDays());
        System.out.printf(Locale.ROOT, "Indice risco : %s / 10 (%s)%n", summary.riskIndex(), summary.level().getLabel());
    }

    private void fail(String message) {
        System.err.println("Erro: " + message);
        System.exit(SpringApplication.exit(context, () -> 1));
    }

    private String textArg(ApplicationArguments args, String name, String fallback) {
        List<String> values = args.getOptionValues(name);
        return values == null || values.isEmpty() ? fallback : values.get(0);
    }

    private int intArg(ApplicationArguments args, String name, int fallback) {
        String value = textArg(args, name, String.valueOf(fallback));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new InvalidRequestException("Valor inválido para --" + name + ": " + value);
        }
    }
}
