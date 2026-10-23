# Community Volunteer Management System (CVMS)

A web application for running community service programmes: register volunteers, plan events, assign volunteers, record attendance and service hours, and keep organisers informed.

## Features

- **Roles:** Admin, Coordinator and Volunteer, each with their own screens and permissions
- **Volunteers:** registration, profiles with photos, directory and PDF export
- **Events:** create and edit events with schedule, location, capacity and photo; categories (and their chart colours) are configured by the admin in Settings
- **Assignments:** assign volunteers to events, with schedule-conflict and capacity checks; volunteers can also join or leave events themselves
- **Leave notifications:** when a volunteer leaves an event they give a reason, which shows up in the Admin and Coordinator notifications
- **Attendance and hours:** check-in / check-out, hours worked, top volunteers report
- **Dashboard:** statistics, charts, recent events and activities for Admin and Coordinator
- **Accounts:** login, volunteer self-registration, forgot / reset password by email
- **Email:** notification when a volunteer is assigned to an event

## Built with

- Java 21, **Spring Boot**
- Spring Security (stateless JWT, BCrypt)
- Spring Data JPA / Hibernate, **MySQL**
- OpenPDF for PDF export, Spring Mail (Gmail SMTP)
- HTML, CSS and vanilla JavaScript (no framework), served by Spring Boot

## Getting started

### Requirements

- JDK 21
- MySQL 8 running locally
- A Gmail account with an App Password (optional, only needed for emails)

### Setup

1. Create the database:
   ```sql
   CREATE DATABASE community_service;
   ```
2. Copy `src/main/resources/secrets.properties.example` to `src/main/resources/secrets.properties` and fill in your own values (database password, JWT secret of at least 32 characters, default admin email and password, mail login). This file is git-ignored and must never be committed.
3. Start the app:
   ```
   ./mvnw spring-boot:run
   ```
   On Windows you can use `mvnw.cmd spring-boot:run`.
4. Open http://localhost:8080 and log in with the admin email and password you set in `secrets.properties`. The admin account is created on first start.

Tables are created automatically on startup.

## Roles

| Role | Can do |
| --- | --- |
| Admin | Everything: manage volunteers, users, events, assignments, attendance and Settings (event categories) |
| Coordinator | Manage events, assignments and attendance, view volunteers and reports |
| Volunteer | Browse and join events, leave an event with a reason, track their own hours and profile |

## Project structure

```
src/main/java/.../controller   REST endpoints
src/main/java/.../service      business logic
src/main/java/.../entity       JPA entities
src/main/java/.../repository   Spring Data repositories
src/main/java/.../security     JWT and security configuration
src/main/resources/static      HTML, CSS and JavaScript front end
```

## Security notes

- Secrets are read from `secrets.properties` or environment variables, never from the repository.
- Change the default admin password after the first login.
