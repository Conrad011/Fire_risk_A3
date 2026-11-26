import { date, isLowConfidence, number, time } from "./format.js";

const KM_PER_DEGREE = 111.32;
const MAX_MARKERS = 500;
const ESRI = "https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/{layer}/MapServer/tile/{z}/{y}/{x}";
const CITY_PIN_SVG = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' width='24' height='24' aria-hidden='true'>"
  + "<path d='M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7z' fill='#ff4b4b' stroke='#ffffff' stroke-width='1.5'/>"
  + "<circle cx='12' cy='9' r='2.5' fill='#ffffff'/></svg>";

let map = null;
let overlays = null;

function createMap() {
  map = L.map("map", { maxZoom: 16 });
  // Basemap escuro sem chave: os tiles da CARTO passaram a exigir API key
  L.tileLayer(ESRI.replace("{layer}", "World_Dark_Gray_Base"), {
    attribution: "Tiles &copy; Esri — Esri, HERE, Garmin, OpenStreetMap", maxZoom: 16,
  }).addTo(map);
  L.tileLayer(ESRI.replace("{layer}", "World_Dark_Gray_Reference"), { maxZoom: 16 }).addTo(map);
  overlays = L.layerGroup().addTo(map);
}

function fireTooltip(fire) {
  const place = (fire.insideMunicipality ? " · dentro do município" : "")
    + (isLowConfidence(fire) ? " · baixa confiança, fora do índice" : "");
  const hour = fire.detectedAt ? ` às ${time(fire.detectedAt)} (Brasília)` : "";
  return `${date(fire.acqDate)}${hour} · ${number(fire.distanceKm)} km da cidade · `
    + `intensidade ${number(fire.frp)} MW${place}`;
}

export function renderMap(data) {
  if (map === null) createMap();
  overlays.clearLayers();

  const { lat, lon, nome, uf } = data.municipality;
  const radiusKm = data.params.radiusKm;

  if (data.municipalityGeoJson) {
    L.geoJSON(data.municipalityGeoJson, {
      style: { fillColor: "#ff6b00", color: "#ff9933", weight: 2, fillOpacity: 0.15 },
    }).bindTooltip(`${nome} / ${uf}`).addTo(overlays);
  }

  // Heatmap dos focos ponderado por FRP, só com os que entram no índice
  const counted = data.fires.filter((f) => !isLowConfidence(f));
  if (counted.length > 0) {
    L.heatLayer(counted.map((f) => [f.latitude, f.longitude, Math.max(f.frp, 1)]), {
      radius: 12, blur: 8, minOpacity: 0.4, max: 30,
      gradient: { 0.4: "blue", 0.65: "yellow", 1: "red" },
    }).addTo(overlays);
  }

  // Pontos individuais: o heatmap sozinho quase não aparece com poucos focos
  if (data.fires.length <= MAX_MARKERS) {
    data.fires.forEach((fire) => {
      // Baixa confiança: círculo cinza vazado, para não ser confundido com os focos que contam
      const style = isLowConfidence(fire)
        ? { radius: 5, color: "#9aa4af", weight: 2, dashArray: "2 3", fillOpacity: 0 }
        : {
          radius: fire.insideMunicipality ? 6 : 5,
          color: fire.insideMunicipality ? "#ffffff" : "#ff4444",
          weight: 2, fillColor: "#ff4444", fillOpacity: 0.9,
        };
      L.circleMarker([fire.latitude, fire.longitude], style).bindTooltip(fireTooltip(fire)).addTo(overlays);
    });
  }

  L.circle([lat, lon], {
    radius: radiusKm * 1000, color: "#9aa4af", weight: 1, dashArray: "6", fill: false, interactive: false,
  }).addTo(overlays);

  L.marker([lat, lon], {
    title: `${nome} / ${uf}`,
    icon: L.divIcon({ className: "map-pin", html: CITY_PIN_SVG, iconSize: [24, 24], iconAnchor: [12, 22] }),
  }).bindPopup(`${nome} / ${uf}`).addTo(overlays);

  map.invalidateSize();
  const dLat = radiusKm / KM_PER_DEGREE;
  const dLon = dLat / Math.cos((lat * Math.PI) / 180);
  map.fitBounds([[lat - dLat, lon - dLon], [lat + dLat, lon + dLon]]);

  let legend = `Legenda: ponto vermelho = foco · contorno branco = foco dentro do município · `
    + `área laranja = ${nome} · círculo tracejado = ${radiusKm} km`;
  if (counted.length < data.fires.length) legend += " · círculo cinza vazado = baixa confiança (fora do índice)";
  if (data.fires.length > MAX_MARKERS) legend += ` · acima de ${MAX_MARKERS} focos, apenas a mancha de calor é exibida`;
  document.getElementById("map-legend").textContent = legend;
}
