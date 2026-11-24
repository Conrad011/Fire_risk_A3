import sys
import os
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import math
from datetime import datetime
from zoneinfo import ZoneInfo

import requests
import pandas as pd
import geopandas as gpd
import streamlit as st
import folium
from folium.plugins import HeatMap
from streamlit_folium import st_folium
import plotly.graph_objects as go
from plotly.subplots import make_subplots

from src.collectors.Climate import Climate
from src.collectors.Firms import Firms
from src.collectors.Vegetation import Vegetation
from src.processors.RiskProcessor import RiskProcessor

st.set_page_config(page_title="FireRisk", page_icon="🔥", layout="wide")

# ── Constantes ────────────────────────────────────────────────────────────────
DEFAULTS = {"city": "Sete Lagoas", "uf": "MG", "days": 5, "radius": 100}

UF_NAMES = {
    "AC": "Acre", "AL": "Alagoas", "AM": "Amazonas", "AP": "Amapá", "BA": "Bahia", "CE": "Ceará",
    "DF": "Distrito Federal", "ES": "Espírito Santo", "GO": "Goiás", "MA": "Maranhão",
    "MG": "Minas Gerais", "MS": "Mato Grosso do Sul", "MT": "Mato Grosso", "PA": "Pará",
    "PB": "Paraíba", "PE": "Pernambuco", "PI": "Piauí", "PR": "Paraná", "RJ": "Rio de Janeiro",
    "RN": "Rio Grande do Norte", "RO": "Rondônia", "RR": "Roraima", "RS": "Rio Grande do Sul",
    "SC": "Santa Catarina", "SE": "Sergipe", "SP": "São Paulo", "TO": "Tocantins",
}

# Uma única definição das faixas: banner, medidor e metodologia usam as mesmas cores e nomes
RISK_LEVELS = [
    {"max": 3, "label": "Baixo", "badge": "green", "icon": ":material/check_circle:", "color": "#44cc44",
     "step": "#1a3a1a", "meaning": "Condições menos favoráveis ao fogo no momento."},
    {"max": 6, "label": "Moderado", "badge": "orange", "icon": ":material/warning:", "color": "#ffaa00",
     "step": "#3a2a00", "meaning": "Atenção: evite queimadas e acompanhe a evolução dos focos."},
    {"max": 10.01, "label": "Alto", "badge": "red", "icon": ":material/local_fire_department:", "color": "#ff4444",
     "step": "#3a0000", "meaning": "Condições críticas: não use fogo ao ar livre e fique atento às autoridades locais."},
]

CONFIDENCE = {"h": "Alta", "n": "Normal", "l": "Baixa"}
PERIOD = {"D": "Dia", "N": "Noite"}


class AnalysisError(Exception):
    def __init__(self, title, hint, suggestions=()):
        super().__init__(title)
        self.title, self.hint, self.suggestions = title, hint, list(suggestions)


# ── Formatação pt-BR ──────────────────────────────────────────────────────────
def num(value, decimals=1):
    if value is None or pd.isna(value):
        return "—"
    text = f"{float(value):,.{decimals}f}"
    return text.replace(",", "_").replace(".", ",").replace("_", ".")

def plural(count, singular, plural_form):
    return f"{num(count, 0)} {singular if count == 1 else plural_form}"

def risk_level(index):
    return next(level for level in RISK_LEVELS if index < level["max"])


# ── Dados ─────────────────────────────────────────────────────────────────────
@st.cache_resource(show_spinner="Carregando a lista de municípios do IBGE...")
def load_geo():
    return Vegetation().read_geo_data()

@st.cache_data(show_spinner=False)
def load_city_names(_gdf, uf):
    return Vegetation().list_municipalities(_gdf, uf)

@st.cache_data(show_spinner=False, ttl=86400)
def load_location(_gdf, city, uf):
    return Vegetation().find_municipality(_gdf, city, uf)

@st.cache_data(show_spinner=False, ttl=3600)
def load_climate(lat, lon):
    return Climate().get_data(lat, lon)

@st.cache_data(show_spinner=False, ttl=900)
def load_firms(lat, lon, days, radius_km):
    return Firms().get_fires(lat, lon, days=days, radius_km=radius_km)


# ── Estado e ações do usuário ─────────────────────────────────────────────────
state = st.session_state

