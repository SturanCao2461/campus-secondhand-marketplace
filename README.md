# 🎓 Campus Secondhand Marketplace

A campus-only second-hand marketplace web app for university students, staff, and employees.
Browse, list, and arrange local pickup for second-hand goods — no payments, no shipping.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 18 + TypeScript + Vite |
| Backend | Java 17 + Spring Boot 3 |
| Database | MySQL 8 |
| Cache | Redis 7 |
| Dev environment | Docker Compose |

## Project Structure

```
campus-secondhand-marketplace/
├── frontend/    # React + TypeScript SPA
├── backend/     # Spring Boot REST API
├── infra/       # Docker Compose & environment config
├── docs/        # Project documentation
└── README.md
```

See [docs/project-structure.md](docs/project-structure.md) for details.

## Getting Started

### Prerequisites

- Docker & Docker Compose
- Node.js 20+ (for local frontend dev)
- Java 17+ & Maven 3.9+ (for local backend dev)

### 1. Clone the repository

```bash
git clone https://github.com/SturanCao2461/campus-secondhand-marketplace.git
cd campus-secondhand-marketplace
```

### 2. Configure environment

```bash
cp infra/.env.example infra/.env
# Edit infra/.env and set secure values
```

### 3. Run with Docker Compose

```bash
cd infra
docker compose up --build
```

Services:
- Frontend: http://localhost:3000
- Backend API: http://localhost:8080
- Health check: http://localhost:8080/api/health
- MySQL: localhost:3306
- Redis: localhost:6379

### 4. Run locally (without Docker)

**Frontend:**
```bash
cd frontend
npm install
cp .env.example .env
npm run dev
# → http://localhost:3000
```

**Backend:**

Start MySQL and Redis first (or use `docker compose up mysql redis`), then:

```bash
cd backend
./mvnw spring-boot:run
# → http://localhost:8080
```

## Documentation

| Doc | Description |
|-----|-------------|
| [docs/project-structure.md](docs/project-structure.md) | Monorepo layout explained |
| [docs/mvp-scope.md](docs/mvp-scope.md) | What is and isn't in the MVP |
| [docs/api-overview.md](docs/api-overview.md) | REST API endpoints |
| [docs/database-draft.md](docs/database-draft.md) | Database schema draft |

## Development Notes

- This is a **12-week university course project** — keep it simple.
- Local **in-person pickup only** — no payment or delivery features.
- Campus users only — university email required (TODO: enforce in auth).
- Backend is intentionally permissive for now — see TODO comments in `AppConfig.java`.