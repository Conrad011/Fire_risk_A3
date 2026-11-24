import { isLowConfidence } from "./format.js";

const LAYOUT = {
  paper_bgcolor: "rgba(0,0,0,0)",
  plot_bgcolor: "rgba(0,0,0,0)",
  font: { color: "#e6edf3" },
  separators: ",.",
};

const CONFIG = { displayModeBar: false, responsive: true };

export const RISK_COLORS = { low: "#44cc44", moderate: "#ffaa00", high: "#ff6b6b" };

export function renderGauge(summary) {
  Plotly.react("gauge", [{
    type: "indicator",
    mode: "gauge+number",
    value: summary.riskIndex,
    number: { suffix: " / 10", font: { size: 26 }, valueformat: ".2f" },
    gauge: {
      axis: { range: [0, 10], tickvals: [0, 3, 6, 10], tickwidth: 1, tickcolor: "white" },
      bar: { color: RISK_COLORS[summary.level.code] },
      steps: [
        { range: [0, 3], color: "#1a3a1a" },
        { range: [3, 6], color: "#3a2a00" },
        { range: [6, 10], color: "#3a0000" },
      ],
    },
  }], {
    ...LAYOUT,
    height: 230,
    // Margem folgada: o painel é estreito e os rótulos 0 e 10 seriam cortados
    margin: { t: 25, b: 10, l: 40, r: 40 },
  }, CONFIG);
}

export function renderClimateChart(climate) {
  const dates = climate.map((d) => d.date);

  Plotly.react("chart-climate", [
    {
      type: "scatter",
      x: dates,
      y: climate.map((d) => d.temperatureMax),
      name: "Temperatura máxima (°C)",
      line: { color: "#ff4444", width: 2 },
      fill: "tozeroy",
      fillcolor: "rgba(255,68,68,0.1)",
      hovertemplate: "%{x|%d/%m/%Y}<br>%{y:.1f} °C<extra></extra>",
    },
    {
      type: "bar",
      x: dates,
      y: climate.map((d) => d.precipitation),
      name: "Chuva (mm)",
      marker: { color: "rgba(68,136,255,0.55)" },
      yaxis: "y2",
      hovertemplate: "%{x|%d/%m/%Y}<br>%{y:.1f} mm<extra></extra>",
    },
  ], {
    ...LAYOUT,
    height: 340,
    margin: { t: 16, b: 60, l: 50, r: 50 },
    legend: { orientation: "h", y: -0.2 },
    xaxis: { tickformat: "%d/%m", gridcolor: "#2a313c" },
    yaxis: { title: "°C", gridcolor: "#2a313c", rangemode: "tozero" },
    yaxis2: { title: "mm", overlaying: "y", side: "right", showgrid: false, rangemode: "tozero" },
  }, CONFIG);
}

/** Mostra só os dias do período pesquisado, inclusive os sem foco, e só os focos que entram no índice. */
export function renderFiresChart(data) {
  const counts = new Map();
  data.fires
    .filter((f) => !isLowConfidence(f))
    .forEach((f) => counts.set(f.acqDate, (counts.get(f.acqDate) || 0) + 1));

  const candidates = [...counts.keys(), ...data.daily.map((d) => d.date)].sort();
  const end = new Date(candidates[candidates.length - 1] + "T00:00:00Z");
  const days = [];
  for (let i = data.params.days - 1; i >= 0; i--) {
    days.push(new Date(end.getTime() - i * 86400000).toISOString().slice(0, 10));
  }

  Plotly.react("chart-fires", [{
    type: "bar",
    x: days,
    y: days.map((d) => counts.get(d) || 0),
    marker: { color: "#ff4444" },
    hovertemplate: "%{x|%d/%m/%Y}<br>%{y} focos<extra></extra>",
  }], {
    ...LAYOUT,
    title: { text: `Focos por dia em até ${data.params.radiusKm} km (confiança normal ou alta)`, font: { size: 14 } },
    height: 270,
    margin: { t: 40, b: 36, l: 50, r: 20 },
    xaxis: { type: "date", tickformat: "%d/%m", tickvals: days, gridcolor: "#2a313c" },
    yaxis: { title: "Focos", gridcolor: "#2a313c", tickformat: "d", rangemode: "tozero" },
  }, CONFIG);
}

/** Gráficos desenhados em aba oculta precisam medir o container ao aparecer. */
export function resizeCharts(panel) {
  panel.querySelectorAll(".js-plotly-plot").forEach((chart) => Plotly.Plots.resize(chart));
}
