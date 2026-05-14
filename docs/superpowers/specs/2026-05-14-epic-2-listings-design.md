# Epic 2: Listings — Design Spec

- **Date**: 2026-05-14
- **Status**: Draft (in progress — sections being added incrementally as decisions are confirmed)
- **Owner**: Josh
- **Estimated effort**: 2–3 weeks
- **Depends on**: Epic 1 (auth complete) ✓
- **Blocks**: Epic 3 (Browse / Search / Filter / Detail)

---

## 1. Overview

第二个 Epic 实现校园二手交易平台的"商品主人端"——卖家（含赠送方）发布、维护、管理自己 listing
的全部能力。本 Epic **不**涉及"买家端浏览"——那部分留给 Epic 3。

### 1.1 In Scope

- Listing CRUD（创建 / 查看 / 编辑 / 删除）—— 主人视角
- 1 张图片上传 + 通过后端服务文件
- 状态机：Available / Reserved / Sold / Removed，6 条转换边
- 分类（独立 `categories` 表 + 8 个种子分类）
- Listing 字段集合（详见 §3 Data Model）
- 前端：My Listings 页、Create 页、Edit 页（共 3 个新页面）
- 后端：8 个 endpoint，全部需要登录，写操作仅 owner 可改自己的 listing（详见 §4 API Contract、§7 Security）

### 1.2 Out of Scope（推迟到 Epic 3 或更后）

- ❌ 公开 Browse / Search / Filter / 公开详情页 → Epic 3
- ❌ 多图上传（MVP 简化为 1 张）
- ❌ 买家"申请 reserve" → M5 消息系统
- ❌ 商品评分、评论 → V2
- ❌ 类似 eBay 的拍卖、出价 → V2+
- ❌ DRAFT 草稿状态（KISS）
- ❌ Listing 自动过期（30 天后自动 Removed 等）
- ❌ Brand / Model / Year-purchased 等 per-category 子字段
- ❌ 浏览次数、收藏次数等度量

### 1.3 Dependencies

- Epic 1 已完成：JWT cookie 认证、`AuthPrincipal` 注入机制、`UserRepository`
- 复用 Epic 1 的 `ApiException` + `ErrorCode` + `GlobalExceptionHandler` 模式（保持错误 JSON 形状一致）
- 复用 Epic 1 的 `apiClient.ts` 前端 HTTP 封装

---

## 2. User Stories

### 2.1 Create Listing
> 作为登录用户，我用一个表单提交标题、描述、价格、分类、成色、自取地点、1 张图片，
> 提交后立即可见（状态默认 `AVAILABLE`），出现在我的 "My Listings" 页面里。

### 2.2 View My Listings
> 作为登录用户，我能看到自己发过的所有 listing（按发布时间倒序），每条卡片
> 显示当前状态（`AVAILABLE` / `RESERVED` / `SOLD`），点进去能编辑。`REMOVED` 状态默认隐藏
> （可通过开关查看历史）。

### 2.3 Edit Listing
> 作为 listing 主人，我能修改自己 listing 的所有字段，包括换一张新图。
> 修改不会改变状态。`REMOVED` 状态的 listing **不能再编辑**。

### 2.4 Change Status
> 作为 listing 主人，我能在合法转换路径上切换状态。

### 2.5 Remove Listing
> 作为 listing 主人，我能把任何一个 listing 移到 `REMOVED` 状态（软删除——记录留在 DB，任何前台都不再展示）。

---

## Decisions Locked So Far

> 这些决定在 brainstorming 阶段已与 owner 确认。具体技术细节（数据模型字段类型、API 形状、URL 等）
> 会在后续 §3-§7 展开。这一节只记录**取舍本身**，给后续工作和论文 "Decision Log" 章节提供锚点。

### DC-1 — 图片采用本地文件系统（封装为接口）

**Choice**: 后端服务器一个 `uploads/listings/<uuid>.<ext>` 目录，DB 存相对路径。
通过 `ImageStorageService` 接口包装，`LocalImageStorageService` 实现读写。

**Alternatives considered**: 对象存储（S3 / Cloudflare R2 / OSS），数据库 BLOB。

**Why**:
- 部署目标为单台 VPS，本地 FS 单机部署完全够用；零依赖、零运维成本。
- 当前学习曲线已经叠了 Spring Security + JWT + Redis + Testcontainers + React Context；再叠云对象存储 SDK / IAM 心智负担过大。
- 用接口隔离，未来一步迁移到 S3 / R2 只换实现类，业务层不动 —— 跟 Epic 1 的 `EmailService`（Console / SMTP 双实现）同一个套路。
- 论文反而能写一段明确的取舍："选择本地存储是 YAGNI 在 MVP 阶段的体现；规模上去后的迁移路径是……"

**Trade-off accepted**: 后端不能横向扩展（每个实例各自的 `uploads/` 目录是孤岛）。VPS 单机部署下不是问题。

---

### DC-2 — 分类用独立 `categories` 表（不是枚举）

**Choice**: 单独建 `categories(id, code, name_en, name_zh, sort_order, active)` 表；listing 通过 `category_id` 外键引用。
初始化 8 个种子分类：BOOKS / ELECTRONICS / FURNITURE / CLOTHING / KITCHEN / SPORTS / TICKETS / OTHER。

**Alternatives considered**:
- Java 枚举 + `VARCHAR(20)`：最简，但加分类要改代码 + 重部署。
- 自由文本 `category` 字段：分类筛选无法做。直接 pass。

**Why**:
- 论文里能展示一个独立的 ER 图实体（多一个值得讲的设计点）。
- 未来给分类加图标、翻译、激活开关都不需要改字段类型。
- 加分类目前还是要 SQL（MVP 没后台管理界面），但比改 enum + 重部署成本低。

**Trade-off accepted**: 多一个 `Category` entity / `CategoryRepository` / 一个 GET endpoint 返回分类列表。Epic 2 任务数 +2。

---

### DC-3 — 状态机：4 个状态 / 7 条转换边 / SOLD 可逆

**Choice**: 状态 `AVAILABLE / RESERVED / SOLD / REMOVED`。允许的转换：

| 起点 | 允许转到 |
|---|---|
| `AVAILABLE` | `RESERVED`, `SOLD`, `REMOVED` |
| `RESERVED` | `AVAILABLE`, `SOLD`, `REMOVED` |
| `SOLD` | `AVAILABLE`, `REMOVED` |
| `REMOVED` | （无 — 终点） |

listing 创建时默认状态 = `AVAILABLE`。所有 transition 仅 owner 可触发。`REMOVED` 是**软删除终点**，记录留在 DB，前台不再显示。

**Alternatives considered**:
- SOLD 也是终点（不可逆）：状态机更干净，但买家退货 / 误点 SOLD 没法挽救。

**Why**:
- 选 "SOLD 可退回 AVAILABLE" 更贴近现实场景。
- `REMOVED` 是终点保留软删除语义（与 User 表的 `deleted_at` 一致 —— 与 Epic 1 D-7 一脉相承）。
- 简化为 7 条边（不是全连通的 12 条），前端 UI 上每个状态最多 3 个动作按钮。

**Trade-off accepted**: SOLD 不再是"成交事实不可变"的语义。未来做销售分析需要看时间戳 / 流水日志，不是看当前状态。

---

### DC-4 — Listing 状态变更走统一 PATCH endpoint

**Choice**: `PATCH /api/listings/{id}/status` body 为 `{ "newStatus": "RESERVED" }`。
Service 层用一张表（`Map<Status, Set<Status>>`）校验合法转换，非法时抛 `ApiException(INVALID_STATUS_TRANSITION, 400)`。

**Alternatives considered**:
- 每个状态变更一个独立动作端点（`POST /reserve`, `/sell`, `/relist`, `/remove`）：URL 语义清晰但 Controller 重复 4 个方法。

**Why**:
- 一个端点一处校验，不重复。
- 转换规则集中在一个 `Map`，论文里画一张状态机图直接对应代码。
- REST 风格上 "PATCH 子资源" 是状态变更的合理形状。

---

