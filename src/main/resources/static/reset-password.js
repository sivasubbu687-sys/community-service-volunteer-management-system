// Reset password page: reads the token from the emailed link, asks for a new password, then goes to the login page.
const $ = (id) => document.getElementById(id);

// Take the token out of the address, then remove it from the address bar and browser history
const token = new URLSearchParams(location.search).get("token");
history.replaceState(null, "", location.pathname);

function showMessage(text, type) {
  const box = $("reset-message");
  box.textContent = text;   // textContent, never innerHTML
  box.className = "message " + type;
}

// The link cannot be used: hide the form and offer a new link
function showInvalid(text) {
  $("reset-form").classList.add("hidden");
  $("invalid-box").classList.remove("hidden");
  showMessage(text, "error");
}

if (!token || token.length < 20) {
  showInvalid("This reset link is invalid. Please request a new one.");
}

$("reset-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  $("reset-message").className = "message hidden";

  const newPassword = $("new-password").value;
  if (newPassword.length < 8) return showMessage("The new password must be at least 8 characters.", "error");
  if (newPassword !== $("confirm-password").value) return showMessage("The two passwords do not match.", "error");

  const button = $("reset-btn");
  button.disabled = true;
  try {
    const response = await fetch("/api/auth/reset-password", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token, newPassword }),
    });
    let data = null;
    try { data = await response.json(); } catch (e) { /* no body */ }

    if (response.ok) {
      $("reset-form").classList.add("hidden");
      showMessage((data && data.message) + " Taking you to the login page…", "success");
      setTimeout(() => location.replace("login.html?ok=" + encodeURIComponent("Password reset. Please log in with your new password.")), 2500);
      return;
    }
    const message = (data && data.message) || "Request failed (status " + response.status + ").";
    if (response.status === 400 && message.includes("reset link")) {
      showInvalid(message);          // invalid, used or expired link
    } else {
      showMessage(message, "error"); // for example: password problems
    }
  } catch (e) {
    showMessage("Cannot reach the server. Is the application running?", "error");
  } finally {
    button.disabled = false;
  }
});