def params_from_url():
    query = st.query_params
    uf, city = query.get("uf", "").upper(), query.get("cidade", "").strip()
    if uf not in UF_NAMES or not city:
        return dict(DEFAULTS)
    try:
        days = min(max(int(query.get("dias", DEFAULTS["days"])), 1), 5)
        radius = min(max(int(query.get("raio", DEFAULTS["radius"])), 25), 500)
    except ValueError:
        days, radius = DEFAULTS["days"], DEFAULTS["radius"]
    return {"city": city, "uf": uf, "days": days, "radius": radius - radius % 25}

def set_form(params):
    state.form_uf, state.form_city = params["uf"], params["city"]
    state.form_days, state.form_radius = params["days"], params["radius"]

def form_params():
    return {"city": state.form_city, "uf": state.form_uf, "days": state.form_days, "radius": state.form_radius}

def same_params(first, second):
    return (first["uf"], first["days"], first["radius"]) == (second["uf"], second["days"], second["radius"]) \
        and bool(first["city"]) and bool(second["city"]) and Vegetation().same_name(first["city"], second["city"])

def request_analysis(params=None):
    params = dict(params or form_params())
    set_form(params)
    state.run_request = params

def on_uf_change():
    state.form_city = None

def refresh_data():
    load_climate.clear()
    load_firms.clear()
    request_analysis(state.result["params"])

def sync_url(params):
    st.query_params.from_dict({
        "cidade": params["city"], "uf": params["uf"], "dias": params["days"], "raio": params["radius"],
    })

def undo_analysis():
    state.result, state.previous = state.previous, state.result
    state.error = None
    set_form(state.result["params"])
    sync_url(state.result["params"])

if "form_uf" not in state:
    first_params = params_from_url()
    set_form(first_params)
    state.update(run_request=first_params, result=None, previous=None, error=None, history=[])


def analyze(params, gdf, holder):
    source = "IBGE"
    title = f"Analisando {params['city']} / {params['uf']}..."
    try:
        with holder.status(title, expanded=True) as status:
            st.write("1 de 3 · Localizando o município (IBGE)")
            location = load_location(gdf, params["city"], params["uf"])
            if location is None:
                raise AnalysisError(
                    f"Não encontramos \"{params['city']}\" em {UF_NAMES[params['uf']]}.",
                    "Confira a grafia e o estado, ou escolha uma das opções abaixo.",
                    Vegetation().suggest_municipalities(gdf, params["city"], params["uf"]),
                )

            source = "Open-Meteo"
            st.write("2 de 3 · Buscando o clima dos últimos 30 dias (Open-Meteo)")
            df_climate = load_climate(location["lat"], location["lon"])

            source = "NASA FIRMS"
            st.write("3 de 3 · Buscando focos de calor por satélite (NASA FIRMS)")
            df_fires = load_firms(location["lat"], location["lon"], params["days"], params["radius"]).copy()

            satellite = df_fires.attrs.get("source", "VIIRS")

            polygon = gdf.loc[gdf["codarea"] == location["codarea"], "geometry"].iloc[0]
            df_fires["no_municipio"] = gpd.points_from_xy(
                df_fires["longitude"].astype(float), df_fires["latitude"].astype(float)
            ).within(polygon)
            df, summary = RiskProcessor.process(df_climate, df_fires, Vegetation().get_biome(location["uf"]))
            status.update(label="Análise concluída", state="complete")
    # As mensagens das exceções não vão para a tela: as do requests trazem a URL com a chave da NASA
    except AnalysisError:
        raise
    except requests.Timeout:
        raise AnalysisError(f"{source} demorou demais para responder.",
                            "O serviço pode estar sobrecarregado. Aguarde um instante e tente novamente.")
    except requests.RequestException:
        raise AnalysisError(f"Não foi possível obter os dados de {source}.",
                            "Verifique sua conexão com a internet e tente novamente.")
    except EnvironmentError:
        raise AnalysisError("A chave de acesso da NASA FIRMS não foi configurada.",
                            "Crie o arquivo .env na raiz do projeto com a linha NASA_FIRMS_KEY=sua_chave "
                            "e reinicie o painel.")
    except Exception as e:
        if source == "NASA FIRMS" and "invalid" in str(e).lower():
            raise AnalysisError("A NASA FIRMS recusou a chave de acesso.",
                                "Confira o valor de NASA_FIRMS_KEY no arquivo .env ou gere uma chave nova "
                                "em firms.modaps.eosdis.nasa.gov/api/area.")
        raise AnalysisError(f"{source} devolveu uma resposta inesperada.",
                            "Tente novamente em alguns minutos.")

    return {
        "params": {**params, "city": location["nome"]},
        "municipality": location, "summary": summary, "df": df,
        "df_climate": df_climate, "df_fires": df_fires, "satellite": satellite,
        "analyzed_at": datetime.now(ZoneInfo("America/Sao_Paulo")),
    }


