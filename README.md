# MarketLens

AI-assisted stock analysis backend. Give it a ticker; it pulls fundamentals, the two most recent earnings reports and the broader market context, then returns a structured **BUY / HOLD / SELL** view with the reasoning behind it.

> ⚠️ **Disclaimer:** MarketLens is a portfolio/educational project. Output is **not financial advice**. LLMs can be wrong; never trade on this alone.

---

## Table of Contents

- [What it does](#what-it-does)
- [How it works](#how-it-works)
- [Tech stack](#tech-stack)
- [Data sources](#data-sources)
- [Getting started](#getting-started)
- [Configuration](#configuration)
- [API](#api)
- [Project structure](#project-structure)
- [Design decisions](#design-decisions)
- [Testing](#testing)
- [Roadmap](#roadmap)
- [License](#license)

---

## What it does

For a given ticker (e.g. `AAPL`), MarketLens:

1. **Collects company fundamentals**
   - Earnings (EPS, revenue, net income)
   - Debt (total debt, debt-to-equity, net debt)
   - Free cash flow
   - Valuation (P/E ratio, and optionally P/S, P/B, EV/EBITDA)
2. **Fetches the last two earnings reports** (quarter-over-quarter comparison, beats/misses vs. estimates, notable changes)
3. **Adds market context** (major indices such as S&P 500, Nasdaq, Dow; trend and volatility)
4. **Summarizes everything with an LLM** and returns:
   - a recommendation: `BUY`, `HOLD` or `SELL`
   - a confidence level
   - bullish and bearish factors
   - key risks
   - a plain-language summary

## How it works

```mermaid
flowchart LR
    Client[Client / curl / UI] -->|GET /api/v1/analysis/AAPL| API[Spring Boot REST API]
    API --> Orchestrator[Analysis Service]
    Orchestrator --> Cache[(Redis cache)]
    Orchestrator --> MCP[MCP tools: market data]
    MCP --> Ext[(External market data APIs)]
    Orchestrator --> Metrics[Metrics Engine<br/>deterministic Java]
    Metrics --> Rules[Rule-based scoring]
    Orchestrator --> LLM[Spring AI ChatClient]
    Rules --> LLM
    LLM --> Result[Structured Recommendation JSON]
    Result --> Client
```

**Key principle: the LLM never does the math.** Ratios, growth rates and scores are computed in plain Java and verified by unit tests. The LLM receives those finished numbers and is only responsible for interpretation and explanation. This keeps results reproducible and prevents hallucinated figures.

### Request flow

1. Client calls `GET /api/v1/analysis/{ticker}`.
2. Service checks Redis for a fresh cached result.
3. On a miss, market-data tools fetch fundamentals, the last two earnings reports and index data (in parallel).
4. The metrics engine calculates ratios and a rule-based score.
5. The prompt is assembled from the computed data and sent through Spring AI.
6. The model's response is mapped to a typed Java record (structured output), validated, cached and returned.

## Tech stack

| Layer | Technology |
|---|---|
| Language | Java 21+ |
| Framework | Spring Boot |
| AI | Spring AI (`ChatClient`, structured output, tool calling) |
| Tooling protocol | Model Context Protocol (MCP) via Spring AI MCP |
| Cache | Redis |
| Resilience | Resilience4j (retry, circuit breaker, rate limiter) |
| API docs | springdoc-openapi (Swagger UI) |
| Build | Maven |
| Testing | JUnit 5, Mockito, Testcontainers, WireMock |
| Containers | Docker or Podman (OCI-compliant, works with both) |

## Data sources

The market data provider is hidden behind an interface, so providers can be swapped without touching business logic.

| Data | Candidate providers |
|---|---|
| Fundamentals, ratios, earnings | **Alpha Vantage** (chosen provider); Financial Modeling Prep, Finnhub as alternatives |
| Earnings filings (10-Q / 10-K) | SEC EDGAR (free, official) |
| Index data (S&P 500, Nasdaq, Dow) | **Alpha Vantage** via ETF proxies (`SPY`, `QQQ`, `DIA`); Finnhub, Twelve Data as alternatives |

Most providers have a free tier with tight rate limits. That is exactly why the Redis cache and rate limiter exist. Check each provider's terms before deploying anything publicly.

## Getting started

> ℹ️ The container setup below works today (app + Redis, health check at `/actuator/health`).
> The `/api/v1/...` endpoints and Swagger UI arrive in later phases — see [`context/ROADMAP.md`](context/ROADMAP.md).

### Prerequisites

- Docker **or** Podman (with `compose` support)
- An API key for your chosen market data provider
- An API key for your LLM provider (or a local model via Ollama)
- Optional for local development: JDK 21+ and Maven

### Run with Docker

```bash
git clone https://github.com/StoyanMihaylov99/market-lens.git
cd market-lens

cp .env.example .env
# edit .env and add your API keys

docker compose up --build
```

### Run with Podman

```bash
podman compose up --build
# or: podman-compose up --build
```

The API is then available at `http://localhost:8080` and Swagger UI at `http://localhost:8080/swagger-ui.html`.

### Run locally (without containers for the app)

```bash
docker compose up -d redis
./mvnw spring-boot:run
```

### Run with mock data (no API key needed)

Alpha Vantage's free tier allows only 25 requests per day, so for demos and day-to-day work the app can talk to a
[WireMock](https://wiremock.org) container that replays real recorded responses instead:

```bash
docker compose -f compose.yaml -f compose.mock.yaml up --build
# or: podman compose -f compose.yaml -f compose.mock.yaml up --build

curl http://localhost:8080/api/v1/companies/AAPL/fundamentals    # also: PLUG
```

`compose.mock.yaml` adds the WireMock service and switches the app to the `mock` Spring profile
(`application-mock.yaml`), which points the provider at WireMock with a dummy key — your real key is never used.
Demo data: `AAPL`, `PLUG` (fundamentals, earnings) and `SPY` (index); any other ticker behaves like an unknown one.
Recorded data lives in `src/test/resources/fixtures/alphavantage/`, stub mappings in `wiremock/mappings/`.

> With a real key, one fundamentals call costs 5 Alpha Vantage requests and takes ~50 s, because the client spaces
> requests 12 s apart to respect the free tier's burst limit (caching arrives in Phase 7).

### Quick test

```bash
curl http://localhost:8080/api/v1/analysis/AAPL
```

## Configuration

| Variable | Description | Example |
|---|---|---|
| `MARKET_DATA_API_KEY` | Market data provider key | `abc123` |
| `MARKET_DATA_PROVIDER` | Provider implementation to use | `alphavantage` |
| `LLM_API_KEY` | LLM provider key | `sk-...` |
| `LLM_MODEL` | Model name | `your-model-id` |
| `REDIS_HOST` / `REDIS_PORT` | Redis connection | `redis` / `6379` |
| `CACHE_TTL_MINUTES` | Cache lifetime for analysis results | `60` |

Scoring thresholds and rule weights are not secrets and have no env vars; they live under `marketlens.scoring` in
`src/main/resources/scoring.yaml`, imported by `application.yaml` (fractions as decimals: `0.05` = 5%). Any Spring property can still be overridden from the
environment, e.g. `MARKETLENS_SCORING_RECOMMENDATION_BUY=70`.

## API

### `GET /api/v1/analysis/{ticker}`

Example response:

```json
{
  "ticker": "AAPL",
  "recommendation": "HOLD",
  "confidence": "MEDIUM",
  "score": 58,
  "metrics": {
    "peRatio": 29.4,
    "debtToEquity": 1.8,
    "freeCashFlow": 98500000000,
    "revenueGrowthQoQ": 0.03,
    "epsSurprise": 0.04
  },
  "bullishFactors": ["Consistent free cash flow", "Earnings beat estimates"],
  "bearishFactors": ["Valuation above sector average"],
  "risks": ["Slowing revenue growth", "Elevated market volatility"],
  "marketContext": "Major indices are in an uptrend with moderate volatility.",
  "summary": "…",
  "generatedAt": "2026-11-12T21:00:00Z",
  "disclaimer": "Not financial advice."
}
```

### Other endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/market/overview` | Index snapshot and market context |
| `GET` | `/api/v1/companies/{ticker}/fundamentals` | Computed metrics + rule-based score, no LLM (see below) |
| `GET` | `/actuator/health` | Health check |

### `GET /api/v1/companies/{ticker}/fundamentals`

Deterministic part of the analysis: metrics computed in Java from the latest statements (TTM = last four quarters)
and a 0–100 score from configurable rules. Ratios and fractions have 4 decimal places; fractions are decimals
(`0.2760` = 27.60%). A metric whose inputs are missing or meaningless (e.g. P/E with losses) is `null`.
A rule without inputs is skipped: it lowers `coverage` and `confidence`, not the score. `LOW` confidence always gives `HOLD`.

```json
{
  "ticker": "AAPL",
  "metrics": { "priceToEarnings": 38.6537, "debtToEquity": 0.7147, "freeCashFlowYield": 0.0266, "netMargin": 0.2760, "…": "…" },
  "score": { "value": 53, "recommendation": "HOLD", "confidence": "HIGH", "coverage": 1.0000 },
  "rules": [
    { "rule": "valuation", "applied": true, "points": 0, "maxPoints": 10, "rationale": "P/E 38.6537 is above 35" },
    { "rule": "leverage", "applied": true, "points": 7, "maxPoints": 10, "rationale": "Debt-to-equity 0.7147 is at or below 1" }
  ],
  "dataAsOf": "2026-11-20T12:00:00Z",
  "disclaimer": "For information only. … Not financial advice."
}
```

Errors are RFC 9457 `application/problem+json`: `400` invalid ticker, `404` unknown ticker,
`503` market data unavailable or rate limited (with `Retry-After` when known).

## Project structure

```
market-lens/
├── src/main/java/com/service/market_lens/
│   ├── api/            # REST controllers, DTOs, exception handling
│   ├── analysis/       # Orchestration, scoring, prompt building
│   ├── marketdata/     # Provider interface + implementations
│   ├── mcp/            # MCP server / tool definitions
│   ├── ai/             # Spring AI configuration, structured output models
│   ├── cache/          # Redis configuration
│   └── config/         # Properties, resilience config
├── src/test/
├── Dockerfile
├── compose.yaml
├── .env.example
└── README.md
```

## Design decisions

- **Deterministic numbers, LLM narrative.** Metrics and the base score are computed in code; the model explains them. Easier to test, no invented figures.
- **Structured output.** The LLM must return JSON that maps to a Java record. Invalid output is rejected and retried, not passed through.
- **Provider abstraction.** `MarketDataProvider` interface keeps vendors replaceable.
- **MCP for tools.** Market data capabilities are exposed as MCP tools so the model can request what it needs and other MCP clients can reuse them.
- **Cache first.** Fundamentals change quarterly, so aggressive caching is both cheaper and kinder to free-tier limits.
- **Multi-stage Docker build, non-root user.** Small image, no secrets baked in, OCI-compatible for Podman rootless.
- **Honest output.** Every response carries a disclaimer, a confidence level and the data timestamp.

## Testing

```bash
./mvnw test      # unit tests
./mvnw verify    # + integration tests (Testcontainers needs Docker/Podman socket)
```

- Metrics engine and scoring: pure unit tests, plus golden tests on recorded `AAPL`/`PLUG` data
- Package boundaries: ArchUnit (`ArchitectureTest`)
- Provider clients: in-process WireMock (runs inside the test JVM, no Docker), no real API calls in CI
- LLM layer: mocked `ChatClient`; prompt and output-mapping tests
- Redis: Testcontainers (`*IT`, needs a Docker/Podman socket)
- Demo / manual runs without API keys: see [Run with mock data](#run-with-mock-data-no-api-key-needed)

## License

MIT. See [LICENSE](LICENSE).