import { normalize } from "./format.js";

const MAX_OPTIONS = 50;

/**
 * Campo de texto com autocompletar acessível por teclado.
 * Aceita texto livre: o servidor valida e sugere nomes parecidos.
 */
export function createCombobox(input, list, { onChange, onSubmit }) {
  let names = [];
  let visible = [];
  let active = -1;

  function close() {
    list.hidden = true;
    active = -1;
    input.setAttribute("aria-expanded", "false");
    input.removeAttribute("aria-activedescendant");
  }

  function open() {
    const query = normalize(input.value);
    const starts = [];
    const contains = [];
    for (const name of names) {
      const normalized = normalize(name);
      if (normalized.startsWith(query)) starts.push(name);
      else if (normalized.includes(query)) contains.push(name);
    }
    visible = starts.concat(contains).slice(0, MAX_OPTIONS);
    active = -1;

    list.replaceChildren(...visible.map((name, index) => {
      const item = document.createElement("li");
      item.id = "city-option-" + index;
      item.role = "option";
      item.textContent = name;
      // mousedown vem antes do blur do campo, que fecharia a lista
      item.addEventListener("mousedown", (event) => {
        event.preventDefault();
        choose(name);
      });
      return item;
    }));

    list.hidden = visible.length === 0;
    input.setAttribute("aria-expanded", String(!list.hidden));
  }

  function choose(name) {
    input.value = name;
    close();
    onChange();
  }

  function highlight(index) {
    list.children[active]?.classList.remove("active");
    active = index;
    const item = list.children[active];
    item.classList.add("active");
    item.scrollIntoView({ block: "nearest" });
    input.setAttribute("aria-activedescendant", item.id);
  }

  input.addEventListener("input", () => {
    open();
    onChange();
  });
  input.addEventListener("focus", () => input.select());
  input.addEventListener("click", open);
  input.addEventListener("blur", close);

  input.addEventListener("keydown", (event) => {
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      if (list.hidden) open();
      if (visible.length === 0) return;
      const step = event.key === "ArrowDown" ? 1 : -1;
      highlight((active + step + visible.length) % visible.length);
    } else if (event.key === "Enter") {
      event.preventDefault();
      if (!list.hidden && active >= 0) choose(visible[active]);
      else {
        close();
        onSubmit();
      }
    } else if (event.key === "Escape") {
      close();
    }
  });

  return {
    get value() { return input.value.trim(); },
    set value(text) { input.value = text; close(); },
    setNames(newNames) { names = newNames; },
  };
}
