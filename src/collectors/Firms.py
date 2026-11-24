import math
import os
import requests
import numpy as np
import pandas as pd
from io import StringIO
from pathlib import Path
from dotenv import load_dotenv


class Firms:

    _KM_PER_DEGREE = 111.32
    _MAX_DATA_AGE_DAYS = 2
    _SOURCES = ["VIIRS_NOAA20_NRT", "VIIRS_NOAA21_NRT", "VIIRS_SNPP_NRT"]
    _SOURCE_NAMES = {
        "VIIRS_NOAA20_NRT": "VIIRS NOAA-20",
        "VIIRS_NOAA21_NRT": "VIIRS NOAA-21",
        "VIIRS_SNPP_NRT": "VIIRS S-NPP",
    }

    def __init__(self):
        load_dotenv(Path(__file__).resolve().parents[2] / ".env")
        self._api_key = os.getenv("NASA_FIRMS_KEY")
        if not self._api_key:
            raise EnvironmentError("Variável de ambiente NASA_FIRMS_KEY não definida.")

    def _bbox(self, lat, lon, radius_km):
        d_lat = radius_km / self._KM_PER_DEGREE
        d_lon = radius_km / (self._KM_PER_DEGREE * math.cos(math.radians(lat)))
        west, east = max(lon - d_lon, -180), min(lon + d_lon, 180)
        south, north = max(lat - d_lat, -90), min(lat + d_lat, 90)
        return f"{west:.4f},{south:.4f},{east:.4f},{north:.4f}"

    def _active_source(self):
        # um satelite pode parar de publicar e a API segue respondendo 200 com zero linhas,
        # o que pareceria "nenhum foco"; por isso so usa fonte com dado recente
        url = f"https://firms.modaps.eosdis.nasa.gov/api/data_availability/csv/{self._api_key}/all"
        try:
            response = requests.get(url, timeout=30)
            response.raise_for_status()
            available = pd.read_csv(StringIO(response.text)).set_index("data_id")["max_date"]
        except (requests.RequestException, ValueError, KeyError, pd.errors.ParserError):
            return self._SOURCES[0]

        limit = pd.Timestamp.now("UTC").tz_localize(None).normalize() - pd.Timedelta(days=self._MAX_DATA_AGE_DAYS)
        for source in self._SOURCES:
            if source in available.index and pd.to_datetime(available[source]) >= limit:
                return source
        raise Exception("Nenhum satélite VIIRS com dados recentes na API FIRMS")

    def get_fires(self, lat, lon, days=5, radius_km=100):
        source = self._active_source()
        url = (
            f"https://firms.modaps.eosdis.nasa.gov/api/area/csv/"
            f"{self._api_key}/{source}/"
            f"{self._bbox(lat, lon, radius_km)}/{days}"
        )
        response = requests.get(url, timeout=60)
        if response.status_code != 200:
            raise Exception(f"Erro da API FIRMS: {response.status_code} - {response.text[:300]}")
        if not response.text.strip():
            df = pd.DataFrame(columns=["latitude", "longitude", "acq_date", "frp", "confidence"])
        else:
            df = pd.read_csv(StringIO(response.text))
        if "latitude" not in df.columns:
            # a FIRMS responde 200 com texto de erro (ex.: chave invalida) em vez de CSV
            raise Exception(f"Resposta inesperada da API FIRMS: {response.text[:300]}")

        # a API so aceita retangulo; o corte pela distancia transforma a busca em um circulo
        df["distance_km"] = self._distance_km(lat, lon, df["latitude"], df["longitude"])
        df = df[df["distance_km"] <= radius_km].reset_index(drop=True)
        df.attrs["source"] = self._SOURCE_NAMES[source]
        return df

    @staticmethod
    def _distance_km(lat, lon, lats, lons):
        lat1, lon1 = np.radians(lat), np.radians(lon)
        lat2, lon2 = np.radians(lats.astype(float)), np.radians(lons.astype(float))
        a = np.sin((lat2 - lat1) / 2) ** 2 + np.cos(lat1) * np.cos(lat2) * np.sin((lon2 - lon1) / 2) ** 2
        return (2 * 6371.0 * np.arcsin(np.sqrt(a))).round(1)
