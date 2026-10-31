# Diventa organizzatore — plan de implementación

Fecha: 18/10/2026 · Objetivo: implementarlo todo en la semana del 19 al 23 de octubre.
Actualizado 23/10/2026: el admin crea organizador y venue ya enlazados, así que en una CLAIM el
usuario ve su local con la página; un perfil nuevo de tipo local propone la dirección y el admin crea
el venue al aprobar (D11). Los organizadores CLUB/VENUE solo publican en sus venues (D12). Sin datos
que migrar en prod (sección 3.2).

Diseño de todas las pantallas: lienzo «Diventa organizzatore»
(https://claude.ai/artifact/BDanStXYuPb3wHDcDXosdB). Este archivo reemplaza la tabla «Qué falta»
del doc «Diventa organizzatore: qué hay y qué falta» del 06/10, que ya está desactualizada.

Convención de estado (la misma que `ROADMAP.md`):
- `[ ]` no empezado
- `[~]` en progreso (dejar nota de qué falta)
- `[x]` hecho y probado (tests + Postman)

## ▶ Dónde retomar (actualizado 31/10/2026)

Rama `feature/organizer-requests`. El push lo hace el usuario. En el remoto: todo hasta `b6b6e40`
(`release` + `unclaim` y su §11 incluidos); nada pendiente de push.

Suite: 166 tests en verde (todavía sin tests de `release`/`unclaim`).

| Fase | Estado |
|---|---|
| 0 — Arreglos | **Hecha y probada en Postman** (`POSTMAN_TESTING.md` §9) |
| 1 — Modelo | Hecho en código y en la base local. **Aplazado por decisión:** tipos + tabla en `ballastasera_schema.sql` y el script `docs/migrations/2026-10-organizer-requests.sql` (prod no tiene nada que migrar) |
| 2 — Endpoints del usuario | **Hecha y probada en Postman** (`POSTMAN_TESTING.md` §7), menos los logos, aplazados |
| 3 — Admin | **En curso.** Hecho y probado en Postman (§8): lista, detalle, emails después del commit, approve, reject, handlers 409. D13 (borrar): hecho y probado en Postman (§10). `release` + `unclaim`: hecho y probado en Postman (§11). **← SIGUIENTE: retirar los eventos futuros al borrar (B)** |
| 4 — Tests | 10 tests unitarios adelantados en la fase 0. Faltan los de `OrganizerRequestsServiceImpl` (create… approve, reject) y `findClaimable` |
| 5a/5b — Frontend | No empezado |
| 6/7 — Docs y despliegue | No empezado |

**Siguiente paso: retirar los eventos futuros al borrar** (B: D13, fase 3; el paso 1, series sin organizador,
ya está hecho y probado en la §12). Después, por orden: borrar `pending`/`verify` y `PATCH
/admin/organizers/{id}/claim` (D10) → ban en approve y `logoUrl` del dueño (revisión del 29/10) → D12.

**Lo hecho el 29/10 que no estaba en el plan:**
- `OTHER` sin mensaje se valida en `OrganizerRequestRejectDto` con un `@AssertTrue`
  (`isMessageProvidedForOther` → `400` con `errors.messageProvidedForOther`), no en el servicio.
- Handler 400 de `HttpMessageNotReadableException` (sin body, JSON roto, enum que no existe): antes
  daba 500 en todos los `@RequestBody`. Si se sabe el campo, va en `errors.<campo>`.
- Para recrear la base local desde cero: `ballastasera_schema.sql` + el bloque de la §3.2 + el seed
  (el schema todavía no tiene `organizer_requests`).
- En prod todos los organizadores los creó el admin. Ningún usuario ha creado uno: el frontend no lo
  permitía y el antiguo `POST /rest/organizers` no se usó. D13 no tiene casos antiguos.

**Lo hecho el 27/10 que no estaba en el plan:**
- `SecurityConfig`: `dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()`. Sin esto, el 403 de
  `hasRole("ADMIN")` se reenviaba a `/error` sin JWT y salía como 401.
- Emails después del commit hechos con eventos (paquete `events/`): `OrganizerClaimedEvent`,
  `OrganizerRequestReviewedEvent` (`approved()` / `rejected()`) y `OrganizerEmailListener`
  (`@TransactionalEventListener(AFTER_COMMIT)`). `claim()` también publica su evento y ahora es
  `@Transactional`: **no queda deuda en `ROADMAP.md`** por el email de `claim()`.
- `claim()` comprueba también `user != null`, además de `claimed`: un organizador creado por su dueño y
  verificado tiene `claimed = false` y antes se le podía cambiar el dueño.
- Los emails convierten con `HtmlUtils.htmlEscape` el nombre del organizador y el mensaje del admin.
- `approve` rechaza **todas** las CLAIM PENDING del mismo organizador, no solo una (probado con dos).

**Forma de trabajar en el backend:** Claude escribe archivo por archivo, el usuario aprueba cada diff en
IntelliJ (con el modo auto desactivado); después compilar, `mvnw test`, probar en Postman. Claude
actualiza `POSTMAN_TESTING.md` directamente. Commit con una sola línea en inglés, sin referencia a Claude.

**Decisiones abiertas:**
- Logos (D7, §5.3): `PATCH /rest/organizer-requests/{id}/logo`, `PATCH /rest/organizers/{id}/logo` y
  `ObjectStorageService.uploadOrganizerLogo/deleteOrganizerLogo` aplazados. Al recuperarlos: copiar el
  `logoUrl` de la REJECTED al reenviar (hay un `TODO 5.3` en `OrganizerRequestsServiceImpl.create`).
- D13 **confirmada el 27/10 y ampliada el 29/10** con `release` (dueño) + `unclaim` (admin): ver la tabla
  de decisiones. Revisada el 29/10: el creador solo borra, nunca suelta. `code` del 409, caché y email ya
  decididos (fase 3). Queda implementarla.
- D13 ampliada otra vez el 29/10: al borrar un organizador se cancelan sus eventos futuros y se desactivan
  sus series, `status` en `EventDetailDto`, y antes se arregla el 500 de las series sin organizador. Va
  después de `release`/`unclaim` (fase 3).
- Revisión del plan contra el código (29/10), todo decidido: `unclaim` de una página creada con NEW → 409
  `ORGANIZER_CREATED_BY_OWNER`; borrar la asignación manual `PATCH /admin/organizers/{id}/claim` junto con D10;
  `approve` → 403 si el usuario está baneado; el dueño ya no puede poner `logoUrl`; el filtro JWT que no mira
  el baneo va a `ROADMAP.md`. Ver los últimos puntos de la fase 3.

---

## 1. Qué estamos haciendo y por qué

### El flujo

Un usuario logueado (rol `USER`) puede pedir convertirse en organizador desde la tarjeta
«Organizza» del menú. Hay dos caminos:

- **CLAIM**: en el Passo 2 el usuario busca por nombre y elige un organizador de la lista de los que
  ya existen sin dueño. El admin los crea con `POST /rest/admin/organizers/unclaimed` y, si tienen
  local, crea también su venue enlazado (`POST /rest/admin/venues` con `organizerId`). Los datos de
  ese organizador y su local se precargan (solo lectura, D6) y el usuario añade la prueba de que es suyo.
- **NEW**: no está en la lista → crea un perfil de organizador nuevo (Chi sei → Il tuo profilo → Conferma).
  Si el tipo tiene local propio (`CLUB`, `VENUE`, `SCHOOL`), aparece un paso más, **«Il tuo locale»**:
  dirección y ciudad. El admin crea el venue al aprobar (D11).

La solicitud queda **PENDING**. Mientras tanto el usuario sigue siendo `USER` y usa la app con
normalidad. Un admin la revisa:

- **Approva**: el usuario pasa a `ORGANIZER` y queda dueño del organizador, que se crea en ese
  momento (NEW) o se le asigna con sus eventos y sus venues (CLAIM). En un NEW de tipo local, el admin
  crea el venue con la dirección propuesta y lo pasa al aprobar, así que queda conectado
  (`venues.organizer_id`) en ese mismo momento. Recibe un email y, al siguiente acceso, ve la
  pantalla de bienvenida.
- **Rifiuta**: el admin elige un motivo y, si quiere, escribe un mensaje. El usuario ve el motivo
  en el menú y puede «Modifica e reinvia», lo que crea una solicitud nueva prellenada.

```
USER ──Diventa organizzatore──► PENDING ──approve──► APPROVED  (rol ORGANIZER, organizador creado/asignado)
                                  │  ▲
                                  │  └──── reinvia (fila nueva) ────┐
                                  ├──reject──► REJECTED ────────────┘
                                  └──cancel──► CANCELLED
```

### Decisiones tomadas

| # | Decisión | Por qué |
|---|---|---|
| D1 | **Tabla nueva `organizer_requests`.** La solicitud es una entidad propia, separada de `organizers` | Permite guardar historial (cada intento es una fila), auditoría (quién revisó, cuándo y por qué) y solicitudes CLAIM sobre páginas que ya están verificadas, con varios usuarios pidiendo la misma página. `organizers` se queda solo con perfiles reales |
| D2 | **El organizador se crea al aprobar**, no al enviar la solicitud. La solicitud guarda los datos propuestos | No quedan organizadores basura (rechazados o cancelados) ocupando slugs. `isVerified` vuelve a significar solo la insignia pública |
| D3 | **Máximo 1 organizador por usuario**, con la constante `MAX_ORGANIZERS_PER_USER = 1`, comprobado al crear la solicitud y al aprobarla | Menos perfiles falsos y una gestión más simple: el frontend no necesita selector de organizador en «Pubblica una serata». Es una regla del servicio, no del esquema: se puede subir sin migrar |
| D4 | **Una sola solicitud PENDING por usuario**, garantizada por la base de datos con un índice único parcial | Evita duplicados por doble clic o por dos pestañas a la vez (condición de carrera) |
| D5 | **Reenviar = fila nueva**, prellenada desde la rechazada | No se pierde el historial de rechazos, y el admin ve «2º intento» |
| D6 | **CLAIM solo lleva datos de prueba** (Instagram, email, mensaje). El wizard muestra los datos del organizador elegido precargados y en solo lectura. Los cambios de perfil se hacen después de la aprobación con `PATCH /rest/organizers/{id}` | La página ya existe y es pública. La solicitud solo tiene que demostrar que es suya, y el admin revisa una sola cosa |
| D7 | **El logo se sube a la solicitud** (multipart, webp en S3), no como URL libre. Al aprobar, la URL pasa al organizador | Hoy `logoUrl` es un string que pone el cliente: inseguro. Se reutiliza la tubería de logos de venues |
| D8 | **Descripción: máximo 500 en la solicitud** (`OrganizerRequestCreateDto`, contador /500 en el wizard) y **2000 en create y update del organizador** (`OrganizerCreateDto`, `OrganizerUpdateDto`, contador /2000 en la edición del perfil). Actualizado 24/10/2026 | Create y update iguales: bajar update a 500 haría que el PATCH de una descripción larga que ya existe diera 400. Una solicitud aprobada (≤ 500) siempre cabe en el organizador |
| D9 | **El rol cambia sin repetir el login** | `JwtAuthenticationFilter` vuelve a leer el usuario de la base de datos en cada request. El frontend solo tiene que volver a pedir `/rest/auth/me`. El claim `role` del JWT queda viejo, pero no se usa para autorizar |
| D10 | **Los endpoints viejos se borran en la misma entrega** (`POST /rest/organizers`, `pending` y `verify`), y backend y frontend se despliegan juntos | En prod no hay ninguna solicitud ni ningún organizador con dueño (sección 3.2), así que no hay nada que mantener en paralelo |
| D11 | **Organizador y venue los enlaza siempre el admin.** `CLUB`, `VENUE` y `SCHOOL` necesitan al menos un venue para aprobarse (`OrganizerType.requiresVenue()`). **CLAIM**: el admin ya creó el organizador con su venue enlazado (`createVenueAsAdmin` + `organizerId`). El usuario lo ve en el wizard y no elige nada · **NEW**: la solicitud lleva dirección + ciudad del local. El admin crea el venue con el endpoint que ya existe y pasa su id en el approve. Los usuarios nunca eligen ni crean venues | El usuario ve su local desde la solicitud, sin búsquedas ni venues «libres» que se puedan pedir dos veces. El admin controla qué entra en el mapa y con quién está enlazado |
| D12 | **CLUB y VENUE solo publican en sus venues.** Al crear o editar un evento o una serie como organizador, `venueId` es obligatorio y tiene que estar en `findByOrganizerId(org.id)` (`OrganizerType.isVenueBound()`). `SCHOOL`, `PERSON` y `ASSOCIATION` siguen eligiendo cualquier venue (o ninguno), como hoy. `createAsAdmin` no cambia | Un club no puede publicar en la otra punta de la ciudad. Un DJ, una asociación o una escuela que hace un stage en otro local sí tienen que poder hacerlo |
| D13 | **✅ CONFIRMADA (27/10/2026), ampliada el 29/10. El dueño no puede borrar un organizador que creó el admin, pero sí puede soltarlo.** **Borrar:** `DELETE /rest/organizers/{id}` → 403 `AccessDeniedException` si el organizador no viene de una solicitud NEW APPROVED de ese usuario (`existsByOrganizerIdAndUserIdAndTypeAndStatus`), es decir, si llegó por una CLAIM o el admin se lo asignó con `PATCH /admin/organizers/{id}/claim`. Esos solo los borra el admin (`deleteAsAdmin`). Los de una NEW los sigue borrando su dueño. **Soltar:** se le quita el dueño sin borrar nada (`user = null`, `claimed = false`, `demoteToUser`). El organizador, su venue y sus eventos siguen ahí, y vuelve a salir en `claimable`. Dos endpoints con la misma lógica: `PATCH /rest/organizers/{id}/release` (dueño, solo los que **no** vienen de su NEW; los suyos los borra) y `PATCH /rest/admin/organizers/{id}/unclaim` (admin, cualquiera con dueño: para errores en un approve o si el usuario lo pide por email). **Frontend:** un solo botón en el perfil, «Elimina» si viene de su NEW y «Lascia la pagina» si no, deducido de `GET /rest/organizer-requests/me` (sin campo `deletable`). **Revisado el 29/10: el creador solo borra, nunca suelta.** Si pudiera soltar, su NEW APPROVED seguiría enlazada y, al volver a reclamarla, podría borrar lo que otro hizo después; además sus datos (`contactEmail`, `phone`) quedarían públicos en una página que ya no controla. Al borrar, el venue queda libre (`ON DELETE SET NULL`) y quien venga después lo enlaza con una NEW (`venueId` en el approve) | El organizador, su venue y sus eventos los puso el admin: un usuario no tiene que poder hacerlos desaparecer (si lo borra, el venue y los eventos quedan sin organizador por `ON DELETE SET NULL`). Pero tiene que poder dejarlo, y el admin tiene que poder corregir una CLAIM mal aprobada sin SQL a mano |

### Problemas en el código actual que se arreglan aquí

1. `createForUser` no pone `claimed=true`. Un organizador creado por un usuario queda `claimed=false`,
   y `PATCH /admin/organizers/{id}/claim` podría reasignarlo a otro usuario. En prod no hay ningún caso
   (todos los organizadores son unclaimed) y `createForUser` se borra en la fase 2, así que basta con no
   repetir el error en `approve` (fase 3).
2. `OrganizerAlreadyClaimedException` no tiene handler y devuelve **500** en vez de 409.
3. Un usuario puede crear N organizadores pendientes (`POST /rest/organizers` no tiene ningún control).
4. `UsersServiceImpl.demoteToUser` y `becomeOrganizer` **devuelven `null`**: no están implementados.
   Si un organizador borra su único perfil, se queda como ORGANIZER sin nada que gestionar.
5. `endpoints.ts` del frontend define `claimOrganizer` como `/rest/organizers/{id}/claim`, que no
   existe (el real es `PATCH /rest/admin/organizers/{id}/claim`).
6. `OrganizerCreateDto`/`OrganizerUpdateDto`: `contactEmail` sin `@Email`, `instagram` sin normalizar
   (`InstagramUtils` ya existe) y límites de descripción distintos (500 / 2000).
7. Cualquier `GET /rest/organizers/<palabra>` nuevo cae en el `permitAll` de `{slug}`, y un slug
   llamado `me` o `claimable` quedaría tapado por la ruta literal.
8. No hay Flyway ni Liquibase: cada cambio de esquema va en `ballastasera_schema.sql` **y** en un script
   manual para staging y prod.
9. Con la tabla nueva, `deleteAsAdmin` de un organizador sin dueño con una CLAIM PENDING encima falla:
   `ON DELETE SET NULL` rompe `chk_org_request_claim` y llega un 500. Se arregla en la fase 3 cerrando
   antes esas solicitudes.

---

## 2. Plan por días

| Día | Fases | Resultado al final del día |
|---|---|---|
| Lun 19 | 0 + 1 | Arreglos hechos, tabla y entidad creadas, script de migración probado en local |
| Mar 20 | 2 | Endpoints del usuario funcionando en Postman |
| Mié 21 | 3 + 4 | Endpoints del admin y suite de tests en verde |
| Jue 22 | 5a | Frontend: modelos, servicio, wizard de 3 pasos, «Richiesta inviata» |
| Vie 23 | 5b + 6 + despliegue | Frontend: menú por estado, admin, bienvenida. Docs. Staging: script SQL y despliegue |
| Sáb/Dom | Margen | Pruebas end-to-end en staging y paso a prod |

Las fases 2 y 5a pueden ir en paralelo si los DTOs de la sección 4 se dan por cerrados.

---

## 3. Modelo de datos

Resumen de todo lo SQL de esta entrega (no hay más cambios de esquema: D12 y el `organizerId` de
`VenueUpdateDto` son solo código):

| Archivo | Qué | Sección |
|---|---|---|
| `docs/ballastasera_schema.sql` | 3 tipos nuevos + tabla `organizer_requests` + 4 índices | 3.1 |
| `docs/migrations/2026-10-organizer-requests.sql` | Lo mismo, para develop, staging y prod (idempotente con los tipos) | 3.2 |
| — (se pega en psql/IntelliJ, no se guarda) | Comprobación de las restricciones, sin dejar datos | 3.2b |
| — (solo local/staging) | Marcha atrás | 3.2c |
| `src/main/resources/db/seed_test_data.sql` | Datos de prueba del flujo | 3.2d |

### 3.1 SQL en `docs/ballastasera_schema.sql`

**a) Tipos:** en el bloque «Tipi enumerati», debajo de `venue_type`:

