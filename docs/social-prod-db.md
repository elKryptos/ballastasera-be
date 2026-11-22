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
