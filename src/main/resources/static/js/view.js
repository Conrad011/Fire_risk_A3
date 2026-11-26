import { renderClimateChart, renderFiresChart, renderGauge, renderHistoryChart } from "./charts.js";
import { date, dateTime, isLowConfidence, number, plural, time } from "./format.js";
import { renderMap } from "./map.js";

const MAX_TABLE_ROWS = 500;
const CONFIDENCE = { h: "Alta", n: "Normal", l: "Baixa" };
const PERIOD = { D: "Dia", N: "Noite" };

const COLUMNS = [
  { title: "Data", value: (f) => date(f.acqDate) },
  { title: "Hora (Brasília)", value: (f) => time(f.detectedAt) },
  { title: "Distância (km)", value: (f) => number(f.distanceKm), numeric: true },
  { title: "No município", value: (f) => (f.insideMunicipality ? "Sim" : "Não") },
  { title: "Intensidade (MW)", value: (f) => number(f.frp), numeric: true },
  {
    title: "Confiança",
    value: (f) => (CONFIDENCE[f.confidence] || f.confidence || "—") + (isLowConfidence(f) ? " (fora do índice)" : ""),
  },
  { title: "Período", value: (f) => PERIOD[f.daynight] || f.daynight || "—" },
  { title: "Latitude", value: (f) => number(f.latitude, 4), numeric: true },
  { title: "Longitude", value: (f) => number(f.longitude, 4), numeric: true },
];

const el = (id) => document.getElementById(id);

function cell(tag, text, numeric) {
  const node = document.createElement(tag);
  node.textContent = text;
  if (numeric) node.className = "num";
  return node;
}

function row(tag, cells) {
  const tr = document.createElement("tr");
  tr.append(...cells.map(([text, numeric]) => cell(tag, text, numeric)));
  return tr;
}

function metric(label, value, help) {
  const box = document.createElement("div");
  box.className = "metric";
  if (help) box.title = help;
  const name = document.createElement("span");
  name.className = "metric-label";
  name.textContent = label;
  const amount = document.createElement("span");
  amount.className = "metric-value";
  amount.textContent = value;
  box.append(name, amount);
  return box;
}

function renderSummary(data) {
  const { summary, municipality: city, params } = data;
  const badge = el("risk-badge");
  badge.className = "badge " + summary.level.code;
  badge.textContent = "Risco " + summary.level.label.toLowerCase();
  el("risk-title").textContent = `${city.nome} / ${city.uf}: ${number(summary.riskIndex, 2)} de 10`;
  el("risk-meaning").textContent = summary.level.meaning;

  const point = city.urbanCenter ? "" : " (ponto aproximado dentro do município)";
  const saved = data.dataSource.mode === "snapshot";
  const when = saved
    ? `dados salvos até ${date(data.dataSource.referenceDate)}`
    : `dados de ${dateTime(data.analyzedAt)} (horário de Brasília)`;
  el("risk-meta").textContent = `Bioma ${summary.biome} · ${when} · `
    + `satélite ${data.satellite} · coordenada ${number(city.lat, 4)}; ${number(city.lon, 4)}${point}`;

  el("offline").hidden = !saved;
  if (saved) {
    el("offline").textContent = "Não foi possível consultar as fontes de dados agora. Exibindo os dados salvos de "
      + `${city.nome} até ${date(data.dataSource.referenceDate)} (gravados em ${dateTime(data.dataSource.savedAt)}, `
      + "horário de Brasília). A situação atual pode ser diferente. Clique em Atualizar dados para tentar de novo.";
  }

  const period = plural(params.days, "dia", "dias");
  const low = summary.lowConfidenceFires;
  el("main-metrics").replaceChildren(
    metric(`Focos em até ${params.radiusKm} km`, number(summary.totalFires, 0),
      `Detecções de calor por satélite nos últimos ${period}, com confiança normal ou alta.`),
    metric(`Focos em ${city.nome}`, number(summary.firesInMunicipality, 0),
      "Detecções dentro do limite oficial do município."),
    metric("Foco mais próximo", summary.nearestFireKm == null ? "Nenhum" : number(summary.nearestFireKm) + " km",
      "Distância em linha reta do centro da cidade ao foco mais próximo."),
    metric("Dias seguidos sem chuva", number(summary.dryDays, 0),
      "Dias consecutivos, até hoje, com menos de 1 mm de chuva."),
  );

  el("low-note").hidden = low === 0;
  el("low-note").textContent = `Além destes, ${plural(low, "foco de baixa confiança foi detectado", "focos de baixa confiança foram detectados")}. `
    + "Eles aparecem no mapa e na tabela, mas não entram no índice por terem mais chance de ser alarme falso.";

  el("no-fires").hidden = summary.totalFires > 0;
  el("no-fires").textContent = (low > 0
    ? `Nenhum foco de confiança normal ou alta em até ${params.radiusKm} km nos últimos ${period}. `
    : `Nenhum foco detectado em até ${params.radiusKm} km nos últimos ${period}. `)
    + "O índice reflete apenas o clima.";
  el("saturated").hidden = summary.fireScore < 1;

  el("biome-note").textContent = `Bioma ${summary.biome}: multiplicador ${number(summary.biomeWeight)}×`;
}