# ── Cabeçalho ─────────────────────────────────────────────────────────────────
st.title("🔥 FireRisk — Monitor de Risco de Incêndio")
st.caption("Focos de calor por satélite, clima e limites municipais para qualquer cidade do Brasil.")

try:
    gdf = load_geo()
except Exception:
    st.error("**Não foi possível carregar a lista de municípios do IBGE.**  \n"
             "Verifique sua conexão com a internet e tente novamente.", icon=":material/cloud_off:")
    st.button("Tentar novamente", type="primary")
    st.stop()

# ── Barra lateral ─────────────────────────────────────────────────────────────
with st.sidebar:
    st.header("O que analisar")
    st.selectbox(
        "Estado", list(UF_NAMES), key="form_uf", on_change=on_uf_change,
        format_func=lambda code: f"{code} — {UF_NAMES[code]}",
    )
    city_options = load_city_names(gdf, state.form_uf)
    if state.form_city and state.form_city not in city_options:
        city_options = [state.form_city] + city_options
    st.selectbox(
        "Cidade", city_options, key="form_city", accept_new_options=True,
        placeholder="Digite o nome da cidade",
        help="Digite parte do nome e escolha na lista. Acentos e maiúsculas não importam.",
    )
    st.slider(
        "Período dos focos (dias)", min_value=1, max_value=5, key="form_days",
        help="Quantos dias para trás buscar focos de calor. O limite de 5 dias é da API da NASA usada aqui.",
    )
    st.slider(
        "Distância ao redor da cidade (km)", min_value=25, max_value=500, step=25, key="form_radius",
        help="Focos dentro deste raio entram no índice. Raios maiores tendem a aumentar o risco calculado.",
    )
    st.button(
        "🔍 Analisar", type="primary", width="stretch", on_click=request_analysis,
        disabled=not state.form_city,
        help=None if state.form_city else "Escolha uma cidade para continuar.",
    )
    if not state.form_city:
        st.caption("Escolha uma cidade para continuar.")

# ── Execução da análise ───────────────────────────────────────────────────────
if state.get("run_request"):
    params, state.run_request = state.run_request, None
    holder = st.empty()
    try:
        new_result = analyze(params, gdf, holder)
    except AnalysisError as e:
        holder.empty()
        state.error = {"title": e.title, "hint": e.hint, "suggestions": e.suggestions, "params": params}
    else:
        holder.empty()
        had_result = state.result is not None
        if had_result and not same_params(state.result["params"], new_result["params"]):
            state.previous = state.result
        state.result, state.error = new_result, None
        place = (new_result["params"]["city"], new_result["params"]["uf"])
        state.history = ([place] + [p for p in state.history if p != place])[:5]
        sync_url(new_result["params"])
        if had_result:
            st.toast(f"Análise de {place[0]} / {place[1]} atualizada.", icon=":material/check_circle:")

result = state.result

with st.sidebar:
    shortcuts = [p for p in state.history if not result or p != (result["params"]["city"], result["params"]["uf"])]
    if shortcuts:
        st.divider()
        st.caption("Cidades recentes")
        for name, code in shortcuts[:4]:
            st.button(
                f"{name} / {code}", key=f"recent_{code}_{name}", width="stretch", on_click=request_analysis,
                args=({"city": name, "uf": code, "days": state.form_days, "radius": state.form_radius},),
            )
    st.divider()
    if state.previous:
        back = state.previous["params"]
        st.button(f"↩ Voltar para {back['city']} / {back['uf']}", width="stretch", on_click=undo_analysis,
                  help="Retorna à análise anterior sem consultar as APIs de novo.")
    st.button("Restaurar padrão", width="stretch", on_click=request_analysis, args=(DEFAULTS,),
              help="Volta para Sete Lagoas / MG, 5 dias e 100 km.")

# ── Erros (mantêm a última análise válida na tela) ────────────────────────────
if state.error and not same_params(form_params(), state.error["params"]):
    state.error = None

