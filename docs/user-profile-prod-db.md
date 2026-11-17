# Base de datos: perfil de usuario ampliado

Escrito el 16/11/2026. Rama `feature/user-profile`. Plan completo: `user-profile-plan.md`.
Esquema final de referencia (dev y prod): `ballastasera_schema.sql`.

No hay Flyway ni Liquibase: todo se ejecuta **a mano**. El mismo script vale para dev y para prod.

El cambio es **solo aditivo**: 4 tipos nuevos, 5 columnas nuevas en `users` (todas nullable menos
`whatsapp_visibility`, que tiene default) y una tabla nueva. No se toca ni se borra nada existente.

**Orden respecto al deploy:**
- El SQL se puede correr **antes** del deploy y sin riesgo: el backend que está hoy en prod ignora las
  columnas nuevas, y sus INSERT en `users` (login de un usuario nuevo) toman el default `'EVERYONE'`.
- El backend nuevo **no** puede salir antes del SQL: la entidad `Users` mapea columnas que no existirían,
  y falla cualquier query sobre `users`, **incluido el login**.

| # | Qué | Cuándo | Estado |
|---|---|---|---|
| 0 | Backup (`pg_dump`) | Antes del paso 2 | ☐ |
| 1 | Comprobaciones previas | Antes del paso 2 | ☐ |
| 2 | Script (tipos + columnas + tabla) | Antes del deploy del backend | ☑ Dev · ☐ Prod |
| 2b | Opcional: `show_profile_public` default `TRUE` | Cuando sea | ☑ Dev · ☐ Prod |
| 3 | Comprobaciones posteriores | Después del 2 | ☐ Dev · ☐ Prod |
| 4 | Rollback | Solo si hace falta | — |

---

## 0. Backup

```bash
pg_dump -Fc -d <base_prod> -f ballastasera_prod_$(date +%Y%m%d_%H%M).dump
```

---

## 1. Comprobaciones previas (solo lectura)

```sql
-- Los 4 tipos no existen todavía → 0 filas
SELECT typname FROM pg_type
WHERE typname IN ('gender', 'dance_role', 'dance_level', 'contact_visibility');

-- Las columnas nuevas no existen todavía → 0 filas
SELECT column_name FROM information_schema.columns
WHERE table_name = 'users'
  AND column_name IN ('whatsapp', 'whatsapp_visibility', 'birth_date', 'gender', 'dance_role');

-- La tabla no existe todavía → NULL
SELECT to_regclass('public.user_dance_styles');

-- Hay estilos de baile cargados (los usuarios eligen de acá) → > 0
SELECT count(*) FROM dance_styles;
```

Si alguna da distinto de lo esperado, parar y revisar antes de seguir.

---

## 2. Script

Todo dentro de una transacción: en PostgreSQL `CREATE TYPE`, `ALTER TABLE` y `CREATE TABLE` son
transaccionales, así que si algo falla no queda nada a medias.

`ADD COLUMN ... NOT NULL DEFAULT 'EVERYONE'` con un default constante no reescribe la tabla
(PostgreSQL ≥ 11): el lock sobre `users` dura muy poco.

```sql
BEGIN;

CREATE TYPE gender             AS ENUM ('MALE', 'FEMALE', 'OTHER', 'UNDISCLOSED');
CREATE TYPE dance_role         AS ENUM ('LEADER', 'FOLLOWER', 'BOTH');
CREATE TYPE dance_level        AS ENUM ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'TEACHER');
CREATE TYPE contact_visibility AS ENUM ('NOBODY', 'EVERYONE');   -- MUTUALS en la fase 2 (follows)

ALTER TABLE users
    ADD COLUMN whatsapp            TEXT,                -- E.164: +393331234567
    ADD COLUMN whatsapp_visibility contact_visibility NOT NULL DEFAULT 'EVERYONE',
    ADD COLUMN birth_date          DATE,
    ADD COLUMN gender              gender,
    ADD COLUMN dance_role          dance_role;

CREATE TABLE user_dance_styles (
    user_id        UUID   NOT NULL REFERENCES users(id)        ON DELETE CASCADE,
    dance_style_id BIGINT NOT NULL REFERENCES dance_styles(id) ON DELETE CASCADE,
    level          dance_level NOT NULL,
    PRIMARY KEY (user_id, dance_style_id)
);
CREATE INDEX idx_user_dance_styles_style ON user_dance_styles(dance_style_id);

COMMIT;
```

---

## 2b. Opcional: `show_profile_public` con default `TRUE`

No es de esta feature. El commit `5dff216` («Make user profiles public by default», 09/11) cambió
`schema.sql` y la entidad (`showProfilePublic = true`), pero no la base. En dev la columna seguía con
`DEFAULT false` (corregido el 16/11), y prod probablemente también.

No afecta a la app: la entidad siempre manda `true` al crear un usuario, así que el default de la base
nunca se usa. Es solo para que la base coincida con `ballastasera_schema.sql`.

```sql
-- Antes: ver el default actual
SELECT column_default FROM information_schema.columns
WHERE table_name = 'users' AND column_name = 'show_profile_public';

ALTER TABLE users ALTER COLUMN show_profile_public SET DEFAULT TRUE;
```

No cambia los usuarios que ya existen: solo el valor para los INSERT que no lo indiquen.

---

## 3. Comprobaciones posteriores

```sql
-- Las 5 columnas nuevas, con su tipo
SELECT column_name, udt_name, is_nullable, column_default
FROM information_schema.columns
WHERE table_name = 'users'
  AND column_name IN ('whatsapp', 'whatsapp_visibility', 'birth_date', 'gender', 'dance_role');

-- Todos los usuarios existentes quedaron con EVERYONE → una sola fila
SELECT whatsapp_visibility, count(*) FROM users GROUP BY 1;

-- La tabla nueva, vacía
SELECT count(*) FROM user_dance_styles;
```

En `psql` también sirve: `\d users` y `\d user_dance_styles`.

---

## 4. Rollback (solo si hace falta)

Solo con el backend **viejo** en ejecución: el nuevo mapea estas columnas y dejaría de funcionar.
Se pierden los datos de perfil que los usuarios hayan cargado.

```sql
BEGIN;

DROP TABLE user_dance_styles;

ALTER TABLE users
    DROP COLUMN whatsapp,
    DROP COLUMN whatsapp_visibility,
    DROP COLUMN birth_date,
    DROP COLUMN gender,
    DROP COLUMN dance_role;

DROP TYPE contact_visibility;
DROP TYPE dance_level;
DROP TYPE dance_role;
DROP TYPE gender;

COMMIT;
```