function renderClimate(data) {
  const { summary } = data;
  el("climate-metrics").replaceChildren(
    metric("Temperatura máxima média", number(summary.avgTemp) + " °C"),
    metric("Chuva média por dia", number(summary.avgRain) + " mm"),
    metric("Maior vento", number(summary.maxWind) + " km/h"),
    metric("Menor umidade do ar", number(summary.minHumidity, 0) + " %",
      "Valor mais baixo de umidade relativa nos últimos 30 dias."),
  );
  renderClimateChart(data.climate);
}

function sortedFires(data, onlyCity) {
  return data.fires
    .filter((f) => !onlyCity || f.insideMunicipality)
    .sort((a, b) => b.acqDate.localeCompare(a.acqDate) || a.distanceKm - b.distanceKm);
}

export function renderFiresTable(data) {
  const fires = sortedFires(data, el("only-city").checked);
  const head = document.createElement("thead");
  head.append(row("th", COLUMNS.map((c) => [c.title, c.numeric])));
  const body = document.createElement("tbody");
  body.append(...fires.slice(0, MAX_TABLE_ROWS)
    .map((fire) => row("td", COLUMNS.map((c) => [c.value(fire), c.numeric]))));
  el("fires-table").replaceChildren(head, body);

  el("table-note").hidden = fires.length <= MAX_TABLE_ROWS;
  el("table-note").textContent = `Exibindo ${number(MAX_TABLE_ROWS, 0)} de ${number(fires.length, 0)} focos. `
    + "O arquivo CSV contém todos.";
}

function renderFires(data) {
  const { summary, municipality: city, params } = data;
  el("tab-fires").textContent = `Focos detectados (${number(data.fires.length, 0)})`;

  const empty = data.fires.length === 0;
  el("fires-content").hidden = empty;
  el("fires-empty").hidden = !empty;
  el("fires-empty").textContent = `Nenhum foco detectado em até ${params.radiusKm} km nos últimos `
    + `${plural(params.days, "dia", "dias")}.`;

  el("only-city").checked = false;
  el("only-city").disabled = summary.firesInMunicipality === 0;
  el("only-city-label").textContent = `Mostrar apenas focos dentro de ${city.nome}`;

  if (!empty) {
    renderFiresChart(data);
    renderFiresTable(data);
  }
}

