# ShrinkIt

**JWT-Secured URL Shortener with Per-Link Click Analytics**

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.6-6DB33F?logo=springboot)](https://spring.io/projects/spring-boot)
[![Spring Security](https://img.shields.io/badge/Spring_Security-JWT-6DB33F?logo=springsecurity)](https://spring.io/projects/spring-security)
[![MySQL](https://img.shields.io/badge/MySQL-JPA%2FHibernate-4479A1?logo=mysql)](https://www.mysql.com/)
[![Maven](https://img.shields.io/badge/Build-Maven-C71A36?logo=apachemaven)](https://maven.apache.org/)

ShrinkIt is a Spring Boot REST API for creating short, shareable links and tracking how they're used. Every account is JWT-authenticated, every shortened link is owned by its creator, and every click is recorded as a timestamped event — enabling per-day click analytics over arbitrary date ranges. Short-link redirection is public and unauthenticated; everything else sits behind a stateless JWT security filter chain.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Data Model](#data-model)
- [How It Works](#how-it-works)
  - [1. Authentication](#1-authentication)
  - [2. Short Code Generation](#2-short-code-generation)
  - [3. Redirection & Click Tracking](#3-redirection--click-tracking)
  - [4. Click Analytics](#4-click-analytics)
- [Features](#features)
- [Project Structure](#project-structure)
- [Tech Stack](#tech-stack)
- [Setup & Running](#setup--running)
- [API Reference](#api-reference)
- [Design Decisions](#design-decisions)
- [Roadmap](#roadmap)

---

## Architecture Overview

```
                              ┌──────────────────────────────────────────┐
                              │              Spring Security              │
                              │      (stateless JWT filter chain)         │
                              └──────────────────────────────────────────┘
                                              │
        ┌─────────────────────────────────────────────────────────────────────┐
        │                                    │                                 │
        ▼                                    ▼                                 ▼
┌───────────────────┐           ┌─────────────────────────┐       ┌────────────────────────┐
│   AuthController    │           │      UrlController       │       │   RedirectController    │
│  /api/auth/**       │           │     /api/urls/**          │       │      /{shortUrl}         │
│  (public)           │           │     (JWT required)        │       │      (public)            │
│                     │           │                           │       │                          │
│  • register         │           │  • POST /short            │       │  • GET /{shortUrl}        │
│  • login → JWT      │           │  • GET  /getAll           │       │    → 302 redirect         │
└───────────────────┘           │  • GET  /analytics/{code} │       │    → +1 click count        │
                                  │  • GET  /dated-analytics/  │       │    → log ClickEvent       │
                                  │         {code}            │       └────────────────────────┘
                                  └─────────────────────────┘
                                              │
                                              ▼
                                  ┌─────────────────────────┐
                                  │     UrlService           │
                                  │  • generate() short code  │
                                  │  • collision retry loop   │
                                  │  • DTO mapping             │
                                  │  • analytics aggregation   │
                                  └─────────────────────────┘
                                              │
                                              ▼
                                  ┌─────────────────────────┐
                                  │   MySQL (JPA/Hibernate)   │
                                  │  User ──< UrlMapping ──< ClickEvent │
                                  └─────────────────────────┘
```

---

## Data Model

```
┌──────────────┐        ┌────────────────────┐        ┌──────────────────┐
│     User       │        │     UrlMapping       │        │    ClickEvent       │
├──────────────┤  1    * ├────────────────────┤  1    * ├──────────────────┤
│ id             │◄───────│ id                   │◄───────│ id                  │
│ username       │        │ original             │        │ date               │
│ email          │        │ shortUrl (unique)    │        │ urlMapping (FK)     │
│ password (hash)│        │ clicks (counter)     │        └──────────────────┘
│ role           │        │ createdAt            │
└──────────────┘        │ user (FK)            │
                          └────────────────────┘
```

Each `UrlMapping` belongs to exactly one `User` and accumulates a denormalized `clicks` counter plus a full history of `ClickEvent` rows — the counter gives O(1) reads for dashboards, while the event history powers time-series analytics.

---

## How It Works

### 1. Authentication

`AuthController` exposes `/api/auth/register` and `/api/auth/login`, both publicly accessible. Registration hashes the password with **BCrypt** and persists a `User` with a default `USER` role. Login delegates to Spring Security's `AuthenticationManager`, which authenticates against `UserDetailsServiceImpl` (backed by `UserRepo`), then issues a signed **JWT** via `JwtUtils`:

```
POST /api/auth/login {username, password}
        │
        ▼
AuthenticationManager ──► UserDetailsServiceImpl ──► UserRepo (MySQL)
        │
        ▼
JwtUtils.generateToken(userDetails)
   • subject  = username
   • claim    = roles
   • signed   = HMAC-SHA (jwt.secret)
   • expiry   = now + jwt.expiration
        │
        ▼
{ "token": "<jwt>" }
```

Every subsequent request to `/api/urls/**` must carry `Authorization: Bearer <token>`. `JwtAuthFilter` (a `OncePerRequestFilter` registered before `UsernamePasswordAuthenticationFilter`) extracts and validates the token on each request, then populates the `SecurityContext` so controllers can resolve the caller via `Principal`.

### 2. Short Code Generation

`UrlService.generate()` builds a random short code from a configurable character set and length:

```java
for (int i = 0; i < len; i++) {
    shortUrl.append(charSet.charAt(rand.nextInt(charSet.length())));
}
```

To guarantee uniqueness, `shorten()` regenerates the code in a loop until `urlMappingRepo.existsByShortUrl(...)` returns `false`:

```
generate() ──► exists in DB? ──yes──► generate() again
     │
     no
     │
     ▼
persist UrlMapping { original, shortUrl, user, clicks=0, createdAt=now }
```

Both the **length** and the **character set** are externalized to `application.properties` (`url.length`, `url.charSet`), so the collision probability and code "shape" (e.g. alphanumeric vs. base62) can be tuned without touching code.

### 3. Redirection & Click Tracking

`RedirectController` maps the **root path** `/{shortUrl}` — deliberately public, since the whole point of a short link is that anyone can open it without an account:

```
GET /{shortUrl}
   │
   ▼
UrlMappingRepo.findByShortUrl(shortUrl)
   │
   ├─ not found ──► 404
   │
   └─ found ──► clicks += 1
              ──► save ClickEvent { date = now, urlMapping }
              ──► 302 redirect → original URL
```

Each click is recorded as its **own `ClickEvent` row** (timestamped), in addition to incrementing the `UrlMapping.clicks` counter — giving both a fast aggregate count and a queryable history.

### 4. Click Analytics

`UrlController` exposes two analytics endpoints, both backed by the same aggregation logic in `UrlService`:

- **`GET /api/urls/analytics/{shortUrl}`** — all-time click history
- **`GET /api/urls/dated-analytics/{shortUrl}?startDate=...&endDate=...`** — clicks within an ISO-8601 date-time range, via `ClickEventRepo.findAllByUrlMappingAndDateBetween`

Both return a list of `ClickEventDto { clickDate, clickCount }`. The raw `ClickEvent` rows (one per click) are reduced to **per-day counts** in `UrlService`:

```java
Map<LocalDate, Integer> map = new HashMap<>();
for (ClickEvent click : clickEvents) {
    LocalDate date = click.getDate().toLocalDate();
    map.put(date, map.getOrDefault(date, 0) + 1);
}
// → List<ClickEventDto> { clickDate, clickCount }
```

This shape is ready to drop directly into a time-series chart on the frontend — one data point per day, no further client-side aggregation needed.

---

## Features

- **JWT-based stateless authentication** — BCrypt password hashing, HMAC-signed tokens with configurable expiry, validated per-request via a custom `OncePerRequestFilter`
- **Configurable short-code generation** — code length and character set are externalized to properties; a collision-retry loop guarantees uniqueness without unique-constraint exceptions reaching the client
- **Ownership-scoped link management** — every `UrlMapping` is tied to its creating `User`; `getAll` returns only the caller's own links
- **Public redirection, private management** — `/{shortUrl}` is open to anyone, while creation, listing, and analytics require a valid JWT — enforced declaratively in `SecurityConfig`
- **Event-sourced click analytics** — every redirect logs a `ClickEvent`, enabling both an O(1) total-click counter and arbitrary date-range, day-bucketed analytics
- **Clean layering** — Controllers → Services → Repositories (Spring Data JPA), with DTOs (`UrlDto`, `ClickEventDto`) decoupling the persistence model from API responses
- **Role-aware security** — `User.role` and JWT `roles` claim lay the groundwork for role-based authorization via `@EnableMethodSecurity`

---

## Project Structure

```
ShrinkIt/
├── pom.xml
└── src/main/java/com/Inhuman/shrinkit/
    ├── ShrinkItApplication.java
    │
    ├── controllers/
    │   ├── AuthController.java        # /api/auth/register, /api/auth/login
    │   ├── UrlController.java         # /api/urls/short, /getAll, /analytics, /dated-analytics
    │   └── RedirectController.java    # /{shortUrl} → 302 redirect + click logging
    │
    ├── services/
    │   ├── UserService.java           # register, login, getByUsername
    │   ├── UrlService.java            # short-code generation, DTO mapping, analytics
    │   ├── UserDetailsServiceImpl.java # Spring Security UserDetailsService
    │   └── UserDetailImpl.java        # UserDetails adapter over User entity
    │
    ├── security/
    │   ├── SecurityConfig.java        # Filter chain, route rules, beans (encoder, auth manager)
    │   └── jwt/
    │       ├── JwtUtils.java          # token generation, parsing, validation
    │       ├── JwtAuthFilter.java     # per-request token extraction & context population
    │       └── JwtAuthResponse.java   # { token } response wrapper
    │
    ├── models/
    │   ├── User.java                  # id, username, email, password, role
    │   ├── UrlMapping.java            # id, original, shortUrl, clicks, createdAt, user
    │   └── ClickEvent.java            # id, date, urlMapping
    │
    ├── dtos/
    │   ├── RegisterRequest.java
    │   ├── LoginRequest.java
    │   ├── UrlDto.java
    │   └── ClickEventDto.java
    │
    └── repos/
        ├── UserRepo.java              # findByUsername
        ├── UrlMappingRepo.java        # existsByShortUrl, findByShortUrl, findAllByUser
        └── ClickEventRepo.java        # findAllByUrlMappingAndDateBetween
```

---

## Tech Stack

| Concern | Library / Tool | Version |
|---|---|---|
| Language | Java | 21 |
| Framework | Spring Boot (Web, Security, Data JPA) | 3.5.6 |
| Auth | Spring Security + JJWT (HMAC-SHA, JWT) | 0.13.0 |
| Database | MySQL (via `mysql-connector-j`) | — |
| ORM | Hibernate / Spring Data JPA | — |
| Boilerplate | Lombok | — |
| Build | Maven | — |
| Testing | JUnit 5, Spring Boot Test, Spring Security Test | — |

---

## Setup & Running

### Prerequisites

- **Java 21**
- **Maven 3.8+**
- A running **MySQL** instance

### 1. Clone the repository

```bash
git clone https://github.com/krishnadaga5106/shrinkit.git
cd shrinkit
```

### 2. Configure `application.properties`

Create `src/main/resources/application.properties` (not checked into the repo) with your database, JWT, and short-code settings:

```properties
spring.application.name=ShrinkIt
server.port=8080

# MySQL
spring.datasource.url=jdbc:mysql://localhost:3306/shrinkit
spring.datasource.username=root
spring.datasource.password=yourpassword
spring.jpa.hibernate.ddl-auto=update

# JWT
jwt.secret=<base64-encoded-256-bit-secret>
jwt.expiration=86400000

# Short code generation
url.length=7
url.charSet=ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789
```

> `jwt.secret` must be a Base64-encoded key compatible with `Keys.hmacShaKeyFor(...)` — generate one with, e.g., `openssl rand -base64 32`.

### 3. Build and run

```bash
mvn clean install
mvn spring-boot:run
```

The API starts on `http://localhost:8080`.

### 4. Try it out

```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password123","email":"alice@example.com"}'

# Login → get JWT
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"password123"}'

# Shorten a URL (use the token from login)
curl -X POST http://localhost:8080/api/urls/short \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"originalUrl":"https://github.com/krishnadaga5106"}'

# Visit the short link (no auth needed)
curl -L http://localhost:8080/<shortUrl>
```

---

## API Reference

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/api/auth/register` | Public | Register a new user (`username`, `password`, `email`) |
| `POST` | `/api/auth/login` | Public | Authenticate and receive a JWT |
| `POST` | `/api/urls/short` | JWT | Shorten a URL for the authenticated user (`{ "originalUrl": "..." }`) |
| `GET` | `/api/urls/getAll` | JWT | List all short links owned by the authenticated user |
| `GET` | `/api/urls/analytics/{shortUrl}` | JWT | Get all-time daily click counts for a link |
| `GET` | `/api/urls/dated-analytics/{shortUrl}?startDate=&endDate=` | JWT | Get daily click counts within an ISO-8601 date range |
| `GET` | `/{shortUrl}` | Public | Redirect to the original URL; increments click count and logs a `ClickEvent` |

**Example — `POST /api/urls/short` response:**

```json
{
  "id": 14,
  "originalUrl": "https://github.com/krishnadaga5106",
  "shortUrl": "aZ9kLm2",
  "clickCount": 0,
  "createdAt": "2026-06-14T10:32:00",
  "username": "alice"
}
```

**Example — `GET /api/urls/analytics/{shortUrl}` response:**

```json
[
  { "clickDate": "2026-06-12", "clickCount": 4 },
  { "clickDate": "2026-06-13", "clickCount": 11 },
  { "clickDate": "2026-06-14", "clickCount": 2 }
]
```

---

## Design Decisions

**Why a separate `ClickEvent` table instead of just a counter on `UrlMapping`?**
A counter alone answers "how many clicks total?" but not "when did they happen?". By writing one `ClickEvent` row per redirect — in the same transaction as the counter increment — ShrinkIt gets both a cheap O(1) total (for list views) and a full time-series (for analytics), without needing to backfill or re-derive history later.

**Why retry short-code generation instead of deriving codes from the row ID?**
ID-derived codes (e.g. Base62-encoded auto-increment IDs) are sequential and predictable — they leak how many links exist and make enumeration trivial. Random generation with a uniqueness check trades a small, bounded number of extra DB round-trips (collisions are rare at reasonable code lengths) for non-guessable short codes.

**Why is `/{shortUrl}` mapped at the application root rather than under a prefix like `/r/{shortUrl}`?**
Short URLs are valuable precisely *because* they're short. Mapping the redirect at the root means every generated link is `host/<code>` — the minimum possible length — which is the entire point of a URL shortener.

**Why JWT instead of session-based auth?**
A stateless token lets `UrlController` and `RedirectController` scale horizontally with no shared session store, and keeps the security filter chain simple: one filter (`JwtAuthFilter`) validates every request independently. The `/api/auth/**` and `/{shortUrl}` routes are explicitly `permitAll()` in `SecurityConfig`, while everything else defaults to `authenticated()`.

**Why DTOs (`UrlDto`, `ClickEventDto`) instead of returning JPA entities directly?**
Returning `UrlMapping` directly would either serialize the full `User` and `List<ClickEvent>` graph (heavy, and a potential Hibernate lazy-loading exception) or require `@JsonIgnore` scattered across the entity. `UrlService.convertToDto(...)` shapes exactly the fields the client needs — including flattening `user.username` into a single string field — keeping the persistence model free to evolve independently of the API contract.

---

## Roadmap

- **Custom aliases** — allow users to request a specific short code instead of a random one
- **Link expiration** — optional TTL on `UrlMapping`, with expired links returning `410 Gone`
- **Rate limiting** — protect `/api/urls/short` and `/{shortUrl}` from abuse (e.g. Bucket4j or Redis-based limiter)
- **Redirect caching** — front `UrlMappingRepo.findByShortUrl` with a Redis cache, since redirects are the highest-traffic, read-heavy path
- **QR code generation** — return a QR code alongside each shortened URL for easy mobile sharing
- **Refresh tokens** — pair short-lived access tokens with refresh tokens for better session hygiene
- **Test coverage** — controller, service, and security-filter tests with JUnit 5, Mockito, and Spring Security Test
