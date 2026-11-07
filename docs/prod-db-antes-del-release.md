# Base de datos de prod: qué falta antes del release del backend

Escrito el 06/11/2026. Rama `feature/organizer-requests`. Plan completo: `organizer-requests-plan.md`.

No hay Flyway ni Liquibase: todo se ejecuta **a mano**, en este orden, y **antes** de desplegar el backend
nuevo. Si el backend sale primero, falla: la entidad `OrganizerRequests` apunta a una tabla que no existe
y `OrganizersServiceImpl` la consulta al borrar o soltar un organizador.

Comparado con `main`, la rama solo añade a la base la tabla `organizer_requests` y sus tres tipos. Las
entidades que ya existían no cambian de columnas. A eso se suma D14.

**D15 (`claimed` = «llegó por una CLAIM», 06/11) no toca la base de prod:** no hay columna nueva, y en prod
todos los organizadores tienen `user = null` y `claimed = false`, que ya es correcto. La corrección de datos
de D15 es solo para develop (ver `organizer-requests-plan.md`, fase 3, approve).

| # | Qué | Cuándo | Estado |
|---|---|---|---|
| 0 | Backup (`pg_dump`) | Antes de cada paso | — |
| 1 | `organizer_type` sin `ASSOCIATION` (D14) | Se puede hacer ya | ☐ Pendiente |
| 2 | Tabla `organizer_requests` + sus tres tipos | Justo antes del deploy, después del 1 | ☐ Pendiente |
| 3 | Comprobaciones | Después de 1–2 | ☐ |

Después del deploy hay trabajo de datos, no de esquema: ver la última sección.

---

## 0. Backup

```bash
pg_dump -Fc -d <base_prod> -f ballastasera_prod_$(date +%Y%m%d_%H%M).dump
```

---

## 1. D14: `organizer_type` sin `ASSOCIATION`

Hecho en develop el 06/11. PostgreSQL no borra valores de un ENUM: hay que recrear el tipo. En prod
solo lo usa `organizers.type` (`organizer_requests` todavía no existe).

**Riesgo si se hace antes del deploy:** el frontend que está hoy en prod todavía ofrece «Associazione» en
los formularios del admin (crear organizador unclaimed y actualizar organizador). Si alguien lo eligiera,
PostgreSQL lo rechazaría y la API daría 500. No se guarda nada corrupto. Basta con no elegirlo hasta el deploy.

**Antes:**
```sql
SELECT count(*) FROM organizers WHERE type = 'ASSOCIATION';                     -- tiene que dar 0
SELECT table_name, column_name FROM information_schema.columns
WHERE udt_name = 'organizer_type';                                              -- solo organizers.type
```

**El cambio.** En DBeaver, ejecútalo como script (Alt+X). Si da cualquier error, `ROLLBACK;`:
```sql
BEGIN;
ALTER TYPE organizer_type RENAME TO organizer_type_old;
CREATE TYPE organizer_type AS ENUM ('PERSON', 'VENUE', 'CLUB', 'SCHOOL');
ALTER TABLE organizers ALTER COLUMN type DROP DEFAULT;      -- el default está ligado al tipo viejo
ALTER TABLE organizers ALTER COLUMN type TYPE organizer_type USING type::text::organizer_type;
ALTER TABLE organizers ALTER COLUMN type SET DEFAULT 'PERSON';
DROP TYPE organizer_type_old;
COMMIT;
```

**Después:**
```sql
SELECT enum_range(NULL::organizer_type);                                        -- {PERSON,VENUE,CLUB,SCHOOL}
SELECT typname FROM pg_type WHERE typname LIKE 'organizer_type%';               -- solo organizer_type
```
Luego, en DBeaver: Invalidate/Reconnect (si no, el editor sigue mostrando el valor viejo).

El paso 2 da por hecho este paso: la columna `organizer_requests.organizer_type` usa este tipo, así que
tiene que existir ya sin `ASSOCIATION`.

---

## 2. Tabla `organizer_requests` (fase 1 del plan)

Es la única fuente de este SQL: el plan (§3.2) remite aquí. No hay datos que migrar: en prod nadie ha
podido pedir ser organizador todavía. Los tipos van dentro de un `DO` que ignora `duplicate_object`, así
que repetir el script tras un fallo no se queja de los tipos.

**Antes:**
```sql
SELECT enum_range(NULL::organizer_type);              -- {PERSON,VENUE,CLUB,SCHOOL}: si no, falta el paso 1
SELECT to_regclass('public.organizer_requests');      -- NULL: la tabla todavía no existe
```

