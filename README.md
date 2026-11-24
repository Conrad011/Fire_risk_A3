# FireRisk

Wildfire risk monitor for any Brazilian municipality. Combines NASA satellite fire detections, daily weather data, and IBGE municipal boundaries to compute a risk index and display it on an interactive dashboard. The default analysis is Sete Lagoas, MG.

**Quick start on Windows:** create the `.env` file (see [Configuration](#configuration)) and double-click `iniciar.cmd`. It builds the project if needed, starts the server, and opens `http://localhost:8080`.

![Java](https://img.shields.io/badge/java-17%2B-orange) ![Spring Boot](https://img.shields.io/badge/spring--boot-3.3-brightgreen) ![License](https://img.shields.io/badge/license-MIT-green)

---

## How it works

1. **Collectors** pull data from three public APIs:
   - `Climate` — 30-day weather history from [Open-Meteo](https://open-meteo.com/) (max temperature, precipitation, wind speed, minimum humidity)
   - `Firms` — active fire hotspots from [NASA FIRMS](https://firms.modaps.eosdis.nasa.gov/), inside a radius around the city. Uses the first VIIRS satellite with recent data (NOAA-20, NOAA-21, then S-NPP), because S-NPP stopped publishing on 2026-11-02 and the API still answers with an empty result for it
   - `Vegetation` — municipality polygons and names from [IBGE](https://servicodados.ibge.gov.br/)
   - `Geocoder` — urban center of the city from the Open-Meteo geocoding API (no key), accepted only inside the IBGE polygon

2. **RiskProcessor** merges the datasets and computes a 0–10 risk index:

   ```
   fire_score    = clamp(hotspot_count / 100, 0, 1)
   climate_score = clamp(avg_max_temp / 40, 0, 1) × clamp(1 − avg_rain / 50, 0, 1)
   risk_index    = (fire_score × 0.6 + climate_score × 0.4) × biome_weight × 10
   ```

   Biome multipliers: Amazônia ×1.5 · Cerrado ×1.2 · Caatinga ×1.3 · others ×1.0. Risk bands: Low < 3 · Moderate 3–6 · High ≥ 6.

   `hotspot_count` only includes detections with nominal or high confidence. Low-confidence VIIRS detections are more likely to be false alarms (sun glint, industrial sites, sensor noise); they still appear on the map and in the table, marked as outside the index.

3. **Dashboard** — a Spring Boot app serves a REST API plus a single-page dashboard with city search, an interactive heatmap, risk gauge, climate series, and CSV export. The interface follows Nielsen's 10 usability heuristics.

---

## Project structure

```
FireRisk/
├── pom.xml
├── iniciar.cmd                # one-click start on Windows
├── mvnw / mvnw.cmd            # Maven wrapper (no local Maven install needed)
├── .env                       # secrets (see Configuration)
├── data/
│   ├── raw/                   # municipios.geojson (git-ignored)
│   └── processed/
└── src/
    ├── main/java/com/firerisk/
    │   ├── FireRiskApplication.java   # entry point (web + --cli)
    │   ├── cli/                       # terminal mode
    │   ├── collectors/                # Climate, Firms, Geocoder, Vegetation
    │   ├── processors/                # RiskProcessor (index formula)
    │   ├── service/                   # orchestration + cache
    │   ├── web/                       # REST controller, DTOs, error handling
    │   ├── model/                     # domain records and enums
    │   ├── exception/                 # domain errors
    │   ├── config/                    # .env loading, cache policy
    │   └── util/                      # HTTP, geographic math, name matching
    ├── main/resources/
    │   ├── application.yml
    │   └── static/                    # index.html, styles.css, js/ modules
    └── test/java/com/firerisk/        # unit and web tests
```

The Python files (`main.py`, `dashboard/`, `src/collectors/*.py`, `src/processors/*.py`) are the original prototype. The Java application is the version to run and present.

---

## Requirements

- Java 17 or newer (`java -version`)
- A free [NASA FIRMS API key](https://firms.modaps.eosdis.nasa.gov/api/area/)

Maven is **not** required — the bundled wrapper (`mvnw` / `mvnw.cmd`) downloads it on first use.

---

## Installation

```bash
git clone https://github.com/your-username/FireRisk.git
cd FireRisk
./mvnw clean package          # Windows: mvnw.cmd clean package
```

This produces `target/firerisk-1.0.0.jar`.

---

## Configuration

Create a `.env` file in the project root:

```env
NASA_FIRMS_KEY=your_api_key_here
```

The app reads `.env` on startup (environment variables take precedence). You can also export the variable directly:

```bash
export NASA_FIRMS_KEY=your_api_key_here      # Windows: $env:NASA_FIRMS_KEY="..."
```

The geographic data file (`municipios.geojson`) is downloaded automatically on first run via the IBGE API and saved to `data/raw/`. To change its location, set `firerisk.geojson-path` in `src/main/resources/application.yml` or pass `--firerisk.geojson-path=...` on the command line.

---

## Usage

### CLI

Runs a single analysis for the configured coordinates and prints the result:

```bash
java -jar target/firerisk-1.0.0.jar --cli
```

Defaults: Sete Lagoas, MG, 5 days, 100 km. Override them with arguments:

```bash
java -jar target/firerisk-1.0.0.jar --cli --city="Belo Horizonte" --uf=MG --days=3 --radius=150
```

**Example output:**

```
Analisando Sete Lagoas / MG...

--- RESULTADO ---
Municipio    : Sete Lagoas / MG
Coordenada   : -19.4658, -44.2467
Bioma        : Cerrado
Satelite     : VIIRS NOAA-20
Focos no raio: 1 (100 km, 5 dias)
No municipio : 0
Temp media   : 27.2 C
Chuva media  : 7.4 mm
Dias s/ chuva: 0
Indice risco : 2.85 / 10 (Baixo)
```

> On Windows consoles that are not set to UTF-8, accented output may render incorrectly. Run `chcp 65001` first, or add `-Dstdout.encoding=UTF-8` before `-jar`.

### Dashboard

```bash
java -jar target/firerisk-1.0.0.jar
```

Opens at `http://localhost:8080`. Pick the state, type the city, set the period (1–5 days) and search distance (25–500 km), then click **Analisar**. To use another port, set the `PORT` environment variable.

During development you can skip the packaging step:

```bash
./mvnw spring-boot:run
```

Dashboard features:
- City search with autocomplete, typo suggestions, recent cities, and shareable links
- Interactive fire heatmap, individual hotspots, municipality polygon, and search radius
- Risk gauge (0–10) with a plain-language summary
- 30-day temperature and precipitation chart
- Daily fire count chart and hotspot table with CSV export
- Index breakdown, help, and glossary

### Saved data (fallback)

If weather or hotspots cannot be fetched, the dashboard uses the saved data for that city and shows a warning with the date it covers. Saved data lives in `data/snapshot/` (one file per city plus the IBGE name list) and always covers the largest window (5 days, 500 km), so any smaller filter can be cut from it. Cities without a saved file show the usual error.

Save or refresh it for a closed day (default: yesterday, Brasília time):

```bash
java -jar target/firerisk-1.0.0.jar --cli --snapshot --city="Sete Lagoas" --uf=MG
java -jar target/firerisk-1.0.0.jar --cli --snapshot --city="Sete Lagoas" --uf=MG --date=2026-11-23
```

### Time zone

NASA FIRMS reports dates and times in UTC. Each hotspot is converted to Brasília time before being grouped by day, so a detection at 01:30 UTC on the 21st counts as 22:30 on the 20th. The query asks FIRMS for one extra UTC day so that the evening of the last local day is not lost. Every date and time on screen is in Brasília time, regardless of the viewer's computer time zone.

### REST API

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/states` | The 27 states and their predominant biome |
| `GET` | `/api/municipalities?uf=MG` | Municipality names in a state |
| `GET` | `/api/risk?city=Sete Lagoas&uf=MG&days=5&radius=100` | Full analysis |
| `POST` | `/api/refresh` | Discards cached weather and hotspots |

Errors come back as `{"error": "...", "hint": "...", "suggestions": [...]}` with `400` (invalid filters), `404` (city not found), `503` (missing or rejected API key), or `502` (external API failure). Messages never include upstream URLs, because the FIRMS URL carries the API key.

### Python / Streamlit dashboard

The original Python version lives alongside the Java one (`main.py`, `dashboard/app.py`, `src/collectors/*.py`, `src/processors/RiskProcessor.py`). It reads the same `.env` file.

```bash
python -m venv venv
venv\Scripts\activate            # Linux/macOS: source venv/bin/activate
pip install -r requirements.txt

python main.py                                   # terminal mode
python -m streamlit run dashboard/app.py         # dashboard at http://localhost:8501
```

The location is chosen by typing the city name and picking the state (default: Sete Lagoas, MG); the name match ignores accents and case. Latitude and longitude come from the Open-Meteo geocoding API (no key), accepted only when the point falls inside the IBGE polygon of that municipality; if the geocoder is unavailable, an interior point of the polygon is used instead. Unlike the Java version, hotspots are searched in a circle around the chosen point (25–500 km, default 100 km) instead of a fixed Amazon bounding box, so `hotspot_count` in the formula is the number of detections inside that radius. The Python version picks the first VIIRS satellite with recent data (NOAA-20, NOAA-21, then S-NPP), because S-NPP stopped publishing on 2026-11-02 and the API still answers with an empty result for it. The dashboard also shows the hotspots inside the municipality polygon, the distance to the nearest one, the current dry-day streak, and a breakdown of how the index was computed.

---

## Tests

```bash
./mvnw test
```

25 tests cover the risk-index formula, FIRMS response parsing, city lookup and suggestions, input validation, and API error handling.

---

## Implementation notes

| Python | Java |
|--------|------|
| `pandas.DataFrame` | `record` types (`ClimateRecord`, `FireRecord`, `DailyRisk`) + streams |
| `shapely` / `geopandas` | [JTS](https://github.com/locationtech/jts) (`jts-core`, `jts-io-common`) |
| `requests` | `java.net.http.HttpClient` (`util/Http.java`) |
| `pandas.read_csv` | Apache Commons CSV |
| `python-dotenv` | `config/DotEnv.java` |
| `streamlit` + `folium` + `plotly` | Spring Boot + Leaflet.js + Plotly.js |
| `@st.cache_data` | Spring `@Cacheable` + Caffeine (`service/DataCache.java`, `config/CacheConfig.java`) |

Cache lifetimes: hotspots 15 minutes, weather 1 hour, city location 1 day, municipality boundaries until restart.

---

## Data sources

| Source | Data | License |
|--------|------|---------|
| [NASA FIRMS](https://firms.modaps.eosdis.nasa.gov/) | VIIRS NRT active fire detections | NASA Open Data |
| [Open-Meteo](https://open-meteo.com/) | Daily weather forecast & history | CC BY 4.0 |
| [IBGE](https://servicodados.ibge.gov.br/) | Municipality boundaries & metadata | Open Government Data |
| [Esri](https://www.arcgis.com/home/item.html?id=358ec1e175ea41c3bf5c68f0da11ae2b) | Dark Gray Canvas basemap tiles | Esri Terms of Use |

> The original Python dashboard used CartoDB `dark_matter` tiles through Folium. CARTO now requires an API key for those, so the dashboard uses Esri's Dark Gray Canvas (no key).

---

## Roadmap

- [ ] Random Forest classifier trained on historical fire/climate data
- [ ] Alert system for high-risk events
- [ ] Support for additional biomes and regions

---

## License

MIT
