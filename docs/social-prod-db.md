# Base de datos: social

Rama `feature/social`. Plan: `social-plan.md`. Esquema final de referencia (dev y prod): `ballastasera_schema.sql`.

No hay Flyway ni Liquibase: todo se ejecuta **a mano**. El mismo script vale para dev y para prod. Cada fase
suma su bloque aquí; en prod se corren todos juntos antes del deploy del BE con todo lo social.

**Orden respecto al deploy:**
- El SQL se puede correr **antes** del deploy: es aditivo y el BE que está en prod no lo usa.
- El BE nuevo **no** puede salir antes del SQL: mapea tablas y un valor del enum que no existirían.

| Fase | Qué | Dev | Prod |
|---|---|---|---|
| 1 | `MUTUALS`, default de WhatsApp, `user_follows`, `user_blocks` | ☐ | ☐ |
| 2 | Columnas del perfil en `users` (bio, serate, escuela, ciudad, país, foto) | ☐ | ☐ |
| 3 | `looking_for_partner` en `event_attendance` | ☐ | ☐ |
| 4 | `user_reports`, `muted` en `user_follows`, `banned_at` en `users` | ☐ | ☐ |
| 6 | `notifications`, `email_notifications` en `users` | ☐ | ☐ |

---

## Fase 1 — Seguir + bloquear

### Comprobaciones previas (solo lectura)

```sql
-- MUTUALS todavía no existe → 0 filas
SELECT enumlabel FROM pg_enum e JOIN pg_type t ON t.oid = e.enumtypid
WHERE t.typname = 'contact_visibility' AND enumlabel = 'MUTUALS';

-- Las tablas no existen todavía → NULL, NULL
SELECT to_regclass('public.user_follows'), to_regclass('public.user_blocks');
```

### Paso 1: valor nuevo del enum (solo, fuera de una transacción)

`ALTER TYPE ... ADD VALUE` no se puede usar en la misma transacción que lo crea: el `SET DEFAULT 'MUTUALS'`
del paso 2 fallaría. Por eso va solo y antes.

```sql
ALTER TYPE contact_visibility ADD VALUE IF NOT EXISTS 'MUTUALS';
```

### Paso 2: default de WhatsApp y tablas

El default solo afecta a los usuarios **nuevos**: a los que ya existen no se les toca su `whatsapp_visibility`.

```sql
BEGIN;

ALTER TABLE users ALTER COLUMN whatsapp_visibility SET DEFAULT 'MUTUALS';

CREATE TABLE user_follows (
    follower_id UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    followed_id UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (follower_id, followed_id),
    CHECK (follower_id <> followed_id)
);
CREATE INDEX idx_user_follows_followed ON user_follows(followed_id, created_at DESC);
CREATE INDEX idx_user_follows_follower ON user_follows(follower_id, created_at DESC);

CREATE TABLE user_blocks (
    blocker_id UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);
CREATE INDEX idx_user_blocks_blocked ON user_blocks(blocked_id);

COMMIT;
```

### Comprobaciones posteriores

```sql
-- → 'MUTUALS'
SELECT column_default FROM information_schema.columns
WHERE table_name = 'users' AND column_name = 'whatsapp_visibility';

-- → user_follows, user_blocks
SELECT to_regclass('public.user_follows'), to_regclass('public.user_blocks');
```

### Rollback

```sql
BEGIN;
DROP TABLE IF EXISTS user_blocks;
DROP TABLE IF EXISTS user_follows;
UPDATE users SET whatsapp_visibility = 'NOBODY' WHERE whatsapp_visibility = 'MUTUALS';
ALTER TABLE users ALTER COLUMN whatsapp_visibility SET DEFAULT 'EVERYONE';
COMMIT;
-- El valor MUTUALS queda en el enum: PostgreSQL no permite quitar un valor. No molesta.
```

El `UPDATE` del rollback pasa a `NOBODY` a quien eligió «solo amigos»: sin amigos no se puede respetar esa
elección, y `EVERYONE` mostraría su WhatsApp a gente que no eligieron.

---

## Fase 2 — Perfil

Va **después** de la fase 1 (`attendance_visibility` usa `contact_visibility`). Solo columnas nuevas en
`users`, todas con default o nullable: no se toca ningún dato existente salvo copiar la foto de Google.

### Comprobaciones previas (solo lectura)