```sql
CREATE TYPE organizer_request_type   AS ENUM ('NEW', 'CLAIM');
CREATE TYPE organizer_request_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED');
CREATE TYPE organizer_reject_reason  AS ENUM ('INSTAGRAM_NOT_FOUND', 'INCOMPLETE_DATA',
                                              'NOT_DANCE_ORGANIZER', 'DUPLICATE', 'OTHER');
```

**b) Tabla:** **después de `venues`** (no de `organizers`). Tiene FK a `venues` y a `cities`, y en el
schema `venues` se crea después de `organizers`:

```sql
-- ============================================================================
--  ORGANIZER_REQUESTS — solicitudes de un usuario para ser organizador
--  NEW: crea un perfil nuevo al aprobarse · CLAIM: pide una página ya existente
--  Cada intento es una fila (reenviar tras un rechazo = fila nueva).
-- ============================================================================
CREATE TABLE organizer_requests (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type               organizer_request_type   NOT NULL,
    status             organizer_request_status NOT NULL DEFAULT 'PENDING',
    organizer_id       UUID REFERENCES organizers(id) ON DELETE SET NULL, -- CLAIM: la página pedida · NEW: la creada al aprobar
    -- local (D11), solo NEW de tipo local: dirección propuesta por el usuario + el venue que el admin crea al aprobar
    venue_address      VARCHAR(150),
    venue_city_id      BIGINT REFERENCES cities(id),
    venue_id           UUID REFERENCES venues(id) ON DELETE SET NULL,
    -- datos propuestos (NEW: perfil completo · CLAIM: solo prueba de identidad)
    name               TEXT,
    organizer_type     organizer_type,
    description        TEXT,
    logo_url           TEXT,
    website            TEXT,
    phone              TEXT,
    contact_email      TEXT NOT NULL,
    instagram          TEXT NOT NULL,
    facebook           TEXT,
    message            TEXT,                          -- nota libre del usuario para el admin
    terms_accepted_at  TIMESTAMPTZ NOT NULL,          -- «Pubblicherò solo serate reali…»
    -- revisión
    rejection_reason   organizer_reject_reason,
    rejection_message  TEXT,
    reviewed_by        UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at        TIMESTAMPTZ,
    acknowledged_at    TIMESTAMPTZ,                   -- el usuario ya vio la bienvenida / el rechazo
    version            BIGINT NOT NULL DEFAULT 0,     -- optimistic locking (dos admins a la vez)
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
```

`chk_org_request_claim` permite `organizer_id NULL` en una CLAIM ya cerrada: si el organizador se borra
después (`ON DELETE SET NULL`), la fila histórica no rompe el CHECK. Una CLAIM **PENDING** sí lo
rompería, por eso `deleteAsAdmin` cierra antes las solicitudes pendientes (fase 3).

`venue_id` solo lo rellena el approve (es historial: «qué venue se creó para esta solicitud»). Si
luego se borra el venue, pasa a NULL sin romper nada. `chk_org_request_venue_proposal` obliga a que
la dirección propuesta lleve también la ciudad.

No hay CHECK de «PERSON no lleva dirección»: la regla vive en el servicio, junto con la de «CLUB sin
dirección», que depende del tipo.

### 3.2 Script para develop, staging y prod: `docs/migrations/2026-10-organizer-requests.sql`

**Estado de prod:** todos los organizadores los creó el admin como unclaimed y nadie ha podido pedir
ser organizador todavía (el flujo no existe). **No hay datos que migrar.** El script solo crea los
tipos, la tabla y los índices, que siguen haciendo falta porque no hay Flyway.

Los tipos van dentro de un `DO` que ignora `duplicate_object`. Así el mismo script sirve también en
develop, donde ya creaste los tipos: allí solo crea la tabla y los índices. Antes, comprobar que los
valores de los tipos de develop coinciden:
`SELECT typname, enum_range(NULL::organizer_request_status) FROM pg_type WHERE typname = 'organizer_request_status';`
(y lo mismo con los otros dos). Si alguno es distinto: `DROP TYPE <nombre>;` y volver a ejecutar.

Se ejecuta **una vez** en cada base, antes de desplegar el backend nuevo:

```sql
-- ============================================================================
--  2026-10 · Diventa organizzatore: tabla organizer_requests
--  Develop, staging y prod. Sin datos que migrar. Ver docs/organizer-requests-plan.md §3.
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

Si el script falla a mitad, el `BEGIN`/`COMMIT` deja la base como estaba. En staging, si su base no
coincide con prod, lo más simple es recrearla (`down -v` + `up`) con el `ballastasera_schema.sql` actualizado.

### 3.2b Comprobar las restricciones (develop o local)

Se pega entero en psql o en la consola de IntelliJ. Cada prueba imprime `OK …` como NOTICE. Si una
restricción no funciona, el bloque para con `FALLO: …`. El `ROLLBACK` final borra todo lo que insertó.
Necesita al menos un usuario sin solicitud PENDING.

```sql
BEGIN;

DO $$
DECLARE
    u   UUID := (SELECT id FROM users usr
                 WHERE NOT EXISTS (SELECT 1 FROM organizer_requests r
                                   WHERE r.user_id = usr.id AND r.status = 'PENDING')
                 LIMIT 1);
    org UUID;