if state.error:
    error = state.error
    st.error(f"**{error['title']}**  \n{error['hint']}", icon=":material/error:")
    if error["suggestions"]:
        st.caption("Você quis dizer:")
        cols = st.columns(min(len(error["suggestions"]), 5))
        for col, name in zip(cols, error["suggestions"]):
            col.button(name, key=f"suggest_{name}", width="stretch", on_click=request_analysis,
                       args=({**error["params"], "city": name},))
    else:
        st.button("Tentar novamente", on_click=request_analysis, args=(error["params"],))
    if result:
        st.caption(f"Os resultados abaixo continuam sendo de {result['params']['city']} / {result['params']['uf']}.")

if result is None:
    st.info("Escolha o estado e a cidade na barra lateral e clique em **Analisar**.")
    st.stop()

used         = result["params"]
municipality = result["municipality"]
summary      = result["summary"]
df           = result["df"]
df_climate   = result["df_climate"]
df_fires     = result["df_fires"]
lat_used     = municipality["lat"]
lon_used     = municipality["lon"]
radius_used  = used["radius"]
days_used    = used["days"]
place_name   = f"{municipality['nome']} / {municipality['uf']}"

if not state.error and state.form_city and not same_params(form_params(), used):
    st.warning(f"Você alterou os filtros. Os resultados abaixo ainda são de **{place_name}**, "
               f"{plural(days_used, 'dia', 'dias')} e {radius_used} km. Clique em **Analisar** para atualizar.",
               icon=":material/sync_problem:")

# ── Resumo ────────────────────────────────────────────────────────────────────
risk_index   = summary["risk_index"]
level        = risk_level(risk_index)
weight       = summary["biome_weight"]
total_fires  = summary["total_fires"]
fires_in_city = int(df_fires["no_municipio"].sum())
nearest_km   = df_fires["distance_km"].min() if not df_fires.empty else None

with st.container(border=True):
    info, action = st.columns([5, 1], vertical_alignment="center")
    with info:
        st.badge(f"Risco {level['label'].lower()}", icon=level["icon"], color=level["badge"])
        st.markdown(f"### {place_name}: {num(risk_index, 2)} de 10")
        st.markdown(level["meaning"])
        st.caption(
            f"Bioma {summary['biome']} · dados de {result['analyzed_at']:%d/%m/%Y às %H:%M} · "
            f"satélite {result['satellite']} · "
            f"coordenada {num(lat_used, 4)}; {num(lon_used, 4)}"
            + ("" if municipality["is_urban_center"] else " (ponto aproximado dentro do município)")
        )
    action.button("Atualizar dados", icon=":material/refresh:", width="stretch", on_click=refresh_data,
                  help="Busca os dados mais recentes, ignorando o que está guardado em memória.")

c1, c2, c3, c4 = st.columns(4)
c1.metric(f"Focos em até {radius_used} km", num(total_fires, 0),
          help=f"Detecções de calor por satélite nos últimos {plural(days_used, 'dia', 'dias')}.")
c2.metric(f"Focos em {municipality['nome']}", num(fires_in_city, 0),
          help="Detecções dentro do limite oficial do município.")
c3.metric("Foco mais próximo", "Nenhum" if nearest_km is None else f"{num(nearest_km)} km",
          help="Distância em linha reta do centro da cidade ao foco mais próximo.")
c4.metric("Dias seguidos sem chuva", summary["dry_days"],
          help="Dias consecutivos, até hoje, com menos de 1 mm de chuva.")

if total_fires == 0:
    st.success(f"Nenhum foco detectado em até {radius_used} km nos últimos {plural(days_used, 'dia', 'dias')}. "
               "O índice reflete apenas o clima.", icon=":material/check_circle:")
elif summary["fire_score"] >= 1:
    st.info("Há 100 focos ou mais na área, o limite da fórmula. Aumentar o raio não elevará mais essa parte do índice.",
            icon=":material/info:")

# ── Mapa + Medidor ────────────────────────────────────────────────────────────
col_map, col_gauge = st.columns([3, 1])

