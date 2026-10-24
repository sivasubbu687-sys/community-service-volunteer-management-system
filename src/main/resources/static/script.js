// Base URL of the Spring Boot REST API (same origin as this page)
const API_URL = "/api/volunteers";
const EVENTS_URL = "/api/events";
const ASSIGNMENTS_URL = "/api/assignments";
const USERS_URL = "/api/users";
const CATEGORIES_URL = "/api/categories";

// ---------- Login session (JWT kept in localStorage) ----------
const TOKEN_KEY = "vms_token";
const USER_KEY = "vms_user";

// Returns { token, user } or null when there is no valid (unexpired) login
function readSession() {
  try {
    const token = localStorage.getItem(TOKEN_KEY);
    const user = JSON.parse(localStorage.getItem(USER_KEY));
    if (!token || !user) return null;
    const payload = JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
    if (payload.exp * 1000 <= Date.now()) return null;
    return { token, user };
  } catch (e) {
    return null;
  }
}

// Clears the login and opens the login page (optionally with a message)
function goToLogin(message) {
  try { localStorage.removeItem(TOKEN_KEY); localStorage.removeItem(USER_KEY); } catch (e) { /* ignore */ }
  location.replace("login.html" + (message ? "?msg=" + encodeURIComponent(message) : ""));
}

const session = readSession();
if (!session) goToLogin("Please log in to continue.");

const role = session ? session.user.role : null;
const isAdmin = role === "ADMIN";
const isStaff = role === "ADMIN" || role === "COORDINATOR";   // can manage events, assignments, attendance
const isVolunteer = role === "VOLUNTEER";
const myVolunteerId = session ? session.user.volunteerId : null;

// Holds the volunteers loaded from the backend
let volunteers = [];
// Holds the events loaded from the backend
let events = [];
// Event categories configured by the admin in Settings
let categories = [];
// Holds all assignments (each one links a volunteer to an event)
let assignments = [];
// Profile photos: volunteerId -> version (the pictures themselves are fetched on demand)
let photos = {};
// Event pictures: eventId -> version
let eventPhotos = {};
// Event photo chosen in the form but not saved yet, and whether "remove current photo" was clicked
let pendingEventPhotoRemoval = false;
// Downloaded pictures kept as blob addresses: "kind:id:version" -> url
const photoUrls = new Map();
// Volunteers only: how many volunteers each event has (eventId -> count) and their own assignments
let eventSpots = {};
let myAssignments = [];
// Current page of the volunteer and event lists (10 per page)
const PAGE_SIZE = 10;
let volunteerPage = 1;
let eventPage = 1;
// Assignment whose attendance times are being corrected
let timesTarget = null;
// Ids of volunteers that already have a login account
let volunteersWithLogin = new Set();
// Volunteer for whom the "create a login?" question is open
let loginTarget = null;
// What is waiting for delete confirmation: { type: "volunteer" | "event" | "assignment", id }
let pendingDelete = null;

// ---------- Element shortcuts ----------
const $ = (id) => document.getElementById(id);
const form = $("volunteer-form");
const fields = { name: $("name"), email: $("email"), phone: $("phone") };

// ---------- Navigation ----------
function showView(name) {
  document.querySelectorAll(".view").forEach((v) => v.classList.add("hidden"));
  $("view-" + name).classList.remove("hidden");
  document.querySelectorAll(".nav-btn").forEach((b) =>
    b.classList.toggle("active", b.dataset.view === name)
  );
  hideMessage();
  if (isVolunteer) {
    const onHome = name === "dashboard";
    $("volunteer-home").classList.toggle("hidden", !onHome);
    document.querySelector("main.container").classList.toggle("hidden", onHome);
    if (!onHome) vSetActive(name === "events" ? "v-events" : "");
  }
  // Leaving the form view while editing cancels the edit
  if (name !== "register") resetForm();
  if (name !== "events") resetEventForm();
  // Reload everything so the dropdowns and counts are up to date
  if (name === "assignments") refreshAssignmentData();
  if (name === "attendance") { loadVolunteers(); loadEvents(); loadAttendance(); loadHours(); }
  if (name === "dashboard" && isStaff) loadTopVolunteers();
  if (name === "dashboard" && isVolunteer) loadMyActivity();
  if (name === "profile") loadMyActivity();
  if (name === "users") loadUsers();
  if (name === "settings" && typeof renderCategorySettings === "function") renderCategorySettings();
}

// Any element with data-view navigates to that view
document.querySelectorAll("[data-view]").forEach((el) =>
  el.addEventListener("click", () => showView(el.dataset.view))
);

// ---------- Messages ----------
let messageTimer;
function showMessage(text, type) {
  const box = $("message");
  box.textContent = text;
  box.className = "message " + type;
  clearTimeout(messageTimer);
  if (type === "success") messageTimer = setTimeout(hideMessage, 4000);
  if (isVolunteer) vToast(text, type);
  window.scrollTo({ top: 0, behavior: "smooth" });
}
function hideMessage() {
  if (isVolunteer) vToastHide();
  $("message").className = "message hidden";
}

// ---------- API helpers ----------
// Sends a request and turns failures into readable Error messages
async function api(url, options = {}) {
  let response;
  try {
    response = await fetch(url, {
      ...options,
      headers: { "Content-Type": "application/json", Authorization: "Bearer " + session.token },
    });
  } catch (e) {
    throw new Error("Cannot reach the server. Is the application running?");
  }

  if (!response.ok) {
    let detail = "";
    try { detail = (await response.json()).message || ""; } catch (e) { /* no JSON body */ }
    if (response.status === 401) {
      // missing, invalid or expired token: back to the login page
      goToLogin(detail || "Your session has expired. Please log in again.");
      throw new Error(detail || "Please log in again.");
    }
    if (response.status === 403) throw new Error(detail || "You do not have permission to do that.");
    if (response.status === 404) throw new Error(detail || "Not found.");
    // 400 / 409 from the backend carry a clear message (validation, schedule conflict, event full ...)
    if (detail && (response.status === 400 || response.status === 409)) throw new Error(detail);
    if (url.startsWith(API_URL) && response.status === 500) {
      // email column is unique, so a failed save is most often a duplicate email
      throw new Error("Could not save. This email address may already be registered.");
    }
    throw new Error(detail || "Request failed (status " + response.status + ").");
  }
  // DELETE returns 204 with no body
  return response.status === 204 ? null : response.json();
}

// ---------- Load & render ----------
async function loadVolunteers() {
  $("loading").classList.remove("hidden");
  $("empty").classList.add("hidden");
  $("volunteer-table").classList.add("hidden");
  try {
    volunteers = await api(API_URL);
    if (isAdmin) {
      try {
        const accounts = await api(USERS_URL);
        volunteersWithLogin = new Set(accounts.filter((u) => u.volunteerId != null).map((u) => u.volunteerId));
      } catch (e) { /* the list still works without it */ }
    }
    renderDashboard();
    renderTable();
    renderAssignmentView();
  } catch (e) {
    volunteers = [];
    renderDashboard();
    $("loading").classList.add("hidden");
    showError("Failed to load volunteers: " + e.message);
  }
}

function showError(text) {
  showMessage(text, "error");
}

function renderDashboard() {
  $("stat-total").textContent = volunteers.length;
  $("stat-events").textContent = events.length;
  if (volunteers.length === 0) {
    $("stat-latest").textContent = "No volunteers yet";
  } else {
    const latest = volunteers.reduce((a, b) => (b.id > a.id ? b : a));
    $("stat-latest").textContent = latest.name;
  }
  if (typeof renderAdminDashboard === "function") renderAdminDashboard();
}

function renderTable() {
  const term = $("search").value.trim().toLowerCase();
  const filtered = volunteers.filter((v) =>
    [v.name, v.email, v.phone].some((f) => (f || "").toLowerCase().includes(term))
  );
  const pg = pageSlice(filtered, volunteerPage);
  volunteerPage = pg.page;
  renderPager("volunteer-pager", pg, filtered.length, (p) => { volunteerPage = p; renderTable(); });

  $("loading").classList.add("hidden");
  const body = $("volunteer-body");
  body.innerHTML = "";

  if (filtered.length === 0) {
    $("volunteer-table").classList.add("hidden");
    $("empty").classList.remove("hidden");
    $("empty").textContent = volunteers.length === 0
      ? "No volunteers registered yet. Use the Register tab to add the first one."
      : "No volunteers match your search.";
    return;
  }

  $("empty").classList.add("hidden");
  $("volunteer-table").classList.remove("hidden");

  pg.items.forEach((v) => {
    const tr = document.createElement("tr");
    // textContent is used (never innerHTML) so user input can't inject HTML
    [["ID", v.id], ["Name", v.name], ["Email", v.email], ["Phone", v.phone]].forEach(([label, value]) => {
      const td = document.createElement("td");
      td.dataset.label = label;
      if (label === "Name") td.appendChild(personCell(v.id, v.name));
      else td.textContent = value;
      tr.appendChild(td);
    });

    const actions = document.createElement("td");
    actions.dataset.label = "Actions";
    const wrap = document.createElement("div");
    wrap.className = "row-actions";

    const editBtn = document.createElement("button");
    editBtn.className = "btn btn-outline btn-sm";
    editBtn.textContent = "Edit";
    editBtn.addEventListener("click", () => startEdit(v));

    const delBtn = document.createElement("button");
    delBtn.className = "btn btn-danger btn-sm";
    delBtn.textContent = "Delete";
    delBtn.addEventListener("click", () => askDelete(v));

    wrap.append(editBtn, delBtn);
    if (isAdmin && !volunteersWithLogin.has(v.id)) {
      const loginBtn = document.createElement("button");
      loginBtn.className = "btn btn-primary btn-sm";
      loginBtn.textContent = "Create login";
      loginBtn.addEventListener("click", () => askCreateLogin(v));
      wrap.appendChild(loginBtn);
    }
    actions.appendChild(wrap);
    if (isAdmin) tr.appendChild(actions);   // volunteers table: edit / delete are ADMIN only
    body.appendChild(tr);
  });
}