BEGIN
    IF u IS NULL THEN RAISE EXCEPTION 'No hay ningún usuario sin solicitud PENDING'; END IF;

    -- 1. NEW sin name / organizer_type
    BEGIN
        INSERT INTO organizer_requests (user_id, type, contact_email, instagram, terms_accepted_at)
        VALUES (u, 'NEW', 'test@test.it', 'test', now());
        RAISE EXCEPTION 'FALLO: NEW sin name ha entrado';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK chk_org_request_new';
    END;

    -- 2. CLAIM PENDING sin organizer_id
    BEGIN
        INSERT INTO organizer_requests (user_id, type, contact_email, instagram, terms_accepted_at)
        VALUES (u, 'CLAIM', 'test@test.it', 'test', now());
        RAISE EXCEPTION 'FALLO: CLAIM PENDING sin organizer_id ha entrado';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK chk_org_request_claim';
    END;

    -- 3. Dirección sin ciudad
    BEGIN
        INSERT INTO organizer_requests (user_id, type, name, organizer_type, venue_address,
                                        contact_email, instagram, terms_accepted_at)
        VALUES (u, 'NEW', 'Test Club', 'CLUB', 'Via Test 1', 'test@test.it', 'test', now());
        RAISE EXCEPTION 'FALLO: dirección sin ciudad ha entrado';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK chk_org_request_venue_proposal';
    END;

    -- 4. Dos PENDING del mismo usuario
    BEGIN
        INSERT INTO organizer_requests (user_id, type, name, organizer_type, contact_email, instagram, terms_accepted_at)
        VALUES (u, 'NEW', 'Test 1', 'PERSON', 'test@test.it', 'test', now());
        INSERT INTO organizer_requests (user_id, type, name, organizer_type, contact_email, instagram, terms_accepted_at)
        VALUES (u, 'NEW', 'Test 2', 'PERSON', 'test@test.it', 'test', now());
        RAISE EXCEPTION 'FALLO: dos PENDING del mismo usuario han entrado';
    EXCEPTION WHEN unique_violation THEN RAISE NOTICE 'OK uq_org_requests_one_pending';
    END;

    -- 5. Borrar un organizador con una CLAIM PENDING → tiene que fallar (por eso deleteAsAdmin cancela antes)
    BEGIN
        INSERT INTO organizers (name, slug, type, is_verified) VALUES ('Test Org', 'test-org-chk-5', 'SCHOOL', true)
        RETURNING id INTO org;
        INSERT INTO organizer_requests (user_id, type, organizer_id, contact_email, instagram, terms_accepted_at)
        VALUES (u, 'CLAIM', org, 'test@test.it', 'test', now());
        DELETE FROM organizers WHERE id = org;
        RAISE EXCEPTION 'FALLO: se ha borrado un organizador con CLAIM PENDING';
    EXCEPTION WHEN check_violation THEN RAISE NOTICE 'OK borrar con CLAIM PENDING falla';
    END;

    -- 6. Borrar un organizador con una CLAIM ya cerrada → funciona y deja organizer_id en NULL
    INSERT INTO organizers (name, slug, type, is_verified) VALUES ('Test Org', 'test-org-chk-6', 'SCHOOL', true)
    RETURNING id INTO org;
    INSERT INTO organizer_requests (user_id, type, status, organizer_id, contact_email, instagram, terms_accepted_at)
    VALUES (u, 'CLAIM', 'REJECTED', org, 'test@test.it', 'test', now());
    DELETE FROM organizers WHERE id = org;
    IF EXISTS (SELECT 1 FROM organizer_requests WHERE user_id = u AND type = 'CLAIM'
               AND status = 'REJECTED' AND organizer_id IS NULL) THEN
        RAISE NOTICE 'OK borrar con CLAIM cerrada → organizer_id NULL';
    ELSE
        RAISE EXCEPTION 'FALLO: la CLAIM cerrada no quedó con organizer_id NULL';
    END IF;
END $$;

ROLLBACK;
```

### 3.2c Marcha atrás (solo local o staging)

```sql
BEGIN;
DROP TABLE IF EXISTS organizer_requests;
DROP TYPE IF EXISTS organizer_reject_reason;
DROP TYPE IF EXISTS organizer_request_status;
DROP TYPE IF EXISTS organizer_request_type;
COMMIT;
```

### 3.2d Datos de prueba: añadir al final de `seed_test_data.sql`

Mismo estilo que el resto del seed: IDs fijos para pegarlos en Postman. Usa los usuarios que ya
existen (Ana, Marco, Lucia) y añade a Dario.

| Solicitud | Usuario | Caso |
|---|---|---|
| `ab000000-…-01` | Ana | CLAIM PENDING de «Salsa Loca Milano» (SCHOOL sin dueño, con su venue enlazado) |
| `ab000000-…-02` | Marco | CLAIM PENDING de la misma página: la competidora que se auto-rechaza al aprobar la de Ana |
| `ab000000-…-03` | Lucia | NEW PERSON REJECTED (Instagram non trovato) |
| `ab000000-…-04` | Lucia | NEW PERSON PENDING: el reenvío, «2º intento» |
| `ab000000-…-05` | Dario | NEW CLUB PENDING con dirección: el admin crea el venue y aprueba con su id |

```sql
-- ----------------------------------------------------------------------------
-- Diventa organizzatore (docs/organizer-requests-plan.md §3.2d)
--   88888888 = pagina senza dueño (SCHOOL) creada por el admin
--   99999999 = su venue, ya enlazado (D11)
--   ab000000-…-0X = solicitudes, ver la tabla del plan
-- ----------------------------------------------------------------------------
INSERT INTO users (id, google_id, email, display_name, avatar_url, instagram, show_profile_public, role)
VALUES ('77777777-7777-7777-7777-777777777777', 'seed-google-dario',
        'dario.seed@ballastasera.test', 'Dario', NULL, 'tropicalclub.mi', false, 'USER');

INSERT INTO organizers (id, user_id, name, slug, type, description, contact_email, instagram, is_verified, claimed)
VALUES ('88888888-8888-8888-8888-888888888888', NULL,
        'Salsa Loca Milano', 'salsa-loca-milano', 'SCHOOL',
        'Scuola di salsa e bachata in zona Isola. Corsi tutte le sere, social il venerdì.',
        'info@salsaloca.it', 'salsaloca.milano', true, false);

INSERT INTO venues (id, name, type, organizer_id, city_id, address, latitude, longitude, description, created_by)
VALUES ('99999999-9999-9999-9999-999999999999', 'Salsa Loca · Sala Isola', 'SCHOOL',
        '88888888-8888-8888-8888-888888888888',
        (SELECT id FROM cities WHERE slug = 'milano'),
        'Via Borsieri 12', 45.4889, 9.1890,
        'La sala della scuola Salsa Loca.', NULL);

INSERT INTO organizer_requests (id, user_id, type, status, organizer_id, contact_email, instagram,
                                message, terms_accepted_at, submitted_at)
VALUES
    ('ab000000-0000-0000-0000-000000000001', '44444444-4444-4444-4444-444444444444',
     'CLAIM', 'PENDING', '88888888-8888-8888-8888-888888888888',
     'ana.seed@ballastasera.test', 'salsaloca.milano',
     'Sono la titolare, mi trovate in sala il martedì.', now() - INTERVAL '1 day', now() - INTERVAL '1 day'),
    ('ab000000-0000-0000-0000-000000000002', '55555555-5555-5555-5555-555555555555',
     'CLAIM', 'PENDING', '88888888-8888-8888-8888-888888888888',
     'marco2.seed@ballastasera.test', 'marco_salsero',
     NULL, now() - INTERVAL '3 hours', now() - INTERVAL '3 hours');

INSERT INTO organizer_requests (id, user_id, type, status, name, organizer_type, description,
                                contact_email, instagram, terms_accepted_at, submitted_at,
                                rejection_reason, rejection_message, reviewed_at)
VALUES
    ('ab000000-0000-0000-0000-000000000003', '66666666-6666-6666-6666-666666666666',
     'NEW', 'REJECTED', 'Lucia Bachata', 'PERSON', 'Insegnante di bachata sensual.',
     'lucia.seed@ballastasera.test', 'lucia.bachata.mi', now() - INTERVAL '4 days', now() - INTERVAL '4 days',
     'INSTAGRAM_NOT_FOUND', 'Non troviamo @lucia.bachata.mi: controlla il nome.', now() - INTERVAL '2 days'),
    ('ab000000-0000-0000-0000-000000000004', '66666666-6666-6666-6666-666666666666',
     'NEW', 'PENDING', 'Lucia Bachata', 'PERSON', 'Insegnante di bachata sensual.',
     'lucia.seed@ballastasera.test', 'luciabachata', now() - INTERVAL '1 hour', now() - INTERVAL '1 hour',
     NULL, NULL, NULL);

INSERT INTO organizer_requests (id, user_id, type, status, name, organizer_type, description,
                                venue_address, venue_city_id,
                                contact_email, instagram, terms_accepted_at, submitted_at)
VALUES ('ab000000-0000-0000-0000-000000000005', '77777777-7777-7777-7777-777777777777',
        'NEW', 'PENDING', 'Tropical Club Navigli', 'CLUB',
        'Serate latine il giovedì e il sabato, due sale: salsa e bachata.',
        'Via Gola 12', (SELECT id FROM cities WHERE slug = 'milano'),
        'info@tropicalclub.it', 'tropicalclub.mi', now() - INTERVAL '30 minutes', now() - INTERVAL '30 minutes');
```

Con este seed, la comprobación 3.2b sigue funcionando: busca un usuario sin PENDING (el organizador
`22222222` del seed original).

### 3.3 Enums Java (`enums/`)

```java
public enum OrganizerRequestType { NEW, CLAIM }

public enum OrganizerRequestStatus { PENDING, APPROVED, REJECTED, CANCELLED }

public enum OrganizerRejectReason {
    INSTAGRAM_NOT_FOUND,   // «Instagram non trovato»
    INCOMPLETE_DATA,       // «Dati incompleti»
    NOT_DANCE_ORGANIZER,   // «Non organizza serate di ballo»
    DUPLICATE,             // «Doppione» (también el rechazo automático de CLAIMs perdedoras)
    OTHER                  // requiere rejectionMessage
}
```

En el `OrganizerType` que ya existe, dos métodos para D11 y D12. Así la regla está en un solo sitio y
no hace falta una clase nueva:

```java
public enum OrganizerType {
    PERSON, VENUE, CLUB, SCHOOL, ASSOCIATION;

    /** D11: necesita al menos un venue para aprobarse. */
    public boolean requiresVenue() {
        return this == VENUE || this == CLUB || this == SCHOOL;
    }

    /** D12: solo puede publicar eventos en sus propios venues. */
    public boolean isVenueBound() {
        return this == VENUE || this == CLUB;
    }
}
```

### 3.4 Entidad `models/entities/OrganizerRequests.java`

Mismo estilo que `Organizers`: Lombok `@Getter @Setter`, `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` para los enums.

```java
@Entity
@Table(name = "organizer_requests")
@Getter
@Setter
public class OrganizerRequests {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "type", nullable = false, columnDefinition = "organizer_request_type")
    private OrganizerRequestType type;

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "organizer_request_status")
    private OrganizerRequestStatus status = OrganizerRequestStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id")
    private Organizers organizer;

    @Column(name = "venue_address") private String venueAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_city_id")
    private Cities venueCity;

    /** El venue que el admin crea y conecta al aprobar un NEW de tipo local. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id")
    private Venues venue;

    @Column(name = "name") private String name;

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "organizer_type", columnDefinition = "organizer_type")
    private OrganizerType organizerType;

    @Column(name = "description") private String description;
    @Column(name = "logo_url") private String logoUrl;
    @Column(name = "website") private String website;
    @Column(name = "phone") private String phone;
    @Column(name = "contact_email", nullable = false) private String contactEmail;
    @Column(name = "instagram", nullable = false) private String instagram;
    @Column(name = "facebook") private String facebook;
    @Column(name = "message") private String message;
    @Column(name = "terms_accepted_at", nullable = false) private OffsetDateTime termsAcceptedAt;

    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "rejection_reason", columnDefinition = "organizer_reject_reason")
    private OrganizerRejectReason rejectionReason;

    @Column(name = "rejection_message") private String rejectionMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private Users reviewedBy;

    @Column(name = "reviewed_at") private OffsetDateTime reviewedAt;
    @Column(name = "acknowledged_at") private OffsetDateTime acknowledgedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private OffsetDateTime submittedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
```

En `Users` añadir la relación inversa sin cascade (el borrado lo hace la base de datos con `ON DELETE CASCADE`):

```java
@OneToMany(mappedBy = "user")
private List<OrganizerRequests> organizerRequests = new ArrayList<>();
```

---

## 4. Contrato de la API: DTOs

### 4.1 `OrganizerRequestCreateDto` (body de `POST /rest/organizer-requests`)

```java
@Getter
@Setter
@ValidOrganizerRequest   // validación de clase: NEW exige name+organizerType, CLAIM exige targetOrganizerId
public class OrganizerRequestCreateDto {

    @NotNull
    private OrganizerRequestType type;

    /** Solo CLAIM: id de la página (verificada y sin reclamar) que el usuario pide. */
    private UUID targetOrganizerId;

    /** Solo NEW (obligatorio). */
    @Size(max = 120)
    @Pattern(regexp = "(?s).*\\S.*", message = "name must not be blank")
    private String name;

    /** Solo NEW (obligatorio). Passo 1: Insegnante/DJ/promoter=PERSON, Scuola=SCHOOL,
     *  Discoteca/club=CLUB, Locale/bar=VENUE, Associazione=ASSOCIATION. */
    private OrganizerType organizerType;

    @Size(max = 500)
    private String description;

    /** Obligatorio en los dos tipos: lo usamos para verificar que el perfil es suyo. Con o sin "@". */
    @NotBlank
    @Pattern(regexp = "^@?[A-Za-z0-9._]{1,30}$", message = "invalid Instagram handle")
    private String instagram;

    @NotBlank
    @Email
    @Size(max = 100)
    private String contactEmail;

    @Size(max = 20)
    private String phone;

    @URL
    @Size(max = 100)
    private String website;

    @URL
    @Size(max = 100)
    private String facebook;

    /** Nota libre para el admin (sobre todo en CLAIM: «soy la dueña, escribidme a…»). */
    @Size(max = 500)
    private String message;

    /** Solo NEW de tipo local, «Il tuo locale» (D11): dirección para que el admin cree el venue. Las dos juntas o ninguna. */
    @Size(max = 150)
    private String venueAddress;
    private Long venueCityId;

    /** Checkbox del Passo 3. */
    @AssertTrue(message = "terms must be accepted")
    private boolean termsAccepted;
}
```

`@ValidOrganizerRequest` sigue el patrón de `validations/ValidEventTiming` + `EventTimingValidator`.
Error 400 con `errors.name`, `errors.organizerType` o `errors.targetOrganizerId` según el caso. En CLAIM,
`name`, `organizerType`, `description`, `website` y `facebook` se ignoran (no se guardan).

El validador también comprueba el local: `venueAddress` y `venueCityId` van los dos o ninguno
(`errors.venueAddress`); en CLAIM se ignoran. En NEW, `organizerType.requiresVenue()` exige la
dirección, y un tipo que no la exige (PERSON, ASSOCIATION) no puede traerla (`errors.venueAddress`).

No hay `logoUrl`: el logo se sube aparte (sección 5.3).

### 4.2 `OrganizerRequestDto` (respuesta para el usuario)

```java
@Getter
@Setter
public class OrganizerRequestDto {
    private UUID id;
    private OrganizerRequestType type;
    private OrganizerRequestStatus status;

