// Home page for logged-in VOLUNTEERS (navbar, hero, impact numbers, event cards, footer).
// It is loaded before script.js and uses its shared values (events, myAssignments, joinEvent ...) when called.


// Small line icons (same style as the design). Built with createElementNS, so no HTML strings are involved.
const V_ICONS = {
  calendar: ['<rect x="3" y="4" width="18" height="18" rx="2"/>', '<path d="M16 2v4M8 2v4M3 10h18"/>'],
  pin: ['<path d="M21 10c0 7-9 13-9 13S3 17 3 10a9 9 0 0118 0z"/>', '<circle cx="12" cy="10" r="3"/>'],
  users: ['<path d="M17 21v-2a4 4 0 00-4-4H5a4 4 0 00-4 4v2"/>', '<circle cx="9" cy="7" r="4"/>', '<path d="M23 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75"/>'],
  arrow: ['<path d="M5 12h14M13 5l7 7-7 7"/>'],
};

function vIcon(name) {
  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  svg.setAttribute("viewBox", "0 0 24 24");
  svg.setAttribute("class", "v-icon");
  svg.setAttribute("aria-hidden", "true");
  // the shapes above are fixed strings written in this file (never user data)
  svg.innerHTML = V_ICONS[name].join("");
  return svg;
}

function vFormatDate(ev) {
  const d = new Date(ev.eventDate + "T" + (ev.startTime || "00:00"));
  const date = d.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
  const time = d.toLocaleTimeString("en-US", { hour: "numeric", minute: "2-digit" });
  return date + " · " + time;
}

function vMeta(icon, text) {
  const row = document.createElement("div");
  row.className = "v-meta";
  const span = document.createElement("span");
  span.textContent = text;            // textContent: event text can never inject HTML
  row.append(vIcon(icon), span);
  return row;
}

// The four next events as cards. Called again whenever events, spots or my assignments change.
function renderHomeEvents() {
  const box = document.getElementById("v-cards");
  if (!box) return;
  box.replaceChildren();

  const upcoming = events
    .filter((e) => e.status === "UPCOMING" || e.status === "ONGOING")
    .sort((a, b) => (a.eventDate + a.startTime).localeCompare(b.eventDate + b.startTime))
    .slice(0, 4);

  if (upcoming.length === 0) {
    const empty = document.createElement("div");
    empty.className = "v-empty";
    empty.textContent = "No upcoming events right now. Please check back soon.";
    box.appendChild(empty);
    return;
  }

  upcoming.forEach((ev) => {
    const mine = myAssignments.find((a) => a.eventId === ev.id && a.status !== "CANCELLED");
    const taken = activeCount(ev.id);
    const full = taken >= ev.requiredVolunteers;

    const card = document.createElement("article");
    card.className = "v-card";

    const media = document.createElement("div");
    media.className = "v-card-media";
    media.appendChild(makeEventThumb(ev.id, ev.title, "v-card-img"));
    const badge = document.createElement("span");
    const category = ev.category || "Community";
    let hash = 0;
    for (const ch of category) hash = (hash * 31 + ch.charCodeAt(0)) % 997;
    badge.className = "v-badge v-badge-c" + (hash % 5);
    badge.textContent = category;
    media.appendChild(badge);
    if (mine) {
      const chip = document.createElement("span");
      chip.className = "v-joined-chip";
      chip.textContent = "✓ You're in";
      media.appendChild(chip);
    }

    const title = document.createElement("h3");
    title.textContent = ev.title;

    const button = document.createElement("button");
    button.type = "button";
    if (mine) {
      button.className = "v-btn v-btn-line";
      button.textContent = mine.checkInTime ? "Checked in" : "Leave event";
      button.disabled = Boolean(mine.checkInTime);
      button.addEventListener("click", () => leaveEvent(ev));
    } else if (full) {
      button.className = "v-btn v-btn-line";
      button.textContent = "Event full";
      button.disabled = true;
    } else {
      button.className = "v-btn v-btn-fill";
      button.append("Join Now ", vIcon("arrow"));
      button.addEventListener("click", () => joinEvent(ev));
    }

    card.append(
      media, title,
      vMeta("calendar", vFormatDate(ev)),
      vMeta("pin", ev.location),
      vMeta("users", taken + "/" + ev.requiredVolunteers + " slots"),
      button
    );
    box.appendChild(card);
  });
}

// Real totals from the database
async function loadHomeStats() {
  try {
    const s = await api("/api/stats/overview");
    const fmt = (n) => Number(n).toLocaleString("en-US", { maximumFractionDigits: 1 });
    document.getElementById("vs-volunteers").textContent = fmt(s.activeVolunteers);
    document.getElementById("vs-events").textContent = fmt(s.communityEvents);
    document.getElementById("vs-hours").textContent = fmt(s.hoursContributed);
    document.getElementById("vs-shifts").textContent = fmt(s.shiftsCompleted);
  } catch (e) { /* the numbers stay as dashes */ }
}

// Message bar for the home page (the normal message box lives in a part of the page that is hidden here)
let vToastTimer;
function vToast(text, type) {
  const home = document.getElementById("volunteer-home");
  const toast = document.getElementById("v-toast");
  if (!toast || home.classList.contains("hidden")) return;
  toast.textContent = text;
  toast.className = "v-toast " + type;
  clearTimeout(vToastTimer);
  vToastTimer = setTimeout(vToastHide, type === "success" ? 4000 : 7000);
}
function vToastHide() {
  const toast = document.getElementById("v-toast");
  if (toast) toast.className = "v-toast hidden";
}

// Highlights the navbar link of the part of the page being looked at
function vSetActive(sectionId) {
  document.querySelectorAll(".v-link").forEach((b) => b.classList.toggle("active", b.dataset.target === sectionId));
}

function initVolunteerHome() {
  document.body.classList.add("role-volunteer");
  document.getElementById("volunteer-nav").classList.remove("hidden");
  document.getElementById("v-footer").classList.remove("hidden");

  // navbar + footer links scroll to a part of the home page (opening it first if another page is showing)
  document.querySelectorAll("[data-target]").forEach((el) => {
    el.addEventListener("click", () => {
      showView("dashboard");
      const target = document.getElementById(el.dataset.target);
      if (target) target.scrollIntoView({ behavior: "smooth", block: "start" });
      vSetActive(el.dataset.target);
    });
  });
  document.querySelectorAll("[data-vview]").forEach((el) =>
    el.addEventListener("click", () => { showView(el.dataset.vview); window.scrollTo({ top: 0 }); })
  );
  document.getElementById("v-logout").addEventListener("click", () => goToLogin());

  // scroll spy
  const sections = ["v-hero", "v-impact", "v-events", "v-why"].map((id) => document.getElementById(id));
  const spy = new IntersectionObserver((entries) => {
    entries.forEach((entry) => { if (entry.isIntersecting) vSetActive(entry.target.id); });
  }, { rootMargin: "-35% 0px -55% 0px" });
  sections.forEach((s) => s && spy.observe(s));

  document.getElementById("v-year").textContent = new Date().getFullYear();
  document.getElementById("v-nav-avatar").replaceChildren(makeAvatar(myVolunteerId, session.user.name, "avatar-sm"));
  loadHomeStats();
  renderHomeEvents();
}