// ---------- Form: validation ----------
function validate() {
  const errors = {};
  const name = fields.name.value.trim();
  const email = fields.email.value.trim();
  const phone = fields.phone.value.trim();

  if (name.length < 2) errors.name = "Please enter the full name (at least 2 characters).";
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) errors.email = "Please enter a valid email address.";
  if (!/^[6-9][0-9]{9}$/.test(phone)) errors.phone = "Phone number must be 10 digits and start with 6, 7, 8 or 9.";

  Object.keys(fields).forEach((key) => {
    $(key + "-error").textContent = errors[key] || "";
    fields[key].classList.toggle("invalid", Boolean(errors[key]));
  });
  return Object.keys(errors).length === 0;
}

// ---------- Form: submit (create or update) ----------
form.addEventListener("submit", async (event) => {
  event.preventDefault();
  hideMessage();
  if (!validate()) return;

  const id = $("volunteer-id").value;
  const payload = {
    name: fields.name.value.trim(),
    email: fields.email.value.trim(),
    phone: fields.phone.value.trim(),
  };

  const button = $("submit-btn");
  button.disabled = true;
  let newVolunteer = null;
  try {
    if (id) {
      await api(API_URL + "/" + id, { method: "PUT", body: JSON.stringify(payload) });
      resetForm();
      showView("directory");
      showMessage("Volunteer updated successfully.", "success");
    } else {
      newVolunteer = await api(API_URL, { method: "POST", body: JSON.stringify(payload) });
      resetForm();
      showMessage("Volunteer registered successfully.", "success");
    }
    await loadVolunteers();
    if (newVolunteer) askCreateLogin(newVolunteer);
  } catch (e) {
    showError(e.message);
  } finally {
    button.disabled = false;
  }
});

// Clear field errors as the user types
Object.keys(fields).forEach((key) =>
  fields[key].addEventListener("input", () => {
    $(key + "-error").textContent = "";
    fields[key].classList.remove("invalid");
  })
);

// Phone numbers are checked while typing: digits only, must start with 6-9, and be 10 digits long.
// A wrong first digit is flagged immediately; the length is flagged once the user leaves the field.
function phoneProblem(value, finished) {
  if (!value) return finished ? "Phone number is required." : "";
  if (!/^[6-9]/.test(value)) return "Phone number should not start with " + value[0] + ". It should start with 6, 7, 8 or 9.";
  if (finished && value.length !== 10) return "Phone number must be exactly 10 digits.";
  return "";
}

function watchPhone(inputId, errorId) {
  const input = $(inputId);
  const show = (finished) => {
    const text = phoneProblem(input.value, finished);
    $(errorId).textContent = text;
    input.classList.toggle("invalid", Boolean(text));
  };
  input.addEventListener("input", () => {
    const digits = input.value.replace(/\D/g, "");
    if (digits !== input.value) input.value = digits;
    show(input.value.length === 10);
  });
  input.addEventListener("blur", () => show(true));
}
watchPhone("phone", "phone-error");
watchPhone("profile-phone", "profile-phone-error");

// ---------- Edit ----------
function startEdit(volunteer) {
  showView("register");
  $("volunteer-id").value = volunteer.id;
  fields.name.value = volunteer.name;
  fields.email.value = volunteer.email;
  fields.phone.value = volunteer.phone;
  $("form-title").textContent = "Edit Volunteer #" + volunteer.id;
  $("submit-btn").textContent = "Save Changes";
  $("cancel-btn").classList.remove("hidden");
  fields.name.focus();
}

function resetForm() {
  form.reset();
  $("volunteer-id").value = "";
  $("form-title").textContent = "Register a Volunteer";
  $("submit-btn").textContent = "Register Volunteer";
  $("cancel-btn").classList.add("hidden");
  Object.keys(fields).forEach((key) => {
    $(key + "-error").textContent = "";
    fields[key].classList.remove("invalid");
  });
}

$("cancel-btn").addEventListener("click", () => {
  resetForm();
  showView("directory");
});

// ---------- Delete (with confirmation) ----------
function askDelete(volunteer) {
  pendingDelete = { type: "volunteer", id: volunteer.id };
  $("confirm-title").textContent = "Delete volunteer?";
  $("confirm-text").textContent = "Are you sure you want to delete " + volunteer.name + "? This cannot be undone.";
  $("confirm-overlay").classList.remove("hidden");
}

function askDeleteEvent(event) {
  pendingDelete = { type: "event", id: event.id };
  $("confirm-title").textContent = "Delete event?";
  $("confirm-text").textContent = "Are you sure you want to delete \"" + event.title + "\"? This cannot be undone.";
  $("confirm-overlay").classList.remove("hidden");
}

function closeConfirm() {
  pendingDelete = null;
  $("confirm-overlay").classList.add("hidden");
}

$("confirm-no").addEventListener("click", closeConfirm);

$("confirm-yes").addEventListener("click", async () => {
  const target = pendingDelete;
  closeConfirm();
  if (!target) return;
  const config = {
    volunteer: { url: API_URL, text: "Volunteer deleted.", reload: loadVolunteers },
    event: { url: EVENTS_URL, text: "Event deleted.", reload: loadEvents },
    assignment: { url: ASSIGNMENTS_URL, text: "Assignment removed.", reload: loadAssignments },
    user: { url: USERS_URL, text: "User deleted.", reload: loadUsers },
  }[target.type];
  try {
    await api(config.url + "/" + target.id, { method: "DELETE" });
    showMessage(config.text, "success");
    await config.reload();
  } catch (e) {
    showError(e.message);
  }
});

// ---------- Search & refresh ----------
$("search").addEventListener("input", () => { volunteerPage = 1; renderTable(); });
$("refresh-btn").addEventListener("click", loadVolunteers);

// ---------- Download PDF (admin, coordinator) ----------
// Asks the server for the PDF with the same search / filter that is on the screen, then saves the file.
// (A plain link cannot be used: the request needs the login token.)
async function downloadPdf(button, url) {
  hideMessage();
  const label = button.textContent;
  button.disabled = true;
  button.textContent = "Preparing PDF…";
  try {
    const response = await fetch(url, { headers: { Authorization: "Bearer " + session.token } });
    if (!response.ok) {
      let detail = "";
      try { detail = (await response.json()).message; } catch (e) { /* no JSON body */ }
      if (response.status === 401) { goToLogin(detail || "Your session has expired. Please log in again."); return; }
      throw new Error(detail || "The PDF could not be created (status " + response.status + ").");
    }
    const name = ((response.headers.get("Content-Disposition") || "").match(/filename="?([^";]+)"?/) || [])[1] || "download.pdf";
    const link = document.createElement("a");
    link.href = URL.createObjectURL(await response.blob());
    link.download = name;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(link.href), 10000);
    showMessage("PDF downloaded: " + name, "success");
  } catch (e) {
    showError(e.message);
  } finally {
    button.disabled = false;
    button.textContent = label;
  }
}

$("pdf-volunteers-btn").addEventListener("click", (event) =>
  downloadPdf(event.currentTarget, API_URL + "/export/pdf?search=" + encodeURIComponent($("search").value.trim())));

// =====================================================================
// EVENTS
// =====================================================================
const eventFieldIds = { title: "event-title", date: "event-date", start: "event-start", end: "event-end", location: "event-location", required: "event-required" };
const eventForm = $("event-form");

const STATUS_LABELS = { UPCOMING: "Upcoming", ONGOING: "Ongoing", COMPLETED: "Completed", CANCELLED: "Cancelled" };

async function loadEvents() {
  $("event-loading").classList.remove("hidden");
  $("event-empty").classList.add("hidden");
  $("event-table").classList.add("hidden");
  try {
    events = await api(EVENTS_URL);
    if (!isStaff) {
      try {
        eventSpots = {};
        (await api(EVENTS_URL + "/summary")).forEach((s) => { eventSpots[s.eventId] = s.assigned; });
      } catch (e) { /* counts stay at 0 */ }
    }
    renderDashboard();
    renderEvents();
    renderAssignmentView();
  } catch (e) {
    events = [];
    renderDashboard();
    $("event-loading").classList.add("hidden");
    showError("Failed to load events: " + e.message);
  }
}

// "14:30:00" -> "14:30"
function shortTime(t) {
  return (t || "").slice(0, 5);
}