with col_map:
    st.subheader("Mapa de focos de calor")

    # A CARTO passou a exigir chave de API no dark_matter; o Dark Gray Canvas da Esri não exige
    esri = "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/{layer}/MapServer/tile/{{z}}/{{y}}/{{x}}"
    m = folium.Map(location=[lat_used, lon_used], zoom_start=8, max_zoom=16, tiles=None)
    folium.TileLayer(
        esri.format(layer="World_Dark_Gray_Base"), attr="Tiles © Esri — Esri, HERE, Garmin, OpenStreetMap",
        name="Base", max_zoom=16, control=False,
    ).add_to(m)
    folium.TileLayer(
        esri.format(layer="World_Dark_Gray_Reference"), attr="Esri",
        name="Rótulos", max_zoom=16, overlay=True, control=False,
    ).add_to(m)

    # Polígono do município
    municipality_gdf = gdf[gdf["codarea"] == municipality["codarea"]]
    if not municipality_gdf.empty:
        folium.GeoJson(
            municipality_gdf.__geo_interface__,
            tooltip=place_name,
            style_function=lambda _: {
                "fillColor": "#ff6b00", "color": "#ff9933",
                "weight": 2, "fillOpacity": 0.15,
            },
        ).add_to(m)

    # Heatmap dos focos ponderado por FRP
    if not df_fires.empty and "latitude" in df_fires.columns:
        heat_data = [
            [row["latitude"], row["longitude"], max(float(row.get("frp", 1)), 1)]
            for _, row in df_fires.iterrows()
        ]
        HeatMap(
            heat_data, radius=12, blur=8, min_opacity=0.4,
            gradient={"0.4": "blue", "0.65": "yellow", "1": "red"},
        ).add_to(m)

    # Focos individuais: o heatmap sozinho quase não aparece quando há poucos pontos
    if len(df_fires) <= 500:
        for _, row in df_fires.iterrows():
            inside = bool(row["no_municipio"])
            folium.CircleMarker(
                location=[row["latitude"], row["longitude"]],
                radius=6 if inside else 5, color="#ffffff" if inside else "#ff4444",
                fill=True, fill_color="#ff4444", fill_opacity=0.9,
                tooltip=(f"{pd.to_datetime(row['acq_date']):%d/%m/%Y} · {num(row['distance_km'])} km da cidade · "
                         f"intensidade {num(row.get('frp'))} MW"
                         + (" · dentro do município" if inside else "")),
            ).add_to(m)

    # Área de busca
    folium.Circle(
        location=[lat_used, lon_used], radius=radius_used * 1000,
        color="#888888", weight=1, dash_array="6", fill=False,
    ).add_to(m)
    # Circle.get_bounds() devolve só o centro, então o enquadramento do raio é calculado aqui
    d_lat = radius_used / 111.32
    d_lon = d_lat / math.cos(math.radians(lat_used))
    m.fit_bounds([[lat_used - d_lat, lon_used - d_lon], [lat_used + d_lat, lon_used + d_lon]])

    # Marcador do ponto analisado
    folium.Marker(
        location=[lat_used, lon_used],
        popup=f"{place_name} ({lat_used:.4f}, {lon_used:.4f})",
        tooltip=place_name,
        icon=folium.Icon(color="red", icon="fire", prefix="fa"),
    ).add_to(m)

    # returned_objects=[] evita recarregar a página a cada arraste ou zoom no mapa
    st_folium(m, use_container_width=True, height=450, returned_objects=[],
              key=f"map_{municipality['codarea']}_{days_used}_{radius_used}")
    legend = ("Legenda: ponto vermelho = foco · contorno branco = foco dentro do município · "
              f"área laranja = {municipality['nome']} · círculo tracejado = {radius_used} km")
    if len(df_fires) > 500:
        legend += " · acima de 500 focos, apenas a mancha de calor é exibida"
    st.caption(legend)

with col_gauge:
    st.subheader("Índice de risco")

    fig_gauge = go.Figure(go.Indicator(
        mode="gauge+number",
        value=risk_index,
        number={"suffix": " / 10", "font": {"size": 28}, "valueformat": ".2f"},
        gauge={
            "axis": {"range": [0, 10], "tickvals": [0, 3, 6, 10], "tickwidth": 1, "tickcolor": "white"},
            "bar": {"color": level["color"]},
            "steps": [
                {"range": [0, 3], "color": RISK_LEVELS[0]["step"]},
                {"range": [3, 6], "color": RISK_LEVELS[1]["step"]},
                {"range": [6, 10], "color": RISK_LEVELS[2]["step"]},
            ],
        },
    ))
    fig_gauge.update_layout(
        height=240, margin=dict(t=20, b=10, l=20, r=20), separators=",.",
        paper_bgcolor="rgba(0,0,0,0)", font_color="white",
    )
    st.plotly_chart(fig_gauge, width="stretch")
    st.markdown("**Baixo** abaixo de 3  \n**Moderado** de 3 a 6  \n**Alto** a partir de 6")
    st.caption(f"Bioma {summary['biome']}: multiplicador {num(weight)}×")

