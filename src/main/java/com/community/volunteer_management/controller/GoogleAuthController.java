package com.community.volunteer_management.controller;

import com.community.volunteer_management.dto.AuthResponse;
import com.community.volunteer_management.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// "Continue with Google": the standard server-side OAuth sign-in.
// 1. /start sends the browser to Google.  2. Google sends it back to /callback with a one-time code.
// 3. The server swaps the code for the verified email address, then hands the browser a normal app token.
// Needs GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET (see secrets.properties.example).
@RestController
@RequestMapping("/api/auth/google")
public class GoogleAuthController {

	private static final Logger log = LoggerFactory.getLogger(GoogleAuthController.class);
	private static final String STATE_COOKIE = "vms_google_state";
	private static final Pattern VERIFIED = Pattern.compile("\"email_verified\"\\s*:\\s*(true|\"true\")");

	private final AuthService authService;
	private final String clientId;
	private final String clientSecret;
	private final String baseUrl;
	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	private final SecureRandom random = new SecureRandom();

	public GoogleAuthController(AuthService authService,
			@Value("${app.google.client-id:}") String clientId,
			@Value("${app.google.client-secret:}") String clientSecret,
			@Value("${app.base-url}") String baseUrl) {
		this.authService = authService;
		this.clientId = clientId.trim();
		this.clientSecret = clientSecret.trim();
		this.baseUrl = baseUrl.replaceAll("/+$", "");
	}

	private String redirectUri() {
		return baseUrl + "/api/auth/google/callback";
	}

	@GetMapping("/start")
	public void start(HttpServletResponse response) throws IOException {
		if (clientId.isEmpty() || clientSecret.isEmpty()) {
			back(response, "Google sign-in is not set up yet. Please log in with your email and password.");
			return;
		}
		byte[] bytes = new byte[24];
		random.nextBytes(bytes);
		String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

		Cookie cookie = new Cookie(STATE_COOKIE, state);
		cookie.setHttpOnly(true);
		cookie.setPath("/api/auth/google");
		cookie.setMaxAge(600);
		response.addCookie(cookie);

		response.sendRedirect("https://accounts.google.com/o/oauth2/v2/auth"
				+ "?client_id=" + enc(clientId)
				+ "&redirect_uri=" + enc(redirectUri())
				+ "&response_type=code&scope=" + enc("openid email profile")
				+ "&prompt=select_account&state=" + enc(state));
	}

	@GetMapping("/callback")
	public void callback(@RequestParam(required = false) String code, @RequestParam(required = false) String state,
			@RequestParam(required = false) String error,
			@CookieValue(name = STATE_COOKIE, required = false) String savedState,
			HttpServletResponse response) throws IOException {
		Cookie clear = new Cookie(STATE_COOKIE, "");
		clear.setPath("/api/auth/google");
		clear.setMaxAge(0);
		response.addCookie(clear);

		if (error != null || code == null) {
			back(response, "Google sign-in was cancelled.");
			return;
		}
		if (savedState == null || !savedState.equals(state)) {
			back(response, "Google sign-in could not be verified. Please try again.");
			return;
		}
		try {
			String body = "code=" + enc(code) + "&client_id=" + enc(clientId) + "&client_secret=" + enc(clientSecret)
					+ "&redirect_uri=" + enc(redirectUri()) + "&grant_type=authorization_code";
			HttpResponse<String> reply = http.send(HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
					.timeout(Duration.ofSeconds(10))
					.header("Content-Type", "application/x-www-form-urlencoded")
					.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
			if (reply.statusCode() != 200) {
				log.warn("Google token exchange failed: {} {}", reply.statusCode(), reply.body());
				back(response, "Google sign-in failed. Please try again.");
				return;
			}
			String idToken = field(reply.body(), "id_token");
			String[] parts = idToken == null ? new String[0] : idToken.split("\\.");
			if (parts.length < 2) {
				back(response, "Google sign-in failed. Please try again.");
				return;
			}
			// The token came straight from Google over HTTPS in answer to our own request, so it is trusted;
			// the checks below are the usual extra safety.
			String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
			String email = field(claims, "email");
			boolean verified = VERIFIED.matcher(claims).find();
			if (email == null || !clientId.equals(field(claims, "aud")) || !verified) {
				back(response, "Your Google email address could not be verified.");
				return;
			}
			AuthResponse auth = authService.loginWithGoogle(email, field(claims, "name"));
			// the token travels in the #fragment, which is never sent to any server or written to a log
			response.sendRedirect("/login.html#google=" + enc(auth.token()));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			back(response, "Google sign-in was interrupted. Please try again.");
		} catch (IOException | RuntimeException e) {
			log.warn("Google sign-in failed", e);
			back(response, "Google sign-in failed. Please try again.");
		}
	}

	// Reads one top-level string value from a small JSON text
	private static String field(String json, String name) {
		Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
		return m.find() ? m.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : null;
	}

	private void back(HttpServletResponse response, String text) throws IOException {
		response.sendRedirect("/login.html?msg=" + enc(text));
	}

	private static String enc(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
