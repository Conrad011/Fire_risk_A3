from src.collectors.Climate import Climate
from src.collectors.Firms import Firms
from src.collectors.Vegetation import Vegetation
from src.processors.RiskProcessor import RiskProcessor
CITY, UF = "Sete Lagoas", "MG"  ### isso e configuravel diretamente no painel streamlit
FIRE_DAYS = 5
RADIUS_KM = 100
def main():
    veg = Vegetation()
    climate = Climate()
    firms = Firms()

    print("Carregando dados geográficos...")
    gdf = veg.read_geo_data()

    municipality = veg.find_municipality(gdf, CITY, UF)
    if municipality is None:
        raise ValueError(f"Município {CITY} / {UF} não encontrado")
    LAT, LON = municipality["lat"], municipality["lon"]

    biome = veg.get_biome(municipality["uf"])

    print("Coletando dados climáticos...")
    df_climate = climate.get_data(LAT, LON)

    print("Coletando focos de incêndio...")
    df_fires = firms.get_fires(LAT, LON, days=FIRE_DAYS, radius_km=RADIUS_KM)

    print("Processando risco...")
    df, summary = RiskProcessor.process(df_climate, df_fires, biome)

    print("\n--- RESULTADO ---")
    print(f"Município: {municipality['nome']} / {municipality['uf']}")
    print(f"Coordenada: {LAT}, {LON}")
    print(f"Bioma : {summary['biome']}")
    print(f"Total focos : {summary['total_fires']}")
    print(f"Temp média : {summary['avg_temp']}°C")
    print(f"Chuva média : {summary['avg_rain']} mm")
    print(f"Dias s/ chuva: {summary['dry_days']}")
    print(f"Índice risco: {summary['risk_index']} / 10")


if __name__ == "__main__":
    main()
