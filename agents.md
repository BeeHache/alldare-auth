# Agent Context: alldare-auth

## 1. Project Purpose
The central identity and authorization provider for the Alldare platform. This service manages user accounts, handles authentication, and issues OAuth2/OIDC tokens (JWT) using Spring Authorization Server.

## 2. Core Tech Stack
*   **Language/Framework:** Java 21 / Spring Boot 4.x
*   **Security:** Spring Authorization Server, Spring Security
*   **Database:** PostgreSQL
*   **ORM:** Spring Data JPA
*   **Migrations:** Flyway

## 3. Data Strategy (The "Golden Rules")
*   **Identification:** Use `java.util.UUID` for all primary keys.
*   **Timestamps:** Use `java.time.Instant` in Java and `TIMESTAMP WITH TIME ZONE` in PostgreSQL.
*   **Separation of Concerns:** Accounts are separated into `accounts` (credentials/status) and profile-specific tables like `users` and `admins`.
*   **Integrity:** Use `CHECK` constraints in PostgreSQL to enforce allowed values for `status` and `account_type`.

## 4. Key Architectural Patterns
*   **OAuth2/OIDC Provider:** Implements the standard flows for token issuance and validation.
*   **Custom UserDetailsService:** Bridges the JPA `Account` entity with Spring Security's authentication mechanism.

## 5. Infrastructure Constants
*   **Database:** `alldare_auth`
*   **Port:** `9000` (Standard for auth services in this ecosystem)

## 6. Coding Standards
*   **Passwords:** Always use `PasswordEncoder` (BCrypt) for hashing. Never store plain text.
*   **DTOs:** Strictly separate external Request/Response objects from internal JPA Entities.
