import difflib
import unicodedata
import requests
from pathlib import Path
import geopandas as gpd
import pandas as pd
from shapely.geometry import Point


class Vegetation:

    _GEOJSON_PATH = Path(__file__).resolve().parents[2] / "data" / "raw" / "municipios.geojson" #Os dados usados aqui de maneira fixa configuram-se apenas para fins de teste, assim que for tudo finalizado, eu os colocarei no .env


    _BIOMES_BY_STATE = {
        "AM": "Amazônia", "PA": "Amazônia", "RO": "Amazônia",
        "AC": "Amazônia", "RR": "Amazônia", "AP": "Amazônia",
        "TO": "Cerrado",  "MT": "Amazônia", "MA": "Amazônia",
        "GO": "Cerrado",  "MS": "Pantanal", "MG": "Cerrado",
        "BA": "Caatinga", "CE": "Caatinga", "PE": "Caatinga",
        "PI": "Caatinga", "RN": "Caatinga", "PB": "Caatinga",
        "AL": "Caatinga", "SE": "Caatinga", "DF": "Cerrado",
        "SP": "Mata Atlântica", "RJ": "Mata Atlântica", "ES": "Mata Atlântica",
        "PR": "Mata Atlântica", "SC": "Mata Atlântica", "RS": "Pampa",
    }

    UFS = sorted(_BIOMES_BY_STATE)

    def download(self):
        url = (
            "https://servicodados.ibge.gov.br/api/v3/malhas/paises/BR"
            "?formato=application/vnd.geo+json&qualidade=minima&intrarregiao=municipio"
        )
        response = requests.get(url, timeout=120)
        response.raise_for_status()
        self._GEOJSON_PATH.parent.mkdir(parents=True, exist_ok=True)
        self._GEOJSON_PATH.write_text(response.text)

    def read_geo_data(self):
        if not self._GEOJSON_PATH.exists():
            self.download()
        gdf = gpd.read_file(self._GEOJSON_PATH)
        resp = requests.get("https://servicodados.ibge.gov.br/api/v1/localidades/municipios", timeout=60)
        resp.raise_for_status()
        df_names = pd.DataFrame([{
            "codarea": str(m["id"]),
            "nome": m["nome"],
            "uf": m["regiao-imediata"]["regiao-intermediaria"]["UF"]["sigla"],
        } for m in resp.json()])
        return gdf.merge(df_names, on="codarea", how="left")

    def get_municipality(self, gdf, lat, lon):
        point = Point(lon, lat)
        for _, row in gdf.iterrows():
            if row["geometry"].contains(point):
                return {
                    "codarea": row["codarea"],
                    "nome": row["nome"],
                    "uf": row["uf"],
                }
        return None

    @staticmethod
    def _normalize(text):
        # sem acento, minusculo e sem hifen/espacos repetidos: "São João del-Rei" == "sao joao del rei"
        text = unicodedata.normalize("NFKD", str(text)).encode("ascii", "ignore").decode()
        return " ".join(text.lower().replace("-", " ").replace("'", " ").split())

    def find_municipality(self, gdf, name, uf):
        target = self._normalize(name)
        in_state = gdf[gdf["uf"] == uf].dropna(subset=["nome"])
        match = in_state[in_state["nome"].map(self._normalize) == target]
        if match.empty:
            return None
        row = match.iloc[0]
        point, is_urban_center = self._city_center(row["nome"], row["geometry"])
        return {
            "codarea": row["codarea"],
            "nome": row["nome"],
            "uf": row["uf"],
            "lat": round(point.y, 4),
            "lon": round(point.x, 4),
            "is_urban_center": is_urban_center,
        }

    def list_municipalities(self, gdf, uf):
        names = gdf.loc[gdf["uf"] == uf, "nome"].dropna().tolist()
        return sorted(names, key=self._normalize)

    def same_name(self, first, second):
        return self._normalize(first) == self._normalize(second)

    def _city_center(self, name, polygon):
        # Sede urbana pelo geocoder do Open-Meteo (sem chave). So vale se cair dentro do poligono
        # do IBGE, o que descarta homonimos de outros estados.
        try:
            resp = requests.get(
                "https://geocoding-api.open-meteo.com/v1/search",
                params={"name": name, "countryCode": "BR", "language": "pt", "count": 20},
                timeout=15,
            )
            resp.raise_for_status()
            for result in resp.json().get("results", []):
                point = Point(result["longitude"], result["latitude"])
                if polygon.contains(point):
                    return point, True
        except (requests.RequestException, ValueError, KeyError):
            pass
        # sem geocoder: ponto interno do poligono (o centroide pode cair fora dele)
        return polygon.representative_point(), False

    def suggest_municipalities(self, gdf, name, uf, limit=5):
        names = gdf.loc[gdf["uf"] == uf, "nome"].dropna()
        by_normalized = {self._normalize(n): n for n in names}
        close = difflib.get_close_matches(self._normalize(name), list(by_normalized), n=limit, cutoff=0.6)
        return [by_normalized[c] for c in close]

    def get_biome(self, uf):
        return self._BIOMES_BY_STATE.get(uf, "Desconhecido")