function renderEvents() {
  if (isVolunteer) renderHomeEvents();
  const term = $("event-search").value.trim().toLowerCase();
  const status = $("event-filter").value;
  const filtered = events
    .filter((ev) => (ev.title || "").toLowerCase().includes(term) && (!status || ev.status === status))
    .sort((a, b) => (a.eventDate + a.startTime).localeCompare(b.eventDate + b.startTime));
  const pg = pageSlice(filtered, eventPage);
  eventPage = pg.page;
  renderPager("event-pager", pg, filtered.length, (p) => { eventPage = p; renderEvents(); });

  $("event-loading").classList.add("hidden");
  const body = $("event-body");
  body.innerHTML = "";

  if (filtered.length === 0) {
    $("event-table").classList.add("hidden");
    $("event-empty").classList.remove("hidden");
    $("event-empty").textContent = events.length === 0
      ? "No events yet. Use the form above to create the first one."
      : "No events match your search or filter.";
    return;
  }

  $("event-empty").classList.add("hidden");
  $("event-table").classList.remove("hidden");

  pg.items.forEach((ev) => {
    const tr = document.createElement("tr");
    const cells = [
      ["Title", ev.title],
      ["Date", ev.eventDate],
      ["Time", shortTime(ev.startTime) + " – " + shortTime(ev.endTime)],
      ["Location", ev.location],
      ["Assigned / Required", activeCount(ev.id) + " / " + ev.requiredVolunteers],
    ];
    cells.forEach(([label, value]) => {
      const td = document.createElement("td");
      td.dataset.label = label;
      if (label === "Title") td.appendChild(eventCell(ev.id, ev.title));
      else td.textContent = value;
      tr.appendChild(td);
    });

    const statusTd = document.createElement("td");
    statusTd.dataset.label = "Status";
    const badge = document.createElement("span");
    badge.className = "badge badge-" + ev.status;
    badge.textContent = STATUS_LABELS[ev.status] || ev.status;
    statusTd.appendChild(badge);
    tr.appendChild(statusTd);

    if (isVolunteer) tr.appendChild(participationCell(ev));

    const actions = document.createElement("td");
    actions.dataset.label = "Actions";
    const wrap = document.createElement("div");
    wrap.className = "row-actions";

    const editBtn = document.createElement("button");
    editBtn.className = "btn btn-outline btn-sm";
    editBtn.textContent = "Edit";
    editBtn.addEventListener("click", () => startEditEvent(ev));

    const delBtn = document.createElement("button");
    delBtn.className = "btn btn-danger btn-sm";
    delBtn.textContent = "Delete";
    delBtn.addEventListener("click", () => askDeleteEvent(ev));

    wrap.append(editBtn, delBtn);
    actions.appendChild(wrap);
    if (isStaff) tr.appendChild(actions);   // events table: ADMIN and COORDINATOR manage events
    body.appendChild(tr);
  });
}

function setFieldError(key, text) {
  $(eventFieldIds[key] + "-error").textContent = text || "";
  $(eventFieldIds[key]).classList.toggle("invalid", Boolean(text));
}

function validateEvent() {
  const errors = {};
  const v = (key) => $(eventFieldIds[key]).value.trim();

  if (!v("title")) errors.title = "Title is required.";
  if (!v("date")) errors.date = "Date is required.";
  if (!v("start")) errors.start = "Start time is required.";
  if (!v("end")) errors.end = "End time is required.";
  if (v("start") && v("end") && v("end") <= v("start")) errors.end = "End time must be after start time.";
  if (!v("location")) errors.location = "Location is required.";
  if (!(parseInt(v("required"), 10) > 0)) errors.required = "Enter a number greater than 0.";

  Object.keys(eventFieldIds).forEach((key) => setFieldError(key, errors[key]));
  return Object.keys(errors).length === 0;
}

eventForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  hideMessage();
  if (!validateEvent()) return;

  const id = $("event-id").value;
  const payload = {
    title: $("event-title").value.trim(),
    description: $("event-description").value.trim(),
    eventDate: $("event-date").value,
    startTime: $("event-start").value,
    endTime: $("event-end").value,
    location: $("event-location").value.trim(),
    requiredVolunteers: parseInt($("event-required").value, 10),
    status: $("event-status").value,
    category: $("event-category").value || null,
  };

  const button = $("event-submit-btn");
  button.disabled = true;
  try {
    let eventId = id;
    if (id) {
      await api(EVENTS_URL + "/" + id, { method: "PUT", body: JSON.stringify(payload) });
      showMessage("Event updated successfully.", "success");
    } else {
      const created = await api(EVENTS_URL, { method: "POST", body: JSON.stringify(payload) });
      eventId = created.id;
      showMessage("Event created successfully.", "success");
    }
    // the picture is saved after the event itself; a problem here must not hide that the event was saved
    try {
      await saveEventPhoto(eventId);
    } catch (photoError) {
      showError("The event was saved, but its photo was not: " + photoError.message);
    }
    resetEventForm();
    await loadEventPhotos();
    await loadEvents();
  } catch (e) {
    showError(e.message);
  } finally {
    button.disabled = false;
  }
});

Object.keys(eventFieldIds).forEach((key) =>
  $(eventFieldIds[key]).addEventListener("input", () => setFieldError(key, ""))
);

// End time must be after start time: flagged as soon as both are filled in, not only when the form is submitted
function checkEventTimes() {
  const start = $("event-start").value;
  const end = $("event-end").value;
  setFieldError("end", start && end && end <= start ? "End time must be after start time." : "");
}
["event-start", "event-end"].forEach((id) => {
  $(id).addEventListener("input", checkEventTimes);
  $(id).addEventListener("change", checkEventTimes);
});

function startEditEvent(ev) {
  $("event-id").value = ev.id;
  $("event-title").value = ev.title;
  $("event-description").value = ev.description || "";
  $("event-date").value = ev.eventDate;
  $("event-start").value = shortTime(ev.startTime);
  $("event-end").value = shortTime(ev.endTime);
  $("event-location").value = ev.location;
  $("event-required").value = ev.requiredVolunteers;
  $("event-status").value = ev.status;
  setEventCategory(ev.category);
  $("event-form-title").textContent = "Edit Event #" + ev.id;
  $("event-submit-btn").textContent = "Save Changes";
  $("event-cancel-btn").classList.remove("hidden");
  $("event-photo").value = "";
  pendingEventPhotoRemoval = false;
  showEventPhotoPreview(null, ev.id, ev.title);
  $("event-title").scrollIntoView({ behavior: "smooth", block: "center" });
  $("event-title").focus();
}

function resetEventForm() {
  eventForm.reset();
  $("event-id").value = "";
  $("event-form-title").textContent = "Create an Event";
  $("event-submit-btn").textContent = "Create Event";
  $("event-cancel-btn").classList.add("hidden");
  Object.keys(eventFieldIds).forEach((key) => setFieldError(key, ""));
  $("event-photo").value = "";
  $("event-photo-error").textContent = "";
  pendingEventPhotoRemoval = false;
  showEventPhotoPreview(null, null, "");
}

$("event-cancel-btn").addEventListener("click", resetEventForm);
$("event-search").addEventListener("input", () => { eventPage = 1; renderEvents(); });
$("event-filter").addEventListener("change", () => { eventPage = 1; renderEvents(); });
$("event-refresh-btn").addEventListener("click", loadEvents);
$("pdf-events-btn").addEventListener("click", (event) =>
  downloadPdf(event.currentTarget, EVENTS_URL + "/export/pdf?search=" + encodeURIComponent($("event-search").value.trim())
    + "&status=" + encodeURIComponent($("event-filter").value)));

// =====================================================================
// ASSIGNMENTS
// =====================================================================
const ACTIVE_STATUSES = ["ASSIGNED", "CONFIRMED"];

// Number of volunteers actively assigned to an event (cancelled ones don't count)
function activeCount(eventId) {
  // volunteers cannot read the assignment list, so they get counts from /api/events/summary
  if (!isStaff) return eventSpots[eventId] || 0;
  return assignments.filter((a) => a.eventId === eventId && ACTIVE_STATUSES.includes(a.status)).length;
}

async function loadAssignments() {
  $("assign-loading").classList.remove("hidden");
  try {
    assignments = await api(ASSIGNMENTS_URL);
  } catch (e) {
    assignments = [];
    showError("Failed to load assignments: " + e.message);
  }
  $("assign-loading").classList.add("hidden");
  renderEvents();
  renderAssignmentView();
  if (typeof renderAdminDashboard === "function") renderAdminDashboard();
}

function refreshAssignmentData() {
  loadVolunteers();
  loadEvents();
  loadAssignments();
}

// Rebuilds the event dropdown, summary, volunteer dropdown and assignment table
function renderAssignmentView() {
  renderAttendanceSelects();
  const select = $("assign-event");
  const previous = select.value;
  select.innerHTML = "";

  if (events.length === 0) {
    select.appendChild(new Option("No events available – create one first", ""));
  } else {
    select.appendChild(new Option("Select an event…", ""));
    events.forEach((ev) => {
      select.appendChild(new Option(ev.title + " (" + ev.eventDate + ")", ev.id));
    });
    // keep the user's selection after a refresh
    if (events.some((ev) => String(ev.id) === previous)) select.value = previous;
  }

  renderAssignmentDetails();
}

