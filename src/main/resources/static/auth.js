// Login and register page. On success the JWT is stored in localStorage and the dashboard opens.
const TOKEN_KEY = "vms_token";
const USER_KEY = "vms_user";

const $ = (id) => document.getElementById(id);

// ---------- Already logged in? ----------
function tokenIsValid(token) {
  try {
    const payload = JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
    return payload.exp * 1000 > Date.now();
  } catch (e) {
    return false;
  }
}

// A saved token only counts if the server still accepts it; otherwise it is cleared and the login form stays
// (checking only the expiry date opened the dashboard first and then bounced back to login)
try {
  const saved = localStorage.getItem(TOKEN_KEY);
  if (tokenIsValid(saved)) {
    fetch("/api/auth/me", { headers: { Authorization: "Bearer " + saved } })
      .then((response) => {
        if (response.ok) {
          location.replace("index.html");
        } else {
          localStorage.removeItem(TOKEN_KEY);
          localStorage.removeItem(USER_KEY);
        }
      })
      .catch(() => { /* server unreachable: stay on the login page */ });
  }
} catch (e) { /* storage unavailable: stay on the login page */ }

// ---------- Messages ----------
function showMessage(text, type) {
  const box = $("auth-message");
  box.textContent = text;
  box.className = "message " + type;
}
function hideMessage() {
  $("auth-message").className = "message hidden";
}

// Messages passed in the address: ?msg= for problems (for example "Your session has expired"),
// ?ok= for good news (for example after a password reset)
const params = new URLSearchParams(location.search);
if (params.get("msg")) showMessage(params.get("msg"), "error");
if (params.get("ok")) showMessage(params.get("ok"), "success");

// ---------- Views and role tabs ----------
const REMEMBER_KEY = "vms_remember_email";
let role = "volunteer";   // "volunteer" or "admin" (admin = ADMIN or COORDINATOR accounts)

function showView(name) {
  $("auth-card").dataset.view = name;
  $("login-form").classList.toggle("hidden", name !== "login");
  $("register-form").classList.toggle("hidden", name !== "register");
  $("forgot-form").classList.toggle("hidden", name !== "forgot");
  document.title = (name === "register" ? "Register" : name === "forgot" ? "Forgot password" : "Login")
    + " – Community Volunteer Management System";
  if (name === "register") role = "volunteer";   // only volunteers can register
  hideMessage();
  updateRole();
}

function setRole(next) {
  role = next;
  updateRole();
  hideMessage();
}

// Admin accounts are created by an administrator, so the Register form is only available for volunteers
function updateRole() {
  $("role-volunteer").classList.toggle("active", role === "volunteer");
  $("role-admin").classList.toggle("active", role === "admin");
  $("role-volunteer").setAttribute("aria-selected", String(role === "volunteer"));
  $("role-admin").setAttribute("aria-selected", String(role === "admin"));
}

$("role-volunteer").addEventListener("click", () => setRole("volunteer"));
$("role-admin").addEventListener("click", () => setRole("admin"));
$("to-register").addEventListener("click", (event) => { event.preventDefault(); showView("register"); });
$("to-login").addEventListener("click", (event) => { event.preventDefault(); showView("login"); });
$("forgot-link").addEventListener("click", (event) => { event.preventDefault(); showView("forgot"); });
$("back-to-login").addEventListener("click", (event) => { event.preventDefault(); showView("login"); });
if (params.get("forgot")) showView("forgot");
if (params.get("register")) showView("register");

// Remember me: keeps only the email address (never the password) for the next visit
try {
  const saved = localStorage.getItem(REMEMBER_KEY);
  if (saved) { $("login-email").value = saved; $("remember").checked = true; }
} catch (e) { /* storage unavailable */ }

// Continue with Google: the server does the sign-in with Google and sends the browser back here

// ---------- Talking to the API ----------
async function post(url, body) {
  let response;
  try {
    response = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
  } catch (e) {
    throw new Error("Cannot reach the server. Is the application running?");
  }
  let data = null;
  try { data = await response.json(); } catch (e) { /* no body */ }
  if (!response.ok) {
    throw new Error((data && data.message) || "Request failed (status " + response.status + ").");
  }
  return data;
}