**El cambio.** En DBeaver, ejecútalo como script (Alt+X). Si da cualquier error, `ROLLBACK;`: el
`BEGIN`/`COMMIT` deja la base como estaba.
```sql
-- ============================================================================
--  Diventa organizzatore: tabla organizer_requests
--  Staging y prod. Sin datos que migrar. Ver docs/organizer-requests-plan.md §3.
-- ============================================================================
BEGIN;

DO $$ BEGIN
    CREATE TYPE organizer_request_type AS ENUM ('NEW', 'CLAIM');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE organizer_request_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

DO $$ BEGIN
    CREATE TYPE organizer_reject_reason AS ENUM ('INSTAGRAM_NOT_FOUND', 'INCOMPLETE_DATA',
                                                 'NOT_DANCE_ORGANIZER', 'DUPLICATE', 'OTHER');
EXCEPTION WHEN duplicate_object THEN NULL; END $$;

CREATE TABLE organizer_requests (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type               organizer_request_type   NOT NULL,
    status             organizer_request_status NOT NULL DEFAULT 'PENDING',
    organizer_id       UUID REFERENCES organizers(id) ON DELETE SET NULL,
    venue_address      VARCHAR(150),
    venue_city_id      BIGINT REFERENCES cities(id),
    venue_id           UUID REFERENCES venues(id) ON DELETE SET NULL,
    name               TEXT,
    organizer_type     organizer_type,
    description        TEXT,
    logo_url           TEXT,
    website            TEXT,
    phone              TEXT,
    contact_email      TEXT NOT NULL,
    instagram          TEXT NOT NULL,
    facebook           TEXT,
    message            TEXT,
    terms_accepted_at  TIMESTAMPTZ NOT NULL,
    rejection_reason   organizer_reject_reason,
    rejection_message  TEXT,
    reviewed_by        UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at        TIMESTAMPTZ,
    acknowledged_at    TIMESTAMPTZ,
    version            BIGINT NOT NULL DEFAULT 0,
    submitted_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_org_request_new   CHECK (type <> 'NEW'   OR (name IS NOT NULL AND organizer_type IS NOT NULL)),
    CONSTRAINT chk_org_request_claim CHECK (type <> 'CLAIM' OR organizer_id IS NOT NULL OR status <> 'PENDING'),
    CONSTRAINT chk_org_request_venue_proposal CHECK ((venue_address IS NULL) = (venue_city_id IS NULL))
);
CREATE UNIQUE INDEX uq_org_requests_one_pending ON organizer_requests(user_id) WHERE status = 'PENDING';
CREATE INDEX idx_org_requests_status    ON organizer_requests(status, submitted_at);
CREATE INDEX idx_org_requests_organizer ON organizer_requests(organizer_id);
CREATE INDEX idx_org_requests_user      ON organizer_requests(user_id, submitted_at DESC);

COMMIT;
```

**Después:** las comprobaciones de la sección 3.

**Antes de staging:** probar el script en una base de develop recreada desde cero
(`ballastasera_schema.sql` + este paso + el seed). Las comprobaciones de los CHECK están en §3.2b del plan.

---

## 3. Comprobaciones finales

```sql
SELECT enum_range(NULL::organizer_type);
SELECT to_regclass('public.organizer_requests');                                -- no debe ser NULL
SELECT indexname FROM pg_indexes WHERE tablename = 'organizer_requests';        -- 5 índices (pkey incluido)
SELECT conname FROM pg_constraint
WHERE conrelid = 'organizer_requests'::regclass AND contype = 'c';             -- los 3 chk_org_request_*
-- D15: en prod, antes del flujo, ningún organizador tiene dueño
SELECT count(*) FROM organizers WHERE user_id IS NOT NULL OR claimed;           -- 0
```

---

## Después del deploy (datos, no esquema)

Esto está en la fase 7 del plan; aquí solo como recordatorio:
1. Vaciar la caché `eventDetail` de Redis: las entradas antiguas no tienen `status`.
2. Enlazar cada organizador CLUB/VENUE/SCHOOL con su venue desde el admin (`updateVenueAsAdmin` con
   `organizerId`). Sin ese enlace, la CLAIM de esa página da `VENUE_REQUIRED` al aprobar. Para ver cuáles
   faltan:
   ```sql
   SELECT o.id, o.name, o.type FROM organizers o
   WHERE o.type IN ('VENUE', 'CLUB', 'SCHOOL')
     AND NOT EXISTS (SELECT 1 FROM venues v WHERE v.organizer_id = o.id)
   ORDER BY o.type, o.name;
   ```
3. Activar `organizerRequestPage` en `environment.ts` de prod cuando quieras abrir el flujo.
4. Prueba rápida: `GET /rest/organizer-requests/me` → 204, y la cola del admin vacía.