# ── Detalhes ──────────────────────────────────────────────────────────────────
tab_climate, tab_fires, tab_method, tab_help = st.tabs(
    ["Clima (30 dias)", f"Focos detectados ({num(total_fires, 0)})", "Como o índice é calculado", "Ajuda"]
)

with tab_climate:
    m1, m2, m3, m4 = st.columns(4)
    m1.metric("Temperatura máxima média", f"{num(summary['avg_temp'])} °C")
    m2.metric("Chuva média por dia", f"{num(summary['avg_rain'])} mm")
    m3.metric("Maior vento", f"{num(summary['max_wind'])} km/h")
    m4.metric("Menor umidade do ar", f"{num(summary['min_humidity'], 0)} %",
              help="Valor mais baixo de umidade relativa nos últimos 30 dias.")

    fig = make_subplots(specs=[[{"secondary_y": True}]])
    fig.add_trace(go.Scatter(
        x=df_climate["date"], y=df_climate["temperature_max"],
        name="Temperatura máxima (°C)", line=dict(color="#ff4444", width=2),
        fill="tozeroy", fillcolor="rgba(255,68,68,0.1)",
        hovertemplate="%{x|%d/%m/%Y}<br>%{y:.1f} °C<extra></extra>",
    ), secondary_y=False)
    fig.add_trace(go.Bar(
        x=df_climate["date"], y=df_climate["precipitation"],
        name="Chuva (mm)", marker_color="rgba(68,136,255,0.5)",
        hovertemplate="%{x|%d/%m/%Y}<br>%{y:.1f} mm<extra></extra>",
    ), secondary_y=True)
    fig.update_layout(
        height=340, paper_bgcolor="rgba(0,0,0,0)", separators=",.",
        plot_bgcolor="rgba(0,0,0,0)", font_color="white",
        legend=dict(orientation="h", y=-0.2),
        margin=dict(t=20, b=50),
    )
    fig.update_xaxes(tickformat="%d/%m")
    fig.update_yaxes(title_text="°C", secondary_y=False, gridcolor="#333")
    fig.update_yaxes(title_text="mm", secondary_y=True, showgrid=False)
    st.plotly_chart(fig, width="stretch")

with tab_fires:
    if df_fires.empty:
        st.info(f"Nenhum foco detectado em até {radius_used} km nos últimos {plural(days_used, 'dia', 'dias')}.")
    else:
        fire_dates = pd.to_datetime(df_fires["acq_date"])
        end = max(fire_dates.max(), df["date"].max())
        day_range = pd.date_range(end=end, periods=days_used)
        fires_per_day = fire_dates.value_counts().reindex(day_range, fill_value=0).sort_index()

        fig2 = go.Figure(go.Bar(
            x=fires_per_day.index, y=fires_per_day.values, marker_color="#ff4444",
            hovertemplate="%{x|%d/%m/%Y}<br>%{y} focos<extra></extra>",
        ))
        fig2.update_layout(
            title=f"Focos por dia em até {radius_used} km",
            height=280, paper_bgcolor="rgba(0,0,0,0)", separators=",.",
            plot_bgcolor="rgba(0,0,0,0)", font_color="white",
            margin=dict(t=40, b=30),
        )
        fig2.update_xaxes(tickformat="%d/%m", tickvals=fires_per_day.index, gridcolor="#333")
        fig2.update_yaxes(gridcolor="#333", title_text="Focos", tickformat="d", rangemode="tozero")
        st.plotly_chart(fig2, width="stretch")

        only_city = st.toggle(f"Mostrar apenas focos dentro de {municipality['nome']}",
                              disabled=fires_in_city == 0)
        source_df = df_fires[df_fires["no_municipio"]] if only_city else df_fires
        table = pd.DataFrame({
            "Data": pd.to_datetime(source_df["acq_date"]),
            "Distância (km)": source_df["distance_km"],
            "No município": source_df["no_municipio"].map({True: "Sim", False: "Não"}),
            "Intensidade (MW)": source_df.get("frp"),
            "Confiança": source_df["confidence"].map(lambda v: CONFIDENCE.get(v, v)),
            "Período": source_df["daynight"].map(lambda v: PERIOD.get(v, v)) if "daynight" in source_df else None,
            "Latitude": source_df["latitude"],
            "Longitude": source_df["longitude"],
        }).sort_values(["Data", "Distância (km)"], ascending=[False, True])

        st.dataframe(table, width="stretch", hide_index=True, column_config={
            "Data": st.column_config.DateColumn(format="DD/MM/YYYY"),
            "Distância (km)": st.column_config.NumberColumn(format="%.1f"),
            "Intensidade (MW)": st.column_config.NumberColumn(
                format="%.1f", help="Potência radiativa do fogo (FRP) medida pelo satélite."),
        })
        # ; e vírgula decimal abrem direto em colunas no Excel em português
        csv = table.assign(Data=table["Data"].dt.strftime("%d/%m/%Y")).to_csv(index=False, sep=";", decimal=",")
        st.download_button(
            "Baixar tabela (CSV)", csv.encode("utf-8-sig"), icon=":material/download:",
            file_name=f"focos_{municipality['nome'].replace(' ', '_')}_{municipality['uf']}.csv", mime="text/csv",
        )