function renderMethod(data) {
  const { summary: s, params } = data;
  const firePart = s.fireScore * 0.6;
  const climatePart = s.climateScore * 0.4;
  const head = document.createElement("thead");
  head.append(row("th", [["Componente"], ["Valor"], ["Contribuição"]]));
  const body = document.createElement("tbody");
  body.append(
    row("td", [[`Focos (${number(s.totalFires, 0)} em até ${params.radiusKm} km)`], [number(s.fireScore, 3)], [number(firePart, 3)]]),
    row("td", [["Focos de baixa confiança"], [number(s.lowConfidenceFires, 0)], ["fora do cálculo"]]),
    row("td", [[`Clima (${number(s.avgTemp)} °C · ${number(s.avgRain)} mm/dia)`], [number(s.climateScore, 3)], [number(climatePart, 3)]]),
    row("td", [[`Peso do bioma (${s.biome})`], [number(s.biomeWeight) + "×"], ["—"]]),
    row("td", [["Índice"], [number(s.riskIndex, 2) + " / 10"],
      [`(${number(firePart, 3)} + ${number(climatePart, 3)}) × ${number(s.biomeWeight)} × 10`]]),
  );
  el("method-table").replaceChildren(head, body);
}

const SOURCES = { live: "Ao vivo", snapshot: "Dados salvos", backfill: "Recalculado" };
const MAX_HISTORY_ROWS = 20;

function showHistoryStatus(text) {
  el("history-content").hidden = text !== null;
  el("history-status").hidden = text === null;
  el("history-status").textContent = text || "";
}

/** Gráfico com os filtros atuais (um ponto por dia, o mais recente) e tabela com todas as consultas. */
export function renderHistory(data, entries) {
  const { params, municipality: city } = data;
  if (entries.length === 0) {
    showHistoryStatus(`Ainda não há histórico gravado para ${city.nome}.`);
    return;
  }
  showHistoryStatus(null);

  const byDay = new Map();
  entries
    .filter((e) => e.days === params.days && e.radiusKm === params.radiusKm)
    .forEach((e) => { if (!byDay.has(e.referenceDate)) byDay.set(e.referenceDate, e); });
  const points = [...byDay.values()].sort((a, b) => a.referenceDate.localeCompare(b.referenceDate));

  el("chart-history").hidden = points.length === 0;
  if (points.length > 0) renderHistoryChart(points, params);

  const head = document.createElement("thead");
  head.append(row("th", [["Consultado em"], ["Dados até"], ["Período"], ["Distância"], ["Índice", true],
    ["Nível"], ["Focos", true], ["Fonte"]]));
  const body = document.createElement("tbody");
  body.append(...entries.slice(0, MAX_HISTORY_ROWS).map((e) => row("td", [
    [dateTime(e.analyzedAt)], [date(e.referenceDate)], [plural(e.days, "dia", "dias")], [e.radiusKm + " km"],
    [number(e.riskIndex, 2), true], [e.level.label], [number(e.firesCounted, 0), true],
    [SOURCES[e.dataSource] || e.dataSource],
  ])));
  el("history-table").replaceChildren(head, body);
}

export function renderHistoryError(message) {
  showHistoryStatus(message);
}

/** A seção precisa estar visível antes: Leaflet e Plotly medem o container. */
export function renderResults(data) {
  el("results").hidden = false;
  renderSummary(data);
  renderMap(data);
  renderGauge(data.summary);
  renderClimate(data);
  renderFires(data);
  renderMethod(data);
}

/** CSV com ";" e vírgula decimal, que abre em colunas no Excel em português. */
export function downloadCsv(data) {
  const escape = (text) => (/[;"\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text);
  const fires = sortedFires(data, el("only-city").checked);
  const lines = [COLUMNS.map((c) => c.title).join(";")];
  fires.forEach((fire) => {
    lines.push(COLUMNS.map((c) => escape(String(c.value(fire)).replace(/\./g, ""))).join(";"));
  });

  const blob = new Blob(["﻿" + lines.join("\r\n")], { type: "text/csv;charset=utf-8" });
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = `focos_${data.municipality.nome.replace(/\s+/g, "_")}_${data.municipality.uf}.csv`;
  link.click();
  URL.revokeObjectURL(link.href);
}