function renderAssignmentDetails() {
  const eventId = Number($("assign-event").value);
  const ev = events.find((e) => e.id === eventId);
  const summary = $("assign-summary");
  const addBox = $("assign-add");
  const body = $("assign-body");
  body.innerHTML = "";

  if (!ev) {
    summary.classList.add("hidden");
    addBox.classList.add("hidden");
    $("assign-table").classList.add("hidden");
    $("assign-empty").classList.remove("hidden");
    $("assign-empty").textContent = "Select an event to see its assigned volunteers.";
    return;
  }

  // Summary bar
  const taken = activeCount(ev.id);
  const full = taken >= ev.requiredVolunteers;
  summary.innerHTML = "";
  const count = document.createElement("span");
  count.className = "count" + (full ? " full" : "");
  count.textContent = taken + " / " + ev.requiredVolunteers + " volunteers" + (full ? " (full)" : "");
  const when = document.createElement("span");
  when.textContent = ev.eventDate + ", " + shortTime(ev.startTime) + " – " + shortTime(ev.endTime) + " · " + ev.location;
  const badge = document.createElement("span");
  badge.className = "badge badge-" + ev.status;
  badge.textContent = STATUS_LABELS[ev.status] || ev.status;
  summary.append(makeEventThumb(ev.id, ev.title, "avatar-sm"), count, when, badge);
  summary.classList.remove("hidden");

  // Volunteer dropdown: hide volunteers who are already actively assigned to this event
  const mine = assignments.filter((a) => a.eventId === ev.id);
  const takenIds = mine.filter((a) => ACTIVE_STATUSES.includes(a.status)).map((a) => a.volunteerId);
  const volSelect = $("assign-volunteer");
  volSelect.innerHTML = "";
  const available = volunteers.filter((v) => !takenIds.includes(v.id));
  if (available.length === 0) {
    volSelect.appendChild(new Option(volunteers.length === 0 ? "No volunteers registered yet" : "All volunteers are assigned", ""));
  } else {
    volSelect.appendChild(new Option("Select a volunteer…", ""));
    available.forEach((v) => volSelect.appendChild(new Option(v.name + " (" + v.email + ")", v.id)));
  }
  addBox.classList.remove("hidden");

  // Table of this event's assignments
  if (mine.length === 0) {
    $("assign-table").classList.add("hidden");
    $("assign-empty").classList.remove("hidden");
    $("assign-empty").textContent = "No volunteers assigned to this event yet.";
    return;
  }
  $("assign-empty").classList.add("hidden");
  $("assign-table").classList.remove("hidden");

  mine.forEach((a) => {
    const tr = document.createElement("tr");
    [["Volunteer", a.volunteerName], ["Email", a.volunteerEmail], ["Phone", a.volunteerPhone]].forEach(([label, value]) => {
      const td = document.createElement("td");
      td.dataset.label = label;
      if (label === "Volunteer") td.appendChild(personCell(a.volunteerId, a.volunteerName));
      else td.textContent = value;
      tr.appendChild(td);
    });

    const statusTd = document.createElement("td");
    statusTd.dataset.label = "Status";
    const statusBadge = document.createElement("span");
    statusBadge.className = "badge badge-" + a.status;
    statusBadge.textContent = a.status.charAt(0) + a.status.slice(1).toLowerCase();
    statusTd.appendChild(statusBadge);
    tr.appendChild(statusTd);

    const actions = document.createElement("td");
    actions.dataset.label = "Actions";
    const wrap = document.createElement("div");
    wrap.className = "row-actions";

    const addAction = (text, cls, handler) => {
      const btn = document.createElement("button");
      btn.className = "btn btn-sm " + cls;
      btn.textContent = text;
      btn.addEventListener("click", handler);
      wrap.appendChild(btn);
    };

    if (a.status === "ASSIGNED") addAction("Confirm", "btn-primary", () => changeAssignmentStatus(a, "CONFIRMED"));
    if (a.status !== "CANCELLED") addAction("Cancel", "btn-outline", () => changeAssignmentStatus(a, "CANCELLED"));
    if (a.status === "CANCELLED") addAction("Re-assign", "btn-outline", () => changeAssignmentStatus(a, "ASSIGNED"));
    addAction("Remove", "btn-danger", () => askDeleteAssignment(a));

    actions.appendChild(wrap);
    tr.appendChild(actions);
    body.appendChild(tr);
  });
}

// Conflict / full / cancelled-event errors from the backend are shown in the red message bar
$("assign-btn").addEventListener("click", async () => {
  hideMessage();
  const eventId = Number($("assign-event").value);
  const volunteerId = Number($("assign-volunteer").value);
  if (!eventId) return showError("Please select an event first.");
  if (!volunteerId) return showError("Please select a volunteer to assign.");

  const button = $("assign-btn");
  button.disabled = true;
  try {
    const result = await api(ASSIGNMENTS_URL, { method: "POST", body: JSON.stringify({ volunteerId, eventId }) });
    showMessage(result.volunteerName + " was assigned to \"" + result.eventTitle + "\". " + (result.emailStatus || ""),
      "success");
    await loadAssignments();
  } catch (e) {
    showError(e.message);
  } finally {
    button.disabled = false;
  }
});

async function changeAssignmentStatus(assignment, status) {
  hideMessage();
  try {
    await api(ASSIGNMENTS_URL + "/" + assignment.id + "/status", { method: "PUT", body: JSON.stringify({ status }) });
    showMessage("Assignment for " + assignment.volunteerName + " is now " + status.toLowerCase() + ".", "success");
    await loadAssignments();
  } catch (e) {
    showError(e.message);
  }
}

function askDeleteAssignment(a) {
  pendingDelete = { type: "assignment", id: a.id };
  $("confirm-title").textContent = "Remove assignment?";
  $("confirm-text").textContent = "Remove " + a.volunteerName + " from \"" + a.eventTitle + "\"?";
  $("confirm-overlay").classList.remove("hidden");
}

$("assign-event").addEventListener("change", renderAssignmentDetails);
$("assign-refresh-btn").addEventListener("click", refreshAssignmentData);

// =====================================================================
// ATTENDANCE & HOURS
// =====================================================================

