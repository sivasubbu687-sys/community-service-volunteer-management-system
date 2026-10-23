// Adds an eye button to every password box on the page (login, register, reset, change password ...).
// Open eye = "click to show the password", crossed-out eye = "click to hide it".
// Shared by login.html, reset-password.html and index.html.
const SVG_NS = "http://www.w3.org/2000/svg";

// Builds one icon with DOM calls (no innerHTML, so it works with the Content-Security-Policy)
function eyeIcon(crossedOut) {
  const svg = document.createElementNS(SVG_NS, "svg");
  const attrs = { viewBox: "0 0 24 24", width: "20", height: "20", fill: "none", stroke: "currentColor",
    "stroke-width": "2", "stroke-linecap": "round", "stroke-linejoin": "round", "aria-hidden": "true" };
  Object.entries(attrs).forEach(([name, value]) => svg.setAttribute(name, value));

  const add = (tag, shape) => {
    const el = document.createElementNS(SVG_NS, tag);
    Object.entries(shape).forEach(([name, value]) => el.setAttribute(name, value));
    svg.appendChild(el);
  };
  if (crossedOut) {
    add("path", { d: "M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" });
    add("line", { x1: "1", y1: "1", x2: "23", y2: "23" });
  } else {
    add("path", { d: "M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" });
    add("circle", { cx: "12", cy: "12", r: "3" });
  }
  return svg;
}

document.querySelectorAll('input[type="password"]').forEach((input) => {
  // put the box and its button side by side
  const wrap = document.createElement("div");
  wrap.className = "password-wrap";
  input.parentNode.insertBefore(wrap, input);
  wrap.appendChild(input);

  const button = document.createElement("button");
  button.type = "button";             // never submits the form
  button.className = "pw-toggle";
  wrap.appendChild(button);

  const setVisible = (visible) => {
    input.type = visible ? "text" : "password";
    button.replaceChildren(eyeIcon(visible));
    const label = visible ? "Hide password" : "Show password";
    button.setAttribute("aria-label", label);
    button.title = label;
  };
  setVisible(false);

  button.addEventListener("click", () => {
    setVisible(input.type === "password");
    input.focus();
  });

  // Hide the password again whenever its dialog or form is closed
  const container = input.closest(".overlay, form");
  if (container) {
    new MutationObserver(() => {
      if (container.classList.contains("hidden")) setVisible(false);
    }).observe(container, { attributes: true, attributeFilter: ["class"] });
  }
});
