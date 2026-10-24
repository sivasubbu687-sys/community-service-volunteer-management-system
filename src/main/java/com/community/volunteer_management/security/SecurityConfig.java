package com.community.volunteer_management.security;

import com.community.volunteer_management.entity.Role;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.function.Supplier;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
			RestSecurityHandlers handlers) throws Exception {

		http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				// Second line of defence against XSS: the browser only runs scripts and styles served by this app
				.headers(h -> h.contentSecurityPolicy(csp -> csp.policyDirectives(
						"default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: blob:; "
								+ "frame-ancestors 'none'; base-uri 'self'; form-action 'self'")))
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(e -> e
						.authenticationEntryPoint(handlers)   // 401
						.accessDeniedHandler(handlers))       // 403
				.authorizeHttpRequests(auth -> auth
						// frontend files (they contain no data; the API calls are what is protected)
						.requestMatchers("/", "/index.html", "/login.html", "/reset-password.html", "/style.css", "/script.js",
								"/auth.js", "/auth.css", "/staff-init.js", "/admin.js", "/settings.js", "/admin.css", "/login-hero.jpg", "/cvms-logo-white.svg", "/cvms-logo-teal.svg", "/register-art.svg", "/reset-password.js", "/password-toggle.js", "/volunteer-home.js", "/volunteer.css",
								"/logo.png", "/favicon.png", "/hero.svg", "/hero.jpg", "/community.svg", "/favicon.ico", "/error").permitAll()

						// public API
						.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
								"/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/health").permitAll()
						.requestMatchers(HttpMethod.HEAD, "/api/health").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/auth/google/start", "/api/auth/google/callback").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
						// Only ADMIN changes a password while logged in; everyone else uses the emailed reset link
						.requestMatchers(HttpMethod.PUT, "/api/auth/password").hasRole("ADMIN")

						// ADMIN only: user and role management
						.requestMatchers("/api/users/**").hasRole("ADMIN")

						// Volunteer photos: everyone logged in may ask which photos exist (the answer is filtered by role);
						// viewing is for staff or the owner, changing is for ADMIN or the owner
						.requestMatchers(HttpMethod.GET, "/api/volunteers/photos").authenticated()
						.requestMatchers(HttpMethod.GET, "/api/volunteers/{id}/photo").access(staffOrOwner())
						.requestMatchers(HttpMethod.PUT, "/api/volunteers/{id}/photo").access(adminOrOwner())
						.requestMatchers(HttpMethod.DELETE, "/api/volunteers/{id}/photo").access(adminOrOwner())

						// PDF downloads of the lists: ADMIN and COORDINATOR
						.requestMatchers(HttpMethod.GET, "/api/volunteers/export/pdf", "/api/events/export/pdf")
								.hasAnyRole("ADMIN", "COORDINATOR")

						// Volunteers
						.requestMatchers(HttpMethod.GET, "/api/volunteers").hasAnyRole("ADMIN", "COORDINATOR")
						.requestMatchers(HttpMethod.GET, "/api/volunteers/{id}", "/api/volunteers/{id}/hours",
								"/api/volunteers/{id}/events").access(staffOrOwner())
						.requestMatchers(HttpMethod.PUT, "/api/volunteers/{id}").access(adminOrOwner())
						.requestMatchers("/api/volunteers/**").hasRole("ADMIN")   // create / delete

						// Volunteers join / leave events themselves
						.requestMatchers(HttpMethod.POST, "/api/events/{id}/join", "/api/events/{id}/leave")
								.hasRole("VOLUNTEER")

						// Events: everyone logged in can view; ADMIN and COORDINATOR manage
						.requestMatchers("/api/notices/**", "/api/notices").hasAnyRole("ADMIN", "COORDINATOR")
						.requestMatchers(HttpMethod.GET, "/api/categories").authenticated()
						.requestMatchers("/api/categories/**", "/api/categories").hasRole("ADMIN")
						.requestMatchers(HttpMethod.GET, "/api/events", "/api/events/{id}", "/api/events/{id}/photo")
								.authenticated()
						.requestMatchers("/api/events/**").hasAnyRole("ADMIN", "COORDINATOR")

						// Assignments, attendance and reports: ADMIN and COORDINATOR
						.requestMatchers("/api/assignments/**", "/api/reports/**").hasAnyRole("ADMIN", "COORDINATOR")

						.anyRequest().authenticated())
				.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	// ADMIN / COORDINATOR, or a VOLUNTEER whose own volunteer id matches {id} in the URL
	private AuthorizationManager<RequestAuthorizationContext> staffOrOwner() {
		return (Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) -> {
			AuthUser user = currentUser(authentication.get());
			if (user == null) {
				return new AuthorizationDecision(false);
			}
			boolean staff = user.role() == Role.ADMIN || user.role() == Role.COORDINATOR;
			return new AuthorizationDecision(staff || isOwner(user, context));
		};
	}

	// ADMIN, or a VOLUNTEER whose own volunteer id matches {id} in the URL
	private AuthorizationManager<RequestAuthorizationContext> adminOrOwner() {
		return (Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) -> {
			AuthUser user = currentUser(authentication.get());
			if (user == null) {
				return new AuthorizationDecision(false);
			}
			return new AuthorizationDecision(user.role() == Role.ADMIN || isOwner(user, context));
		};
	}

	private boolean isOwner(AuthUser user, RequestAuthorizationContext context) {
		return user.role() == Role.VOLUNTEER && user.volunteerId() != null
				&& user.volunteerId().toString().equals(context.getVariables().get("id"));
	}

	private AuthUser currentUser(Authentication authentication) {
		if (authentication != null && authentication.isAuthenticated()
				&& authentication.getPrincipal() instanceof AuthUser user) {
			return user;
		}
		return null;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	// Logins are handled by AuthService, so no user store is needed here.
	// Defining this bean stops Spring Boot from creating a default user with a random password.
	@Bean
	public UserDetailsService userDetailsService() {
		return username -> {
			throw new UsernameNotFoundException("Not used");
		};
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOriginPatterns(List.of("*"));
		config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("*"));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", config);
		return source;
	}
}