// "2026-10-23T09:05:00" -> "23 Oct, 09:05 AM"
function fmtDateTime(value) {
  if (!value) return "–";
  return new Date(value).toLocaleString([], { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" });
}

// True only on the event date, from its start time up to its end time
function isEventWindowOpen(ev) {
  const start = new Date(ev.eventDate + "T" + ev.startTime);
  const end = new Date(ev.eventDate + "T" + ev.endTime);
  const now = new Date();
  return now >= start && now <= end;
}

function fmtHours(h) {
  return (Number(h) || 0).toFixed(2) + " h";
}

// Fills the event and volunteer dropdowns on the Attendance page (keeps the current selection)
function renderAttendanceSelects() {
  const evSelect = $("att-event");
  const evPrev = evSelect.value;
  evSelect.innerHTML = "";
  if (events.length === 0) {
    evSelect.appendChild(new Option("No events available", ""));
  } else {
    evSelect.appendChild(new Option("Select an event…", ""));
    events.forEach((ev) => evSelect.appendChild(new Option(ev.title + " (" + ev.eventDate + ")", ev.id)));
    if (events.some((ev) => String(ev.id) === evPrev)) evSelect.value = evPrev;
  }

  const volSelect = $("hours-volunteer");
  const volPrev = volSelect.value;
  volSelect.innerHTML = "";
  if (volunteers.length === 0) {
    volSelect.appendChild(new Option("No volunteers registered yet", ""));
  } else {
    volSelect.appendChild(new Option("Select a volunteer…", ""));
    volunteers.forEach((v) => volSelect.appendChild(new Option(v.name + " (" + v.email + ")", v.id)));
    if (volunteers.some((v) => String(v.id) === volPrev)) volSelect.value = volPrev;
  }
}

// Loads and shows the attendance list of the selected event
async function loadAttendance() {
  const eventId = Number($("att-event").value);
  const ev = events.find((e) => e.id === eventId);
  const body = $("att-body");
  body.innerHTML = "";

  if (!ev) {
    $("att-summary").classList.add("hidden");
    $("att-table").classList.add("hidden");
    $("att-empty").classList.remove("hidden");
    $("att-empty").textContent = "Select an event to record attendance.";
    return;
  }

  const summary = $("att-summary");
  summary.innerHTML = "";
  const when = document.createElement("span");
  when.textContent = ev.eventDate + ", " + shortTime(ev.startTime) + " – " + shortTime(ev.endTime) + " · " + ev.location;
  const badge = document.createElement("span");
  badge.className = "badge badge-" + ev.status;
  badge.textContent = STATUS_LABELS[ev.status] || ev.status;
  const windowOpen = isEventWindowOpen(ev);
  const hint = document.createElement("span");
  hint.textContent = windowOpen
    ? "Check-in / check-out is open now."
    : "Check-in / check-out is only available on " + ev.eventDate + " between " + shortTime(ev.startTime) + " and " + shortTime(ev.endTime) + ".";
  summary.append(makeEventThumb(ev.id, ev.title, "avatar-sm"), when, badge, hint);
  summary.classList.remove("hidden");

  $("att-loading").classList.remove("hidden");
  $("att-empty").classList.add("hidden");
  let rows = [];
  try {
    rows = await api(EVENTS_URL + "/" + eventId + "/attendance");
  } catch (e) {
    $("att-loading").classList.add("hidden");
    $("att-table").classList.add("hidden");
    showError("Failed to load attendance: " + e.message);
    return;
  }
  $("att-loading").classList.add("hidden");

  if (rows.length === 0) {
    $("att-table").classList.add("hidden");
    $("att-empty").classList.remove("hidden");
    $("att-empty").textContent = "No volunteers are assigned to this event yet. Use the Assignments tab first.";
    return;
  }
  $("att-table").classList.remove("hidden");

  rows.forEach((a) => {
    const tr = document.createElement("tr");
    [["Volunteer", a.volunteerName], ["Check-in", fmtDateTime(a.checkInTime)],
     ["Check-out", fmtDateTime(a.checkOutTime)], ["Hours", fmtHours(a.hoursWorked)]].forEach(([label, value]) => {
      const td = document.createElement("td");
      td.dataset.label = label;
      if (label === "Volunteer") td.appendChild(personCell(a.volunteerId, a.volunteerName));
      else td.textContent = value;
      tr.appendChild(td);
    });

    const statusTd = document.createElement("td");
    statusTd.dataset.label = "Status";
    const statusBadge = document.createElement("span");
    statusBadge.className = "badge badge-" + a.attendanceStatus;
    statusBadge.textContent = a.attendanceStatus.charAt(0) + a.attendanceStatus.slice(1).toLowerCase();
    statusTd.appendChild(statusBadge);
    tr.appendChild(statusTd);

    const actions = document.createElement("td");
    actions.dataset.label = "Actions";
    const wrap = document.createElement("div");
    wrap.className = "row-actions";
    const addAction = (text, cls, path, doneText, needsWindow) => {
      const btn = document.createElement("button");
      btn.className = "btn btn-sm " + cls;
      btn.textContent = text;
      if (needsWindow && !windowOpen) {
        btn.disabled = true;
        btn.title = "Only available on the event date, between start and end time";
      }
      btn.addEventListener("click", () => attendanceAction(a, path, doneText));
      wrap.appendChild(btn);
    };
    // Only offer the actions that make sense (the backend checks the rules again)
    if (!a.checkInTime) addAction("Check In", "btn-primary", "check-in", "checked in", true);
    if (a.checkInTime && !a.checkOutTime) addAction("Check Out", "btn-primary", "check-out", "checked out", true);
    if (!a.checkInTime && a.attendanceStatus !== "ABSENT") addAction("Mark Absent", "btn-danger", "absent", "marked absent");
    const timesBtn = document.createElement("button");
    timesBtn.className = "btn btn-sm btn-outline";
    timesBtn.textContent = "Edit times";
    timesBtn.addEventListener("click", () => askEditTimes(a));
    wrap.appendChild(timesBtn);
    actions.appendChild(wrap);
    tr.appendChild(actions);
    body.appendChild(tr);
  });
}

async function attendanceAction(assignment, path, doneText) {
  hideMessage();
  try {
    const result = await api(ASSIGNMENTS_URL + "/" + assignment.id + "/" + path, { method: "PUT" });
    let text = assignment.volunteerName + " " + doneText + ".";
    if (path === "check-out") text += " Hours worked: " + fmtHours(result.hoursWorked) + ".";
    showMessage(text, "success");
    await loadAttendance();
    loadHours();
    loadTopVolunteers();
    loadAssignments();
  } catch (e) {
    showError(e.message);
  }
}

// Total hours and per-event hours of the selected volunteer
async function loadHours() {
  const volunteerId = Number($("hours-volunteer").value);
  const body = $("hours-body");
  body.innerHTML = "";
  $("hours-summary").classList.add("hidden");
  $("hours-table").classList.add("hidden");

  if (!volunteerId) {
    $("hours-empty").classList.remove("hidden");
    $("hours-empty").textContent = "Select a volunteer to see their hours.";
    return;
  }

  let data;
  try {
    data = await api(API_URL + "/" + volunteerId + "/hours");
  } catch (e) {
    $("hours-empty").classList.add("hidden");
    showError("Failed to load hours: " + e.message);
    return;
  }

  const summary = $("hours-summary");
  summary.innerHTML = "";
  const total = document.createElement("span");
  total.className = "count";
  total.textContent = data.volunteerName + ": " + fmtHours(data.totalHours) + " in total";
  summary.append(makeAvatar(volunteerId, data.volunteerName, "avatar-sm"), total);
  summary.classList.remove("hidden");

  if (data.events.length === 0) {
    $("hours-empty").classList.remove("hidden");
    $("hours-empty").textContent = "This volunteer has no event assignments yet.";
    return;
  }
  $("hours-empty").classList.add("hidden");
  $("hours-table").classList.remove("hidden");

  data.events.forEach((e) => {
    const tr = document.createElement("tr");
    [["Event", e.eventTitle], ["Date", e.eventDate],
     ["Attendance", e.attendanceStatus.charAt(0) + e.attendanceStatus.slice(1).toLowerCase()],
     ["Hours", fmtHours(e.hoursWorked)]].forEach(([label, value]) => {
      const td = document.createElement("td");
      td.dataset.label = label;
      td.textContent = value;
      tr.appendChild(td);
    });
    body.appendChild(tr);
  });
}

// Top 5 volunteers by hours (dashboard)
async function loadTopVolunteers() {
  const list = $("top-list");
  let top = [];
  try {
    top = await api("/api/reports/top-volunteers");
  } catch (e) {
    list.innerHTML = "";
    $("top-empty").classList.remove("hidden");
    $("top-empty").textContent = "Could not load top volunteers: " + e.message;
    return;
  }
  list.innerHTML = "";
  if (top.length === 0) {
    $("top-empty").classList.remove("hidden");
    $("top-empty").textContent = "No hours recorded yet. Check volunteers in and out on the Attendance page.";
    return;
  }
  $("top-empty").classList.add("hidden");
  top.forEach((t) => {
    const li = document.createElement("li");
    const who = document.createElement("div");
    who.className = "who";
    who.textContent = t.name;
    const email = document.createElement("small");
    email.textContent = t.email;
    who.appendChild(email);
    const person = document.createElement("div");
    person.className = "person";
    person.append(makeAvatar(t.volunteerId, t.name, "avatar-sm"), who);
    const hrs = document.createElement("div");
    hrs.className = "hrs";
    hrs.textContent = fmtHours(t.totalHours);
    li.append(person, hrs);
    list.appendChild(li);
  });
}

$("att-event").addEventListener("change", loadAttendance);
$("hours-volunteer").addEventListener("change", loadHours);
$("att-refresh-btn").addEventListener("click", () => { loadEvents(); loadAttendance(); loadHours(); });

// =====================================================================
// PAGINATION (10 rows per page, done in the browser)
// =====================================================================
function pageSlice(list, page) {
  const pages = Math.max(1, Math.ceil(list.length / PAGE_SIZE));
  const current = Math.min(Math.max(page, 1), pages);
  return { items: list.slice((current - 1) * PAGE_SIZE, current * PAGE_SIZE), page: current, pages };
}

function renderPager(containerId, pg, total, onChange) {
  const box = $(containerId);
  box.innerHTML = "";
  if (pg.pages <= 1) {
    box.classList.add("hidden");
    return;
  }
  const prev = document.createElement("button");
  prev.className = "btn btn-outline btn-sm";
  prev.textContent = "‹ Previous";
  prev.disabled = pg.page <= 1;
  prev.addEventListener("click", () => onChange(pg.page - 1));

  const info = document.createElement("span");
  info.className = "pager-info";
  info.textContent = "Page " + pg.page + " of " + pg.pages + " · " + total + " total";

  const next = document.createElement("button");
  next.className = "btn btn-outline btn-sm";
  next.textContent = "Next ›";
  next.disabled = pg.page >= pg.pages;
  next.addEventListener("click", () => onChange(pg.page + 1));

  box.append(prev, info, next);
  box.classList.remove("hidden");
}

// =====================================================================
// JOIN / LEAVE AN EVENT (volunteers)
// =====================================================================
function participationCell(ev) {
  const td = document.createElement("td");
  td.dataset.label = "My participation";
  const mine = myAssignments.find((a) => a.eventId === ev.id && a.status !== "CANCELLED");
  const wrap = document.createElement("div");
  wrap.className = "row-actions";

  if (mine) {
    const badge = document.createElement("span");
    badge.className = "badge badge-" + mine.status;
    badge.textContent = mine.status === "CONFIRMED" ? "Confirmed" : "Joined";
    wrap.appendChild(badge);
    const leave = document.createElement("button");
    leave.className = "btn btn-outline btn-sm";
    leave.textContent = "Leave";
    if (mine.checkInTime) {
      leave.disabled = true;
      leave.title = "You have already checked in";
    }
    leave.addEventListener("click", () => leaveEvent(ev));
    wrap.appendChild(leave);
  } else {
    const join = document.createElement("button");
    join.className = "btn btn-primary btn-sm";
    join.textContent = "Join event";
    const open = ev.status === "UPCOMING" || ev.status === "ONGOING";
    const full = activeCount(ev.id) >= ev.requiredVolunteers;
    if (!open) {
      join.disabled = true;
      join.title = "This event is " + (STATUS_LABELS[ev.status] || ev.status).toLowerCase();
    } else if (full) {
      join.disabled = true;
      join.title = "This event is full";
    }
    join.addEventListener("click", () => joinEvent(ev));
    wrap.appendChild(join);
  }
  td.appendChild(wrap);
  return td;
}

async function joinEvent(ev) {
  hideMessage();
  try {
    await api(EVENTS_URL + "/" + ev.id + "/join", { method: "POST" });
    showMessage("You joined \"" + ev.title + "\". A confirmation email is being sent.", "success");
  } catch (e) {
    showError(e.message);   // for example a schedule conflict or a full event
  }
  await loadEvents();
  await loadMyActivity();
}

// Asks for a reason first; the server turns it into a notification for admins and coordinators
let leaveTarget = null;

function leaveEvent(ev) {
  leaveTarget = ev;
  $("leave-title").textContent = "Leave \"" + ev.title + "\"?";
  $("leave-reason").value = "";
  $("leave-error").classList.add("hidden");
  $("leave-overlay").classList.remove("hidden");
  $("leave-reason").focus();
}

function closeLeaveDialog() {
  $("leave-overlay").classList.add("hidden");
  leaveTarget = null;
}

$("leave-no").addEventListener("click", closeLeaveDialog);

$("leave-yes").addEventListener("click", async () => {
  const reason = $("leave-reason").value.trim();
  if (reason.length < 3) {
    $("leave-error").textContent = "Please enter the reason you are leaving.";
    $("leave-error").classList.remove("hidden");
    return;
  }
  const ev = leaveTarget;
  closeLeaveDialog();
  hideMessage();
  try {
    await api(EVENTS_URL + "/" + ev.id + "/leave", { method: "POST", body: JSON.stringify({ reason }) });
    showMessage("You left \"" + ev.title + "\". The organisers have been told why.", "success");
  } catch (e) {
    showError(e.message);
  }
  await loadEvents();
  await loadMyActivity();
});

// =====================================================================
// CORRECT CHECK-IN / CHECK-OUT TIMES (admin, coordinator)
// =====================================================================
function askEditTimes(a) {
  timesTarget = a;
  $("times-text").textContent = a.volunteerName + " – " + a.eventTitle
    + ". Leave a field empty to keep it as it is. Times cannot be in the future.";
  $("times-in").value = a.checkInTime ? a.checkInTime.slice(0, 16) : "";
  $("times-out").value = a.checkOutTime ? a.checkOutTime.slice(0, 16) : "";
  $("times-error").textContent = "";
  $("times-overlay").classList.remove("hidden");
}

function closeTimesDialog() {
  timesTarget = null;
  $("times-overlay").classList.add("hidden");
}

$("times-cancel").addEventListener("click", closeTimesDialog);

$("times-save").addEventListener("click", async () => {
  const checkInTime = $("times-in").value || null;
  const checkOutTime = $("times-out").value || null;
  if (!checkInTime && !checkOutTime) {
    $("times-error").textContent = "Enter a check-in and/or check-out time.";
    return;
  }
  const target = timesTarget;
  const button = $("times-save");
  button.disabled = true;
  try {
    await api(ASSIGNMENTS_URL + "/" + target.id + "/attendance", {
      method: "PUT",
      body: JSON.stringify({ checkInTime, checkOutTime }),
    });
    closeTimesDialog();
    showMessage("Attendance times updated for " + target.volunteerName + ".", "success");
    await loadAttendance();
    loadHours();
    loadTopVolunteers();
    loadAssignments();
  } catch (e) {
    $("times-error").textContent = e.message;   // keep the dialog open so it can be fixed
  } finally {
    button.disabled = false;
  }
});

// =====================================================================
// CHANGE PASSWORD (everyone)
// =====================================================================
$("pw-btn").addEventListener("click", () => {
  ["pw-current", "pw-new", "pw-confirm"].forEach((id) => { $(id).value = ""; });
  $("pw-error").textContent = "";
  $("pw-overlay").classList.remove("hidden");
  $("pw-current").focus();
});

$("pw-cancel").addEventListener("click", () => $("pw-overlay").classList.add("hidden"));

$("pw-save").addEventListener("click", async () => {
  const currentPassword = $("pw-current").value;
  const newPassword = $("pw-new").value;
  let problem = "";
  if (!currentPassword) problem = "Enter your current password.";
  else if (newPassword.length < 8) problem = "The new password must be at least 8 characters.";
  else if (newPassword !== $("pw-confirm").value) problem = "The two new passwords do not match.";
  if (problem) { $("pw-error").textContent = problem; return; }

  const button = $("pw-save");
  button.disabled = true;
  try {
    await api("/api/auth/password", { method: "PUT", body: JSON.stringify({ currentPassword, newPassword }) });
    $("pw-overlay").classList.add("hidden");
    showMessage("Your password was changed.", "success");
  } catch (e) {
    $("pw-error").textContent = e.message;
  } finally {
    button.disabled = false;
  }
});

// =====================================================================
// "CREATE A LOGIN?" QUESTION (admin)
// =====================================================================
function askCreateLogin(volunteer) {
  loginTarget = volunteer;
  $("login-text").textContent = "Create a login so " + volunteer.name + " can sign in with the email "
    + volunteer.email + "? You choose their first password.";
  $("login-password").value = "";
  $("login-error").textContent = "";
  $("login-overlay").classList.remove("hidden");
  $("login-password").focus();
}

function closeLoginDialog() {
  loginTarget = null;
  $("login-overlay").classList.add("hidden");
}

$("login-no").addEventListener("click", closeLoginDialog);

$("login-yes").addEventListener("click", async () => {
  const password = $("login-password").value;
  if (password.length < 6) {
    $("login-error").textContent = "Password must be at least 6 characters.";
    return;
  }
  const volunteer = loginTarget;
  const button = $("login-yes");
  button.disabled = true;
  try {
    await api(USERS_URL, {
      method: "POST",
      body: JSON.stringify({ name: volunteer.name, email: volunteer.email, password, role: "VOLUNTEER", volunteerId: volunteer.id }),
    });
    closeLoginDialog();
    showMessage("Login created. " + volunteer.name + " can now sign in with " + volunteer.email + ".", "success");
    await loadVolunteers();
  } catch (e) {
    // keep the dialog open so the problem can be fixed (for example: that email is already used by another login)
    $("login-error").textContent = e.message;
  } finally {
    button.disabled = false;
  }
});

// =====================================================================
// USERS (admin only)
// =====================================================================
let users = [];

async function loadUsers() {
  $("users-loading").classList.remove("hidden");
  $("users-empty").classList.add("hidden");
  $("users-table").classList.add("hidden");
  try {
    await loadVolunteers();            // needed for the "link to volunteer" dropdown
    users = await api(USERS_URL);
  } catch (e) {
    users = [];
    $("users-loading").classList.add("hidden");
    showError("Failed to load users: " + e.message);
    return;
  }
  $("users-loading").classList.add("hidden");
  renderUsers();
  renderUserVolunteerSelect();
}

function roleLabel(r) {
  return r.charAt(0) + r.slice(1).toLowerCase();
}

function renderUsers() {
  const body = $("users-body");
  body.innerHTML = "";
  if (users.length === 0) {
    $("users-table").classList.add("hidden");
    $("users-empty").classList.remove("hidden");
    $("users-empty").textContent = "No users yet.";
    return;
  }
  $("users-empty").classList.add("hidden");
  $("users-table").classList.remove("hidden");

  users.forEach((u) => {
    const isMe = u.id === session.user.userId;
    const tr = document.createElement("tr");
    [["Name", u.name + (isMe ? " (you)" : "")], ["Email", u.email]].forEach(([label, value]) => {
      const td = document.createElement("td");
      td.dataset.label = label;
      if (label === "Name" && u.volunteerId != null) td.appendChild(personCell(u.volunteerId, value));
      else td.textContent = value;
      tr.appendChild(td);
    });

    // Role dropdown (you cannot change your own role)
    const roleTd = document.createElement("td");
    roleTd.dataset.label = "Role";
    const select = document.createElement("select");
    select.className = "role-select";
    ["ADMIN", "COORDINATOR", "VOLUNTEER"].forEach((r) => select.appendChild(new Option(roleLabel(r), r)));
    select.value = u.role;
    select.disabled = isMe;
    select.addEventListener("change", () => changeUserRole(u, select.value));
    roleTd.appendChild(select);
    tr.appendChild(roleTd);

    const volTd = document.createElement("td");
    volTd.dataset.label = "Volunteer ID";
    volTd.textContent = u.volunteerId != null ? u.volunteerId : "–";
    tr.appendChild(volTd);

    const actions = document.createElement("td");
    actions.dataset.label = "Actions";
    if (!isMe) {
      const delBtn = document.createElement("button");
      delBtn.className = "btn btn-danger btn-sm";
      delBtn.textContent = "Delete";
      delBtn.addEventListener("click", () => {
        pendingDelete = { type: "user", id: u.id };
        $("confirm-title").textContent = "Delete user?";
        $("confirm-text").textContent = "Delete the account of " + u.name + " (" + u.email + ")? They will no longer be able to log in.";
        $("confirm-overlay").classList.remove("hidden");
      });
      actions.appendChild(delBtn);
    }
    tr.appendChild(actions);
    body.appendChild(tr);
  });
}

async function changeUserRole(user, role) {
  hideMessage();
  try {
    await api(USERS_URL + "/" + user.id + "/role", { method: "PUT", body: JSON.stringify({ role }) });
    showMessage(user.name + " is now " + roleLabel(role) + ".", "success");
  } catch (e) {
    showError(e.message);
  }
  await loadUsers();
}

// Volunteers that do not have a login yet can be linked to a new VOLUNTEER account
function renderUserVolunteerSelect() {
  const select = $("user-new-volunteer");
  select.innerHTML = "";
  const linked = users.filter((u) => u.volunteerId != null).map((u) => u.volunteerId);
  const free = volunteers.filter((v) => !linked.includes(v.id));
  if (free.length === 0) {
    select.appendChild(new Option("No volunteer records without a login", ""));
    return;
  }
  select.appendChild(new Option("Select a volunteer…", ""));
  free.forEach((v) => select.appendChild(new Option(v.name + " (" + v.email + ")", v.id)));
}

$("user-new-role").addEventListener("change", () => {
  $("user-volunteer-field").classList.toggle("hidden", $("user-new-role").value !== "VOLUNTEER");
});

$("user-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  hideMessage();
  $("user-form-error").textContent = "";
  const payload = {
    name: $("user-new-name").value.trim(),
    email: $("user-new-email").value.trim(),
    password: $("user-new-password").value,
    role: $("user-new-role").value,
    volunteerId: $("user-new-volunteer").value ? Number($("user-new-volunteer").value) : null,
  };
  let problem = "";
  if (payload.name.length < 2) problem = "Please enter the name.";
  else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(payload.email)) problem = "Please enter a valid email address.";
  else if (payload.password.length < 6) problem = "Password must be at least 6 characters.";
  else if (payload.role === "VOLUNTEER" && !payload.volunteerId) problem = "Select the volunteer record to link.";
  if (problem) { $("user-form-error").textContent = problem; return; }
  if (payload.role !== "VOLUNTEER") payload.volunteerId = null;

  const button = $("user-create-btn");
  button.disabled = true;
  try {
    await api(USERS_URL, { method: "POST", body: JSON.stringify(payload) });
    showMessage("User " + payload.email + " created.", "success");
    $("user-form").reset();
    $("user-volunteer-field").classList.add("hidden");
    await loadUsers();
  } catch (e) {
    showError(e.message);
  } finally {
    button.disabled = false;
  }
});

