/**
 * Controlador do dashboard: estado da tela, filtros, histórico e fluxo de análise.
 */
import { ApiError, fetchMunicipalities, fetchRisk, fetchStates, refreshData } from "./api.js";
import { resizeCharts } from "./charts.js";
import { createCombobox } from "./combobox.js";
import { normalize, plural } from "./format.js";
import { downloadCsv, renderFiresTable, renderResults } from "./view.js";

const DEFAULTS = { city: "Sete Lagoas", uf: "MG", days: 5, radiusKm: 100 };
const MAX_RECENT = 4;

const el = (id) => document.getElementById(id);

const state = {
  current: null,
  previous: null,
  error: null,
  recent: [],
  controller: null,
};

const cityBox = createCombobox(el("city"), el("city-options"), {
  onChange: refreshControls,
  onSubmit: () => submit(),
});

// ── Filtros ──────────────────────────────────────────────────────────────────

function readForm() {
  return {
    city: cityBox.value,
    uf: el("uf").value,
    days: Number(el("days").value),
    radiusKm: Number(el("radius").value),
  };
}

function updateSliderLabels() {
  el("days-value").textContent = plural(Number(el("days").value), "dia", "dias");
  el("radius-value").textContent = el("radius").value + " km";
}

async function loadCities(uf) {
  cityBox.setNames([]);
  try {
    const names = await fetchMunicipalities(uf);
    if (el("uf").value === uf) cityBox.setNames(names);
  } catch {
    // Sem a lista o campo segue aceitando texto livre, validado na análise
  }
}

async function applyForm(params) {
  el("uf").value = params.uf;
  cityBox.value = params.city;
  el("days").value = params.days;
  el("radius").value = params.radiusKm;
  updateSliderLabels();
  await loadCities(params.uf);
}

function sameParams(a, b) {
  return a.uf === b.uf && a.days === b.days && a.radiusKm === b.radiusKm
    && Boolean(a.city) && normalize(a.city) === normalize(b.city);
}

function paramsFromUrl() {
  const query = new URLSearchParams(location.search);
  const uf = (query.get("uf") || "").toUpperCase();
  const city = (query.get("cidade") || "").trim();
  if (!city || !Array.from(el("uf").options).some((o) => o.value === uf)) return { ...DEFAULTS };

  const clamp = (value, min, max, fallback) =>
    (Number.isInteger(value) ? Math.min(Math.max(value, min), max) : fallback);
  const days = clamp(Number(query.get("dias")), 1, 5, DEFAULTS.days);
  const radius = clamp(Number(query.get("raio")), 25, 500, DEFAULTS.radiusKm);
  return { city, uf, days, radiusKm: radius - (radius % 25) };
}

function syncUrl(params) {
  const query = new URLSearchParams({
    cidade: params.city, uf: params.uf, dias: params.days, raio: params.radiusKm,
  });
  history.replaceState(null, "", "?" + query);
}

// ── Análise ──────────────────────────────────────────────────────────────────

function setLoading(params) {
  const loading = params !== null;
  el("loading").hidden = !loading;
  el("analyze").disabled = loading;
  el("refresh").disabled = loading;
  el("cancel").hidden = !loading;
  if (loading) {
    el("loading-text").textContent = `Analisando ${params.city} / ${params.uf}: `
      + "consultando IBGE, Open-Meteo e NASA FIRMS...";
  }
}

async function analyze(params) {
  state.controller?.abort();
  const controller = new AbortController();
  state.controller = controller;
  state.error = null;
  setLoading(params);
  refreshControls();

  try {
    const data = await fetchRisk(params, controller.signal);
    if (state.current && !sameParams(state.current.params, data.params)) {
      state.previous = state.current;
    }
    state.current = data;
    remember(data.params);
    syncUrl(data.params);
    setLoading(null);
    renderResults(data);
    activeTabResize();
  } catch (e) {
    if (e.name === "AbortError") return;
    state.error = e instanceof ApiError
      ? { title: e.message, hint: e.hint, suggestions: e.suggestions, params }
      : { title: "Ocorreu um erro ao exibir os dados.", hint: "Tente novamente.", suggestions: [], params };
  } finally {
    if (state.controller === controller) {
      state.controller = null;
      setLoading(null);
      refreshControls();
    }
  }
}

function submit() {
  const params = readForm();
  if (!params.city) {
    el("city").focus();
    return;
  }
  analyze(params);
}

function remember(params) {
  const place = { city: params.city, uf: params.uf };
  state.recent = [place, ...state.recent.filter((p) => p.city !== place.city || p.uf !== place.uf)]
    .slice(0, MAX_RECENT + 1);
}

async function runWith(params) {
  await applyForm(params);
  analyze(params);
}

async function undo() {
  [state.current, state.previous] = [state.previous, state.current];
  state.error = null;
  await applyForm(state.current.params);
  syncUrl(state.current.params);
  renderResults(state.current);
  activeTabResize();
  refreshControls();
}

// ── Atualização da tela ──────────────────────────────────────────────────────