function saveSessionAndOpenDashboard(data) {
  try {
    localStorage.setItem(TOKEN_KEY, data.token);
    localStorage.setItem(USER_KEY, JSON.stringify({
      userId: data.userId, name: data.name, email: data.email, role: data.role, volunteerId: data.volunteerId,
    }));
  } catch (e) {
    showMessage("Your browser blocked storage, so the login cannot be kept. Please allow site data.", "error");
    return;
  }
  location.replace("index.html");
}

// ---------- Login ----------
$("login-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  hideMessage();
  const email = $("login-email").value.trim();
  const password = $("login-password").value;
  if (!email || !password) return showMessage("Please enter your email and password.", "error");

  const button = $("login-btn");
  button.disabled = true;
  try {
    const data = await post("/api/auth/login", { email, password });
    const isStaff = data.role === "ADMIN" || data.role === "COORDINATOR";
    if (role === "admin" && !isStaff) return showMessage("This is a volunteer account. Choose the Volunteer tab to log in.", "error");
    if (role === "volunteer" && isStaff) return showMessage("This is an admin account. Choose the Admin tab to log in.", "error");
    try {
      if ($("remember").checked) localStorage.setItem(REMEMBER_KEY, email);
      else localStorage.removeItem(REMEMBER_KEY);
    } catch (e) { /* storage unavailable */ }
    saveSessionAndOpenDashboard(data);
  } catch (e) {
    showMessage(e.message, "error");
  } finally {
    button.disabled = false;
  }
});

// ---------- Register ----------
$("register-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  hideMessage();
  const name = $("reg-name").value.trim();
  const email = $("reg-email").value.trim();
  const phone = $("reg-phone").value.trim();
  const password = $("reg-password").value;
  const confirm = $("reg-confirm").value;

  if (name.length < 2) return showMessage("Please enter your full name.", "error");
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return showMessage("Please enter a valid email address.", "error");
  if (!/^\+?[0-9\s\-()]{7,20}$/.test(phone)) return showMessage("Please enter a valid phone number (7–20 digits).", "error");
  if (password.length < 6) return showMessage("Password must be at least 6 characters.", "error");
  if (password !== confirm) return showMessage("The two passwords do not match.", "error");

  const button = $("register-btn");
  button.disabled = true;
  try {
    saveSessionAndOpenDashboard(await post("/api/auth/register", { name, email, phone, password }));
  } catch (e) {
    showMessage(e.message, "error");
  } finally {
    button.disabled = false;
  }
});

// ---------- Forgot password ----------
// The server answers with the same message whether or not the email is registered, and so does this page.
$("forgot-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  hideMessage();
  const email = $("forgot-email").value.trim();
  if (!email) return showMessage("Please enter your email address.", "error");

  const button = $("forgot-btn");
  button.disabled = true;
  try {
    const data = await post("/api/auth/forgot-password", { email });
    showMessage(data.message, "success");
    $("forgot-email").value = "";
  } catch (e) {
    showMessage(e.message, "error");   // for example: too many requests
  } finally {
    button.disabled = false;
  }
});

// ---------- Coming back from Google ----------
// The server puts the app token in the #fragment: /login.html#google=<token>
(async function finishGoogleSignIn() {
  const match = /^#google=(.+)$/.exec(location.hash);
  if (!match) return;
  history.replaceState(null, "", location.pathname);   // remove the token from the address bar
  const token = decodeURIComponent(match[1]);
  try {
    const response = await fetch("/api/auth/me", { headers: { Authorization: "Bearer " + token } });
    if (!response.ok) throw new Error("rejected");
    const me = await response.json();
    saveSessionAndOpenDashboard({ token, userId: me.id, name: me.name, email: me.email, role: me.role, volunteerId: me.volunteerId });
  } catch (e) {
    showMessage("Google sign-in failed. Please try again.", "error");
  }
})();