$("users-refresh-btn").addEventListener("click", loadUsers);

// =====================================================================
// MY PROFILE, EVENTS & HOURS (volunteers)
// =====================================================================
async function loadMyActivity() {
  if (!isVolunteer || !myVolunteerId) return;
  let profile, mine;
  try {
    [profile, mine] = await Promise.all([
      api(API_URL + "/" + myVolunteerId),
      api(API_URL + "/" + myVolunteerId + "/events"),
    ]);
  } catch (e) {
    showError("Failed to load your information: " + e.message);
    return;
  }

  $("profile-name").value = profile.name;
  $("profile-email").value = profile.email;
  $("profile-phone").value = profile.phone;

  myAssignments = mine;
  renderEvents();
  const active = mine.filter((a) => a.status !== "CANCELLED");
  const totalHours = active.reduce((sum, a) => sum + (a.hoursWorked || 0), 0);
  $("stat-myhours").textContent = totalHours.toFixed(2) + " h";
  $("stat-myevents").textContent = active.length;

  const summary = $("my-summary");
  summary.innerHTML = "";
  const total = document.createElement("span");
  total.className = "count";
  total.textContent = "Total: " + fmtHours(totalHours) + " across " + active.length + " event(s)";
  summary.appendChild(total);
  summary.classList.remove("hidden");

  const body = $("my-body");
  body.innerHTML = "";
  if (mine.length === 0) {
    $("my-table").classList.add("hidden");
    $("my-empty").classList.remove("hidden");
    $("my-empty").textContent = "You have not been assigned to any events yet.";
    return;
  }
  $("my-empty").classList.add("hidden");
  $("my-table").classList.remove("hidden");

  mine.slice().sort((a, b) => b.eventDate.localeCompare(a.eventDate)).forEach((a) => {
    const tr = document.createElement("tr");
    [["Event", a.eventTitle], ["Date", a.eventDate],
     ["Time", shortTime(a.startTime) + " – " + shortTime(a.endTime)], ["Location", a.location],
     ["Assignment", roleLabel(a.status)], ["Attendance", roleLabel(a.attendanceStatus)],
     ["Hours", fmtHours(a.hoursWorked)]].forEach(([label, value]) => {
      const td = document.createElement("td");
      td.dataset.label = label;
      if (label === "Event") td.appendChild(eventCell(a.eventId, value));
      else td.textContent = value;
      tr.appendChild(td);
    });
    body.appendChild(tr);
  });
}

