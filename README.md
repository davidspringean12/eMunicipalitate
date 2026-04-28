# eMunicipalitate — Servicii Publice Digitale cu CEI

> **Lucrare de Licență** — _Enhancing Public Sector Digital Services through CEI-based Authentication and Electronic Signature_

---

## Overview

A middleware platform enabling Romanian citizens to access municipal services (e.g., Certificat de Urbanism, Grant Applications) using the **Carte de Identitate Electronică (CEI)** for:

- **LoA4 Authentication** — X.509 challenge–response via PKCS#11
- **Qualified Electronic Signatures (QES)** — PAdES-B-LTA via EU DSS

Fully compliant with **eIDAS Regulation 910/2014** and **Romanian Law 455/2001**.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.3, Java 21 |
| Frontend | React 18, TypeScript, Vite |
| Database | PostgreSQL 16 |
| Cache | Redis 7 |
| Storage | MinIO (S3-compatible) |
| Queue | RabbitMQ 3.13 |
| Signing | EU DSS 6.1, Bouncy Castle |
| Auth | PKCS#11 (OpenSC), JWT |

## Quick Start

### Prerequisites

- Java 21+ (e.g., Eclipse Temurin)
- Node.js 20+ & npm
- Docker & Docker Compose

### 1. Start infrastructure

```bash
docker compose up -d
```

### 2. Run the backend

```bash
cd backend
./mvnw spring-boot:run
```

Backend starts at `http://localhost:8080/api`.

### 3. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend starts at `http://localhost:5173`.

## Project Structure

```
licenta/
├── backend/
│   ├── src/main/java/ro/emunicipalitate/
│   │   ├── config/          # Security, MinIO, CORS
│   │   ├── controller/      # REST endpoints
│   │   ├── dto/             # Request/Response DTOs
│   │   ├── model/           # JPA entities
│   │   ├── repository/      # Spring Data repositories
│   │   └── service/         # Business logic
│   ├── src/main/resources/
│   │   ├── application.yml  # Configuration
│   │   └── db/migration/    # Flyway SQL migrations
│   └── pom.xml
├── frontend/
│   ├── src/
│   │   ├── pages/           # Login, Dashboard, Requests, Sign
│   │   ├── services/        # Axios API client
│   │   ├── App.tsx          # Router
│   │   └── index.css        # Design system
│   └── package.json
├── docker-compose.yml
└── README.md
```

## Legal Compliance

| Regulation | Articles Mapped |
|-----------|----------------|
| **eIDAS 910/2014** | Art. 6, 25, 26, 28, 32, 42 |
| **Law 455/2001** | Art. 4, 5, 7, 8, 20, 35 |

## License

This project is part of a Bachelor's Thesis and is provided for academic purposes.
