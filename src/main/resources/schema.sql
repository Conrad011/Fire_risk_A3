-- Banco SQLite do FireRisk: historico das analises de risco.
-- Executado a cada inicializacao; IF NOT EXISTS preserva os dados ja gravados.

-- Municipios ja analisados (codigo e nome oficiais do IBGE)
CREATE TABLE IF NOT EXISTS municipality (
    codarea   TEXT PRIMARY KEY,
    nome      TEXT NOT NULL,
    uf        TEXT NOT NULL,
    latitude  REAL NOT NULL,
    longitude REAL NOT NULL
);

-- Uma linha por analise. Datas em ISO-8601, no horario de Brasilia.
CREATE TABLE IF NOT EXISTS risk_analysis (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    codarea               TEXT    NOT NULL REFERENCES municipality (codarea),
    analyzed_at           TEXT    NOT NULL,
    reference_date        TEXT    NOT NULL,
    days                  INTEGER NOT NULL CHECK (days BETWEEN 1 AND 5),
    radius_km             INTEGER NOT NULL CHECK (radius_km BETWEEN 25 AND 500),
    risk_index            REAL    NOT NULL CHECK (risk_index BETWEEN 0 AND 10),
    risk_level            TEXT    NOT NULL CHECK (risk_level IN ('LOW', 'MODERATE', 'HIGH')),
    fires_counted         INTEGER NOT NULL,
    fires_low_confidence  INTEGER NOT NULL,
    fires_in_municipality INTEGER NOT NULL,
    avg_temp              REAL,
    avg_rain              REAL,
    dry_days              INTEGER NOT NULL,
    satellite             TEXT,
    data_source           TEXT    NOT NULL CHECK (data_source IN ('live', 'snapshot', 'backfill'))
);

CREATE INDEX IF NOT EXISTS idx_risk_analysis_city ON risk_analysis (codarea, reference_date);