$("profile-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  hideMessage();
  const name = $("profile-name").value.trim();
  const email = $("profile-email").value.trim();
  const phone = $("profile-phone").value.trim();

  const errors = {};
  if (name.length < 2) errors.name = "Please enter your full name.";
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) errors.email = "Please enter a valid email address.";
  if (!/^[6-9][0-9]{9}$/.test(phone)) errors.phone = "Phone number must be 10 digits and start with 6, 7, 8 or 9.";
  ["name", "email", "phone"].forEach((key) => {
    $("profile-" + key + "-error").textContent = errors[key] || "";
  });
  if (Object.keys(errors).length) return;

  const button = $("profile-save-btn");
  button.disabled = true;
  try {
    await api(API_URL + "/" + myVolunteerId, { method: "PUT", body: JSON.stringify({ name, email, phone }) });
    showMessage("Your profile was updated.", "success");
    await loadMyActivity();
  } catch (e) {
    showError(e.message);
  } finally {
    button.disabled = false;
  }
});

$("my-refresh-btn").addEventListener("click", loadMyActivity);

// =====================================================================
// ROLE-BASED UI & LOGOUT
// =====================================================================
// Elements with data-roles="ADMIN,COORDINATOR" are hidden for every other role.
// (This only tidies the screen. The real protection is in the backend, which answers 403.)
function applyRole() {
  document.querySelectorAll("[data-roles]").forEach((el) => {
    if (!el.dataset.roles.split(",").includes(role)) el.classList.add("hidden");
  });
  $("user-name").textContent = session.user.name;
  $("user-role").textContent = roleLabel(role);
  $("header-avatar").replaceChildren(makeAvatar(myVolunteerId, session.user.name, "avatar-header"));
  if (isVolunteer) $("profile-avatar").replaceChildren(makeAvatar(myVolunteerId, session.user.name, "avatar-lg"));
  showGreeting();
}

// "Good morning, Siva!" etc., based on the time on the user's own device
const GREETINGS = {
  morning: { text: "Good morning,", icon: "☀️", tip: "A fresh day, a fresh chance to help someone." },
  afternoon: { text: "Good afternoon,", icon: "🌤️", tip: "Keep going. Every hour of help adds up." },
  evening: { text: "Good evening,", icon: "🌇", tip: "Take a moment to be proud of what the community achieved today." },
  night: { text: "Good night,", icon: "🌙", tip: "Rest well. Tomorrow there is more good to do." },
};

function showGreeting() {
  const now = new Date();
  const hour = now.getHours();
  const period = hour >= 5 && hour < 12 ? "morning" : hour >= 12 && hour < 17 ? "afternoon" : hour >= 17 && hour < 21 ? "evening" : "night";
  const g = GREETINGS[period];
  const firstName = session.user.name.trim().split(/\s+/)[0];

  $("hero").className = "hero hero-" + period;
  $("greeting-text").textContent = g.text;
  $("greeting-name").textContent = firstName + "!";
  $("greeting-icon").textContent = g.icon;
  $("greeting-date").textContent = now.toLocaleDateString([], { weekday: "long", day: "numeric", month: "long" });
  $("greeting-sub").textContent = isVolunteer
    ? "Welcome back. Thank you for making a difference in your community."
    : "Welcome back. Here is what is happening in your community today.";
  $("greeting-tip").textContent = g.tip;
  $("v-greeting").textContent = g.text + " " + firstName + "! " + g.icon;
}

$("logout-btn").addEventListener("click", () => goToLogin());

// =====================================================================
// PROFILE PHOTOS
// =====================================================================
// Every place that shows a volunteer builds an avatar with makeAvatar(). It starts with the initials and
// is filled with the real picture as soon as it is known that a photo exists (see fillAvatars).
function initials(name) {
  const parts = (name || "?").trim().split(/\s+/);
  return ((parts[0] || "?")[0] + (parts.length > 1 ? parts[parts.length - 1][0] : "")).toUpperCase();
}

function makeAvatar(volunteerId, name, sizeClass) {
  const el = document.createElement("span");
  el.className = "avatar " + (sizeClass || "");
  el.textContent = initials(name);
  if (volunteerId != null) {
    el.dataset.vid = volunteerId;
    el.dataset.name = name || "";
    fillAvatar(el);
  }
  return el;
}

// Avatar + name, used in table cells
function personCell(volunteerId, name) {
  const box = document.createElement("div");
  box.className = "person";
  const label = document.createElement("span");
  label.className = "person-name";
  label.textContent = name;
  box.append(makeAvatar(volunteerId, name, "avatar-sm"), label);
  return box;
}

// Downloads one picture with the login token (a plain <img src> cannot send it) and remembers it
async function photoUrl(kind, id, version) {
  const key = kind + ":" + id + ":" + version;
  if (photoUrls.has(key)) return photoUrls.get(key);
  const response = await fetch((kind === "event" ? EVENTS_URL : API_URL) + "/" + id + "/photo?v=" + version, {
    headers: { Authorization: "Bearer " + session.token },
  });
  if (!response.ok) throw new Error("no photo");
  const url = URL.createObjectURL(await response.blob());
  photoUrls.set(key, url);
  return url;
}

