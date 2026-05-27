# alldare-auth

Identity and Authorization Provider for Alldare. Implements OAuth2/OIDC using Spring Authorization Server.

## Authentication Flow

```mermaid
sequenceDiagram
    participant User as User (Browser/Mobile)
    participant Gateway as alldare-gateway
    participant Auth as alldare-auth
    participant DB as PostgreSQL (alldare_auth)

    User->>Gateway: GET /protected-resource
    Gateway-->>User: 401 Unauthorized / Redirect to /login
    User->>Gateway: POST /login (credentials)
    Gateway->>Auth: Forward to /login
    Auth->>DB: Verify Credentials
    DB-->>Auth: Account Data
    Auth-->>User: Issue JWT / Session Cookie
    User->>Gateway: GET /protected-resource [Header: Bearer JWT]
    Gateway->>Auth: Validate JWT (JWKS)
    Auth-->>Gateway: 200 OK (Public Key)
    Gateway->>Gateway: Verify Signature
    Gateway-->>User: Authorized Access
```

## Core Entities
*   **Account:** Central credential and status management.
*   **User:** User-specific profile link.
*   **Admin:** Administrator-specific profile link.