### DC-5 — Listing 字段集（含 owner 选定的可选字段）

**Choice — 必备字段**: `id`, `owner_id`, `title`, `description`, `price`, `category_id`, `image_path`, `status`, `created_at`, `updated_at`, `deleted_at`。

**Choice — 可选字段（owner 全部启用）**:
| 字段 | 类型 | 说明 |
|---|---|---|
| `condition` | enum (NEW/LIKE_NEW/GOOD/FAIR/POOR) | 成色，可空 |
| `meet_at` | VARCHAR(100) | 自取地点（如 "Hillcrest D-block"），可空 |
| `negotiable` | BOOLEAN | 可议价标记，默认 `false` |
| `listing_type` | enum (SELL/GIVEAWAY) | 出售或免费送，默认 `SELL`。`GIVEAWAY` 时 `price` 可空 |
| `original_price` | DECIMAL(10,2) | 原价，可空（让买家看折扣） |
| `reason_for_selling` | VARCHAR(100) | 出售原因，可空 |

**Why 加这些**:
- `Condition`：二手市场标配，对未来 Filter（Epic 3）有用。
- `Meet-at`：校园场景定制字段，降低买卖双方约时间的来回。
- `Listing Type (SELL/GIVEAWAY)`：校园场景的"杀手字段"——毕业 / 搬家 / 室友走时大量"送了吧"，论文里能放真实产品观察。
- `Negotiable / Original Price / Reason for Selling`：实现代价低，提升交易信任 / 信息密度。

**Why 不加（已剔除）**:
- Brand/Model/Year purchased：跨分类不统一；
- Tags：搜索属于 Epic 3；
- 手机号 / 微信号：隐私风险；
- Listing 自动过期：需要定时任务，独立 epic 体量；
- 浏览次数：早期优化。

---

### DC-6 — 默认行为锁定（无 DRAFT、不分页、留盘不删）

这些是合理默认，不需要单独大讨论：

- **创建后立即公开可见**：无 `DRAFT` 草稿态，状态机已 4 个不再加。
- **My Listings 不分页（V1）**：单用户量级 < 50 件，分页是早期优化。
- **可编辑状态范围**：任何**非 `REMOVED`** 状态都能编辑，所有字段都能改。
- **图片管理**：编辑时换图覆盖，旧文件留盘不删（KISS，避免误删）；listing `REMOVED` 时图片文件也不删。
- **`GET /api/listings/{id}` 包含进 Epic 2**：编辑页要用它加载现有数据；同一接口 Epic 3 公开详情页复用。

---

## 3. Data Model

### 3.1 MySQL `categories` table

```sql
CREATE TABLE categories (
    id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
    code        VARCHAR(20)  NOT NULL UNIQUE,    -- program-stable identifier (BOOKS, ELECTRONICS, ...)
    name_en     VARCHAR(40)  NOT NULL,           -- "Books & Textbooks"
    name_zh     VARCHAR(40)  NOT NULL,           -- "书籍教材"
    sort_order  INT          NOT NULL DEFAULT 0, -- ordering for the frontend dropdown
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- seed data (initialised on application start or via Flyway / manual script)
INSERT INTO categories (code, name_en, name_zh, sort_order) VALUES
  ('BOOKS',       'Books & Textbooks',  '书籍教材',   10),
  ('ELECTRONICS', 'Electronics',        '电子产品',   20),
  ('FURNITURE',   'Furniture',          '家具',       30),
  ('CLOTHING',    'Clothing & Bags',    '衣物鞋帽',   40),
  ('KITCHEN',     'Kitchen & Home',     '厨房家用',   50),
  ('SPORTS',      'Sports & Outdoors',  '运动器材',   60),
  ('TICKETS',     'Tickets & Events',   '票务',       70),
  ('OTHER',       'Other',              '其他',       99);
```

**Field constraints**:
- `code` is all-uppercase English + underscore; it is the stable identifier used in API request/response bodies.
- `name_en` / `name_zh` are display strings; they can change without affecting business logic.
- When `active = FALSE`, the category cannot be selected for new listings, but existing references stay valid (backward-compatible).

### 3.2 MySQL `listings` table

```sql
CREATE TABLE listings (
    id                  BIGINT         PRIMARY KEY AUTO_INCREMENT,
    owner_id            BIGINT         NOT NULL,
    title               VARCHAR(80)    NOT NULL,
    description         VARCHAR(2000)  NOT NULL,
    price               DECIMAL(10,2)  NULL,         -- NULL allowed when listing_type = GIVEAWAY
    original_price      DECIMAL(10,2)  NULL,
    category_id         BIGINT         NOT NULL,
    image_path          VARCHAR(255)   NOT NULL,     -- relative path, e.g. "listings/abc-123.jpg"
    status              VARCHAR(20)    NOT NULL DEFAULT 'AVAILABLE',
                                                     -- AVAILABLE / RESERVED / SOLD / REMOVED
    listing_type        VARCHAR(20)    NOT NULL DEFAULT 'SELL',
                                                     -- SELL / GIVEAWAY
    `condition`         VARCHAR(20)    NULL,         -- NEW / LIKE_NEW / GOOD / FAIR / POOR
    meet_at             VARCHAR(100)   NULL,
    negotiable          BOOLEAN        NOT NULL DEFAULT FALSE,
    reason_for_selling  VARCHAR(100)   NULL,
    created_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP
                                       ON UPDATE CURRENT_TIMESTAMP,
    deleted_at          DATETIME       NULL,         -- soft-delete column (kept consistent with users)

    CONSTRAINT fk_listings_owner    FOREIGN KEY (owner_id)    REFERENCES users(id),
    CONSTRAINT fk_listings_category FOREIGN KEY (category_id) REFERENCES categories(id)
);

CREATE INDEX idx_listings_owner   ON listings(owner_id);
CREATE INDEX idx_listings_status  ON listings(status);
CREATE INDEX idx_listings_created ON listings(created_at);
CREATE INDEX idx_listings_image   ON listings(image_path);  -- supports the §7.5 image-access owner check
```

**Field-level decisions**:
- **`status` and `condition` are `VARCHAR`, not MySQL `ENUM`** — JPA maps Java enums to `VARCHAR` with `@Enumerated(EnumType.STRING)` by default; this is the most readable, migration-friendly choice. MySQL's native `ENUM` type is awkward to evolve in Flyway.
- **`condition` is backtick-quoted** — `CONDITION` is a reserved word in MySQL; omitting the backticks causes a syntax error. Worth logging as a small gotcha in the journal.
- **`description VARCHAR(2000)` instead of `TEXT`** — keeps an upper bound, plays nicely with indexing and default-value semantics, and is still fully supported under `utf8mb4` in MySQL 8.
- **Soft-delete column `deleted_at`** — kept for parity with the `users` table (Epic 1 D-7), but **not actively used** in this Epic. The `REMOVED` status already covers the "hidden from frontend" need. `deleted_at` is reserved for any future hard-delete pipeline.

**Index strategy**:
- `idx_listings_owner` — used by the "My Listings" page (`WHERE owner_id = ?`).
- `idx_listings_status` — used by Epic 3's public list endpoint (`WHERE status = 'AVAILABLE'`); created up-front to avoid an index migration later.
- `idx_listings_created` — supports the common `ORDER BY created_at DESC` sort.
- `idx_listings_image` — supports the §7.5 image-access owner check (`WHERE image_path = ?`).

### 3.3 Entity relationships (ER diagram)

```
┌──────────┐         ┌────────────┐         ┌──────────────┐
│  users   │ 1     N │  listings  │ N     1 │  categories  │
├──────────┤────────►├────────────┤◄────────┤──────────────┤
│ id (PK)  │         │ id (PK)    │         │ id (PK)      │
│ email    │         │ owner_id   │         │ code         │
│ nickname │         │ category_id│         │ name_en/zh   │
│ ...      │         │ title      │         │ active       │
└──────────┘         │ ...        │         └──────────────┘
                     └────────────┘
```

- A user owns 0..N listings (via `owner_id`).
- A listing belongs to exactly one category (via `category_id`, NOT NULL).
- A category can be referenced by 0..N listings.

