import requests
import pandas as pd
class Climate:
    def get_data(self, lat, lon):
        # forecast_days=1 limita a serie ate hoje; sem isso a API devolve 7 dias de previsao junto do historico
        url = (
            f"https://api.open-meteo.com/v1/forecast"
            f"?latitude={lat}&longitude={lon}"
            f"&daily=temperature_2m_max,precipitation_sum,windspeed_10m_max,relative_humidity_2m_min"
            f"&timezone=America/Sao_Paulo&past_days=30&forecast_days=1"
        )
        response = requests.get(url, timeout=30)
        response.raise_for_status()
        daily = response.json()["daily"]
        return self._to_dataframe(daily)

    def _to_dataframe(self, daily):
        df = pd.DataFrame({
            "date":            daily["time"],
            "temperature_max": daily["temperature_2m_max"],
            "precipitation":   daily["precipitation_sum"],
            "windspeed":       daily["windspeed_10m_max"],
            "humidity_min":    daily["relative_humidity_2m_min"],
        })

        df["date"] = pd.to_datetime(df["date"])
        return df
