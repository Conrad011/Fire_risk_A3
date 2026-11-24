import pandas as pd

class RiskProcessor:
    _DRY_DAY_MM = 1.0  # abaixo disso o dia conta como sem chuva

    @staticmethod
    def process(climate_df, firms_df, biome="Desconhecido"):
        firms_df = firms_df.rename(columns={"acq_date": "date"})
        climate_df["date"] = pd.to_datetime(climate_df["date"])
        firms_df["date"] = pd.to_datetime(firms_df["date"])

        fires_per_day = firms_df.groupby("date").size().reset_index(name="total_fires")
        df = climate_df.merge(fires_per_day, on="date", how="left")
        df["total_fires"] = df["total_fires"].fillna(0)
        df["had_fire"] = (df["total_fires"] > 0).astype(int)

        biome_weight = {"Amazônia": 1.5, "Cerrado": 1.2, "Caatinga": 1.3}# índice de risco
        weight = biome_weight.get(biome, 1.0)

        fire_score = min(len(firms_df) / 100, 1.0)
        climate_score = min(df["temperature_max"].mean() / 40, 1.0) * max(1 - df["precipitation"].mean() / 50, 0)

        risk_index = round((fire_score * 0.6 + climate_score * 0.4) * weight * 10, 2)

        summary = {
            "biome": biome,
            "total_fires": len(firms_df),
            "avg_temp": round(df["temperature_max"].mean(), 1),
            "avg_rain": round(df["precipitation"].mean(), 1),
            "risk_index": min(risk_index, 10.0),
            "fire_score": round(fire_score, 3),
            "climate_score": round(climate_score, 3),
            "biome_weight": weight,
            "dry_days": RiskProcessor._dry_streak(df["precipitation"]),
            "max_wind": RiskProcessor._stat(df, "windspeed", "max"),
            "min_humidity": RiskProcessor._stat(df, "humidity_min", "min"),
        }

        return df, summary

    @staticmethod
    def _dry_streak(precipitation):
        # dias consecutivos sem chuva contando de hoje para tras
        streak = 0
        for value in reversed(precipitation.fillna(0).tolist()):
            if value >= RiskProcessor._DRY_DAY_MM:
                break
            streak += 1
        return streak

    @staticmethod
    def _stat(df, column, how):
        if column not in df.columns or df[column].dropna().empty:
            return None
        return round(float(getattr(df[column], how)()), 1)