    /** NEW: el nombre propuesto · CLAIM: el nombre de la página pedida. Para «Salsa Loca Milano · inviata oggi». */
    private String organizerName;
    private OrganizerType organizerType;
    /** CLAIM: la página pedida · NEW aprobada: el organizador creado. null en NEW sin aprobar. */
    private UUID organizerId;
    /** Para enlazar al perfil público una vez aprobada. */
    private String organizerSlug;

    private String description;
    private String logoUrl;
    private String instagram;
    private String contactEmail;
    private String phone;
    private String website;
    private String facebook;
    private String message;

    /** Solo NEW de tipo local: la dirección propuesta… */
    private String venueAddress;
    private String venueCityName;
    /** …y el venue creado al aprobar (null mientras está PENDING). */
    private UUID venueId;
    private String venueName;

    private OrganizerRejectReason rejectionReason;
    private String rejectionMessage;

    private OffsetDateTime submittedAt;
    private OffsetDateTime reviewedAt;
    private OffsetDateTime acknowledgedAt;
}
```

### 4.3 `OrganizerRequestAdminDto` (respuesta para el admin)

Todo lo de `OrganizerRequestDto` más quién la pidió, quién la revisó y el contexto. Se puede
implementar con herencia (`extends OrganizerRequestDto`), siempre que MapStruct lo resuelva bien;
si no, como clase completa.

```java
@Getter
@Setter
public class OrganizerRequestAdminDto extends OrganizerRequestDto {
    /** «Richiesta da Kryptos». */
    private RequesterDto requester;
    /** Solo CLAIM: la página pedida, para que el admin la compare. */
    private OrganizerSummaryDto targetOrganizer;
    /** Solo CLAIM: los venues que el organizador ya tiene enlazados, para que el admin los vea. */
    private List<VenuesSummaryDto> targetOrganizerVenues;
    private String reviewedByName;
    /** Rechazos anteriores de este usuario: «2º intento». */
    private long previousRejections;
}

@Getter
@Setter
public class RequesterDto {
    private UUID userId;
    private String displayName;
    private String email;
    private String avatarUrl;
    private String instagram;      // el Instagram de su perfil social, si lo tiene
    private OffsetDateTime memberSince;
}
```

### 4.4 `OrganizerRequestRejectDto` (body de `PATCH /rest/admin/organizer-requests/{id}/reject`)

```java
@Getter
@Setter
public class OrganizerRequestRejectDto {
    @NotNull
    private OrganizerRejectReason reason;

    /** «Messaggio per l'utente · facoltativo». Obligatorio si reason = OTHER (lo valida el servicio). */
    @Size(max = 500)
    private String message;
}
```

### 4.5 Búsquedas del wizard

**Organizadores que se pueden reclamar** (Passo 2): se reutiliza `OrganizerSummaryDto` tal cual
(`id`, `name`, `slug`, `logoUrl`, `instagram`, `type`, `verified`). Para precargar el resto del perfil
(descripción, web…) el frontend llama a `GET /rest/organizers/{slug}`, y para mostrar su local a
`GET /rest/organizers/{id}/venues`. Los dos ya existen y son públicos. No hace falta ningún DTO nuevo.

### 4.5b `OrganizerRequestApproveDto` (body opcional de `PATCH /rest/admin/organizer-requests/{id}/approve`)

```java
@Getter
@Setter
public class OrganizerRequestApproveDto {
    /** NEW de tipo local: el venue que el admin acaba de crear con la dirección propuesta. Opcional. */
    private UUID venueId;
}
```

### 4.6 Errores 409: cuerpo con `code`

`ErrorDetails` ya tiene un constructor con mapa de `errors`. Para los conflictos se usa
`errors = {"code": "<CÓDIGO>"}` y el frontend traduce el código:

| `code` | Cuándo |
|---|---|
| `PENDING_REQUEST_EXISTS` | Ya tiene una solicitud PENDING (comprobación del servicio o violación de `uq_org_requests_one_pending`) |
| `ORGANIZER_LIMIT_REACHED` | Ya tiene `MAX_ORGANIZERS_PER_USER` organizadores |
| `ORGANIZER_ALREADY_CLAIMED` | La página de la CLAIM ya tiene dueño |
| `VENUE_ALREADY_LINKED` | Al aprobar: el venue que pasa el admin ya está conectado a otro organizador |
| `VENUE_REQUIRED` | Al aprobar: el tipo exige venue (D11), el organizador no tiene ninguno y el body del approve no trae `venueId` |
| `REQUEST_NOT_PENDING` | Se intenta aprobar, rechazar, cancelar o subir logo a una solicitud que ya no está PENDING |
| `REQUEST_STILL_PENDING` | `acknowledge` sobre una solicitud que sigue PENDING: todavía no hay resultado que ver (añadido 26/10/2026) |
| `CONCURRENT_UPDATE` | Optimistic lock: otro admin la revisó a la vez |
| `ORGANIZER_NOT_CLAIMED` | `PATCH /admin/organizers/{id}/unclaim` sobre un organizador sin dueño (D13, añadido 29/10/2026) |
| `ORGANIZER_CREATED_BY_OWNER` | `PATCH /admin/organizers/{id}/unclaim` sobre un organizador que su dueño creó con una NEW: se borra, no se suelta (D13, añadido 29/10/2026) |

Excepción nueva: `exceptions/OrganizerRequestConflictException(String code, String message)` con su
`@ExceptionHandler` en `BackendErrorResponse` → 409. Los códigos son constantes de la propia excepción
(`PENDING_REQUEST_EXISTS`, `ORGANIZER_LIMIT_REACHED`, `REQUEST_NOT_PENDING`, `REQUEST_STILL_PENDING`);
los de la fase 3 (`VENUE_*`, `CONCURRENT_UPDATE`) se añaden ahí. `ORGANIZER_ALREADY_CLAIMED` tiene su
propia excepción desde la fase 0. También hay que mapear
`ObjectOptimisticLockingFailureException` → 409 `CONCURRENT_UPDATE` y, en el servicio, capturar
`DataIntegrityViolationException` al guardar → `PENDING_REQUEST_EXISTS`.

### 4.7 Tipos TypeScript (`ballastasera-fe/src/app/core/models/organizer-request.model.ts`)

```ts
import { OrganizerSummaryDto, OrganizerType } from './organizer.model';
import { VenuesSummaryDto } from './venue.model'; // ajustar al nombre real del modelo de venues

export type OrganizerRequestType = 'NEW' | 'CLAIM';
export type OrganizerRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
export type OrganizerRejectReason =
  | 'INSTAGRAM_NOT_FOUND' | 'INCOMPLETE_DATA' | 'NOT_DANCE_ORGANIZER' | 'DUPLICATE' | 'OTHER';

export interface OrganizerRequestCreateDto {
  type: OrganizerRequestType;
  targetOrganizerId?: string;
  name?: string;
  organizerType?: OrganizerType;
  description?: string;
  instagram: string;
  contactEmail: string;
  phone?: string;
  website?: string;
  facebook?: string;
  message?: string;
  venueAddress?: string;
  venueCityId?: number;
  termsAccepted: boolean;
}

export interface OrganizerRequestDto {
  id: string;
  type: OrganizerRequestType;
  status: OrganizerRequestStatus;
  organizerName: string;
  organizerType: OrganizerType | null;
  organizerId: string | null;
  organizerSlug: string | null;
  description: string | null;
  logoUrl: string | null;
  instagram: string;
  contactEmail: string;
  phone: string | null;
  website: string | null;
  facebook: string | null;
  message: string | null;
  venueAddress: string | null;
  venueCityName: string | null;
  venueId: string | null;
  venueName: string | null;
  rejectionReason: OrganizerRejectReason | null;
  rejectionMessage: string | null;
  submittedAt: string;
  reviewedAt: string | null;
  acknowledgedAt: string | null;
}

export interface RequesterDto {
  userId: string;
  displayName: string;
  email: string;
  avatarUrl: string | null;
  instagram: string | null;
  memberSince: string;
}

export interface OrganizerRequestAdminDto extends OrganizerRequestDto {
  requester: RequesterDto;
  targetOrganizer: OrganizerSummaryDto | null;
  targetOrganizerVenues: VenuesSummaryDto[];
  reviewedByName: string | null;
  previousRejections: number;
}

export interface OrganizerRequestRejectDto {
  reason: OrganizerRejectReason;
  message?: string;
}

