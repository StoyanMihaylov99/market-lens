# Alpha Vantage fixtures

Real responses recorded from `https://www.alphavantage.co/query` on **2026-11-20** with a free-tier key
(market data as of 2026-11-19). Used by in-process WireMock tests and, later, by the `mock` compose profile.
The API key is never stored here.

File name = `<function>-<SYMBOL>.json` (lower-case function name).

| Files | Why |
|---|---|
| `*-AAPL.json` | happy path: large profitable company |
| `*-PLUG.json` | edge case: negative gross profit, negative operating cash flow, negative EPS and surprises |
| `global_quote-UNKNOWNXYZ.json` → `{"Global Quote": {}}` | unknown ticker, shape 1 (HTTP 200) |
| `income_statement-UNKNOWNXYZ.json` → `{}` | unknown ticker, shape 2 (HTTP 200) |
| `time_series_daily-UNKNOWNXYZ.json` → `{"Error Message": ...}` | unknown ticker, shape 3 (HTTP 200) |
| `symbol_search-AAPL.json` | profile source (`OVERVIEW` is premium on the free tier); 4 matches, pick the exact symbol |
| `time_series_daily-SPY.json`, `sma200-SPY.json` | index via ETF proxy: 100 daily bars + 200-day SMA |
| `information-demo-key.json` | `{"Information": ...}` body shape (recorded with the public `demo` key) |

## Trimmed

To keep the repo small, older entries were removed; values were not edited. Vendor lists are newest first.

- statements and earnings: latest **8 quarters** and **3 years**
- `sma200-SPY.json`: latest **10** points
- `time_series_daily-SPY.json`: untouched (100 days; the 50-day average needs them)

Cases not present in the recordings (e.g. `estimatedEPS: "None"`) are covered with small inline JSON in tests.

## Re-recording

Costs about 1 request per file against the 25/day free limit. Space calls ~13 s apart, stop on any
`Note`/`Information` body, and never write the key into a file.