```sql
-- El tipo no existe todavía → 0 filas
SELECT typname FROM pg_type WHERE typname = 'avatar_source';

-- Las columnas no existen todavía → 0 filas
SELECT column_name FROM information_schema.columns
WHERE table_name = 'users'
  AND column_name IN ('bio', 'attendance_visibility', 'school_organizer_id', 'avatar_source', 'home_city');
```

### Script

```sql
BEGIN;

CREATE TYPE avatar_source AS ENUM ('GOOGLE', 'CUSTOM', 'NONE');

ALTER TABLE users
    ADD COLUMN google_avatar_url     TEXT,
    ADD COLUMN custom_avatar_url     TEXT,
    ADD COLUMN avatar_source         avatar_source NOT NULL DEFAULT 'GOOGLE',
    ADD COLUMN bio                   TEXT CHECK (char_length(bio) <= 150),
    ADD COLUMN attendance_visibility contact_visibility NOT NULL DEFAULT 'EVERYONE',
    ADD COLUMN school_organizer_id   UUID REFERENCES organizers(id) ON DELETE SET NULL,
    ADD COLUMN school_name           TEXT CHECK (char_length(school_name) <= 80),
    ADD COLUMN origin_country        CHAR(2),
    ADD COLUMN home_city             TEXT,
    ADD COLUMN home_region           TEXT,
    ADD COLUMN home_country          CHAR(2),
    ADD COLUMN home_latitude         DOUBLE PRECISION,
    ADD COLUMN home_longitude        DOUBLE PRECISION,
    ADD COLUMN show_home_city        BOOLEAN NOT NULL DEFAULT FALSE;

-- Hoy avatar_url es siempre la de Google: se copia para que el login nuevo la tenga desde el primer día
UPDATE users SET google_avatar_url = avatar_url;

COMMIT;
```

En la DB de **dev**, la FK `school_organizer_id` queda con un nombre automático; en `ballastasera_schema.sql`
se llama `fk_users_school_organizer` porque ahí `users` se crea antes que `organizers`. Funcionan igual.

### Comprobaciones posteriores

```sql
-- Todos los que tenían foto la tienen copiada → 0
SELECT count(*) FROM users WHERE avatar_url IS NOT NULL AND google_avatar_url IS NULL;

-- Defaults → GOOGLE | EVERYONE | false para todos
SELECT avatar_source, attendance_visibility, show_home_city, count(*) FROM users GROUP BY 1, 2, 3;
```

### Rollback

```sql
BEGIN;
ALTER TABLE users
    DROP COLUMN google_avatar_url, DROP COLUMN custom_avatar_url, DROP COLUMN avatar_source,
    DROP COLUMN bio, DROP COLUMN attendance_visibility, DROP COLUMN school_organizer_id, DROP COLUMN school_name,
    DROP COLUMN origin_country, DROP COLUMN home_city, DROP COLUMN home_region, DROP COLUMN home_country,
    DROP COLUMN home_latitude, DROP COLUMN home_longitude, DROP COLUMN show_home_city;
DROP TYPE avatar_source;
COMMIT;
```

Antes del rollback, si alguien subió una foto propia, `avatar_url` apunta a ella (bucket de logos, `avatars/`):
sigue funcionando, pero el login la pisará con la de Google como antes.

---

## Fase 3 — «Cerco qualcuno con cui ballare»

Una columna con default en `event_attendance`: los «Ci vado» que ya existen quedan con `false`.

```sql
-- Antes: la columna no existe → 0 filas
SELECT column_name FROM information_schema.columns
WHERE table_name = 'event_attendance' AND column_name = 'looking_for_partner';

ALTER TABLE event_attendance ADD COLUMN looking_for_partner BOOLEAN NOT NULL DEFAULT FALSE;

-- Después: todos en false → una fila: false | <total>
SELECT looking_for_partner, count(*) FROM event_attendance GROUP BY 1;
```

Rollback: `ALTER TABLE event_attendance DROP COLUMN looking_for_partner;`

---

## Fase 4 — Denuncias, ban, silenciar

Todo aditivo y sin `ALTER TYPE ... ADD VALUE` (los dos enums son nuevos), así que va en una sola transacción.
Los follows que ya existen quedan sin silenciar; `banned_at` queda null también para los que ya estaban baneados
(no sabemos cuándo fue).

