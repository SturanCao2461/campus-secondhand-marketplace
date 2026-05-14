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
- 后端：6 个 endpoint，全部需要登录，仅 owner 可改自己的 listing

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

## (Sections 4–8 to be added incrementally as the design discussion progresses.)

- §4 API Contract — 6 个 endpoint 的 URL / 入参 / 出参 / 错误码
- §5 Image Upload Detail — multipart 处理、文件名生成、磁盘布局、安全（MIME/大小校验）
- §6 Frontend Pages — My Listings / Create / Edit 页面 + 路由 + 表单 / 状态切换 UI
- §7 Security & Permissions — owner-only 校验位置、错误码、防 IDOR
- §8 Testing Strategy — 单元 / 组件 / 集成 三层测试边界
