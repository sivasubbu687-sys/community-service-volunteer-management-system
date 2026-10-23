// Settings page (admin): manage the event categories shown in the event form.
// Relies on globals from script.js: categories, api, CATEGORIES_URL, loadCategories, loadEvents, showMessage, showError, $
function renderCategorySettings() {
  const list = $("category-list");
  if (!list) return;
  list.replaceChildren();
  if (!categories.length) {
    const li = document.createElement("li");
    li.className = "muted";
    li.textContent = "No categories yet. Add one above.";
    list.appendChild(li);
    return;
  }
  categories.forEach((c) => list.appendChild(categoryRow(c)));
}

function categoryRow(c) {
  const li = document.createElement("li");
  const swatch = document.createElement("input");
  swatch.type = "color";
  swatch.value = c.color || "#2F6FED";
  swatch.title = "Chart colour";
  swatch.setAttribute("aria-label", "Chart colour for " + c.name);
  swatch.addEventListener("change", async () => {
    try {
      await api(CATEGORIES_URL + "/" + c.id, { method: "PUT", body: JSON.stringify({ name: c.name, color: swatch.value }) });
      showMessage("Colour updated.", "success");
      await loadCategories();
    } catch (e) {
      showError(e.message);
    }
  });
  const name = document.createElement("span");
  name.className = "cat-name";
  name.textContent = c.name;
  const rename = document.createElement("button");
  rename.type = "button";
  rename.className = "btn";
  rename.textContent = "Rename";
  const remove = document.createElement("button");
  remove.type = "button";
  remove.className = "btn btn-danger";
  remove.textContent = "Delete";
  li.append(swatch, name, rename, remove);

  rename.addEventListener("click", () => {
    const input = document.createElement("input");
    input.type = "text";
    input.maxLength = 60;
    input.value = c.name;
    const save = document.createElement("button");
    save.type = "button";
    save.className = "btn btn-primary";
    save.textContent = "Save";
    const cancel = document.createElement("button");
    cancel.type = "button";
    cancel.className = "btn";
    cancel.textContent = "Cancel";
    li.replaceChildren(input, save, cancel);
    input.focus();
    const submit = async () => {
      try {
        await api(CATEGORIES_URL + "/" + c.id, { method: "PUT", body: JSON.stringify({ name: input.value, color: c.color }) });
        showMessage("Category renamed.", "success");
        await loadCategories();
        if (typeof loadEvents === "function") loadEvents();
      } catch (e) {
        showError(e.message);
      }
    };
    save.addEventListener("click", submit);
    input.addEventListener("keydown", (ev) => { if (ev.key === "Enter") { ev.preventDefault(); submit(); } });
    cancel.addEventListener("click", renderCategorySettings);
  });

  remove.addEventListener("click", async () => {
    if (!window.confirm('Delete the category "' + c.name + '"?')) return;
    try {
      await api(CATEGORIES_URL + "/" + c.id, { method: "DELETE" });
      showMessage("Category deleted.", "success");
      await loadCategories();
    } catch (e) {
      showError(e.message);
    }
  });
  return li;
}

if ($("category-form")) {
  $("category-form").addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const input = $("category-name");
    if (!input.value.trim()) { showError("Enter a category name."); return; }
    try {
      await api(CATEGORIES_URL, { method: "POST", body: JSON.stringify({ name: input.value, color: $("category-color").value }) });
      input.value = "";
      showMessage("Category added.", "success");
      await loadCategories();
    } catch (e) {
      showError(e.message);
    }
  });
  $("settings-pw-btn").addEventListener("click", () => $("pw-btn").click());
}
