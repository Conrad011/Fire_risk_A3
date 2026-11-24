/** Formatação no padrão brasileiro e comparação de nomes. */

export function number(value, decimals = 1) {
  if (value === null || value === undefined || Number.isNaN(value)) return "—";
  return Number(value).toLocaleString("pt-BR", {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  });
}

export function plural(count, singular, pluralForm) {
  return number(count, 0) + " " + (count === 1 ? singular : pluralForm);
}

/** Converte "2026-11-23" em "23/11/2026" sem passar por fuso horário. */
export function date(iso) {
  const [year, month, day] = iso.split("-");
  return `${day}/${month}/${year}`;
}

/**
 * "2026-11-20T22:30" (já em Brasília) vira "22:30". Lê o texto em vez de usar Date,
 * que converteria para o fuso do computador de quem abre a página.
 */
export function time(isoLocal) {
  return isoLocal ? isoLocal.slice(11, 16) : "—";
}

/** Mantém o horário de Brasília enviado pelo servidor. */
export function dateTime(iso) {
  const [day, time] = iso.split("T");
  return `${date(day)} às ${time.slice(0, 5)}`;
}

/** Mesma regra do FireRecord.lowConfidence() do backend: não entra no índice. */
export function isLowConfidence(fire) {
  return (fire.confidence || "").toLowerCase() === "l";
}

/** Mesma regra do TextMatcher do backend. */
export function normalize(text) {
  return (text || "")
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .toLowerCase()
    .replace(/[-'’]/g, " ")
    .trim()
    .replace(/\s+/g, " ");
}
