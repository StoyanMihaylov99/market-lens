package com.service.market_lens.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Type-safe view of the {@code marketlens.*} settings in {@code application.yaml}.
 * <p>
 * Validated at startup: an invalid value stops the app with a clear error instead of failing later at runtime.
 * API keys are optional at startup so the app (and CI) can run without secrets; the components that need a key
 * check it when they are used.
 */
@Validated
@ConfigurationProperties(prefix = "marketlens")
public record MarketLensProperties(
        @Valid @NotNull MarketData marketData,
        @Valid @NotNull Llm llm,
        @Valid @NotNull Cache cache) {

    public record MarketData(@NotBlank String provider, String apiKey) {
        @Override
        public String toString() {
            return "MarketData[provider=" + provider + ", apiKey=" + mask(apiKey) + "]";
        }
    }

    public record Llm(@NotBlank String model, String apiKey) {
        @Override
        public String toString() {
            return "Llm[model=" + model + ", apiKey=" + mask(apiKey) + "]";
        }
    }

    public record Cache(@NotNull Duration analysisTtl) {
        public Cache {
            if (analysisTtl != null && (analysisTtl.isZero() || analysisTtl.isNegative())) {
                throw new IllegalArgumentException("marketlens.cache.analysis-ttl must be positive");
            }
        }
    }

    /** Records print all fields in toString(); never let a secret end up in logs. */
    private static String mask(String secret) {
        return (secret == null || secret.isBlank()) ? "<not set>" : "****";
    }
}
