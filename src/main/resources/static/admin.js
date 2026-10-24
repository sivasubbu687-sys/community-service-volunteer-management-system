// Admin and coordinator screens: sidebar shell, top bar and the new dashboard.
// Runs after script.js and reuses its data (volunteers, events, assignments) and helpers.
(function () {
  if (typeof isStaff === "undefined" || !isStaff) return;

  const SVG_NS = "http://www.w3.org/2000/svg";
  const PALETTE = ["#2F6FED", "#22A66B", "#F5832B", "#7C5CD6", "#14B8A6", "#C5CCD6"];
  const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

  // ---------- small helpers ----------
  function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text != null) node.textContent = text;
    return node;
  }
  function svg(tag, attrs, text) {
    const node = document.createElementNS(SVG_NS, tag);
    Object.entries(attrs || {}).forEach(([k, v]) => node.setAttribute(k, v));
    if (text != null) node.textContent = text;
    return node;
  }
  const label = (name) => (name || "").split("_").map((p) => p.charAt(0) + p.slice(1).toLowerCase()).join(" ");
  const shortDate = (iso) => {
    const d = new Date(iso + "T00:00:00");
    return d.getDate() + " " + MONTHS[d.getMonth()] + " " + d.getFullYear();
  };
  const isoToday = () => {
    const d = new Date();
    return d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" + String(d.getDate()).padStart(2, "0");
  };
  function daysFromToday(iso) {
    const a = new Date(iso + "T00:00:00");
    const b = new Date(isoToday() + "T00:00:00");
    return Math.round((a - b) / 86400000);
  }
  function timeAgo(value) {
    const minutes = Math.max(0, Math.round((Date.now() - new Date(value).getTime()) / 60000));
    if (minutes < 1) return "just now";
    if (minutes < 60) return minutes + (minutes === 1 ? " minute ago" : " minutes ago");
    const hours = Math.round(minutes / 60);
    if (hours < 24) return hours + (hours === 1 ? " hour ago" : " hours ago");
    const days = Math.round(hours / 24);
    return days + (days === 1 ? " day ago" : " days ago");
  }
  const activeStatuses = ["ASSIGNED", "CONFIRMED"];
  const assignedTo = (eventId) => assignments.filter((a) => a.eventId === eventId && activeStatuses.includes(a.status)).length;

  // category -> colour (stable order so a category keeps its colour)
  const NO_CATEGORY = "No category";
  const categoryColor = (cat) => {
    const i = categories.findIndex((c) => c.name === cat);
    if (i < 0) return "#A9B4C4";
    return categories[i].color || PALETTE[i % PALETTE.length];
  };

  // =====================================================================
  // SHELL: sidebar, top bar
  // =====================================================================
  const root = document.documentElement;
  function openMenu(open) {
    $("sidebar").classList.toggle("open", open);
    $("side-backdrop").classList.toggle("hidden", !open);
  }
  $("menu-btn").addEventListener("click", () => openMenu(true));
  $("side-backdrop").addEventListener("click", () => openMenu(false));
  document.querySelectorAll("#sidebar .side-btn").forEach((b) => b.addEventListener("click", () => openMenu(false)));

  $("top-name").textContent = session.user.name;
  $("top-role").textContent = roleLabel(role);
  $("top-avatar").replaceChildren(makeAvatar(myVolunteerId, session.user.name, "avatar-header"));
  $("top-logout").addEventListener("click", () => goToLogin());
  $("adm-name").textContent = session.user.name;
  $("footer-year").textContent = new Date().getFullYear();

  // The search box opens the volunteer list or the event list, whichever has a match
  $("top-search-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const term = $("top-search").value.trim();
    if (!term) return;
    const lower = term.toLowerCase();
    const inVolunteers = volunteers.some((v) => [v.name, v.email, v.phone].some((f) => (f || "").toLowerCase().includes(lower)));
    const inEvents = events.some((e) => (e.title || "").toLowerCase().includes(lower));
    if (inVolunteers) {
      showView("directory");
      $("search").value = term;
      $("search").dispatchEvent(new Event("input"));
    } else if (inEvents) {
      showView("events");
      $("event-search").value = term;
      $("event-search").dispatchEvent(new Event("input"));
    } else {
      showMessage("Nothing matches “" + term + "”.", "error");
    }
  });

  $("bell-btn").addEventListener("click", () => {
    showView("dashboard");
    $("adm-notifications").scrollIntoView({ behavior: "smooth", block: "center" });
  });

  // Quick actions
  $("qa-event").addEventListener("click", () => { showView("events"); const t = $("event-title"); if (t) t.focus(); });
  $("qa-register").addEventListener("click", () => showView("register"));
  $("qa-assign").addEventListener("click", () => showView("assignments"));
  $("qa-attendance").addEventListener("click", () => showView("attendance"));
  // Generate Report: the user picks Volunteers, Events or Attendance first
  const REPORT_URLS = {
    volunteers: API_URL + "/export/pdf?search=",
    events: EVENTS_URL + "/export/pdf?search=&status=",
    attendance: ASSIGNMENTS_URL + "/export/pdf",
  };
  $("qa-report").addEventListener("click", () => $("report-overlay").classList.remove("hidden"));
  $("report-no").addEventListener("click", () => $("report-overlay").classList.add("hidden"));
  $("report-yes").addEventListener("click", async (event) => {
    const type = document.querySelector('input[name="report-type"]:checked').value;
    await downloadPdf(event.currentTarget, REPORT_URLS[type]);
    $("report-overlay").classList.add("hidden");
  });

  // =====================================================================
  // DASHBOARD
  // =====================================================================
  let overview = null;
  let overviewAt = 0;
  async function loadOverview() {
    if (Date.now() - overviewAt < 15000) return;
    overviewAt = Date.now();
    try {
      overview = await api("/api/stats/overview");
      renderStats();
    } catch (e) { /* the other figures still show */ }
  }

  function renderStats() {
    $("adm-vol").textContent = volunteers.length;
    $("adm-vol-sub").textContent = volunteers.length
      ? "Latest: " + volunteers.reduce((a, b) => (b.id > a.id ? b : a)).name
      : "No volunteers yet";

    $("adm-events").textContent = events.length;
    const month = isoToday().slice(0, 7);
    $("adm-events-sub").textContent = events.filter((e) => e.eventDate.startsWith(month)).length + " this month";

    const hours = overview ? overview.hoursContributed : null;
    $("adm-hours").textContent = hours == null ? "–" : (Math.round(hours * 10) / 10).toLocaleString();
    $("adm-hours-sub").textContent = overview ? overview.shiftsCompleted + " shifts completed" : " ";

    const upcoming = events.filter((e) => e.status === "UPCOMING").sort((a, b) => (a.eventDate + a.startTime).localeCompare(b.eventDate + b.startTime));
    $("adm-upcoming").textContent = upcoming.length;
    $("adm-upcoming-sub").textContent = upcoming.length ? "Next: " + upcoming[0].title : "Nothing scheduled";
  }

  // ----- bar chart: events and assigned volunteers for the last 6 months -----
  function renderBars() {
    const box = $("bar-chart");
    const now = new Date();
    const months = [];
    for (let i = 5; i >= 0; i--) {
      const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
      months.push({ key: d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0"), name: MONTHS[d.getMonth()], events: 0, volunteers: 0 });
    }
    events.forEach((e) => {
      const m = months.find((x) => e.eventDate.startsWith(x.key));
      if (m) { m.events++; m.volunteers += assignedTo(e.id); }
    });
    const highest = Math.max(1, ...months.map((m) => Math.max(m.events, m.volunteers)));
    const step = Math.max(1, Math.ceil(highest / 4 / (highest > 20 ? 5 : 1)) * (highest > 20 ? 5 : 1));
    const top = step * Math.ceil(highest / step);

    const W = 560, H = 230, left = 38, bottom = 28, topPad = 8, plotH = H - bottom - topPad, plotW = W - left - 8;
    const chart = svg("svg", { viewBox: "0 0 " + W + " " + H, width: "100%", preserveAspectRatio: "xMidYMid meet" });
    for (let v = 0; v <= top; v += step) {
      const y = topPad + plotH - (v / top) * plotH;
      chart.appendChild(svg("line", { x1: left, x2: W - 8, y1: y, y2: y, stroke: "#E6ECF5", "stroke-width": 1 }));
      chart.appendChild(svg("text", { x: left - 8, y: y + 4, "text-anchor": "end", "font-size": 11, fill: "#7B8AA0" }, v));
    }
    const slot = plotW / months.length, barW = Math.min(18, slot / 4);
    months.forEach((m, i) => {
      const cx = left + slot * i + slot / 2;
      [[m.events, "#2F6FED", -barW - 1], [m.volunteers, "#22A66B", 1]].forEach(([value, color, dx]) => {
        const h = (value / top) * plotH;
        const bar = svg("rect", { x: cx + dx, y: topPad + plotH - h, width: barW, height: Math.max(h, value ? 2 : 0), rx: 3, fill: color });
        bar.appendChild(svg("title", {}, (color === "#2F6FED" ? "Events: " : "Volunteers assigned: ") + value));
        chart.appendChild(bar);
      });
      chart.appendChild(svg("text", { x: cx, y: H - 8, "text-anchor": "middle", "font-size": 11, fill: "#7B8AA0" }, m.name));
    });
    box.replaceChildren(chart);
  }

  // ----- doughnut: events by category -----
  function renderDonut() {
    const counts = new Map();
    events.forEach((e) => counts.set(e.category || NO_CATEGORY, (counts.get(e.category || NO_CATEGORY) || 0) + 1));
    const rows = [...counts.entries()].sort((a, b) => b[1] - a[1]);
    const total = events.length;

    const R = 62, C = 2 * Math.PI * R;
    const chart = svg("svg", { viewBox: "0 0 180 180", width: "100%" });
    chart.appendChild(svg("circle", { cx: 90, cy: 90, r: R, fill: "none", stroke: "#EEF3FA", "stroke-width": 26 }));
    let offset = 0;
    rows.forEach(([cat, n]) => {
      const len = (n / total) * C;
      chart.appendChild(svg("circle", {
        cx: 90, cy: 90, r: R, fill: "none", stroke: categoryColor(cat), "stroke-width": 26,
        "stroke-dasharray": len + " " + (C - len), "stroke-dashoffset": -offset, transform: "rotate(-90 90 90)",
      }));
      offset += len;
    });
    chart.appendChild(svg("text", { x: 90, y: 92, "text-anchor": "middle", "font-size": 26, "font-weight": 700, fill: "#12263F" }, total));
    chart.appendChild(svg("text", { x: 90, y: 110, "text-anchor": "middle", "font-size": 11, fill: "#7B8AA0" }, total === 1 ? "Event" : "Events"));
    $("donut").replaceChildren(chart);

    const legend = $("donut-legend");
    legend.replaceChildren();
    if (!total) legend.appendChild(el("li", "muted", "No events yet"));
    rows.forEach(([cat, n]) => {
      const li = el("li");
      const dot = el("i", "dot");
      dot.style.background = categoryColor(cat);
      li.append(dot, el("span", "lg-name", cat),
        el("span", "lg-val", n + " (" + Math.round((n / total) * 100) + "%)"));
      legend.appendChild(li);
    });
  }

  // ----- recent events table -----
  let eventsAll = false;
  function renderRecentEvents() {
    const body = $("adm-recent-events");
    body.replaceChildren();
    const sorted = [...events].sort((a, b) => b.id - a.id);
    const recent = sorted.slice(0, eventsAll ? sorted.length : 5);
    $("adm-recent-wrap").classList.toggle("expanded", eventsAll);
    $("adm-events-more").textContent = eventsAll ? "Show less" : "View All →";
    $("adm-events-more").classList.toggle("hidden", sorted.length <= 5);
    $("adm-recent-empty").classList.toggle("hidden", recent.length > 0);
    recent.forEach((e) => {
      const tr = el("tr");
      const nameCell = el("td", "ev-name");
      const sq = el("span", "ev-sq");
      sq.style.background = categoryColor(e.category) + "22";
      sq.style.color = categoryColor(e.category);
      sq.textContent = (e.title || "?").charAt(0).toUpperCase();
      nameCell.append(sq, el("span", "ev-title", e.title));
      const pill = el("span", "pill pill-" + e.status, STATUS_LABELS[e.status] || e.status);
      const statusCell = el("td");
      statusCell.appendChild(pill);
      tr.append(nameCell, el("td", null, e.category || "–"), el("td", null, e.location), el("td", null, shortDate(e.eventDate)), statusCell);
      body.appendChild(tr);
    });
  }

  $("adm-events-more").addEventListener("click", () => { eventsAll = !eventsAll; renderRecentEvents(); });

  // ----- recent activities (built from what the system already records) -----
  let actAll = false;
  function renderActivities() {
    const items = [];
    assignments.forEach((a) => {
      if (a.assignedAt) items.push({ time: a.assignedAt, kind: "asg", title: "Volunteer assigned", sub: a.volunteerName + " → " + a.eventTitle });
      if (a.checkInTime) items.push({ time: a.checkInTime, kind: "in", title: "Volunteer checked in", sub: a.volunteerName + " · " + a.eventTitle });
      if (a.checkOutTime) items.push({ time: a.checkOutTime, kind: "out", title: "Volunteer checked out", sub: a.volunteerName + " · " + a.eventTitle });
    });
    items.sort((x, y) => new Date(y.time) - new Date(x.time));
    const shown = items.slice(0, actAll ? items.length : 5);
    const hasMore = items.length > 5;
    if (shown.length < 5 && volunteers.length) {
      const v = volunteers.reduce((a, b) => (b.id > a.id ? b : a));
      shown.push({ kind: "vol", title: "Latest volunteer registered", sub: v.name });
    }
    if (shown.length < 5 && events.length) {
      const e = events.reduce((a, b) => (b.id > a.id ? b : a));
      shown.push({ kind: "ev", title: "Latest event created", sub: e.title });
    }

    const list = $("adm-activities");
    list.classList.toggle("expanded", actAll);
    $("adm-act-more").textContent = actAll ? "Show less" : "View All →";
    $("adm-act-more").classList.toggle("hidden", !hasMore);
    list.replaceChildren();
    $("adm-act-empty").classList.toggle("hidden", shown.length > 0);
    const colors = { asg: "c-purple", in: "c-green", out: "c-orange", vol: "c-blue", ev: "c-teal" };
    shown.forEach((it) => {
      const li = el("li");
      const icon = el("span", "act-ico " + colors[it.kind], it.kind === "asg" ? "✓" : it.kind === "in" ? "↘" : it.kind === "out" ? "↗" : it.kind === "vol" ? "+" : "★");
      const text = el("div", "act-text");
      text.append(el("strong", null, it.title), el("small", null, it.sub));
      li.append(icon, text, el("time", null, it.time ? timeAgo(it.time) : ""));
      list.appendChild(li);
    });
  }

  // ----- notifications: volunteers leaving events (from the server) + ones worked out from events and volunteers -----
  let leaveNotices = [];
  let notifAll = false;
  async function loadLeaveNotices() {
    try {
      leaveNotices = await api("/api/notices");
    } catch (e) {
      leaveNotices = [];
    }
    renderNotifications();
  }
  async function dismissNotice(id) {
    try {
      await api("/api/notices/" + id + "/dismiss", { method: "POST" });
    } catch (e) { /* it will reappear on the next refresh */ }
    loadLeaveNotices();
  }
  function notice(n) {
    const li = el("li");
    li.append(el("i", "n-dot c-red"));
    const text = el("div");
    text.append(el("strong", null, n.volunteerName + " left " + n.eventTitle), el("span", null, "Reason: " + n.reason), el("small", null, timeAgo(n.createdAt)));
    const x = el("button", "link-btn", "Dismiss");
    x.type = "button";
    x.addEventListener("click", () => dismissNotice(n.id));
    li.append(text, x);
    return li;
  }

  function renderNotifications() {
    const notes = [];
    events.forEach((e) => {
      if (e.status === "CANCELLED" || e.status === "COMPLETED") return;
      const days = daysFromToday(e.eventDate);
      const need = e.requiredVolunteers - assignedTo(e.id);
      const when = days === 0 ? "Today" : days === 1 ? "Tomorrow" : shortDate(e.eventDate);
      const at = when + ", " + shortTime(e.startTime);
      if (e.status === "ONGOING") notes.push({ rank: 1, color: "c-blue", title: "Event in progress", text: e.title + " is happening now at " + e.location + ".", when: at });
      else if (days >= 0 && days <= 7 && need > 0) notes.push({ rank: 0, color: "c-red", title: "Volunteers needed", text: e.title + " still needs " + need + " more volunteer" + (need === 1 ? "" : "s") + ".", when: at });
      else if (days >= 0 && days <= 2) notes.push({ rank: 2, color: "c-orange", title: "Event reminder", text: e.title + " is scheduled for " + when.toLowerCase() + ".", when: at });
    });
    notes.sort((a, b) => a.rank - b.rank);
    if (volunteers.length) {
      const v = volunteers.reduce((a, b) => (b.id > a.id ? b : a));
      notes.push({ rank: 3, color: "c-blue", title: "New volunteer registered", text: v.name + " is the latest volunteer.", when: "" });
    }

    const list = $("adm-notif-list");
    list.replaceChildren();
    leaveNotices.slice(0, notifAll ? leaveNotices.length : 5).forEach((n) => list.appendChild(notice(n)));
    notes.slice(0, notifAll ? notes.length : 3).forEach((n) => {
      const li = el("li");
      li.append(el("i", "n-dot " + n.color));
      const text = el("div");
      text.append(el("strong", null, n.title), el("span", null, n.text), el("small", null, n.when));
      li.appendChild(text);
      list.appendChild(li);
    });
    const total = notes.length + leaveNotices.length;
    $("adm-notif-more").textContent = notifAll ? "Show less" : "View All →";
    $("adm-notif-more").classList.toggle("hidden", total <= 3);
    $("adm-notif-empty").classList.toggle("hidden", total > 0);
    [$("adm-notif-count"), $("bell-count")].forEach((badge) => {
      badge.textContent = total;
      badge.classList.toggle("hidden", total === 0);
    });
  }

  $("adm-notif-more").addEventListener("click", () => { notifAll = !notifAll; renderNotifications(); });

  $("adm-act-more").addEventListener("click", () => { actAll = !actAll; renderActivities(); });

  window.renderAdminDashboard = function () {
    renderStats();
    renderBars();
    renderDonut();
    renderRecentEvents();
    renderActivities();
    renderNotifications();
    loadOverview();
  };
  window.renderAdminDashboard();
  loadLeaveNotices();
  setInterval(loadLeaveNotices, 60000);
})();
