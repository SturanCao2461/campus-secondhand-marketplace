# Database Draft Schema

Database: MySQL 8.x  
ORM: Spring Data JPA / Hibernate (auto DDL from entities)

---

## Tables

### `users`

| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT PK AUTO_INCREMENT | |
| email | VARCHAR(255) UNIQUE NOT NULL | Must be campus email |
| password_hash | VARCHAR(255) NOT NULL | bcrypt |
| name | VARCHAR(255) NOT NULL | |
| role | ENUM('STUDENT','STAFF','EMPLOYEE') | Default: STUDENT |
| created_at | TIMESTAMP NOT NULL | |

TODO: Add `phone`, `avatar_url`, `is_verified` columns.

---

### `listings`

| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT PK AUTO_INCREMENT | |
| title | VARCHAR(255) NOT NULL | |
| description | TEXT | |
| price | DECIMAL(10,2) NOT NULL | |
| condition | ENUM('NEW','LIKE_NEW','GOOD','FAIR','POOR') | |
| category | VARCHAR(100) | |
| seller_id | BIGINT FK → users.id | |
| status | ENUM('ACTIVE','SOLD','REMOVED') | Default: ACTIVE |
| created_at | TIMESTAMP NOT NULL | |
| updated_at | TIMESTAMP NOT NULL | |

TODO: Add `image_urls` (JSON column or separate `listing_images` table).  
TODO: Add `campus_location` column for pickup point.

---

### `messages`

| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT PK AUTO_INCREMENT | |
| sender_id | BIGINT FK → users.id | |
| receiver_id | BIGINT FK → users.id | |
| listing_id | BIGINT FK → listings.id | nullable |
| content | TEXT NOT NULL | |
| created_at | TIMESTAMP NOT NULL | |

TODO: Add `is_read` boolean column.  
TODO: Consider adding a `conversations` table to group messages.

---

### `favorites`

| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT PK AUTO_INCREMENT | |
| user_id | BIGINT FK → users.id | |
| listing_id | BIGINT FK → listings.id | |
| created_at | TIMESTAMP NOT NULL | |

Unique constraint: `(user_id, listing_id)`

---

## Relationships

- A **user** can have many **listings** (one-to-many)
- A **user** can have many **favorites** (one-to-many)
- A **listing** can appear in many **favorites** (many-to-many via favorites table)
- A **user** can send/receive many **messages** (one-to-many)

---

## Notes

- Hibernate `spring.jpa.hibernate.ddl-auto=update` auto-creates tables in development.
- For production, use `validate` and manage schema with Flyway or Liquibase migrations.
- TODO: Add Flyway migration scripts in `backend/src/main/resources/db/migration/`.