### 3.4 JPA entity design notes (preview — full code lives in the implementation plan)

- `Listing` entity is annotated `@Entity` + `@Table(name = "listings")`.
- `Listing.status`, `Listing.condition`, `Listing.listingType` use `@Enumerated(EnumType.STRING)`.
- Relationship to `User` is `@ManyToOne(fetch = FetchType.LAZY)` to avoid N+1 explosions when listing many rows.
- Relationship to `Category` is also `@ManyToOne(fetch = FetchType.LAZY)`.
- Timestamp columns rely on MySQL's `DEFAULT CURRENT_TIMESTAMP` / `ON UPDATE CURRENT_TIMESTAMP` — the same approach Epic 1 took for `users`, rather than `@PrePersist` / `@PreUpdate`.

---

## 4. API Contract

All endpoints live under `/api/listings` (the categories list is the only exception). All endpoints **require authentication** — unauthenticated access returns `401 UNAUTHENTICATED` via the `AuthenticationEntryPoint` introduced in Epic 1 (D-27).

Error responses keep the Epic 1 `ApiErrorResponse` shape:
```json
{ "code": "INVALID_INPUT", "message": "Title is required." }
```

### 4.0 New error codes (added to `ErrorCode.java`)

| Code                        | HTTP | Trigger |
|-----------------------------|------|---------|
| `LISTING_NOT_FOUND`         | 404  | Listing id does not exist, or a non-owner tries to fetch a `REMOVED` listing |
| `NOT_LISTING_OWNER`         | 403  | Authenticated user is not the listing's owner |
| `INVALID_STATUS_TRANSITION` | 400  | Attempted transition is not allowed by the FSM (e.g. `REMOVED` → `AVAILABLE`) |
| `LISTING_REMOVED`           | 400  | Editing a listing whose status is `REMOVED` |
| `INVALID_CATEGORY`          | 400  | `categoryCode` does not exist or `active = false` |
| `INVALID_IMAGE`             | 400  | Image fails MIME / size / decode check |
| `MISSING_IMAGE`             | 400  | Create request without an `image` part |
| `INVALID_PRICE`             | 400  | `SELL` listing missing `price` or `price <= 0` |

### 4.1 `POST /api/listings` — Create listing

**Request**: `Content-Type: multipart/form-data`

| Field | Type | Required | Notes |
|---|---|---|---|
| `title` | text | yes | 1–80 chars |
| `description` | text | yes | 1–2000 chars |
| `categoryCode` | text | yes | e.g. `BOOKS`; must match an active category |
| `listingType` | text | yes | `SELL` or `GIVEAWAY`; default `SELL` |
| `price` | text | conditional | Required and `> 0` when `listingType = SELL`; ignored when `GIVEAWAY` |
| `originalPrice` | text | no | optional; must be `> 0` if present |
| `condition` | text | no | one of `NEW / LIKE_NEW / GOOD / FAIR / POOR` |
| `meetAt` | text | no | up to 100 chars |
| `negotiable` | text | no | `"true"` / `"false"`; default `false` |
| `reasonForSelling` | text | no | up to 100 chars |
| `image` | file | yes | see §5 for upload details |

**Success response**: `201 Created`
```json
{
  "id": 42,
  "ownerId": 7,
  "title": "Calculus textbook",
  "description": "Used for ENGG183, good condition",
  "price": 25.00,
  "originalPrice": 80.00,
  "category": { "code": "BOOKS", "nameEn": "Books & Textbooks", "nameZh": "书籍教材" },
  "imageUrl": "/api/uploads/listings/abc-123.jpg",
  "status": "AVAILABLE",
  "listingType": "SELL",
  "condition": "GOOD",
  "meetAt": "Library foyer",
  "negotiable": true,
  "reasonForSelling": "Finished the paper",
  "createdAt": "2026-05-14T10:30:00Z",
  "updatedAt": "2026-05-14T10:30:00Z"
}
```

**Errors**: `INVALID_INPUT`, `INVALID_CATEGORY`, `INVALID_PRICE`, `MISSING_IMAGE`, `INVALID_IMAGE`, `UNAUTHENTICATED`

### 4.2 `GET /api/listings/me` — List my listings

**Query params**: `includeRemoved` (optional, default `false`)

**Success response**: `200 OK`
```json
{
  "items": [
    {
      "id": 42,
      "title": "Calculus textbook",
      "price": 25.00,
      "imageUrl": "/api/uploads/listings/abc-123.jpg",
      "status": "AVAILABLE",
      "listingType": "SELL",
      "category": { "code": "BOOKS", "nameEn": "Books & Textbooks", "nameZh": "书籍教材" },
      "createdAt": "2026-05-14T10:30:00Z"
    }
  ]
}
```

The list response uses a slim `ListingSummary` DTO that omits long fields (`description`, `meetAt`, `reasonForSelling`, …) to keep payloads small. Full details come from §4.3.

### 4.3 `GET /api/listings/{id}` — Get one listing

Returns the same full `ListingResponse` shape as §4.1.

**Permission rules**:
- The owner sees their listing in any status, including `REMOVED`.
- Any other authenticated user: in this Epic only non-`REMOVED` listings are returned; a `REMOVED` listing yields `LISTING_NOT_FOUND` to hide its existence.
- (Epic 3 public browsing will further restrict non-owners to `AVAILABLE` / `RESERVED` / `SOLD`.)

**Errors**: `LISTING_NOT_FOUND`, `UNAUTHENTICATED`

### 4.4 `PUT /api/listings/{id}` — Edit listing

**Request**: `Content-Type: multipart/form-data` (same shape as §4.1), but the `image` part is **optional**.
- New `image` supplied → the old file stays on disk (KISS, see DC-6); the DB row's `image_path` is updated to point at the new file.
- No `image` → the existing `image_path` is preserved.

**Permission rules**: owner-only; refused when the listing is in `REMOVED` status.

**Success response**: `200 OK` with the updated full `ListingResponse`.

**Errors**: §4.1 set plus `LISTING_NOT_FOUND`, `NOT_LISTING_OWNER`, `LISTING_REMOVED`.

### 4.5 `PATCH /api/listings/{id}/status` — Change status

**Request body**:
```json
{ "newStatus": "RESERVED" }
```

**Validation**:
- Owner-only.
- The transition `currentStatus → newStatus` must be allowed by the FSM defined in DC-3.

**Success response**: `200 OK` with the updated full `ListingResponse`.

**Errors**: `LISTING_NOT_FOUND`, `NOT_LISTING_OWNER`, `INVALID_STATUS_TRANSITION`.

### 4.6 `DELETE /api/listings/{id}` — Remove listing

**Effective semantics**: transition the listing's `status` to `REMOVED` (soft-delete terminal). The DB row is **not** actually deleted.

Equivalent to `PATCH /status` with `{"newStatus":"REMOVED"}`, but the `DELETE` verb is the more natural REST shape for a frontend "delete" button. Keeping both endpoints (PATCH for general transitions, DELETE for removal) is intentional — it lets the frontend stay declarative (`api.delete(...)`) without constructing a PATCH body for the common case.

**Success response**: `204 No Content`.

**Errors**: `LISTING_NOT_FOUND`, `NOT_LISTING_OWNER`.

### 4.7 `GET /api/categories` — List categories