```sql
-- Antes: no existen → NULL | NULL | NULL
SELECT to_regclass('public.user_reports'), to_regtype('report_reason'), to_regtype('report_status');

BEGIN;
CREATE TYPE report_reason AS ENUM ('FAKE_PROFILE', 'HARASSMENT', 'INAPPROPRIATE_PHOTO', 'OTHER');
CREATE TYPE report_status AS ENUM ('OPEN', 'DISMISSED', 'BANNED');

CREATE TABLE user_reports (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reported_id UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason      report_reason NOT NULL,
    note        VARCHAR(500),
    status      report_status NOT NULL DEFAULT 'OPEN',
    reviewed_by UUID          REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMPTZ,
    version     BIGINT        NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (reporter_id <> reported_id),
    CHECK (reason <> 'OTHER' OR note IS NOT NULL)
);
CREATE UNIQUE INDEX uq_user_reports_one_open ON user_reports(reporter_id, reported_id) WHERE status = 'OPEN';
CREATE INDEX idx_user_reports_status ON user_reports(status, created_at DESC);
CREATE INDEX idx_user_reports_reported ON user_reports(reported_id) WHERE status = 'OPEN';

ALTER TABLE user_follows ADD COLUMN muted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN banned_at TIMESTAMPTZ;
COMMIT;

-- Después: muted en false para todos → una fila: false | <total>
SELECT muted, count(*) FROM user_follows GROUP BY 1;

-- banned_at null para todos → is_banned | <total> | 0
SELECT is_banned, count(*), count(banned_at) FROM users GROUP BY 1;
```

### Rollback

```sql
BEGIN;
DROP TABLE user_reports;
DROP TYPE report_status;
DROP TYPE report_reason;
ALTER TABLE user_follows DROP COLUMN muted;
ALTER TABLE users DROP COLUMN banned_at;
COMMIT;
```

---

## Fase 6 — Avisos e invitaciones

Todo aditivo y sin `ALTER TYPE ... ADD VALUE` (los dos enums son nuevos), así que va en una sola transacción.
`email_notifications` arranca en `true` para todos, también los usuarios que ya existen (decidido el 24/11: son
avisos de su cuenta, no publicidad, y se pueden apagar).

El único índice parcial es el único de las invitaciones (lo usa el `ON CONFLICT`, con el `'INVITE'` escrito en la
query). Los demás son completos: las queries reciben el tipo como parámetro, y con un parámetro Postgres no siempre
puede usar un índice `WHERE type = 'INVITE'`.

```sql
-- Antes: no existen → NULL | NULL | NULL
SELECT to_regclass('public.notifications'), to_regtype('notification_type'), to_regtype('invite_phrase');

BEGIN;
CREATE TYPE notification_type AS ENUM ('NEW_FOLLOWER', 'NEW_FRIEND', 'INVITE');
CREATE TYPE invite_phrase AS ENUM ('CI_VIENI', 'ANDIAMO_INSIEME', 'MI_ACCOMPAGNI', 'TI_VA_DI_BALLARE');

CREATE TABLE notifications (
    id            UUID              PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_id  UUID              NOT NULL REFERENCES users(id)  ON DELETE CASCADE,
    actor_id      UUID              NOT NULL REFERENCES users(id)  ON DELETE CASCADE,
    type          notification_type NOT NULL,
    event_id      UUID              REFERENCES events(id) ON DELETE CASCADE,
    invite_phrase invite_phrase,
    read_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ       NOT NULL DEFAULT now(),
    CHECK (recipient_id <> actor_id),
    CHECK ((type = 'INVITE') = (event_id IS NOT NULL AND invite_phrase IS NOT NULL))
);
CREATE INDEX idx_notifications_recipient ON notifications(recipient_id, created_at DESC);
CREATE UNIQUE INDEX uq_notifications_invite ON notifications(actor_id, recipient_id, event_id) WHERE type = 'INVITE';
CREATE INDEX idx_notifications_actor ON notifications(actor_id, created_at);
CREATE INDEX idx_notifications_created ON notifications(created_at);

ALTER TABLE users ADD COLUMN email_notifications BOOLEAN NOT NULL DEFAULT TRUE;
COMMIT;

-- Después: la tabla vacía → 0
SELECT count(*) FROM notifications;

-- email_notifications en true para todos → una fila: true | <total>
SELECT email_notifications, count(*) FROM users GROUP BY 1;
```

### Rollback

```sql
BEGIN;
DROP TABLE notifications;
DROP TYPE invite_phrase;
DROP TYPE notification_type;
ALTER TABLE users DROP COLUMN email_notifications;
COMMIT;
```
