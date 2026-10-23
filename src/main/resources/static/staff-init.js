// Runs in <head>, before the page is drawn: admins and coordinators get the sidebar layout straight away
try {
  const user = JSON.parse(localStorage.getItem("vms_user"));
  if (user && (user.role === "ADMIN" || user.role === "COORDINATOR")) document.documentElement.classList.add("staff");
} catch (e) { /* no stored login: script.js sends the visitor to the login page */ }
