package com.firerisk;

import com.firerisk.config.DotEnv;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.util.Arrays;

/**
 * Ponto de entrada da aplicacao.
 *
 * <p>Sem argumentos sobe o dashboard web em {@code http://localhost:8080}.
 * Com {@code --cli} executa uma analise unica no terminal.</p>
 */
@SpringBootApplication
public class FireRiskApplication {

    public static void main(String[] args) {
        DotEnv.load();

        boolean cli = Arrays.asList(args).contains("--cli");
        if (cli) {
            System.setProperty("firerisk.cli", "true");
        }

        new SpringApplicationBuilder(FireRiskApplication.class)
                .web(cli ? WebApplicationType.NONE : WebApplicationType.SERVLET)
                .bannerMode(cli ? Banner.Mode.OFF : Banner.Mode.CONSOLE)
                .run(args);
    }
}