function renderError() {
  const error = state.error;
  el("error").hidden = !error;
  if (!error) return;

  el("error-title").textContent = error.title;
  el("error-hint").textContent = error.hint || "";
  el("suggestions-label").hidden = error.suggestions.length === 0;
  el("suggestions").replaceChildren(...error.suggestions.map((name) => {
    const button = document.createElement("button");
    button.type = "button";
    button.textContent = name;
    button.addEventListener("click", () => runWith({ ...error.params, city: name }));
    return button;
  }));
  el("retry").hidden = error.suggestions.length > 0;
  el("error-keep").hidden = !state.current;
  if (state.current) {
    const { city, uf } = state.current.params;
    el("error-keep").textContent = `Os resultados abaixo continuam sendo de ${city} / ${uf}.`;
  }
}

function renderStale(form) {
  const current = state.current;
  const stale = current && !state.error && !state.controller && form.city && !sameParams(form, current.params);
  el("stale").hidden = !stale;
  if (stale) {
    const { city, uf, days, radiusKm } = current.params;
    el("stale").textContent = `Você alterou os filtros. Os resultados abaixo ainda são de ${city} / ${uf}, `
      + `${plural(days, "dia", "dias")} e ${radiusKm} km. Clique em Analisar para atualizar.`;
  }
}

function renderShortcuts() {
  const current = state.current?.params;
  const others = state.recent
    .filter((p) => !current || p.city !== current.city || p.uf !== current.uf)
    .slice(0, MAX_RECENT);
  el("recent-block").hidden = others.length === 0;
  el("recent").replaceChildren(...others.map((place) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "secondary";
    button.textContent = `${place.city} / ${place.uf}`;
    button.addEventListener("click", () => {
      const form = readForm();
      runWith({ ...place, days: form.days, radiusKm: form.radiusKm });
    });
    return button;
  }));

  el("undo").hidden = !state.previous;
  if (state.previous) {
    const { city, uf } = state.previous.params;
    el("undo").textContent = `↩ Voltar para ${city} / ${uf}`;
  }
}

function refreshControls() {
  const form = readForm();
  if (state.error && !sameParams(form, state.error.params)) state.error = null;
  el("analyze").disabled = Boolean(state.controller) || !form.city;
  el("analyze").title = form.city ? "" : "Escolha uma cidade para continuar.";
  renderError();
  renderStale(form);
  renderShortcuts();
}

// ── Abas ─────────────────────────────────────────────────────────────────────

const tabs = Array.from(document.querySelectorAll('[role="tab"]'));

function selectTab(tab) {
  tabs.forEach((other) => {
    const selected = other === tab;
    other.setAttribute("aria-selected", String(selected));
    other.tabIndex = selected ? 0 : -1;
    el(other.getAttribute("aria-controls")).hidden = !selected;
  });
  activeTabResize();
}

function activeTabResize() {
  const active = tabs.find((t) => t.getAttribute("aria-selected") === "true");
  resizeCharts(el(active.getAttribute("aria-controls")));
}

tabs.forEach((tab, index) => {
  tab.addEventListener("click", () => selectTab(tab));
  tab.addEventListener("keydown", (event) => {
    if (event.key !== "ArrowRight" && event.key !== "ArrowLeft") return;
    const step = event.key === "ArrowRight" ? 1 : -1;
    const next = tabs[(index + step + tabs.length) % tabs.length];
    next.focus();
    selectTab(next);
  });
});

// ── Eventos ──────────────────────────────────────────────────────────────────

el("filters").addEventListener("submit", (event) => {
  event.preventDefault();
  submit();
});

el("uf").addEventListener("change", () => {
  cityBox.value = "";
  loadCities(el("uf").value);
  refreshControls();
  el("city").focus();
});

["days", "radius"].forEach((id) => el(id).addEventListener("input", () => {
  updateSliderLabels();
  refreshControls();
}));

el("cancel").addEventListener("click", () => {
  const controller = state.controller;
  state.controller = null;
  controller?.abort();
  setLoading(null);
  refreshControls();
});

el("reset").addEventListener("click", () => runWith(DEFAULTS));
el("undo").addEventListener("click", undo);
el("retry").addEventListener("click", () => analyze(state.error.params));
el("only-city").addEventListener("change", () => renderFiresTable(state.current));
el("download").addEventListener("click", () => downloadCsv(state.current));

el("refresh").addEventListener("click", async () => {
  const params = state.current.params;
  try {
    await refreshData();
  } catch {
    // Sem limpar o cache a análise ainda funciona, só com os dados em memória
  }
  await applyForm(params);
  analyze(params);
});

// ── Inicialização ────────────────────────────────────────────────────────────

async function init() {
  try {
    const states = await fetchStates();
    el("uf").replaceChildren(...states.map((item) => new Option(`${item.code} — ${item.label}`, item.code)));
  } catch (e) {
    state.error = { title: e.message, hint: e.hint, suggestions: [], params: { ...DEFAULTS } };
    el("error-title").textContent = e.message;
    el("error-hint").textContent = e.hint || "";
    el("error").hidden = false;
    el("retry").hidden = true;
    return;
  }
  await runWith(paramsFromUrl());
}

init();
