/** Cliente da API REST do FireRisk. */

export class ApiError extends Error {
  constructor(title, hint, suggestions = []) {
    super(title);
    this.hint = hint;
    this.suggestions = suggestions;
  }
}

async function request(path, options) {
  let response;
  try {
    response = await fetch(path, options);
  } catch (e) {
    if (e.name === "AbortError") throw e;
    throw new ApiError("Não foi possível falar com o servidor do FireRisk.",
      "Confira se o sistema continua em execução e tente novamente.");
  }

  if (response.status === 204) return null;

  let payload = null;
  try {
    payload = await response.json();
  } catch (e) {
    if (e.name === "AbortError") throw e;
  }
  if (!response.ok || payload === null) {
    throw new ApiError(payload?.error || "Ocorreu um erro inesperado.",
      payload?.hint || "Tente novamente em alguns instantes.", payload?.suggestions || []);
  }
  return payload;
}

export const fetchStates = () => request("/api/states");

export const fetchMunicipalities = (uf) =>
  request("/api/municipalities?" + new URLSearchParams({ uf }));

export const fetchRisk = (params, signal) =>
  request("/api/risk?" + new URLSearchParams({
    city: params.city, uf: params.uf, days: params.days, radius: params.radiusKm,
  }), { signal });

export const refreshData = () => request("/api/refresh", { method: "POST" });