Returns all categories where `active = true` (used by the Create / Edit form's dropdown).

**Success response**: `200 OK`
```json
{
  "items": [
    { "code": "BOOKS", "nameEn": "Books & Textbooks", "nameZh": "书籍教材" },
    { "code": "ELECTRONICS", "nameEn": "Electronics", "nameZh": "电子产品" }
  ]
}
```

This endpoint requires authentication; the list is identical for every signed-in user.

### 4.8 `GET /api/uploads/listings/{filename}` — Serve listing image

Returns the raw image bytes.

**Notes**:
- In this Epic the upload endpoint requires authentication **and** an owner check — see §7.5 for the implementation. Non-owners receive `404 LISTING_NOT_FOUND` (same anti-enumeration treatment as listing access).
- Epic 3 will relax this to `permitAll` for `AVAILABLE` listings only; listing detail and edit are still owner-only here.
- `Cache-Control: max-age=86400` — images are content-addressed by UUID filename and effectively immutable, so a 24-hour client cache is safe.

---

## 5. Image Upload

Image upload is the only place in Epic 2 that introduces binary IO over user-supplied data, so it deserves a dedicated security design. This section defines the multipart handling flow, the on-disk layout, and the validation chain.

### 5.1 Endpoint shape

`POST /api/listings` and `PUT /api/listings/{id}` accept `multipart/form-data`:
- Text fields (`title`, `description`, …) as parts.
- The image as a part with `name="image"`; `Content-Type` is filled in automatically by the browser (e.g. `image/jpeg`).

Spring Boot binds the `image` part to a `MultipartFile` parameter.

### 5.2 Validation chain (inside `ImageStorageService.store()`)

Checks run in order; any failure throws `ApiException(MISSING_IMAGE)` or `ApiException(INVALID_IMAGE)`:

1. **Presence** — `MultipartFile == null` or `isEmpty()` → `MISSING_IMAGE` (only on Create; an Edit without an image part is legal).
2. **Size cap** — `file.getSize() > 5 * 1024 * 1024` (5 MB) → `INVALID_IMAGE`.
3. **Declared content-type whitelist** — must be one of `image/jpeg`, `image/png`, `image/webp`; otherwise `INVALID_IMAGE`.
4. **Magic-number check on the actual bytes** — read the leading bytes and confirm the file *really is* what it claims. Defends against a `.jpg` filename whose contents are an `.exe` or arbitrary binary.
   - JPEG: starts with `FF D8 FF`.
   - PNG:  starts with `89 50 4E 47 0D 0A 1A 0A`.
   - WebP: starts with `52 49 46 46 ?? ?? ?? ?? 57 45 42 50`.
5. **Decodable as an image** — `ImageIO.read(InputStream)` must not return null. Catches "declared as JPEG but the bytes are corrupted".
6. *(Future, V2)* Pixel dimension cap.

Step 3 is what the client *says*; step 4 is what the bytes *prove*. The two layers together stop the classic "rename .exe to .jpg" attack — the browser sets `Content-Type` from the extension, but the magic number doesn't lie.

### 5.3 Filename generation

After the file is accepted, the user's `originalFilename` is **never** retained. Original filenames are an attack vector: path traversal (`../../../etc/passwd`), shell metacharacters, Windows reserved names (`CON.jpg`).

A new filename is computed as `UUID + detected extension`:
```java
String ext = switch (detectedFormat) {
    case JPEG -> ".jpg";
    case PNG  -> ".png";
    case WEBP -> ".webp";
};
String filename = UUID.randomUUID() + ext; // e.g. "3f5a2b1e-...-c9d4.jpg"
```

UUID v4 collisions are practically impossible. The extension comes from the format detected in §5.2 step 4 — never from the original filename.

### 5.4 On-disk layout

```
<app.upload.root>/         # configuration property; defaults to ./uploads
└── listings/              # subdirectory per resource type; future avatars/ etc.
    ├── 3f5a2b1e-...-c9d4.jpg
    ├── b8e7d6c5-...-1a2f.png
    └── ...
```

**Why a flat directory rather than further sharding**: UUID v4 distributes filenames uniformly, listings have at most one image each, so the projected file count is in the low thousands. If the count crosses ~100 000 in the future, a two-level fan-out (`listings/3f/3f5a2b1e-…jpg`) avoids inode pressure on a single directory.

The DB column `image_path` stores the **relative** path (`"listings/3f5a2b1e-...-c9d4.jpg"`). The absolute path is `<app.upload.root> + image_path`.

### 5.5 Configuration (`application-*.properties`)

```properties
# Spring multipart limits (must be >= the §5.2 step-2 cap, otherwise that check never fires)
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB

# Application-defined upload root
app.upload.root=./uploads
```

The `dev` profile uses the relative path `./uploads` (under the project root); `.gitignore` excludes the `uploads/` directory. The `prod` profile uses an absolute path such as `/var/lib/campusmarket/uploads`, and the deployment runbook will `rsync` that directory in backups.

### 5.6 Serving files (`GET /api/uploads/listings/{filename}`)

**Why a controller, not Spring's `ResourceHandler`**: this Epic requires authentication for image access, so the static-resource path won't suffice. The controller is responsible for the security check before reading bytes.

```java
@GetMapping("/api/uploads/listings/{filename:.+}")
public ResponseEntity<Resource> serveImage(@PathVariable String filename) {
    // 1. Validate filename: must not contain "/", "\\", or ".." (path-traversal guard).
    // 2. Resolve absolute path and verify it stays under app.upload.root (defense in depth).
    // 3. Stream the file with Content-Type and Cache-Control headers.
}
```

Key defenses:
- The path-variable regex `{filename:.+}` lets the value keep its dot extension (Spring otherwise truncates after the first `.`).
- An explicit reject list (`..`, `/`, `\\`) plus a strict whitelist (UUID + extension only) is the first line.
- `Path.toRealPath().startsWith(uploadRoot.toRealPath())` is the second line — even if the syntactic checks slip, the resolved physical path must remain inside the upload root.

### 5.7 `ImageStorageService` interface

```java
public interface ImageStorageService {
    /**
     * Validates and stores the uploaded image.
     * @return relative path (e.g. "listings/abc-123.jpg")
     */
    String store(MultipartFile file);

    /** Resolves a stored image to a Resource for serving. */
    Resource load(String relativePath);
}
```

Implementations:
- `LocalImageStorageService` — the only implementation in this Epic; writes under `app.upload.root`.
- `S3ImageStorageService` / `R2ImageStorageService` can be added later without touching business code (DC-1).

### 5.8 Edge-case ledger

| Situation | Handling |
|---|---|
| Edit uploads a new image | Write the new file, update `image_path`; the old file stays on disk (DC-6). |
| Edit omits the image | Keep the existing `image_path`; disk is untouched. |
| Listing transitions to `REMOVED` | The image stays on disk — simpler and avoids accidental deletion; disk cleanup is a future Epic. |
| UUID already exists | Probability ~0; on collision, regenerate the UUID once. A second collision returns `500`. |
| Upload times out / client disconnects | Spring throws `MultipartException`, which `GlobalExceptionHandler` maps to `INVALID_IMAGE`. |

---

## 6. Frontend Pages

Epic 2 introduces **4 new pages and 8 shared components** in the frontend. All UI uses the toolchain already running from Epic 1 (React Router, Tailwind v4, `apiClient`, `AuthContext`). Polish is treated as part of the deliverable: every page handles loading / error / empty states, supports keyboard navigation, and uses optimistic UI for status changes.

### 6.1 Routes

| Path | Component | Guard | Purpose |
|---|---|---|---|
| `/listings/new` | `CreateListingPage` | `ProtectedRoute` | Create a new listing |
| `/listings/mine` | `MyListingsPage` | `ProtectedRoute` | List + filter + paginate + quick actions |
| `/listings/:id` | `ListingDetailPage` | `ProtectedRoute` | Owner read-only detail view (also the prototype reused by Epic 3 public browsing) |
| `/listings/:id/edit` | `EditListingPage` | `ProtectedRoute` + owner guard | Edit form |

Detail and Edit are intentionally separate pages, not a single page with a mode toggle. Modern SPA practice (GitHub, Linear, Notion, Stripe Dashboard) keeps URL = state so refresh / share / back-button all return to a precise view; mode toggles do not survive a refresh.

### 6.2 New components

```
frontend/src/
├── components/
│   ├── ListingForm.tsx         — controlled form shared by Create / Edit / read-only detail
│   ├── ListingCard.tsx         — list card (image, title, price, badge, quick actions)
│   ├── StatusBadge.tsx         — four colors for the four statuses, with ARIA labels
│   ├── ImagePicker.tsx         — file input + preview + client-side validation (5 MB / MIME)
│   ├── ConfirmDialog.tsx       — second-confirm modal for destructive actions
│   ├── EmptyState.tsx          — illustration + CTA for empty pages
│   ├── Pagination.tsx          — prev / next + current/total page indicator
│   └── Spinner.tsx             — loading indicator (also used for skeleton fallbacks)
├── api/
│   └── listings.ts             — endpoint wrappers, TS types, client-side validation helpers
├── hooks/
│   ├── useListings.ts          — fetch / cache / refresh wrapper around listMine
│   └── useOptimisticStatus.ts  — optimistic status changes with rollback on failure
└── pages/
    ├── CreateListingPage.tsx
    ├── MyListingsPage.tsx
    ├── ListingDetailPage.tsx
    └── EditListingPage.tsx
```

`ListingForm` is shared because the Create form, the Edit form, and the read-only Detail view differ only by mode flags; field changes happen in one place.

### 6.3 `frontend/src/api/listings.ts`

```typescript
export type ListingStatus = 'AVAILABLE' | 'RESERVED' | 'SOLD' | 'REMOVED';
export type ListingType = 'SELL' | 'GIVEAWAY';
export type Condition = 'NEW' | 'LIKE_NEW' | 'GOOD' | 'FAIR' | 'POOR';

export interface Category { code: string; nameEn: string; nameZh: string; }

export interface ListingSummary {
  id: number;
  title: string;
  price: number | null;
  imageUrl: string;
  status: ListingStatus;
  listingType: ListingType;
  category: Category;
  createdAt: string;
}

export interface Listing extends ListingSummary {
  ownerId: number;
  description: string;
  originalPrice: number | null;
  condition: Condition | null;
  meetAt: string | null;
  negotiable: boolean;
  reasonForSelling: string | null;
  updatedAt: string;
}

export interface PagedListings {
  items: ListingSummary[];
  page: number;        // 0-indexed
  pageSize: number;    // server-fixed at 12
  totalPages: number;
  totalItems: number;
}

export interface MyListingsQuery {
  page?: number;
  status?: ListingStatus | 'ALL';
  sort?: 'CREATED_DESC' | 'CREATED_ASC' | 'PRICE_DESC' | 'PRICE_ASC';
  includeRemoved?: boolean;
}

export const listingsApi = {
  create: (form: FormData) => apiClient.postForm<Listing>('/api/listings', form),
  listMine: (q: MyListingsQuery) => {
    const params = new URLSearchParams();
    if (q.page != null) params.set('page', String(q.page));
    if (q.status && q.status !== 'ALL') params.set('status', q.status);
    if (q.sort) params.set('sort', q.sort);
    if (q.includeRemoved) params.set('includeRemoved', 'true');
    return apiClient.get<PagedListings>(`/api/listings/me?${params}`);
  },
  getOne: (id: number) => apiClient.get<Listing>(`/api/listings/${id}`),
  update: (id: number, form: FormData) => apiClient.putForm<Listing>(`/api/listings/${id}`, form),
  changeStatus: (id: number, newStatus: ListingStatus) =>
    apiClient.patch<Listing>(`/api/listings/${id}/status`, { newStatus }),
  remove: (id: number) => apiClient.delete<void>(`/api/listings/${id}`),
};

export const categoriesApi = {
  list: () => apiClient.get<{ items: Category[] }>('/api/categories'),
};

// Client-side validation helpers
export const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const;
export const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

export function validateImageClientSide(file: File): string | null {
  if (file.size > MAX_IMAGE_BYTES) return 'Image must be under 5 MB.';
  if (!ALLOWED_IMAGE_TYPES.includes(file.type as typeof ALLOWED_IMAGE_TYPES[number]))
    return 'Image must be JPEG, PNG, or WebP.';
  return null;
}
```

> This raises §4.2 `GET /api/listings/me` to support `page`, `status`, and `sort` query parameters. The backend implementation lands in this Epic too — the service uses Spring Data's `Pageable`.

### 6.4 `MyListingsPage`

**Layout** (top to bottom):
1. **Header bar** — title "My Listings" on the left, primary "+ New Listing" button on the right.
2. **Filter toolbar** — status filter (`All / Available / Reserved / Sold / Removed`), sort dropdown, "Show removed" toggle.
3. **Content area** — depends on the query state:
   - **Loading**: four `ListingCard` skeleton placeholders (preferred over a spinning loader for perceived performance).
   - **Error**: error icon + message + "Try again" button.
   - **Empty (first time)**: illustration + "You haven't posted anything yet — start by creating one!" + CTA to Create.
   - **Empty (filter result)**: "No listings match the current filter." + "Clear filters" button.
   - **Items**: responsive grid 1/2/3/4 columns (mobile / tablet / desktop / wide).
4. **Pagination** — 12 per page; previous / current-of-total / next.

**Quick actions on each card** (ordered by importance):

| Current status | Buttons |
|---|---|
| `AVAILABLE` | "Mark Reserved" / "Mark Sold" / "Edit" / "Remove" (red outline) |
| `RESERVED`  | "Back to Available" / "Mark Sold" / "Edit" / "Remove" |
| `SOLD`      | "Back to Available" / "Edit" / "Remove" |
| `REMOVED`   | Card dimmed / translucent; only "View" is enabled |

**Optimistic update** (key UX):
- On click of a status button, the badge flips to the new status **immediately** while the PATCH request is in flight.
- Success → silent.
- Failure → rollback the badge + toast the error.

**Keyboard accessibility**:
- Card uses `role="article"`, title is `<h3>`.
- Status badge has `aria-label="Status: Available"`.
- Tab order on action buttons follows visual order.
- `ConfirmDialog` autofocuses "Cancel" to reduce accidental destructive clicks.

Clicking "Remove" opens `<ConfirmDialog>` ("This will hide the listing permanently. Continue?"); only confirmation issues the DELETE.

### 6.5 `CreateListingPage`

**Structure**: a single full-width card hosting `<ListingForm mode="create" />`.

**Field order** (organised around the seller's mental flow):
1. Listing Type — radio (`SELL` / `GIVEAWAY`). Toggles whether the price field is required.
2. Category — dropdown (`categoriesApi.list()`).
3. Title + Description — text input + textarea.
4. Price + Original Price — number inputs (greyed out when `GIVEAWAY`).
5. Condition — radio chip group.
6. Meet-at — text.
7. Negotiable — checkbox.
8. Reason for selling — text.
9. Image — `<ImagePicker>` with client-side validation and thumbnail preview.

**Submit flow**:
1. Click Submit → run all client-side validators → on any failure, show field-level errors and focus the first invalid field.
2. All pass → button becomes a Spinner and is disabled → call `listingsApi.create(formData)`.
3. Success → toast "Listing created" → navigate to `/listings/mine` with the new entry first.
4. Failure → dispatch by `ApiError.code` to either field-level error or top banner.

**Unsaved-changes guard**: when the form is dirty (any field has been modified), `useBlocker` from React Router intercepts route changes and shows a `ConfirmDialog` ("You have unsaved changes. Leave anyway?").

### 6.6 `ListingDetailPage` (owner read-only view)

**Why this page**: the demo and thesis benefit from a final-state view of a listing where every field is shown alongside a large image, rather than buried in an edit form. It is also the visual prototype that Epic 3 will reuse for public browsing.

**Layout**:
- Left column: large image (clickable for a lightbox); thumbnail row reserved for a future multi-image V2.
- Right column: title (`<h1>`), price, status badge, category chip, posted-at timestamp.
  - "Edit" primary button + "Delete" outline button (owner only).
  - Detail blocks: description / meet-at / negotiable / condition / original price / reason for selling.
  - Top of column: status-change toolbar mirroring §6.4's quick actions.

**Loading / Error / Not-found states**:
- Loading: full-page skeleton.
- Error: error page + back button.
- Not Found (404): "This listing doesn't exist or has been removed." + back to My Listings.

### 6.7 `EditListingPage`

**Initialisation**:
1. On mount, call `listingsApi.getOne(id)`.
2. If the response is `LISTING_NOT_FOUND` (which also covers "non-owner hidden"), navigate to `/listings/mine` with a toast.
3. If `status === 'REMOVED'`, render a read-only view with "This listing has been removed and cannot be edited."
4. Otherwise, prefill `<ListingForm mode="edit" initial={listing} />`.

**Differences from Create**:
- Submit button is "Save changes".
- Image is **optional**: a "Current image" thumbnail is shown alongside a "Replace" button.
- Top of the page hosts the status-change toolbar plus a "Delete" button (logic mirrors §6.4).
- Same unsaved-changes guard as §6.5.

### 6.8 Integration with Epic 1

| Epic 1 piece | Epic 2 reuse / extension |
|---|---|
| `apiClient` | Add `postForm` / `putForm` for multipart |
| `AuthContext` / `useAuth` | Source `user.id` for client-side owner pre-checks (server is the source of truth) |
| `Navbar` | Add a "My Listings" link, visible only when authenticated |
| `ProtectedRoute` | Wraps all four new routes; redirects unauthenticated users to `/login?next=...` |
| `ApiError` + `code` switching | Reused unchanged; the eight new error codes drop into the existing try/catch fabric |
| `PasswordInput` extraction pattern | Same approach for `ImagePicker`, `StatusBadge`, `ConfirmDialog` |
| Register / Login form aesthetics | Reuse the same field-error styling and loading-button treatment |

### 6.9 Visuals and accessibility

- Tailwind v4 default palette; **no** custom theme colors.
- Status badge colors:
  - `AVAILABLE` → `bg-emerald-100 text-emerald-700`
  - `RESERVED` → `bg-amber-100 text-amber-700`
  - `SOLD` → `bg-sky-100 text-sky-700`
  - `REMOVED` → `bg-gray-200 text-gray-600`
- `ListingCard` images use `aspect-ratio: 4/3` + `object-cover` to keep card heights stable.
- Every interactive element ≥ 44 × 44 px touch target (mobile-friendly).
- Color contrast meets WCAG AA (Tailwind defaults broadly comply).
- Every image carries an `alt` of the listing title; decorative icons use `aria-hidden="true"`.
- Form `<label>` uses strict `htmlFor` ↔ `id` pairing.
- Error messages use `role="alert"` + `aria-live="polite"`.

### 6.10 Centralised copy (i18n preparation)

A new `frontend/src/i18n/listings.ts` keeps user-visible strings in one place:

```typescript
export const t = {
  myListings: { en: 'My Listings', zh: '我的发布' },
  newListing:  { en: '+ New Listing', zh: '+ 新建' },
  emptyTitle:  { en: "You haven't posted anything yet", zh: '你还没发过任何商品' },
  // ...
};
```

V1 reads `t.xxx.en` only. The structure is intentionally library-free — `react-i18next` and friends add bundle weight that the MVP does not need. When V2 introduces locale switching, the call sites are already routing through `t`, so the change is mechanical.

---

## 7. Security & Permissions

Epic 2 introduces the first user-operates-on-others'-data surface, so IDOR (Insecure Direct Object Reference) defenses must be systematic. This section pins down where checks live, the response-shape choices that prevent enumeration, and the rate-limit boundaries.

### 7.1 Threat model

| Vector | Example | Defense |
|---|---|---|
| IDOR — edit others | `PUT /api/listings/43` where 43 belongs to someone else | Service-layer owner check (§7.2) |
| IDOR — delete others | `DELETE /api/listings/43` | Same |
| IDOR — status manipulation | `PATCH /api/listings/43/status` | Same |
| Existence enumeration | Sweep ids `1..1000` to distinguish "absent" vs "exists but not mine" | Both paths return the same `404 LISTING_NOT_FOUND` (§7.3) |
| Path traversal (image) | `GET /api/uploads/listings/../../../../etc/passwd` | §5.6 dual-layer guard |
| MIME forgery (upload) | Upload `.exe` renamed to `.jpg` | §5.2 magic-number check |
| CSRF | Third-party site triggers a state-changing request via the user's cookie | Inherited Epic 1 D-19: `SameSite=Lax` + `HttpOnly` |
| Cookie replay | Stolen cookie reused after logout | Inherited Epic 1 D-13: JWT blacklist + short TTL |
| Image link leak | Owner shares image URL externally; recipient bypasses listing visibility | §7.5 owner check on `/api/uploads/**` |
| Spam / abuse / runaway loop | Authenticated user floods create / update / status / delete | §7.8 per-user rate limits |

### 7.2 Owner check lives in the service layer

Owner verification runs at the top of every write method on `ListingService`:

```java
public Listing update(Long listingId, AuthPrincipal me, UpdateRequest req) {
    Listing listing = listings.findById(listingId)
        .orElseThrow(() -> new ApiException(LISTING_NOT_FOUND));
    if (!listing.getOwnerId().equals(me.userId())) {
        throw new ApiException(LISTING_NOT_FOUND);  // not NOT_LISTING_OWNER — see §7.3
    }
    // apply update
}
```

**Alternatives considered**: `@PreAuthorize` SpEL expressions; `if`-checks in the controller.

**Why service-layer**:
1. Consistent with Epic 1 D-9 / D-10 — business validation belongs in the service.
2. Unit-testable without bootstrapping Spring Security; pass `AuthPrincipal` directly and assert the exception.
3. `@PreAuthorize` failures throw `AccessDeniedException` with no structured `ErrorCode`.
4. Keeps controllers thin (Epic 1 D-23 style).

### 7.3 `404` rather than `403`: deliberate ambiguity

A non-owner who hits another user's listing receives `404 LISTING_NOT_FOUND`, not `403 NOT_LISTING_OWNER`. The 403 code is reserved for client-side preconditions or a future admin view; **the public API never returns it in this Epic**.

**Why**: same anti-enumeration principle as Epic 1 D-11 / D-14 / D-16. If 404 and 403 differed, an attacker scanning ids could distinguish "this id is unused" from "this id exists, owned by someone else" — the latter is a leak.

### 7.4 Validation chain order (preventing leakage)

Every write operation runs checks in strict order; the first failure short-circuits. A failure at step 3 must not reveal whether step 4 would have passed:

1. **Authentication** — enforced by the JWT filter; unauthenticated requests never reach the controller.
2. **Path-variable resolution + listing existence** → 404 `LISTING_NOT_FOUND` if absent.
3. **Owner check** → 404 `LISTING_NOT_FOUND` if not owner.
4. **Business rules** (status editable? transition legal? field values valid?) → specific error codes.

This ordering ensures that a non-owner cannot probe the listing's status or other state via differential responses.

### 7.5 Image access also performs an owner check

`GET /api/uploads/listings/{filename}` enforces ownership: the request resolves the filename to its `Listing` via the DB (`image_path` is unique enough that one indexed lookup suffices), and refuses if the requester is not the owner.

```java
@GetMapping("/api/uploads/listings/{filename:.+}")
public ResponseEntity<Resource> serveImage(@PathVariable String filename,
                                           @AuthenticationPrincipal AuthPrincipal me) {
    Listing l = listings.findByImagePath("listings/" + filename)
        .orElseThrow(() -> new ApiException(LISTING_NOT_FOUND));
    if (!l.getOwnerId().equals(me.userId())) {
        throw new ApiException(LISTING_NOT_FOUND);
    }
    return imageStorage.load(l.getImagePath()) ...
}
```

**Why this is added** (revising the original "UUID is unguessable so skip" reasoning):
1. Image URLs leak in practice — owners share screenshots with the URL bar visible, browser history is synced, pasted links live in chats.
2. The extra DB query is one indexed lookup on `image_path` — well under 1 ms locally; cacheable if it ever becomes hot.
3. Aligns with the project-wide "completeness over minimalism" stance.
4. Epic 3 will relax this to `permitAll` for `AVAILABLE` listings only — the relaxation is per-status, not unconditional.

A new index `idx_listings_image` on the `listings` table (added in §3.2) keeps this lookup `O(log n)`.

### 7.6 Input validation: DTO annotations plus service rules

Inherits Epic 1 D-10:

| Layer | Checks |
|---|---|
| DTO (`@Valid`) | Field non-null, length caps, `@DecimalMin`, etc. |
| Service | Business rules — categoryCode exists and active; SELL needs `price > 0`; status transition legal |

`CreateListingRequest`, `UpdateListingRequest`, `ChangeStatusRequest` all use `@Valid` at the controller boundary; service methods perform the additional business validation and throw `ApiException`, mapped by `GlobalExceptionHandler`.

### 7.7 `SecurityConfig` changes

```java
.authorizeHttpRequests(auth -> auth
    // existing Epic 1 rules carry over
    .requestMatchers(HttpMethod.GET, "/api/categories").authenticated()
    .requestMatchers("/api/listings/**").authenticated()
    .requestMatchers("/api/uploads/**").authenticated()
    .anyRequest().authenticated()
)
.csrf(AbstractHttpConfigurer::disable)  // unchanged from Epic 1 D-19
```

Multipart requests pass through the existing filter chain; no new filter required.

### 7.8 Rate limiting (per-user, per-endpoint)

Even authenticated users can abuse write endpoints. The `RateLimitService` from Epic 1 D-3 is reused; new key prefixes are added:

| Endpoint | Key | Limit | Window | Rationale |
|---|---|---|---|---|
| `POST /api/listings` | `ratelimit:listing:create:<userId>` | **20** | 1 hour | Spam-listing prevention; far above any legitimate user rate. Stripe-class write quota. |
| `PUT /api/listings/{id}` | `ratelimit:listing:update:<userId>` | **60** | 1 hour | Defends against runaway client loops; far above realistic editing pace. |
| `PATCH /api/listings/{id}/status` | `ratelimit:listing:status:<userId>` | **120** | 1 hour | Light operation; allows quick UI-driven flips. |
| `DELETE /api/listings/{id}` | `ratelimit:listing:delete:<userId>` | **30** | 1 hour | Destructive — kept tighter. |
| Image bytes uploaded | `ratelimit:listing:imagebytes:<userId>` | **50 MB** | 1 hour | Disk-exhaustion ceiling; complements per-call counts. |
| Read endpoints (`GET /me`, `GET /:id`, `GET /uploads/**`, `GET /categories`) | — | **none** | — | Industry standard: read endpoints are unrestricted unless cache-busting becomes a concern. |

**Key choices**:
- **By `userId`, not by IP** — campus / corporate networks share NAT; per-IP limits would lock everyone behind the same egress.
- **All limits return `429 Too Many Requests` with `Retry-After: <seconds>`** — see §7.10 for the Epic 1 fix that enables this.
- **One-hour windows** — short enough to recover quickly, long enough that bursts cannot dodge by waiting a few seconds.
- **No global / application-layer DoS limit** — that belongs to nginx / Cloudflare in front of the app. Documented to keep concerns separate.

The byte-counting limiter needs `RateLimitService.incrementBy(key, bytes, ttl)` — a small extension to Epic 1's `increment(key)`. Both delegate to Redis `INCRBY` + `EXPIRE`.

### 7.9 Audit logging

Structured INFO-level logs in the service layer for every state-changing operation:

```java
log.info("listing.create userId={} listingId={} categoryCode={} listingType={}", ...);
log.info("listing.update userId={} listingId={} fieldsChanged={}", ...);
log.info("listing.statusChange userId={} listingId={} from={} to={}", ...);
log.info("listing.remove userId={} listingId={}", ...);
log.info("listing.imageServe userId={} listingId={} filename={}", ...);
```

Sensitive content excluded: image bytes, raw email addresses (use `userId` instead).

### 7.10 Epic 1 carry-over fix: `429` with `Retry-After`

Epic 1's `TOO_MANY_ATTEMPTS` currently maps to HTTP `400` (the default for `ApiException`). The industry standard is `429 Too Many Requests` plus a `Retry-After` header indicating the seconds until the window resets.

**Fix scope** (small, lands alongside the Epic 2 rate-limit code):
1. Extend `ErrorCode` so each entry carries an `HttpStatus` (default `BAD_REQUEST`); `TOO_MANY_ATTEMPTS` overrides to `TOO_MANY_REQUESTS`.
2. `GlobalExceptionHandler.handleApiException` reads the override and sets the response status accordingly.
3. `ApiException(TOO_MANY_ATTEMPTS, ...)` accepts an optional `retryAfterSeconds` payload; the handler emits `Retry-After: <seconds>`.
4. `RateLimitService.exceeded()` returns the remaining TTL alongside the boolean so callers can populate `retryAfterSeconds`.

This brings Epic 1's existing login / register limits up to spec **and** wires the same plumbing for the new Epic 2 limits — done once for both.

---

## 8. Testing Strategy

Epic 2 inherits the testing pyramid that Epic 1 validated and adds **frontend automated testing** as a first-class layer. Epic 1 produced 36 backend tests plus a manual E2E checklist; Epic 2 produces backend tests of similar density **and** adds Vitest / React Testing Library / MSW / Playwright on the frontend.

### 8.1 Layers and tools

| Layer | Backend | Frontend |
|---|---|---|
| **Unit** (mock everything) | JUnit 5 + Mockito | Vitest + React Testing Library |
| **Component** (single real dependency in a container) | Testcontainers (Redis only) | Vitest + RTL + MSW |
| **Integration** (whole stack) | Spring Boot Test + Testcontainers (MySQL + Redis) | Playwright (real browser) |
| **Manual E2E** | `docs/manual-e2e-epic2.md` | Same |

The frontend stack (Vitest, RTL, MSW, Playwright) is installed once in this Epic; Epic 3 onward inherits it.

### 8.2 Backend tests

#### 8.2.1 Unit tests (`src/test/java/.../service/`)

| Test class | Coverage |
|---|---|
| `ListingServiceCreateTest` | Title length, SELL requires price, GIVEAWAY rejects price, unknown / inactive category, owner auto-set, status defaults to `AVAILABLE`, `imageStorage.store()` invoked once |
| `ListingServiceUpdateTest` | Owner check throws `LISTING_NOT_FOUND`, `REMOVED` throws `LISTING_REMOVED`, field updates, optional image (path preserved when absent), `imageStorage.store()` invoked only on replacement |
| `ListingServiceStatusTest` | All 7 legal transitions pass; illegal transitions throw `INVALID_STATUS_TRANSITION`; owner check |
| `ListingServiceQueryTest` | `listMine` paging / sorting / status filter / `includeRemoved`; `getOne` hides `REMOVED` from non-owners |
| `ListingServiceRemoveTest` | Soft delete (`status = REMOVED`), owner check, already-`REMOVED` remains idempotent |
| `LocalImageStorageServiceTest` | Magic-number validation (JPEG / PNG / WebP pass; spoofed MIME rejected), 5 MB cap, UUID filename generation, path-traversal defenses |
| `RateLimitServiceIncrementByTest` | `incrementBy` accumulates byte counts; `exceeded()` returns remaining TTL; isolated keys |

Mocking strategy follows Epic 1:
- All repositories are mocked (`UserRepository`, `ListingRepository`, `CategoryRepository`).
- `ImageStorageService` is mocked at the `ListingService` boundary; the real implementation has its own tests.
- `RateLimitService` is mocked at the service-test layer; the real implementation is exercised by component tests.

Projected: ~35 unit-test methods across 7 classes.

#### 8.2.2 Component tests (Testcontainers)

| Test class | Container | Coverage |
|---|---|---|
| `LocalImageStorageServiceIntegrationTest` | none (uses a temp directory) | Real disk write, readback, concurrent UUID writes |
| `RateLimitServiceTest` (existing) | Redis | Extended with `incrementBy` and TTL-aware `exceeded` behavior |

#### 8.2.3 Integration tests

New `ListingControllerIntegrationTest extends AbstractIntegrationTest`:

| Scenario | Method name |
|---|---|
| Create — happy path | `createReturnsListingWithStatusAvailable` |
| Create — missing image | `createWithoutImageReturns400MissingImage` |
| Create — spoofed MIME | `createWithExeRenamedAsJpgReturns400InvalidImage` |
| Create — unknown category | `createWithUnknownCategoryReturns400` |
| Create — rate-limit hit | `createOver20PerHourReturns429WithRetryAfter` |
| List mine — paging | `listMineRespectsPageAndSize` |
| List mine — status filter | `listMineFiltersByStatus` |
| Get one — owner sees `REMOVED` | `getOneOwnerCanSeeRemoved` |
| Get one — non-owner gets 404 | `getOneNonOwnerReceives404SameAsMissing` |
| Update — non-owner gets 404 | `updateByNonOwnerReceives404` |
| Update — `REMOVED` rejected | `updateOnRemovedReturns400ListingRemoved` |
| Status — all 7 legal transitions | `statusAllLegalTransitionsSucceed` |
| Status — illegal transition | `statusFromRemovedReturns400` |
| Delete — soft delete + 204 | `deleteSetsRemovedAndReturns204` |
| Image — owner sees | `imageServeReturnsBytesForOwner` |
| Image — non-owner gets 404 | `imageServeForNonOwnerReturns404` |
| Image — path traversal blocked | `imageServeRejectsPathTraversal` |
| Categories — list active only | `listCategoriesReturnsActiveOnly` |

Projected: ~18 integration tests.

#### 8.2.4 Epic 1 carry-over fix tests

`AuthServiceLoginLogoutTest` adds:
- `loginRateLimitExceededReturns429StatusCode` — verifies the §7.10 status-code fix.
- `loginRateLimitExceededIncludesRetryAfter` — verifies the response header.

### 8.3 Frontend tests (introduced in this Epic)

#### 8.3.1 Setup

```
frontend/
├── vitest.config.ts           # jsdom env, setup file, coverage config
├── src/test/
│   ├── setup.ts               # @testing-library/jest-dom + vitest matchers
│   └── mocks/handlers.ts      # MSW handlers — mocked backend API
├── playwright.config.ts       # E2E browser automation config
└── e2e/
    └── listings.spec.ts       # E2E flows
```

#### 8.3.2 Unit tests (Vitest, fully mocked)

Pure functions and utilities:
- `validateImageClientSide.test.ts` — 5 MB boundary; the three allowed MIME types pass; everything else rejects.
- `i18n/listings.test.ts` — copy-table lookup correctness.

Hooks:
- `useOptimisticStatus.test.ts` — happy path does not roll back; failure rolls back to the previous status; concurrent requests behave correctly.

#### 8.3.3 Component tests (Vitest + RTL + MSW)

| Test file | Coverage |
|---|---|
| `ListingCard.test.tsx` | All four badge colors / labels; per-status quick-action button sets; Remove opens `ConfirmDialog`; optimistic rollback on failure |
| `ListingForm.test.tsx` | Create vs Edit differences; `GIVEAWAY` greys out price; client-side validation indicators; dirty detection on submit |
| `ImagePicker.test.tsx` | Rejects > 5 MB; rejects non-image MIME; thumbnail render; replace flow |
| `ConfirmDialog.test.tsx` | Focus defaults to Cancel; Esc closes; focus trap prevents Tab leaving the modal |
| `Pagination.test.tsx` | Edge states (prev disabled on first page, next disabled on last); click callbacks fire |
| `MyListingsPage.test.tsx` | loading → empty → items state machine; filter changes refetch; correct paging params |
| `CreateListingPage.test.tsx` | Submit happy path navigates to `/listings/mine`; server-side error codes map to field-level error states; unsaved-changes guard |
| `EditListingPage.test.tsx` | Prefilled from `getOne`; `REMOVED` renders read-only banner; non-owner is redirected |
| `ListingDetailPage.test.tsx` | Owner sees all fields; `REMOVED` banner appears; status toolbar and Delete button visible |

Projected: ~30 component-test methods.

#### 8.3.4 E2E tests (Playwright)

Full stack: backend (Testcontainers) + frontend dev server + headless browser.

| Spec | Flow |
|---|---|
| `e2e/create-flow.spec.ts` | Sign in → `/listings/new` → fill form → upload image → submit → new entry visible at `/listings/mine` |
| `e2e/edit-flow.spec.ts` | Edit existing listing → change title and image → save → detail page reflects changes |
| `e2e/status-flow.spec.ts` | AVAILABLE → RESERVED → SOLD → back to AVAILABLE → REMOVED |
| `e2e/permission.spec.ts` | User A creates listing → user B signs in → user B visiting `/listings/{A_id}` receives 404 |
| `e2e/image-leak.spec.ts` | User A creates listing → A copies image URL → user B signs in and visits the URL → receives 404 (verifies §7.5) |

#### 8.3.5 Manual E2E checklist (`docs/manual-e2e-epic2.md`)

Follows the Epic 1 style and serves the same role for teacher demos:
- Setup (startup order).
- Create flow with all fields and image.
- My Listings — list, filter, paginate.
- Detail page — every field renders.
- Edit flow including image replacement.
- All 7 status transitions.
- Delete with confirmation modal.
- Rate limit: 21 create requests trigger 429.
- Cross-user access → 404.
- Image-URL leak between users → 404.

### 8.4 Coverage targets

| Layer | Target |
|---|---|
| Backend `service` and `controller` packages — line | ≥ 85% |
| Backend overall (including `entity` / `dto` getters) — line | ≥ 75% |
| Frontend hooks and utility functions — line | ≥ 85% |
| Frontend components — branch | ≥ 70% |

**Coverage is reported but not enforced as a CI gate.** JaCoCo (backend) and Vitest's c8 (frontend) generate HTML reports per build; the numbers are documented in the thesis as a quality indicator. A failed build is never caused by a coverage drop alone — the team's judgment, not a percentage, decides whether a test is missing.

The rationale matches the project-wide stance that Coverage *measures presence of test execution*, not test quality. Reaching 100% is straightforward by writing trivial getter tests, but those tests do not protect against regressions. The targets above are calibrated against Spring Framework / React open-source ranges and are realistic given the testing structure designed in §8.2 and §8.3.

### 8.5 Performance budgets

| Suite | Target local runtime |
|---|---|
| Backend unit tests (mocked) | < 5 s |
| Backend component tests (Testcontainers Redis only) | < 30 s |
| Backend integration tests (Testcontainers MySQL + Redis) | < 60 s |
| Frontend unit + component tests (jsdom) | < 15 s |
| Playwright E2E (headless) | < 90 s |
| **CI overall** | **< 4 minutes** |

If a layer exceeds its budget, optimize the tests first (avoid full Spring Boot context per test, parallelize, share containers) rather than removing tests.

### 8.6 Pyramid reuse and evolution

Reused from Epic 1:
- `AbstractIntegrationTest` (extended unchanged).
- `application-test.properties` (one new line: `app.upload.root=${java.io.tmpdir}/test-uploads`).
- Testcontainers BOM `1.21.3` and the surefire `api.version=1.40` override from Problem #1.

Added in Epic 2 for reuse from Epic 3 onward:
- Helpers on `AbstractIntegrationTest` to generate valid JPEG / PNG / WebP byte streams plus spoofed binaries for negative tests.
- The entire frontend testing stack (Vitest, RTL, MSW, Playwright).

### 8.7 Thesis material

The testing design itself is thesis material:
- Why 5 layers (unit / component / integration / E2E / manual) rather than 3, with industrial-practice justification.
- The introduction of frontend automated testing as a trade-off Epic 1 deferred and Epic 2 fulfils.
- A critical view of "Coverage as KPI" rather than as goal.
- Total projected test count (~83 backend + ~30 frontend + ~5 E2E ≈ 118) as evidence of completeness.

---

## Appendix A — Document inventory

This spec produced the following artifacts in the project tree:
- `docs/superpowers/specs/2026-05-14-epic-2-listings-design.md` (this file)
- `docs/superpowers/plans/2026-05-14-epic-2-listings.md` (to be created by the writing-plans workflow next)
- `docs/manual-e2e-epic2.md` (to be authored during implementation, mirroring Epic 1)
- Engineering-journal entries `D-38..` are appended task-by-task during implementation, matching the Epic 1 cadence.

*End of spec.*