with tab_method:
    fire_part = summary["fire_score"] * 0.6
    climate_part = summary["climate_score"] * 0.4
    st.markdown(
        "```\n"
        "pontuação de focos = min(focos no raio / 100, 1)\n"
        "pontuação de clima = min(temp. máx. média / 40, 1) × max(1 − chuva média / 50, 0)\n"
        "índice             = (focos × 0,6 + clima × 0,4) × peso do bioma × 10   (limitado a 10)\n"
        "```"
    )
    st.markdown(
        f"| Componente | Valor | Contribuição |\n"
        f"|---|---|---|\n"
        f"| Focos ({num(total_fires, 0)} em até {radius_used} km) | {num(summary['fire_score'], 3)} | {num(fire_part, 3)} |\n"
        f"| Clima ({num(summary['avg_temp'])} °C · {num(summary['avg_rain'])} mm/dia) | {num(summary['climate_score'], 3)} | {num(climate_part, 3)} |\n"
        f"| Peso do bioma ({summary['biome']}) | {num(weight)}× | — |\n"
        f"| **Índice** | **{num(risk_index, 2)} / 10** | ({num(fire_part, 3)} + {num(climate_part, 3)}) × {num(weight)} × 10 |"
    )
    st.caption(
        "Dias sem chuva, vento e umidade são exibidos como contexto e não entram na fórmula. "
        "O bioma é o predominante no estado, uma simplificação."
    )

with tab_help:
    st.markdown(
        "**Como usar**\n"
        "1. Escolha o estado e digite a cidade na barra lateral.\n"
        "2. Ajuste o período e a distância, se quiser.\n"
        "3. Clique em **Analisar**.\n"
        "4. Veja o resumo e explore o mapa e as abas.\n\n"
        "**Atalhos**\n"
        "- **Cidades recentes** reabrem uma análise com um clique.\n"
        "- **Voltar** retorna à análise anterior.\n"
        "- O **endereço da página** guarda a cidade e os filtros: copie-o para compartilhar a mesma análise.\n\n"
        "**Glossário**\n"
        "- **Foco de calor:** ponto com temperatura anormal detectado por satélite. Pode ser incêndio, "
        "queimada controlada ou outra fonte de calor.\n"
        "- **Intensidade (MW):** energia irradiada pelo fogo (FRP, *Fire Radiative Power*).\n"
        "- **Confiança:** quão segura é a detecção, segundo a NASA.\n"
        "- **Bioma:** grande tipo de vegetação da região. Alguns recebem peso maior no índice.\n\n"
        "**Se algo der errado**\n"
        "- *Cidade não encontrada:* confira o estado e a grafia, ou clique em uma sugestão.\n"
        "- *Falha ao obter dados:* verifique a internet e clique em **Tentar novamente**.\n"
    )

st.divider()
st.caption(
    "Fontes: NASA FIRMS (VIIRS) · Open-Meteo · IBGE. Índice para fins acadêmicos; não substitui alertas oficiais. "
    "Em caso de incêndio, ligue **193** (Corpo de Bombeiros)."
)