function fillAvatar(el) {
  const kind = el.dataset.kind === "event" ? "event" : "volunteer";
  const version = (kind === "event" ? eventPhotos : photos)[el.dataset.vid];
  if (version === undefined) {                       // no photo: show the initials (or the calendar icon)
    if (el.dataset.ver) { el.textContent = kind === "event" ? EVENT_ICON : initials(el.dataset.name); delete el.dataset.ver; }
    return;
  }
  if (el.dataset.ver === String(version)) return;     // already showing the current picture
  el.dataset.ver = String(version);
  photoUrl(kind, el.dataset.vid, version).then((url) => {
    if (el.dataset.ver !== String(version)) return;   // a newer picture arrived meanwhile
    const img = document.createElement("img");
    img.alt = el.dataset.name;
    img.src = url;
    el.replaceChildren(img);
  }).catch(() => { delete el.dataset.ver; });
}

function fillAvatars() {
  document.querySelectorAll(".avatar[data-vid]").forEach(fillAvatar);
}

async function loadPhotos() {
  try {
    const list = await api(API_URL + "/photos");
    photos = {};
    list.forEach((p) => { photos[p.volunteerId] = p.version; });
    fillAvatars();
    updatePhotoButtons();
  } catch (e) { /* pictures are optional: the initials stay */ }
  await loadEventPhotos();
}

async function loadEventPhotos() {
  try {
    const list = await api(EVENTS_URL + "/photos");
    eventPhotos = {};
    list.forEach((p) => { eventPhotos[p.eventId] = p.version; });
    fillAvatars();
  } catch (e) { /* pictures are optional */ }
}

// Event picture (small), with a calendar icon until a picture exists
const EVENT_ICON = "📅";
function makeEventThumb(eventId, title, sizeClass) {
  const el = document.createElement("span");
  el.className = "avatar avatar-event " + (sizeClass || "");
  el.textContent = EVENT_ICON;
  el.dataset.kind = "event";
  el.dataset.vid = eventId;
  el.dataset.name = title || "";
  fillAvatar(el);
  return el;
}

// Event picture + title, used in table cells
function eventCell(eventId, title) {
  const box = document.createElement("div");
  box.className = "person";
  const label = document.createElement("span");
  label.className = "person-name";
  label.textContent = title;
  box.append(makeEventThumb(eventId, title), label);
  return box;
}

// Shrinks a picture so its longest side is at most 1280 px (keeps the shape) before it is uploaded
function shrinkToFit(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      const scale = Math.min(1, 1280 / Math.max(img.width, img.height));
      const canvas = document.createElement("canvas");
      canvas.width = Math.round(img.width * scale);
      canvas.height = Math.round(img.height * scale);
      canvas.getContext("2d").drawImage(img, 0, 0, canvas.width, canvas.height);
      URL.revokeObjectURL(url);
      canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error("fail"))), "image/jpeg", 0.85);
    };
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error("This file is not a picture the browser can open.")); };
    img.src = url;
  });
}

// Uploads (or removes) the event picture chosen in the form, once the event itself is saved
async function saveEventPhoto(eventId) {
  const file = $("event-photo").files[0];
  if (file) {
    const blob = await shrinkToFit(file);
    const body = new FormData();
    body.append("file", blob, "event.jpg");
    const response = await fetch(EVENTS_URL + "/" + eventId + "/photo", {
      method: "PUT", headers: { Authorization: "Bearer " + session.token }, body,
    });
    if (!response.ok) {
      let detail = "";
      try { detail = (await response.json()).message; } catch (e) { /* no body */ }
      throw new Error(detail || "status " + response.status);
    }
  } else if (pendingEventPhotoRemoval && eventPhotos[eventId] !== undefined) {
    await api(EVENTS_URL + "/" + eventId + "/photo", { method: "DELETE" });
  }
}

// Preview in the form: the chosen file, or the event's current picture while editing
function showEventPhotoPreview(localFile, eventId, title) {
  const box = $("event-photo-preview");
  box.replaceChildren();
  if (localFile) {
    const holder = document.createElement("span");
    holder.className = "avatar avatar-event avatar-event-lg";
    const img = document.createElement("img");
    img.alt = "Chosen photo";
    img.src = URL.createObjectURL(localFile);
    holder.appendChild(img);
    box.appendChild(holder);
  } else if (eventId) {
    box.appendChild(makeEventThumb(eventId, title, "avatar-event-lg"));
  } else {
    const holder = document.createElement("span");
    holder.className = "avatar avatar-event avatar-event-lg";
    holder.textContent = EVENT_ICON;
    box.appendChild(holder);
  }
  const editing = $("event-id").value;
  $("event-photo-remove").classList.toggle("hidden",
    !(editing && eventPhotos[editing] !== undefined && !pendingEventPhotoRemoval && !localFile));
}

$("event-photo").addEventListener("change", () => {
  const file = $("event-photo").files[0];
  $("event-photo-error").textContent = "";
  if (file && !file.type.startsWith("image/")) {
    $("event-photo").value = "";
    $("event-photo-error").textContent = "Please choose a picture (JPG, PNG, GIF or WebP).";
  }
  pendingEventPhotoRemoval = false;
  showEventPhotoPreview($("event-photo").files[0], $("event-id").value, $("event-title").value);
});

$("event-photo-remove").addEventListener("click", () => {
  pendingEventPhotoRemoval = true;
  showEventPhotoPreview(null, null, "");
  $("event-photo-remove").classList.add("hidden");
});

function updatePhotoButtons() {
  if (!isVolunteer) return;
  const has = photos[myVolunteerId] !== undefined;
  $("photo-btn").textContent = has ? "Change photo" : "Add photo";
  $("photo-remove").classList.toggle("hidden", !has);
}

// Shrinks the chosen picture to 512 px (JPEG) in the browser before it is uploaded
function shrinkImage(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      const side = Math.min(img.width, img.height);               // centre square crop
      const out = Math.min(512, side);
      const canvas = document.createElement("canvas");
      canvas.width = out;
      canvas.height = out;
      canvas.getContext("2d").drawImage(img, (img.width - side) / 2, (img.height - side) / 2, side, side, 0, 0, out, out);
      URL.revokeObjectURL(url);
      canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error("fail"))), "image/jpeg", 0.88);
    };
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error("This file is not a picture the browser can open.")); };
    img.src = url;
  });
}

if (isVolunteer) {
  $("photo-btn").addEventListener("click", () => $("photo-input").click());
  $("photo-input").addEventListener("change", async () => {
    const file = $("photo-input").files[0];
    $("photo-input").value = "";
    if (!file) return;
    hideMessage();
    if (!file.type.startsWith("image/")) return showError("Please choose a picture (JPG, PNG, GIF or WebP).");
    $("photo-btn").disabled = true;
    try {
      const blob = await shrinkImage(file);
      const body = new FormData();
      body.append("file", blob, "photo.jpg");
      // no Content-Type here: the browser sets it, including the multipart boundary
      const response = await fetch(API_URL + "/" + myVolunteerId + "/photo", {
        method: "PUT", headers: { Authorization: "Bearer " + session.token }, body,
      });
      if (!response.ok) {
        let detail = "";
        try { detail = (await response.json()).message; } catch (e) { /* no body */ }
        throw new Error(detail || "The photo could not be saved (status " + response.status + ").");
      }
      showMessage("Your photo was saved.", "success");
      await loadPhotos();
    } catch (e) {
      showError(e.message);
    } finally {
      $("photo-btn").disabled = false;
    }
  });
  $("photo-remove").addEventListener("click", async () => {
    hideMessage();
    try {
      await api(API_URL + "/" + myVolunteerId + "/photo", { method: "DELETE" });
      showMessage("Your photo was removed.", "success");
      await loadPhotos();
    } catch (e) {
      showError(e.message);
    }
  });
}

// Whatever page part is drawn later (tables, lists, dialogs) gets its pictures automatically
new MutationObserver(() => fillAvatars()).observe(document.body, { childList: true, subtree: true });

// ---------- Start ----------
if (session) {
  applyRole();
  if (isVolunteer) {
    initVolunteerHome();
    $("volunteer-site").classList.remove("hidden");
    $("volunteer-home").classList.remove("hidden");
    document.querySelector("main.container").classList.add("hidden");
  }
  showEventPhotoPreview(null, null, "");
  loadPhotos();
  loadEvents();
  if (isStaff) {
    loadCategories();
    loadVolunteers();
    loadAssignments();
    loadTopVolunteers();
  }
  if (isVolunteer) loadMyActivity();
}

// ---------- Event categories (admin-managed list) ----------
// Fills the event form dropdown; a category that was since removed from the list is still shown for old events
function fillCategorySelect(selected) {
  const select = $("event-category");
  const keep = selected === undefined ? select.value : selected || "";
  select.replaceChildren(new Option("No category", ""));
  categories.forEach((c) => select.appendChild(new Option(c.name, c.name)));
  if (keep && !categories.some((c) => c.name === keep)) select.appendChild(new Option(keep, keep));
  select.value = keep;
}

function setEventCategory(value) {
  fillCategorySelect(value || "");
}

async function loadCategories() {
  try {
    categories = await api(CATEGORIES_URL);
  } catch (e) {
    categories = [];
  }
  fillCategorySelect();
  if (typeof renderCategorySettings === "function") renderCategorySettings();
  if (typeof renderAdminDashboard === "function") renderAdminDashboard();
}
