package com.service.market_lens.analysis.scoring;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring wiring only; the scoring logic itself has no Spring dependencies. */
@Configuration(proxyBeanMethods = false)
class ScoringConfiguration {

    @Bean
    ScoringEngine scoringEngine(ScoringProperties properties) {
        return ScoringEngine.of(properties);
    }
}