export interface OrganizerRequestApproveDto {
  venueId?: string;
}
```

---

## 5. Endpoints

### 5.1 Resumen

| Método y ruta | Quién | Body / params | Respuesta | Errores |
|---|---|---|---|---|
| `POST /rest/organizer-requests` | Usuario | `OrganizerRequestCreateDto` | 201 `OrganizerRequestDto` | 400 validación · 403 baneado · 404 página CLAIM no existe o no está verificada · 404 ciudad del local no existe · 409 `PENDING_REQUEST_EXISTS` / `ORGANIZER_LIMIT_REACHED` / `ORGANIZER_ALREADY_CLAIMED` |
| `GET /rest/organizer-requests/me` | Usuario | — | 200 `OrganizerRequestDto` (la más reciente) · **204** si nunca pidió | — |
| `PATCH /rest/organizer-requests/{id}/logo` | Usuario (dueño) | multipart `logo` | 200 `OrganizerRequestDto` | 400 tipo de imagen · 403 no es suya · 409 `REQUEST_NOT_PENDING` |
| `DELETE /rest/organizer-requests/{id}` | Usuario (dueño) | — | 204 (pasa a CANCELLED) | 403 · 409 `REQUEST_NOT_PENDING` |
| `PATCH /rest/organizer-requests/{id}/acknowledge` | Usuario (dueño) | — | 200 `OrganizerRequestDto` | 403 · 409 si está PENDING |
| `GET /rest/organizers/claimable?name=&limit=5` | Público | `name` mínimo 3 caracteres, `limit` máximo 10 | 200 `List<OrganizerSummaryDto>` | 400 si `name` < 3 |
| `PATCH /rest/organizers/{id}/logo` | Organizador dueño | multipart `logo` | 200 `OrganizerDetailDto` | 403 |
| `DELETE /rest/organizers/{id}` | Organizador dueño | — | 204 (el dueño baja a USER si no le quedan organizadores) | 403 no es suyo · 403 no viene de su NEW (D13) |
| `PATCH /rest/organizers/{id}/release` | Organizador dueño | — | 204 (sin dueño, vuelve a `claimable`; el dueño baja a USER si no le quedan) | 403 no es suyo · 403 viene de su NEW: se borra, no se suelta (D13) |
| `PATCH /rest/admin/organizers/{id}/unclaim` | Admin | — | 200 `OrganizerDetailDto` | 404 · 409 `ORGANIZER_NOT_CLAIMED` (sin dueño) / `ORGANIZER_CREATED_BY_OWNER` (viene de su NEW: se borra) |
| `GET /rest/admin/organizer-requests?status=PENDING&page=0&size=20` | Admin | `status` obligatorio | 200 `Page<OrganizerRequestAdminDto>` | — |
| `GET /rest/admin/organizer-requests/{id}` | Admin | — | 200 `OrganizerRequestAdminDto` | 404 |
| `PATCH /rest/admin/organizer-requests/{id}/approve` | Admin | `OrganizerRequestApproveDto` opcional | 200 `OrganizerRequestAdminDto` | 403 usuario baneado · 404 venue · 409 `REQUEST_NOT_PENDING` / `ORGANIZER_ALREADY_CLAIMED` / `ORGANIZER_LIMIT_REACHED` / `VENUE_ALREADY_LINKED` / `VENUE_REQUIRED` / `CONCURRENT_UPDATE` |
| `PATCH /rest/admin/organizer-requests/{id}/reject` | Admin | `OrganizerRequestRejectDto` | 200 `OrganizerRequestAdminDto` | 400 (OTHER sin mensaje) · 409 `REQUEST_NOT_PENDING` / `CONCURRENT_UPDATE` |

Orden de la cola del admin: PENDING por `submittedAt ASC` (la más antigua primero); APPROVED y
REJECTED por `reviewedAt DESC`. El contador «In attesa · 2» es el `totalElements` de la página.

### 5.2 Seguridad (`SecurityConfig`)

- `/rest/organizer-requests/**` → autenticado. Ya lo cubre `anyRequest().authenticated()`: no hace falta línea nueva.
- `/rest/admin/organizer-requests/**` → ADMIN. Ya lo cubre `/rest/admin/**`.
- `GET /rest/organizers/claimable` → añadir a la lista de `permitAll`. Spring MVC prefiere la ruta
  literal `/claimable` antes que `/{slug}`, así que el controller no tiene conflicto.
- `PATCH /rest/organizers/{id}/logo` → autenticado (por defecto) + ownership en el servicio.
- `SlugUtils`: reservar `me` y `claimable` para que ningún organizador reciba ese slug.

### 5.3 Logos

- `ObjectStorageService` gana `String uploadOrganizerLogo(UUID ownerId, byte[] webpContent)` y
  `void deleteOrganizerLogo(String logoUrl)`.
- En `S3ObjectStorageServiceImpl`: mismo bucket `logos`, prefijo `organizers/`, clave nueva en cada
  subida (`organizers/{ownerId}/logo-{uuid}.webp`; el comentario de `uploadVenueLogo` explica por qué).
  `ownerId` es el id de la solicitud (antes de aprobar) o el del organizador (después).
- Misma tubería que `VenuesServiceImpl.updateLogoAsAdmin`: `ImageTypeValidator.detectExtension` →
  `webpConverterService.convertToWebp(content, LOGO_MAX_LONG_EDGE)` → upload → borrar el anterior con
  `deleteLogoQuietly`.
- Al aprobar una NEW, `organizer.logoUrl = request.logoUrl`: la URL sigue siendo válida, no se mueve nada.
- Al reenviar, la solicitud nueva copia el `logoUrl` de la anterior: no se borra.
- Los logos de solicitudes rechazadas o canceladas que nunca se aprobaron quedan huérfanos. Se acepta
  por ahora (son pocos KB); un job de limpieza queda fuera de esta semana.

---

## 6. Fases detalladas

### Fase 0 — Arreglos en el código existente (lun 19, mañana)

Estado 29/10/2026: **hecha**. Código en `c17df90` con 10 tests unitarios; Postman el 29/10
(`POSTMAN_TESTING.md` §9).

- [x] `BackendErrorResponse`: handler de `OrganizerAlreadyClaimedException` → 409 (con `code`
      `ORGANIZER_ALREADY_CLAIMED`, igual que la sección 4.6).
- [x] `UsersServiceImpl.demoteToUser(userId)`: si el rol es `ORGANIZER` y
      `organizersRepository.countByUserId(userId) == 0` → `USER`. Nunca baja a un ADMIN.
      *(Postman 29/10: A con un organizador → USER · B con dos → sigue ORGANIZER · C ADMIN → sigue ADMIN.)*
- [x] Llamar a `demoteToUser` desde `OrganizersServiceImpl.delete` y desde `deleteAsAdmin`. Hoy
      `deleteAsAdmin` hace `deleteById`: primero hay que cargar el organizador para saber su `user`.
      *(Postman 29/10: `DELETE` de 88888888 con dos CLAIM PENDING → las dos CANCELLED y el venue sin organizador.)*
- [x] Borrar `UsersService.becomeOrganizer` y su implementación vacía (la sustituye la fase 3).
- [x] `OrganizerCreateDto` y `OrganizerUpdateDto`: `@Email` + `@Size(max = 100)` en `contactEmail`, descripción `@Size(max = 2000)` en los dos (D8).
- [x] `OrganizersServiceImpl.createUnclaimed`, `update` y `updateAsAdmin`: normalizar `instagram` con
      `InstagramUtils.normalizeHandle` (como ya hace `updateSocialProfile`).
- [x] `SlugUtils`: palabras reservadas `me` y `claimable`.
- [x] `OrganizersRepository`: `long countByUserId(UUID userId)`.
- [x] `VenueUpdateDto` + `updateVenueAsAdmin`: `organizerId` opcional para enlazar a su organizador un
      venue que ya existe (hoy solo se puede al crearlo). Hace falta para preparar en prod los
      organizadores CLUB/VENUE/SCHOOL antes de abrir el flujo (D11, fase 7).
      *(Postman 29/10: 200 con `organizerId` y `organizerName` · organizador inventado → 404 · `{}` no cambia el enlace.)*

**Terminado cuando:** `mvn test` en verde y `DELETE /rest/organizers/{id}` del último organizador deja
al usuario como USER (comprobado en Postman). *El caso A de §9 lo comprobó con `deleteAsAdmin`; el
`DELETE` del dueño siendo USER necesita una segunda cuenta de Google (pendiente, misma lógica).*

### Fase 1 — Modelo (lun 19, tarde)

Estado 26/10/2026: código hecho (`c17df90`) y la tabla ya existe en la base local. El esquema y el
script de migración están **aplazados por decisión** (prod no tiene datos que migrar): hacerlos antes
de la fase 6/7.

- [ ] Tipos y tabla en `docs/ballastasera_schema.sql` (sección 3.1: tipos arriba, tabla después de `venues`). *Aplazado.*
- [ ] `docs/migrations/2026-10-organizer-requests.sql` (sección 3.2) y ejecutarlo en develop. *Aplazado.*
- [~] Ejecutar la comprobación 3.2b en develop: los 6 `OK`. *Pasada en local; falta develop.*
- [ ] Probar en local: `down -v` + `up` (esquema limpio + seed) y, por separado, el script 3.2 sobre una
      base con el esquema viejo + el seed viejo (tiene que crear la tabla sin errores).
- [x] Añadir el bloque 3.2d al final de `seed_test_data.sql`.
- [x] Enums `OrganizerRequestType`, `OrganizerRequestStatus` y `OrganizerRejectReason`, más
      `requiresVenue()` / `isVenueBound()` en `OrganizerType` (sección 3.3).
- [x] Entidad `OrganizerRequests` + relación inversa en `Users`.
- [x] `repositories/OrganizerRequestsRepository`. *Hecho con `@Query` + `LEFT JOIN FETCH` en vez de
      `@EntityGraph` (convención del proyecto): `findLatestByUserIdWithDetails`,
      `findByStatusWithDetails` (con `countQuery`) y `findByIdWithDetails`. El bloque de abajo es el
      diseño original:*

```java
public interface OrganizerRequestsRepository extends JpaRepository<OrganizerRequests, UUID> {
    Optional<OrganizerRequests> findFirstByUserIdOrderBySubmittedAtDesc(UUID userId);
    boolean existsByUserIdAndStatus(UUID userId, OrganizerRequestStatus status);
    long countByUserIdAndStatus(UUID userId, OrganizerRequestStatus status);

    @EntityGraph(attributePaths = {"user", "organizer", "reviewedBy"})
    Page<OrganizerRequests> findByStatus(OrganizerRequestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "organizer", "reviewedBy"})
    Optional<OrganizerRequests> findWithDetailsById(UUID id);

    List<OrganizerRequests> findByOrganizerIdAndTypeAndStatus(UUID organizerId,
                                                              OrganizerRequestType type,
                                                              OrganizerRequestStatus status);
}
```

- [x] `VenuesRepository`:

```java
/** D11: conecta solo si el venue sigue libre. Devuelve 0 si ya tiene organizador. */
@Modifying
@Query("update Venues v set v.organizer = :organizer where v.id = :venueId and v.organizer is null")
int linkOrganizerIfFree(@Param("venueId") UUID venueId, @Param("organizer") Organizers organizer);
```

- [x] `OrganizersRepository`:
      `List<Organizers> findByIsVerifiedTrueAndClaimedFalseAndUserIsNullAndNameContainingIgnoreCaseOrderByNameAsc(String name, Limit limit)`.
      *Cambiado en la fase 2 (`03f0b29`): el `findTop10…` original no filtraba `UserIsNull`, y un
      organizador creado por su dueño y verificado sigue con `claimed=false` (ej. Tropical Milano).
      Con `Limit` la base devuelve solo `limit` filas.*
- [x] `models/mappers/OrganizerRequestMapper` (MapStruct, `componentModel = "spring"`):
      `toDto`, `toAdminDto`, `toEntity(OrganizerRequestCreateDto)`. Mappings: `organizerName` (`name`
      en NEW, `organizer.name` en CLAIM, con `@AfterMapping` o una expresión), `organizerId`,
      `organizerSlug`, `requester` desde `user`, `reviewedByName` desde `reviewedBy.displayName`,
      `targetOrganizer` solo si `type == CLAIM`, `venueId`/`venueName` desde `venue`, `venueCityName`
      desde `venueCity.name`. `previousRejections` y `targetOrganizerVenues` (con el mapper de venues
      que ya existe) los rellena el servicio.

**Terminado cuando:** la app arranca contra la base nueva sin errores de Hibernate (los tipos enum
de Postgres coinciden) y el script de migración pasa en local.

### Fase 2 — Endpoints del usuario (mar 20)

Servicio `services/manager/OrganizerRequestsService` + `services/implementations/OrganizerRequestsServiceImpl`.
Controller `controllers/OrganizerRequestsController` con `RestConstants.ORGANIZER_REQUESTS = "/rest/organizer-requests"`.

Estado 26/10/2026: **hecha y probada en Postman** (`fdfd05e` + `03f0b29`; pruebas en
`POSTMAN_TESTING.md` §7), menos los logos, que quedan aplazados. Decisiones tomadas al implementarla:
- Solicitud de otro usuario → 403 `AccessDeniedException` (como `assertOwnership` de organizadores).
- Página de la CLAIM inexistente **o sin verificar** → 404 con el mismo mensaje, para no revelar borradores.
- `acknowledge` sobre PENDING → 409 `REQUEST_STILL_PENDING` (código nuevo, §4.6).
- `cancel` también rellena `acknowledgedAt`: lo canceló el propio usuario, no hay resultado que enseñarle.
- `MAX_ORGANIZERS_PER_USER = 1` es una constante privada de `OrganizerRequestsServiceImpl`.
- `saveAndFlush` en `create`: con `save` el INSERT sale en el commit y el `catch` no vería la violación.
  Solo la violación de `uq_org_requests_one_pending` se convierte en `PENDING_REQUEST_EXISTS`; cualquier
  otra se relanza.
- `claimable` se valida con `@Size`/`@Min`/`@Max` en el `@RequestParam` (Spring Boot 4 no necesita
  `@Validated`); handler 400 nuevo para `HandlerMethodValidationException`, que antes daba 500.
- Handler 405 nuevo para `HttpRequestMethodNotSupportedException` (el `POST /rest/organizers` borrado
  daba 500).

- [x] **`create(userId, dto)`**, `@Transactional`:
  1. `user = usersService.findById(userId)`; si `isBanned` → `AccessDeniedException`.
  2. Si `existsByUserIdAndStatus(userId, PENDING)` → 409 `PENDING_REQUEST_EXISTS`.
  3. Si `organizersRepository.countByUserId(userId) >= MAX_ORGANIZERS_PER_USER` → 409 `ORGANIZER_LIMIT_REACHED`.
     (Un ADMIN también puede pedir; la regla es la misma.)
  4. CLAIM: `target = organizersRepository.findById(targetOrganizerId)`; si no existe o no está
     `isVerified` → 404; si `claimed` o `user != null` → 409 `ORGANIZER_ALREADY_CLAIMED`.
     `request.organizer = target`; no se copian los campos de perfil.
  5. NEW: copiar `name`, `organizerType`, `description`, `website` y `facebook`. Además, si existe una
     REJECTED previa con logo, copiar su `logoUrl` (reenviar mantiene el logo). *Copiar el logo queda
     aplazado con los logos: `TODO 5.3` en el código.*
  6. Los dos tipos: `instagram` normalizado, `contactEmail`, `phone`, `message`,
     `termsAcceptedAt = now()`, `status = PENDING`.
  7. **Local (D11), solo NEW.** Las reglas de tipo ya las aplicó `@ValidOrganizerRequest`. Si llega
     `venueCityId`, la ciudad tiene que existir (404). `venueAddress` y `venueCity` se guardan tal cual.
     En CLAIM no se guarda nada: el local es el que el admin ya enlazó al organizador.
  8. `save`; capturar `DataIntegrityViolationException` → 409 `PENDING_REQUEST_EXISTS`.
- [x] **`findMyLatest(userId)`** → `Optional`; el controller devuelve 204 si está vacío.
- [ ] **`updateLogo(requestId, userId, file)`**: ownership (`request.user.id == userId`, si no → 403),
      `status == PENDING` (si no → 409 `REQUEST_NOT_PENDING`), luego la tubería de la sección 5.3.
      *Aplazado (logos).*
- [x] **`cancel(requestId, userId)`**: ownership + PENDING → `CANCELLED` (+ `acknowledgedAt = now()`).
- [x] **`acknowledge(requestId, userId)`**: ownership; si está `PENDING` → 409 `REQUEST_STILL_PENDING`; si no,
      `acknowledgedAt = now()` (idempotente: si ya tiene fecha, no se toca).
- [x] **`findClaimable(name, limit)`** en `OrganizersService`: `name.trim()`. El controller valida
      `@Size(min = 3, max = 120)` en `name` y `@Min(1) @Max(10)` en `limit` (por defecto 5) → 400.
      Endpoint `GET /rest/organizers/claimable` en `OrganizersController`.
- [ ] **`OrganizersService.updateLogo(id, requesterId, file)`** + `PATCH /rest/organizers/{id}/logo`
      (ownership con el `assertOwnership` que ya existe). `@CacheEvict` igual que `update`. *Aplazado (logos).*
- [ ] `ObjectStorageService.uploadOrganizerLogo` / `deleteOrganizerLogo` (sección 5.3). *Aplazado (logos).*
- [x] `SecurityConfig`: `GET /rest/organizers/claimable` en `permitAll`.
- [x] Javadoc corto en cada endpoint, con el estilo del controller (quién puede llamarlo y para qué pantalla).
- [x] Borrar `POST /rest/organizers` (`OrganizersController.create`), `OrganizersService.createForUser`
      y su implementación (D10). Ahora devuelve 405.

**Terminado cuando:** en Postman, con el token de un USER: crear NEW → `me` devuelve PENDING → segundo
create da 409 → subir logo → cancelar → crear CLAIM sobre una página sin reclamar → `claimable?name=sal`
la devuelve y `organizers/{id}/venues` devuelve su local → cancelar → crear NEW CLUB sin dirección da
400 → con `venueAddress` + `venueCityId` da 201 → crear NEW PERSON con dirección da 400.

### Fase 3 — Endpoints del admin (mié 21, mañana)

En `AdminController` (constantes `GET_ORGANIZER_REQUESTS = "/organizer-requests"`, etc.) y en el mismo
`OrganizerRequestsService`.

- [x] **`findByStatus(status, pageable)`**: el `Sort` lo decide el servicio según el estado (sección 5.1).
      `previousRejections` = `countByUserIdAndStatus(userId, REJECTED)`, sin contar la fila actual si es
      REJECTED. Con `size` ≤ 50 el coste es aceptable (una query por fila). Si molesta, se hace una sola
      query agrupada por `user_id`. *(`c3127b0`. CANCELLED va por `updatedAt` DESC. `previousRejections` y los
      venues del organizador pedido los junta un método privado de `AdminController`, en la lista y en el detalle.)*
- [x] **`findById(id)`** con `findByIdWithDetails` *(`c3127b0`)*.
- [x] **`approve(id, adminId, dto)`**, `@Transactional` (`dto` puede ser null). *(`c9e4e40`, probado en
      Postman §8. Diferencias con lo de abajo: los emails van por eventos después del commit; el
      `@CacheEvict` va siempre en `approve`, no solo si se enlaza un venue; `linkVenue` distingue 404
      (venue inexistente) de 409 `VENUE_ALREADY_LINKED`.)*
  1. Cargar; si no está PENDING → 409 `REQUEST_NOT_PENDING`.
  2. `organizersRepository.countByUserId(user) >= MAX` → 409 `ORGANIZER_LIMIT_REACHED`. El admin tendrá
     que rechazarla; es un caso raro, solo pasa si se le asignó una página por la vía directa mientras tanto.
  3. **NEW**: crear `Organizers` con los datos de la solicitud: slug con
     `SlugUtils.uniqueSlug(name, slug -> organizersRepository.findBySlug(slug).isPresent())`,
     `isVerified = true`, `claimed = true`, `user = request.user`, `logoUrl = request.logoUrl`.
     `request.organizer = saved`. `usersService.promoteToOrganizer(userId)`. Email
     `sendOrganizerApprovedEmail` (ya existe).
  4. **CLAIM**: `organizersService.claim(target.id, user.id)`. Ya lanza `OrganizerAlreadyClaimedException`
     (ahora 409), pone `claimed`, promueve y manda `sendOrganizerClaimedEmail`. Después, rechazar
     automáticamente las otras CLAIM PENDING de esa página: `reason = DUPLICATE`,
     `message = "La pagina è stata assegnata a un altro utente."`, `reviewedBy = admin`, más su email de rechazo.
  5. **Local (D11)**, con `org` = el organizador creado o reclamado:
     - Si llega `dto.venueId` (NEW de tipo local: el admin creó antes el venue con
       `POST /rest/admin/venues`, sin `organizerId`, con la dirección y la ciudad de la solicitud):
       `venuesRepository.linkOrganizerIfFree(dto.venueId, org)`. Si devuelve 0 → 409
       `VENUE_ALREADY_LINKED` (la transacción hace rollback del organizador creado). `request.venue = venue`.
     - Si `org.type.requiresVenue()` y `findByOrganizerId(org.id)` sigue vacío → 409 `VENUE_REQUIRED`.
       En una CLAIM solo pasa si el admin creó la página sin su venue: se enlaza con
       `updateVenueAsAdmin` (fase 0) y se vuelve a aprobar.
     - Si se enlazó un venue: `@CacheEvict` de `VENUE_DETAIL`, `VENUE_BY_CITY`, `VENUE_MAP_BY_CITY` y
       `EVENT_DETAIL` (`allEntries = true`, como en `VenuesServiceImpl`). Si no, el venue sigue
       saliendo sin organizador desde Redis.
  6. `status = APPROVED`, `reviewedBy = admin`, `reviewedAt = now()`.
- [x] **`reject(id, adminId, dto)`**, `@Transactional`: PENDING o 409; si `reason == OTHER` y el mensaje
      está vacío → 400; guardar `rejectionReason`, `rejectionMessage` (trim, null si está vacío),
      `reviewedBy`, `reviewedAt`, `status = REJECTED`; email de rechazo. *(`4910656`, probado en Postman
      §8. El 400 de `OTHER` sin mensaje lo da `@AssertTrue isMessageProvidedForOther` en el DTO, no el
      servicio. En una CLAIM el email lleva el nombre del organizador pedido. Probado que rechazar una
      CLAIM deja PENDING a la competidora.)*
- [x] *(`77664a7`. Recibe el enum `OrganizerRejectReason`, no la etiqueta: el `switch` con las etiquetas
      está en `EmailServiceImpl`. También se convierte el nombre del organizador. Probado con un email real
      en el rechazo automático del approve.)*
      **`EmailService.sendOrganizerRejectedEmail(toEmail, organizerName, reasonLabel, message)`**, en
      italiano, como los otros dos. Etiquetas: el texto de la pantalla del admin («Instagram non
      trovato»…). **Escapar el mensaje del admin con `HtmlUtils.htmlEscape`** antes de meterlo en el
      HTML. Enlace al menú: `frontendUrl`.
- [x] **Emails después del commit.** Hoy `verify` y `claim` llaman al `@Async` dentro de la transacción:
      si el commit falla, el email sale igual. En los métodos nuevos, publicar un evento
      (`OrganizerRequestReviewedEvent`) y mandar el email desde un
      `@TransactionalEventListener(phase = AFTER_COMMIT)`. ~~`claim()` se llama dentro de `approve`, así
      que su email se queda como está esta semana (anotarlo como deuda técnica en `ROADMAP.md`).~~
      *(`77664a7`. Al final `claim()` también: publica `OrganizerClaimedEvent` y pasa a `@Transactional`,
      así que no hay deuda que anotar. Sin transacción el evento se descarta: quien publica tiene que ser
      `@Transactional`. `verify` se queda como está porque se borra en esta fase, D10.)*
- [x] Handlers 409 de la sección 4.6 (`OrganizerRequestConflictException`,
      `ObjectOptimisticLockingFailureException`). *(`8a78043`. `CONCURRENT_UPDATE` es una constante de
      `OrganizerRequestConflictException`, pero la usa el handler: el conflicto salta en el commit, fuera del
      servicio. Probado con el debugger (§8). En el mismo commit, el 400 de `HttpMessageNotReadableException`.)*
- [x] **`OrganizersServiceImpl.deleteAsAdmin`** (problema 9) *(adelantado en `c17df90`)*: antes de borrar, pasar a CANCELLED las
      CLAIM PENDING de ese organizador (`findByOrganizerIdAndTypeAndStatus(id, CLAIM, PENDING)`), con
      `rejectionMessage = "La pagina è stata rimossa."`. Así `ON DELETE SET NULL` no rompe
      `chk_org_request_claim`.
- [x] **D13 (confirmada el 27/10): el dueño no borra un organizador creado por el admin.** Criterio con
      `existsByOrganizerIdAndUserIdAndTypeAndStatus`, 403 `AccessDeniedException`; el frontend lo deduce de
      `GET /organizer-requests/me`. *Código hecho el 29/10, 166 tests en verde: método del repositorio,
      `assertDeletableByOwner` en `OrganizersServiceImpl.delete` (después de `assertOwnership`), stub en
      `deleteRemovesOrganizerAndThenDemotesOwner`, test nuevo
      `deleteOfOrganizerNotCreatedByOwnerIsRejectedAndDeletesNothing` y, en `EventsCacheTest`,
      `OrganizerRequestsRepository` como campo `@MockitoBean` con stub en «organizer delete». Probado en
      Postman el 29/10: `POSTMAN_TESTING.md` §10 (a–d). El caso C de §9 ya usa el DELETE del admin.*
- [x] **D13, ampliada el 29/10: soltar el organizador.** Un método privado en `OrganizersServiceImpl` que pone
      `user = null` y `claimed = false` y llama a `demoteToUser` del antiguo dueño, usado por:
      *Código hecho el 30/10, 166 tests en verde: constantes `ORGANIZER_NOT_CLAIMED` y
      `ORGANIZER_CREATED_BY_OWNER`, `OrganizerUnclaimedEvent`, `EmailService.sendOrganizerUnclaimedEmail`
      (texto solo con «invia una nuova richiesta»: el remitente es noreply), `onOrganizerUnclaimed` en el
      listener, `release` + `unclaimAsAdmin` con los privados `removeOwner` e `isCreatedByOwner` (que ahora
      usa también `assertDeletableByOwner`), y los dos endpoints. `SecurityConfig` sin cambios: `release` cae
      en `anyRequest().authenticated()` y `unclaim` en `/rest/admin/**`. Probado en Postman el 31/10
      (`POSTMAN_TESTING.md` §11, con una cuenta USER y otra ADMIN). Faltan los tests (fase 4).
      Confirmado el 30/10: el admin no puede hacer `unclaim` de una página creada con NEW (409), solo
      borrarla.*
  - `release(id, requesterId)` → `PATCH /rest/organizers/{id}/release` (204): `assertOwnership` y 403 si
    **sí** viene de su NEW APPROVED (ese se borra, no se suelta).
  - `unclaimAsAdmin(id)` → `PATCH /rest/admin/organizers/{id}/unclaim` (200 `OrganizerDetailDto`): cualquier
    organizador con dueño; 409 `ORGANIZER_NOT_CLAIMED` si no tiene. **409 `ORGANIZER_CREATED_BY_OWNER` si
    viene de la NEW de su dueño** (`isCreatedByOwner`, decidido 29/10): esas páginas solo se borran. Si no,
    la NEW APPROVED seguiría enlazada (al volver a reclamarla podría borrar lo que otro hizo después) y sus
    datos quedarían públicos. Si el admin quiere quitársela, la borra con `deleteAsAdmin`.
  - El organizador, su venue y sus eventos no cambian, y vuelve a salir en `claimable`. La CLAIM APPROVED
    queda como historial y el usuario puede volver a pedir.
  - **Decidido el 29/10:**
    - 409 `ORGANIZER_NOT_CLAIMED`, constante en `OrganizerRequestConflictException` (sin clase ni handler
      nuevos). «Tiene dueño» se mira con `user != null`, no con `claimed`.
    - `@CacheEvict(EVENT_DETAIL, allEntries = true)` en los dos, como `claim()`: `EventDetailDto` lleva
      `claimed`. `VENUE_DETAIL` no, porque `organizerId`/`organizerName` no cambian.
    - Email solo en `unclaim` (el `release` lo decide el propio dueño): `OrganizerUnclaimedEvent` +
      `OrganizerEmailListener` + `EmailService.sendOrganizerUnclaimedEmail`, después del commit.
    - `isCreatedByOwner(organizer, userId)` sale de `assertDeletableByOwner`: `delete`, `release` y
      `unclaimAsAdmin` usan la misma condición.
    - ~~Los eventos de un organizador borrado quedan publicados sin organizador (`ON DELETE SET NULL`), como
      hasta ahora: el frontend avisa antes de confirmar «Elimina» (ver fase 5b). Sin cambios en el backend.~~
      Cambiado el 29/10 (opción B): al borrar se cancelan sus eventos futuros, ver la casilla siguiente.
  - Frontend (5b): después de un `release`, `GET /me` sigue devolviendo la CLAIM APPROVED: el menú decide con
    el rol o con `/organizers/me`, no con el estado de la solicitud.
  - Tests: dueño de una CLAIM suelta → ok y baja a USER · dueño de una NEW suelta → 403 · `unclaim` sin dueño
    → 409 `ORGANIZER_NOT_CLAIMED` · `unclaim` de una página creada con NEW → 409 `ORGANIZER_CREATED_BY_OWNER` y
    no cambia nada · `unclaim` publica el evento del email.
- [ ] **D13, decidido el 29/10: al borrar un organizador se retiran sus eventos futuros (después de
      `release`/`unclaim`).** Hasta ahora, con `ON DELETE SET NULL`, sus eventos futuros seguían publicados sin
      organizador y nadie salvo el admin podía cancelarlos.
  1. ✅ **Bug que ya existía, hecho el 31/10 y probado en Postman (`POSTMAN_TESTING.md` §12):**
     `EventSeriesServiceImpl` no aguantaba una serie sin organizador (lo deja así cualquier borrado).
     `findByIdWithDetails` hacía `JOIN FETCH s.organizer`, así que el `GET /rest/event-series/{id}` (público)
     daba 404; ahora es `LEFT JOIN FETCH`, como en `EventsRepository`. `buildDetailDto` hacía
     `series.getOrganizer().getInstagram()` (NPE) y `assertOwnership` hacía `series.getOrganizer().getUser()`
     (500 en vez de 403); los dos comprueban ya `organizer == null`, como `EventsServiceImpl.assertOwnership`.
  2. `EventsRepository.cancelUpcomingByOrganizerId(organizerId, now, CANCELLED)` y
     `EventSeriesRepository.deactivateByOrganizerId(organizerId, now)`: `UPDATE` JPQL con `@Modifying`;
     `updatedAt` a mano porque el `UPDATE` masivo no pasa por `@UpdateTimestamp`. Eventos con
     `startAt > now` y no `CANCELLED` (también `DRAFT`/`PENDING`) → `CANCELLED`; series → `active = false`.
     Los pasados y los que están en curso no se tocan.
  3. `retireUpcomingEvents(organizerId)` privado en `OrganizersServiceImpl`, llamado por `delete` y
     `deleteAsAdmin` **antes** de `organizersRepository.delete` (después `organizer_id` ya es null). Inyecta
     `EventsRepository` y `EventSeriesRepository`, como ya hace con `OrganizerRequestsRepository`. La caché
     ya se vacía (`EVENT_DETAIL`, `allEntries`). `release`/`unclaim` no tocan los eventos.
  4. `status` en `EventDetailDto` (MapStruct lo mapea solo): `GET /rest/events/{id}` no filtra por estado y,
     desde un enlace guardado, un evento cancelado se veía como si siguiera en pie. El frontend muestra
     «Annullato».
  - Por qué cancelar y no borrar: mapa, lista por ciudad, eventos del organizador, favoritos y «ci vado»
    filtran `PUBLISHED`, así que desaparecen solos; no hay que limpiar flyers ni favoritos/asistencia; y
    `VenuesServiceImpl` ya ignora los `CANCELLED`, así que el venue queda libre del todo.
  - Fuera: avisar por email a quien marcó «ci vado».
  - Tests: `delete` y `deleteAsAdmin` cancelan y desactivan antes de borrar · el `delete` rechazado por D13 no
    toca nada · serie sin organizador → detalle 200 y PATCH/DELETE del usuario → 403.

  Preguntas originales (27/10), ya resueltas arriba:
  - **Cómo saberlo.** `claimed` no sirve: el approve de una NEW también pone `claimed = true`. Criterio
    propuesto: `delete` solo deja borrar si existe una solicitud NEW APPROVED de ese usuario con ese
    `organizer_id` (método nuevo `existsByOrganizerIdAndUserIdAndTypeAndStatus`). Así quedan protegidos
    también los asignados a mano con `PATCH /admin/organizers/{id}/claim`, que no tienen solicitud.
  - **Error.** 403 `AccessDeniedException` o 409 con `code` nuevo (`ORGANIZER_NOT_DELETABLE`) para que el
    frontend muestre «Questa pagina è gestita da BallaStasera: scrivici per rimuoverla».
  - **Frontend.** Ocultar «Elimina» en el perfil cuando no se puede borrar: hace falta un campo en
    `OrganizerDetailDto` (p. ej. `deletable`) o deducirlo de `GET /rest/organizer-requests/me`.
  - **Si el usuario quiere dejar la página.** Hoy no hay endpoint para quitarle el dueño: o lo borra el
    admin, o se añade un `PATCH /admin/organizers/{id}/unclaim` (`user = null`, `claimed = false`,
    `demoteToUser`).
  - **Tests.** `OrganizersServiceImplTest`: dueño de una NEW borra → ok · dueño de una CLAIM borra → 403/409
    y no se borra nada · `deleteAsAdmin` sigue borrando cualquiera.
- [ ] **D12: CLUB y VENUE solo publican en sus venues.** En `EventsServiceImpl.create` / `update` y en
      `EventSeriesServiceImpl.create` / `update` (los métodos del organizador, **no** los `AsAdmin`):
      si `organizer.getType().isVenueBound()`, `venueId` es obligatorio y tiene que estar en
      `venuesRepository.findByOrganizerId(organizer.getId())`; si no → 403 `AccessDeniedException`
      («Venue not owned by this organizer»). Un método privado en cada servicio, llamado justo antes de
      `resolveVenue`. En `update` solo se comprueba si el body cambia `venueId`.
- [ ] Borrar `GET /rest/admin/organizers/pending`, `PATCH /rest/admin/organizers/{id}/verify`,
      `OrganizersService.findPendingVerification` y `verify`, y `OrganizersRepository.findByIsVerifiedFalse` (D10).
      `GET /rest/admin/organizers/verified` se queda (lo usa `verified-organizer-list`).
      **Añadido 29/10: borrar también `PATCH /rest/admin/organizers/{id}/claim`** (`CLAIM_ORGANIZER` en
      `AdminController` y `OrganizerClaimDto` si nadie más lo usa). Se saltaba las reglas del flujo nuevo: no
      comprueba `MAX_ORGANIZERS_PER_USER` (D3) y deja en la cola las CLAIM PENDING de otros usuarios sobre esa
      página, que vuelven a ser aprobables si la página vuelve a `claimable`. El frontend nunca lo usó
      (`endpoints.ts` apunta a una ruta que no existe y nadie lo llama). Asignar una página = CLAIM aprobada.
      `OrganizersService.claim()` se queda: lo usa `approve`. Después, en D13 ya no existe «asignado a mano»
      (el criterio `isCreatedByOwner` no cambia) y el caso c de `POSTMAN_TESTING.md` §10 se puede quitar.
- [ ] **Revisión del 29/10 contra el código:**
  - `approve`: si el usuario de la solicitud está baneado → 403, como en `create` (si lo banean mientras
    está PENDING, hoy se aprobaría y subiría a ORGANIZER).
  - `OrganizersServiceImpl.update` (el dueño) ignora `logoUrl`: método del mapper con
    `@Mapping(target = "logoUrl", ignore = true)`. Hoy el dueño puede poner cualquier URL externa como logo
    (el problema de D7, que sigue abierto mientras los logos estén aplazados). `updateAsAdmin` lo sigue
    aceptando (lo usa `update-organizer` del frontend). Cuando vuelva 5.3, el dueño lo cambia por
    `PATCH /rest/organizers/{id}/logo`.
  - Fuera de esta entrega: el filtro JWT no mira `isBanned`/`isDeleted` → `ROADMAP.md`, «Seguridad — deuda
    conocida».

**Terminado cuando:** en Postman, con el token de ADMIN: cola PENDING → approve de una NEW (el usuario ve
`role: ORGANIZER` en `/auth/me` y su organizador en `/organizers/me`) → reject de otra con motivo →
approve de una CLAIM con otra CLAIM competidora, que queda REJECTED/DUPLICATE (el usuario ve el venue en
`/organizers/me` → `venues`) → approve de la NEW CLUB con dirección: sin body da 409 `VENUE_REQUIRED`;
crear el venue y aprobar con su id da 200, y `/rest/venues/{id}` muestra el organizador → con el token del
nuevo organizador CLUB, crear un evento en otro venue da 403.

### Fase 4 — Tests del backend (mié 21, tarde)

Mismo estilo que los tests existentes: Mockito para servicios, `@AutoConfigureMockMvc` + `@MockitoBean`
para controllers.

- [ ] `OrganizerRequestsServiceImplTest`:
  - create NEW ok · create CLAIM ok
  - create con PENDING existente → `PENDING_REQUEST_EXISTS`
  - create con un organizador ya propio → `ORGANIZER_LIMIT_REACHED`
  - create CLAIM sobre una página reclamada → `ORGANIZER_ALREADY_CLAIMED` · sobre una no verificada → 404
  - usuario baneado → 403
  - reenviar copia el logo de la REJECTED anterior
  - `DataIntegrityViolationException` → `PENDING_REQUEST_EXISTS`
  - cancel / logo / acknowledge: no es el dueño → 403 · no está PENDING → 409
  - approve NEW: crea el organizador verificado y reclamado, slug único, promueve, publica el evento de email
  - approve CLAIM: llama a `claim`, auto-rechaza las CLAIM competidoras
  - create NEW: ciudad del local inexistente → 404 · CLAIM con `venueAddress` → se ignora
  - approve: NEW con `venueId` conecta el venue · `linkOrganizerIfFree` devuelve 0 → `VENUE_ALREADY_LINKED`
    · NEW CLUB sin `venueId` → `VENUE_REQUIRED` · CLAIM de una página con venue → ok sin body · CLAIM de
    una SCHOOL sin venue → `VENUE_REQUIRED`
  - `deleteAsAdmin` cancela las CLAIM PENDING antes de borrar
- [ ] `EventsServiceImplTest` / `EventSeriesServiceImplTest` (D12): CLUB en su venue → ok · CLUB en otro
      venue o sin venue → 403 · PERSON y SCHOOL en cualquier venue → ok · `createAsAdmin` sin cambios
  - approve / reject de una no PENDING → 409 · reject OTHER sin mensaje → 400
- [ ] `UsersServiceImplTest`: `demoteToUser` (ORGANIZER sin organizadores → USER; con organizadores → sigue igual; ADMIN → sigue igual).
- [ ] `OrganizerRequestsControllerTest`: 201 / 204 / 400 de validación (NEW sin nombre, CLAIM sin
      target, NEW CLUB sin dirección, NEW PERSON con dirección, dirección sin ciudad,
      `termsAccepted=false`, Instagram inválido) / 409 con `errors.code`.
- [ ] `AdminControllerTest`: lista por estado, approve y reject (200 / 400 / 409).
- [ ] `SecurityConfigTest`: anónimo → 401 en `/rest/organizer-requests/me`; USER → 403 en
      `/rest/admin/organizer-requests`; anónimo → 200 en `/rest/organizers/claimable?name=sal`.
- [ ] `OrganizerRequestCreateDto`: test de validación como `VenueDtoValidationTest`.

**Terminado cuando:** `mvn test` en verde. Anotar el número de tests en `ROADMAP.md`, igual que en el bloque 1.

### Fase 5a — Frontend: base y wizard (jue 22)

Rutas en italiano, como las existentes (`benvenuto`, `le-tue-serate`, `luogo/:id`).

- [ ] `core/models/organizer-request.model.ts` (sección 4.7).
- [ ] `endpoints.ts`:
  - nuevo `organizerRequests: { create, me, logo(id), cancel(id), acknowledge(id) }`
  - `organizers.claimable` y `organizers.logo(id)`
  - `admin.organizerRequests`, `admin.organizerRequest(id)`, `admin.approveRequest(id)`, `admin.rejectRequest(id)`
  - corregir `admin.claimOrganizer` a `/rest/admin/organizers/${id}/claim`
- [ ] `core/services/organizer-requests.service.ts`: métodos HTTP + un signal `myRequest` (con su
      estado de carga). Se carga después del login si `role` es `USER` o `ORGANIZER`, y se vuelve a
      cargar al crear, cancelar o hacer acknowledge.
- [ ] `FEATURE_FLAGS.organizerRequestPage` (+ cada `environment*.ts`): protege las rutas nuevas y la
      tarjeta del menú hasta que todo esté listo.
- [ ] `features/organizer-request/`: un `OrganizerRequestFormStore` (signals) que guarda el borrador
      entre pasos, y estas rutas bajo `diventa-organizzatore`, con
      `canMatch: [featureFlagGuard(...), roleGuard('USER')]`:
  - `diventa-organizzatore` → redirige a `chi-sei`, o a `richiesta-inviata` si ya hay una PENDING.
  - **`chi-sei`** (Passo 1): 5 tarjetas → `OrganizerType` (mapeo en la sección 4.1). «Continua»
    desactivado hasta elegir una.
  - **`profilo`** (Passo 2): arriba, el campo nombre con la búsqueda `organizers/claimable` (debounce
    300 ms, a partir de 3 caracteres) y debajo **la lista de resultados** (logo, nombre, tipo,
    Instagram) + la última fila «Non c'è, crea un profilo nuovo».
    - **Elegir uno → modo CLAIM**: se cargan `organizers/{slug}` y `organizers/{id}/venues`. Tarjeta de
      solo lectura con logo, nombre, tipo, descripción y **su local** (nombre + dirección de cada venue
      enlazado), con «Cambia» para volver a la lista (D6). El formulario se reduce a Instagram + email +
      mensaje («Come verifichiamo che è tua»). El wizard tiene 3 pasos: «Il tuo locale» no aparece.
    - **«Non c'è» → modo NEW**: logo (elegir archivo + preview local, se sube después del create),
      descripción con contador /500, Instagram (obligatorio, texto «Lo usiamo per verificare…»), email
      de contacto (obligatorio, prellenado con el email del usuario), teléfono y web (facultativos).
  - **`locale`** («Il tuo locale», solo NEW con un tipo que `requiresVenue()`): dirección y ciudad
    (select con `GET /rest/cities`) → `venueAddress` + `venueCityId`, con el texto «Lo aggiungiamo noi
    alla mappa quando approviamo la richiesta». En el mapa sale con el nombre del perfil; si es otro, el
    admin lo cambia al crear el venue. El indicador de pasos pasa a 4 cuando este paso existe.
  - **`conferma`** (Passo 3): resumen con «Modifica», bloque «Cosa succede adesso», checkbox obligatoria
    «Pubblicherò solo serate reali…», «Invia richiesta» → `POST`, luego `PATCH logo` si hay logo, y
    navegar a `richiesta-inviata`. Si el logo falla, la solicitud ya está creada: avisar y ofrecer
    reintentar desde «Vedi la richiesta». Traducir los `errors.code` 409 a mensajes.
  - **`richiesta-inviata`**: timeline (Inviata con `submittedAt` → In revisione → Pronto a pubblicare),
    «Torna alla home». También sirve de «Vedi la richiesta» (con «Annulla richiesta» → `DELETE`).
- [ ] «Modifica e reinvia»: `diventa-organizzatore/chi-sei?da=<idRechazada>` prellena el store desde `myRequest`.
- [ ] Textos en `public/i18n/it.json`, `en.json` y `es.json` (transloco), con claves bajo `organizerRequest.*`.
- [ ] Tests vitest: el servicio (204 → `null`), el store (mapeo de tipos, modo CLAIM, cuándo aparece
      «Il tuo locale») y el guard de redirección.

### Fase 5b — Frontend: menú, admin y bienvenida (vie 23, mañana)

- [ ] **`shared/navbar/navbar.html`**, tarjeta «Organizza» (`*appRole="'USER'"`) según `myRequest`:
  - sin solicitud, `CANCELLED` o `APPROVED` → «Organizzi serate?» + «Diventa organizzatore» → `/diventa-organizzatore`.
    Un `CANCELLED` con `rejectionMessage` y `acknowledgedAt` null lo canceló el admin al borrar la página
    («La pagina è stata rimossa.», sin email): mostrar el mensaje una vez y llamar a `acknowledge`
  - `PENDING` → «Richiesta in revisione» · «{organizerName} · inviata {fecha relativa}» · «Vedi la richiesta»
  - `REJECTED` → «Richiesta non approvata» · nombre · «Motivo: {etiqueta}» + `rejectionMessage` · «Modifica e reinvia»
  - en el rail plegado: el icono con un punto de color según el estado
- [ ] Segunda línea del perfil en el menú para ORGANIZER: «Organizzatore · {nombre}» desde
      `GET /rest/organizers/me` (tarea 12 del doc del 06/10).
- [ ] **Bienvenida de organizador** (`benvenuto-organizzatore`, pantalla «Approvata»): se muestra una
      sola vez si `role === 'ORGANIZER'` y `myRequest.status === 'APPROVED'` con
      `acknowledgedAt === null`. Al salir (cualquier botón) → `acknowledge`. «Completa il profilo:
      manca ancora il logo» solo si el organizador no tiene `logoUrl`.
- [ ] Cuando la app vuelve a tener el foco (`visibilitychange`) o cada 5 minutos con la pestaña
      visible, si hay una solicitud PENDING: volver a pedir `/auth/me` + `myRequest`. Así el
      usuario ve la aprobación sin recargar la página.
- [ ] **Admin: `features/admin/organizer-requests/`** (ruta `admin/organizer-requests`, sustituye a
      `pending-organizers`):
  - tabs «In attesa · N» / «Approvate» / «Rifiutate» → `status`
  - tarjeta: logo o iniciales, nombre, tipo, insignia «Rivendica una pagina esistente» si es CLAIM (con
    enlace a la página), «Richiesta da {displayName}», Instagram con «Apri»
    (`https://instagram.com/{handle}`, `target=_blank rel=noopener`), email, «Inviata …»,
    descripción, «2º intento» si `previousRejections > 0`
  - bloque **«Locale»**: en CLAIM, los venues que ya tiene el organizador (`targetOrganizerVenues`, con
    enlace a `luogo/:id`); en NEW de tipo local, la dirección propuesta con el botón «Crea il locale»
    (abre `admin/create-venue` prellenado y vuelve con el id); si falta el venue, aviso «Manca il locale»
  - «Approva» → confirmación → `approve` (con `{ venueId }` si se acaba de crear el local)
  - «Rifiuta» → panel con los 4 motivos + «Altro» (radio), mensaje facoltativo (obligatorio con
    «Altro»), «Invia rifiuto»
  - un 409 `CONCURRENT_UPDATE` o `REQUEST_NOT_PENDING` → aviso «Già revisionata» + recargar la lista
- [ ] **Perfil del organizador (D13):** un solo botón. «Elimina» si `myRequest` es una NEW APPROVED con
      `organizerId` igual al suyo; si no, «Lascia la pagina» (`PATCH /rest/organizers/{id}/release`).
      Confirmación de «Elimina»: «Gli eventi futuri resteranno pubblicati senza organizzatore: annullali
      prima». Confirmación de «Lascia»: «I contatti e gli eventi resteranno pubblici: modificali prima».
- [ ] Admin: botón «Rimuovi proprietario» en el organizador → `PATCH /rest/admin/organizers/{id}/unclaim`.
- [ ] `admin-home`: cambiar el enlace de «Organizzatori in attesa» a la página nueva.
- [ ] Borrar `features/admin/pending-organizers`, su ruta, `FEATURE_FLAGS.pendingOrganizersPage` (y su
      valor en cada `environment*.ts`), `AdminService.getPendingOrganizers/verifyOrganizer` y
      `endpoints.organizers.create` / `endpoints.admin.getPendingOrganizers` / `verifyOrganizer` (D10).
      También `endpoints.organizers.claimOrganizer` (apunta a una ruta que no existe y el backend borra la
      asignación manual).

### Fase 6 — Documentación y despliegue (vie 23, tarde)

- [ ] `docs/API.md`: sección «Solicitudes de organizador» con cada endpoint de la sección 5.1; quitar `POST /api/organizers`.
- [ ] `docs/ROADMAP.md`: bloque nuevo marcado `[x]`, más la deuda técnica (email de `claim()`
      después del commit, limpieza de logos huérfanos).
- [ ] `docs/POSTMAN_TESTING.md`: flujo completo USER → ADMIN.
- [ ] **Staging, en este orden:**
  1. Backup (`pg_dump`).
  2. Ejecutar `2026-10-organizer-requests.sql` (solo DDL, sin datos que migrar).
  3. Desplegar backend y frontend juntos (D10), con `organizerRequestPage` activado en staging. Vaciar la
     caché `eventDetail` de Redis: las entradas antiguas no tienen `status` (D13, eventos retirados).
  4. Probar el flujo end-to-end con dos cuentas (USER y ADMIN): NEW aprobada, NEW rechazada →
     reenviada, CLAIM sobre una de tus páginas unclaimed (con su venue) con una CLAIM competidora, NEW
     CLUB con dirección (creas el venue y apruebas), cancelar.

### Fase 7 — Paso a prod (fin de semana, después de validar staging)

- [ ] Backup de prod (`pg_dump`).
- [ ] Ejecutar `2026-10-organizer-requests.sql`.
- [ ] Desplegar backend y frontend juntos. Vaciar la caché `eventDetail` de Redis (igual que en staging).
- [ ] Desde el admin, enlazar cada organizador unclaimed de tipo CLUB/VENUE/SCHOOL con su venue
      (`updateVenueAsAdmin` con `organizerId`, fase 0). Es trabajo de datos a mano, no una migración:
      sin ese enlace, la CLAIM de esa página da `VENUE_REQUIRED` al aprobar.
- [ ] Activar `organizerRequestPage` en `environment.ts` de prod cuando quieras abrir el flujo al público.
      Hasta entonces la tarjeta «Diventa organizzatore» sigue escondida.
- [ ] Prueba rápida en prod con tu cuenta: `GET /rest/organizer-requests/me` → 204, y la cola del admin vacía.

---

## 7. Fuera de alcance esta semana

- Páginas del organizador «Pubblica una serata» y «Le mie serate». Hoy crear eventos solo existe en el
  admin (`admin/create-event`). Es el siguiente bloque, y la bienvenida enlaza ahí. La regla D12 ya
  queda en el backend esta semana. En ese bloque, el selector de venue de un CLUB/VENUE mostrará solo
  `GET /rest/organizers/{id}/venues`.
- Que un organizador conecte o cree venues por su cuenta (D11: siempre lo hace el admin).
- Más de un organizador por usuario (D3: solo hay que cambiar la constante y la UI de selección).
- Job de limpieza de logos huérfanos.
- Moderación de eventos del organizador antes de publicarlos (pregunta abierta del doc del 06/10).
- Notificaciones push o in-app (el aviso es por email y por la tarjeta del menú).
- Borrar la cuenta de un usuario. Hoy no hay endpoint, pero `organizers.user_id` es `ON DELETE CASCADE`: un
  borrado directo se saltaría D13 y la retirada de eventos. Qué tener en cuenta: `docs/user-account-deletion-plan.md`.

## 8. Checklist de cierre

- [ ] Fases 0–6 marcadas `[x]`
- [ ] `mvn test` en verde y tests de vitest en verde
- [ ] Flujo end-to-end en staging con las 5 variantes (aprobada, rechazada → reenviada, CLAIM con su venue y una competidora, NEW CLUB con dirección, cancelada)
- [ ] `API.md`, `ROADMAP.md` y `POSTMAN_TESTING.md` actualizados
- [ ] Fase 7 hecha y desplegada en prod
