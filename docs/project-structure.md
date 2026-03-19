# Project Structure

This document describes the monorepo layout for Campus Marketplace.

## Directory Overview

```
campus-secondhand-marketplace/
├── frontend/          # React + TypeScript SPA
├── backend/           # Spring Boot REST API
├── infra/             # Docker Compose and infrastructure config
├── docs/              # Project documentation
└── README.md
```

## Frontend (`frontend/`)

| Path | Purpose |
|------|---------|
| `src/pages/` | Page-level React components (one per route) |
| `src/components/` | Shared/reusable UI components |
| `src/styles/` | Global CSS |
| `vite.config.ts` | Vite build + dev server config |

## Backend (`backend/`)

Follows a standard layered architecture:

| Package | Purpose |
|---------|---------|
| `controller/` | REST controllers — receive HTTP requests, delegate to services |
| `service/` | Business logic layer |
| `repository/` | Spring Data JPA interfaces — data access layer |
| `model/` | JPA entity classes mapped to DB tables |
| `dto/` | Data Transfer Objects for request/response |
| `config/` | Spring configuration beans (Security, Redis, etc.) |

Each feature domain (auth, listings, messages, favorites) has its own sub-package within each layer.

## Infrastructure (`infra/`)

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Starts MySQL, Redis, backend, and frontend |
| `.env.example` | Template for environment variables |

## Documentation (`docs/`)

| File | Purpose |
|------|---------|
| `project-structure.md` | This file |
| `mvp-scope.md` | What is and isn't in the MVP |
| `api-overview.md` | REST API endpoint summary |
| `database-draft.md` | Draft database schema |
