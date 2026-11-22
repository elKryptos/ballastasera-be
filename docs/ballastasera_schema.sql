-- ============================================================================
--  BallaStasera — Schema database  (PostgreSQL 14+)
--  Piattaforma di eventi di ballo latino/social (salsa, bachata, kizomba, ...)
--
--  Idee guida:
--   * Utenti con login Google (OAuth) — ma la consultazione è pubblica.
--   * Chi pubblica ("organizer") può essere una persona, un locale, una
--     discoteca o una scuola (anche un'ASD).
--   * Ogni evento è geolocalizzato (lat/lng) per la mappa Leaflet.
--   * Progettato partendo da Milano ma già multi-città / multi-Italia.
--
--  NOTA: la gestione di created_at / updated_at è delegata al codice
--  applicativo (Hibernate: @CreationTimestamp / @UpdateTimestamp).
--  I DEFAULT now() restano solo come rete di sicurezza sull'INSERT.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Estensioni
-- ----------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS "pgcrypto";   -- gen_random_uuid()
-- CREATE EXTENSION IF NOT EXISTS "postgis"; -- opzionale, vedi sezione GEO in fondo


-- ----------------------------------------------------------------------------
-- Tipi enumerati
-- ----------------------------------------------------------------------------
CREATE TYPE user_role          AS ENUM ('USER', 'ORGANIZER', 'ADMIN');
CREATE TYPE organizer_type     AS ENUM ('PERSON', 'VENUE', 'CLUB', 'SCHOOL');
CREATE TYPE event_status       AS ENUM ('DRAFT', 'PENDING', 'PUBLISHED', 'CANCELLED');
CREATE TYPE flyer_status       AS ENUM ('NONE', 'PROCESSING', 'READY', 'FAILED');
CREATE TYPE event_type         AS ENUM ('EVENT', 'SCHOOL', 'CLUB', 'BAR', 'RESTAURANT');
CREATE TYPE venue_type         AS ENUM ('SCHOOL', 'CLUB', 'BAR', 'RESTAURANT', 'OTHER');
--organizer request
CREATE TYPE organizer_request_type   AS ENUM ('NEW', 'CLAIM');
CREATE TYPE organizer_request_status AS ENUM ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED');
CREATE TYPE organizer_reject_reason  AS ENUM ('INSTAGRAM_NOT_FOUND', 'INCOMPLETE_DATA', 'NOT_DANCE_ORGANIZER', 'DUPLICATE', 'OTHER');
--user profile
CREATE TYPE gender             AS ENUM ('MALE', 'FEMALE', 'OTHER', 'UNDISCLOSED');
CREATE TYPE dance_role         AS ENUM ('LEADER', 'FOLLOWER', 'BOTH');
CREATE TYPE dance_level        AS ENUM ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'TEACHER');
CREATE TYPE contact_visibility AS ENUM ('NOBODY', 'EVERYONE', 'MUTUALS');   -- MUTUALS = solo amigos (social fase 1)


-- ============================================================================
--  CITIES — città/aree (Milano oggi, resto d'Italia domani)
-- ============================================================================
CREATE TABLE cities (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        TEXT       NOT NULL,              -- es. 'Milano'
    province    TEXT,                             -- sigla provincia, es. 'MI'
    region      TEXT,                             -- es. 'Lombardia'
    country     CHAR(2)    NOT NULL DEFAULT 'IT',
    latitude    DOUBLE PRECISION,                 -- centro città (per centrare la mappa)
    longitude   DOUBLE PRECISION,
    slug        TEXT       NOT NULL,              -- es. 'milano' (per URL /milano)+++
    is_active   BOOLEAN    NOT NULL DEFAULT TRUE, -- città già "aperta" sulla piattaforma
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (slug),
    UNIQUE (name, province)
);


-- ============================================================================
--  USERS — account (identità + autenticazione Google)
-- ============================================================================
CREATE TABLE users (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    google_id                   TEXT UNIQUE,                     -- 'sub' restituito da Google OAuth
    email                       TEXT        NOT NULL UNIQUE,
    display_name                TEXT        NOT NULL,
    avatar_url                  TEXT,
    instagram                   TEXT,
    whatsapp                    TEXT,
    whatsapp_visibility         contact_visibility NOT NULL DEFAULT 'MUTUALS',
    birth_date                  DATE,
    gender                      gender,
    dance_role                  dance_role,
    show_profile_public         BOOLEAN NOT NULL DEFAULT TRUE,
    role                        user_role   NOT NULL DEFAULT 'USER',
    is_banned                   BOOLEAN     NOT NULL DEFAULT FALSE,
    is_deleted                  BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted_at                  TIMESTAMPTZ,
    login_count                 INTEGER     NOT NULL DEFAULT 0,
    last_login                  TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================================
--  ORGANIZERS — profilo pubblico di chi pubblica eventi
--  (una persona, un locale, una discoteca, una scuola o ASD)
--  Un utente può gestire più profili (es. gestisce 2 locali).
-- ============================================================================
CREATE TABLE organizers (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID REFERENCES users(id) ON DELETE CASCADE,  -- nullable: admin puo crear sin dueño
    name          TEXT NOT NULL,                  -- nome pubblico (es. 'Tropical Milano')
    slug          TEXT NOT NULL UNIQUE,           -- per URL /organizzatore/tropical-milano
    type          organizer_type NOT NULL DEFAULT 'PERSON',
    description   TEXT,
    logo_url      TEXT,
    website       TEXT,
    phone         TEXT,
    contact_email TEXT,
    instagram     TEXT,
    facebook      TEXT,
    is_verified   BOOLEAN NOT NULL DEFAULT FALSE, -- badge "verificato" gestito da admin
    claimed       BOOLEAN NOT NULL DEFAULT FALSE, -- true: llegó por una CLAIM (perfil del admin reclamado). false con user_id: lo creó su dueño con una NEW
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_organizers_user ON organizers(user_id);
CREATE INDEX idx_organizers_type ON organizers(type);
CREATE INDEX idx_organizers_claimed ON organizers(claimed);


-- ============================================================================
--  VENUES — luoghi fisici riutilizzabili (locali, discoteche, scuole...)
--  Separato dagli eventi: lo stesso locale può ospitare tanti eventi,
--  anche di organizzatori diversi.
-- ============================================================================
CREATE TABLE venues (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         TEXT NOT NULL,
    type         venue_type NOT NULL DEFAULT 'OTHER', -- tipo proprio del luogo (non dipende dall'organizer)
    organizer_id UUID REFERENCES organizers(id) ON DELETE SET NULL, -- se il locale ha un profilo proprio
    city_id      BIGINT NOT NULL REFERENCES cities(id),
    address      VARCHAR(150) NOT NULL,           -- via e numero civico
    latitude     DOUBLE PRECISION NOT NULL,
    longitude    DOUBLE PRECISION NOT NULL,
    description  TEXT,
    website      VARCHAR(100),                    -- opzionale
    whatsapp     VARCHAR(20),                     -- formato E.164: +393331234567
    email        VARCHAR(100),
    facebook     VARCHAR(100),                    -- URL completo
    instagram    VARCHAR(100),                    -- URL completo
    youtube      VARCHAR(100),                    -- URL completo
    tiktok       VARCHAR(100),                    -- URL completo
    logo_url     TEXT,                            -- URL pubblica del logo (webp, upload gestito da backend)
    created_by   UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),   
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_venues_city      ON venues(city_id);
CREATE INDEX idx_venues_coords    ON venues(latitude, longitude);
CREATE INDEX idx_venues_city_type ON venues(city_id, type);


-- ============================================================================
--  DANCE_STYLES — tipi di ballo (tabella di lookup, facile da estendere)
-- ============================================================================
CREATE TABLE dance_styles (
    id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,                    -- 'Salsa', 'Bachata', ...
    slug TEXT NOT NULL UNIQUE
);

-- ============================================================================
--  USER_DANCE_STYLES — tipi di ballo del usuario
-- ============================================================================
CREATE TABLE user_dance_styles (
    user_id        UUID   NOT NULL REFERENCES users(id)        ON DELETE CASCADE,
    dance_style_id BIGINT NOT NULL REFERENCES dance_styles(id) ON DELETE CASCADE,
    level          dance_level NOT NULL,
    PRIMARY KEY (user_id, dance_style_id)
);
CREATE INDEX idx_user_dance_styles_style ON user_dance_styles(dance_style_id);

-- ============================================================================
--  USER_FOLLOWS — follower segue followed. Amici = si seguono a vicenda
-- ============================================================================
CREATE TABLE user_follows (
    follower_id UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    followed_id UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (follower_id, followed_id),
    CHECK (follower_id <> followed_id)
);
CREATE INDEX idx_user_follows_followed ON user_follows(followed_id, created_at DESC);
CREATE INDEX idx_user_follows_follower ON user_follows(follower_id, created_at DESC);

-- ============================================================================
--  USER_BLOCKS — blocker ha bloccato blocked (vale nei due sensi)
-- ============================================================================
CREATE TABLE user_blocks (
    blocker_id UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);
CREATE INDEX idx_user_blocks_blocked ON user_blocks(blocked_id);

-- ============================================================================
--  EVENT_SERIES — raggruppa eventi ricorrenti ("ogni giovedì salsa al locale X")
--  Non è un evento: è il template da cui si generano le occorrenze in `events`
--  (colonna events.series_id). Specchia gran parte delle colonne di `events`
--  (stessa denormalizzazione lat/lng, stesso criterio "gratis o prezzo
--  obbligatorio") perché ogni occorrenza generata ha bisogno di quei dati.
--  I giorni di ricorrenza vivono in event_series_recurrence_days (niente
--  iCalendar/rrule): start_time/end_time restano l'orario del giorno.
--  generated_until è il cursore di quanto già generato in `events`;
--  active=false congela la serie (non genera più occorrenze nuove).
-- ============================================================================
CREATE TABLE event_series (
    id              UUID   PRIMARY KEY DEFAULT gen_random_uuid(),
    organizer_id    UUID            REFERENCES organizers(id) ON DELETE SET NULL,
    venue_id        UUID            REFERENCES venues(id)     ON DELETE SET NULL,
    city_id         BIGINT NOT NULL REFERENCES cities(id),

    title           TEXT NOT NULL,
	active          BOOLEAN NOT NULL DEFAULT TRUE,
	generated_until DATE,         -- cursor: hasta qué fecha ya se generaron occurrences en `events`

    description     TEXT,
    flyer_url       TEXT,
	instagram_url   TEXT,
	whatsapp_url    TEXT,

    is_free         BOOLEAN NOT NULL DEFAULT TRUE,
    price           NUMERIC(8,2),
    currency        CHAR(3) NOT NULL DEFAULT 'EUR',

    -- posizione (obbligatoria, stesso criterio di events)
    address         TEXT   NOT NULL,
    latitude        DOUBLE PRECISION NOT NULL,
    longitude       DOUBLE PRECISION NOT NULL,

    start_time      TIME NOT NULL,  -- orario di inizio di ogni occorrenza generata
    end_time        TIME,           -- orario di fine (opzionale)

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_event_series_price CHECK (is_free OR price IS NOT NULL)
);
CREATE INDEX idx_event_series_organizer ON event_series(organizer_id);
CREATE INDEX idx_event_series_venue     ON event_series(venue_id);
CREATE INDEX idx_event_series_city      ON event_series(city_id);


-- ============================================================================
--  EVENT_SERIES_RECURRENCE_DAYS — giorni della settimana in cui si ripete
--  la serie (es. LUNEDI + MERCOLEDI). Sostituisce la vecchia colonna rrule.
-- ============================================================================
CREATE TABLE event_series_recurrence_days (
    series_id   UUID NOT NULL REFERENCES event_series(id) ON DELETE CASCADE,
    day_of_week TEXT NOT NULL,   -- nome del java.time.DayOfWeek: 'MONDAY', 'TUESDAY', ...
    PRIMARY KEY (series_id, day_of_week)
);


-- ============================================================================
--  EVENT_SERIES_DANCE_STYLES — molti-a-molti serie <-> tipi di ballo
--  Copiati su ogni occorrenza generata in event_dance_styles.
-- ============================================================================
CREATE TABLE event_series_dance_styles (
    series_id      UUID   NOT NULL REFERENCES event_series(id) ON DELETE CASCADE,
    dance_style_id BIGINT NOT NULL REFERENCES dance_styles(id) ON DELETE CASCADE,
    PRIMARY KEY (series_id, dance_style_id)
);


-- ============================================================================
--  EVENTS — la tabella centrale: una riga = una serata/occorrenza
--
--  Nota geo: le coordinate sono SEMPRE presenti sull'evento (denormalizzate).
--  Se c'è un venue, l'app le copia dal venue; altrimenti sono inserite a mano.
--  Così la query per la mappa legge una sola tabella indicizzata.
-- ============================================================================
CREATE TABLE events (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organizer_id  UUID            REFERENCES organizers(id)   ON DELETE SET NULL,
    venue_id      UUID            REFERENCES venues(id)       ON DELETE SET NULL,
    series_id     UUID            REFERENCES event_series(id) ON DELETE SET NULL,
    city_id       BIGINT NOT NULL REFERENCES cities(id),

    title         TEXT   NOT NULL,
    slug          TEXT   NOT NULL UNIQUE,
    type          event_type NOT NULL DEFAULT 'EVENT',
    description   TEXT,
    flyer_url     TEXT,                            -- immagine/volantino (URL, upload gestito da backend)
	flyer_status  flyer_status NOT NULL DEFAULT 'NONE',
	instagram_url TEXT,
	whatsapp_url  TEXT,
	likes_count   BIGINT NOT NULL DEFAULT 0,
	going_count   BIGINT NOT NULL DEFAULT 0,

    start_at      TIMESTAMPTZ NOT NULL,           -- inizio (data + ora, con timezone)
    end_at        TIMESTAMPTZ,                    -- fine (opzionale)

    is_free       BOOLEAN NOT NULL DEFAULT TRUE,
    price         NUMERIC(8,2),                   -- prezzo ingresso (NULL se gratis)
    currency      CHAR(3) NOT NULL DEFAULT 'EUR',

    -- posizione sulla mappa (obbligatoria)
    address       TEXT   NOT NULL,
    latitude      DOUBLE PRECISION NOT NULL,
    longitude     DOUBLE PRECISION NOT NULL,

    status        event_status NOT NULL DEFAULT 'PENDING', -- moderazione: pending -> published
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_event_time  CHECK (end_at IS NULL OR end_at > start_at),
    CONSTRAINT chk_event_price CHECK (is_free OR price IS NOT NULL)
);
CREATE INDEX idx_events_start      ON events(start_at);
CREATE INDEX idx_events_city       ON events(city_id);
CREATE INDEX idx_events_status_end ON events(status, end_at);
CREATE INDEX idx_events_organizer  ON events(organizer_id);
CREATE INDEX idx_events_venue      ON events(venue_id);
CREATE INDEX idx_events_coords     ON events(latitude, longitude);
-- indice utile per la vista "prossimi eventi pubblicati di una città"
CREATE INDEX idx_events_city_time  ON events(city_id, start_at) WHERE status = 'PUBLISHED';
CREATE INDEX idx_events_type       ON events(type);


-- ============================================================================
--  EVENT_DANCE_STYLES — molti-a-molti evento <-> tipi di ballo
-- ============================================================================
CREATE TABLE event_dance_styles (
    event_id       UUID   NOT NULL REFERENCES events(id)       ON DELETE CASCADE,
    dance_style_id BIGINT NOT NULL REFERENCES dance_styles(id) ON DELETE CASCADE,
    PRIMARY KEY (event_id, dance_style_id)
);
CREATE INDEX idx_eds_style ON event_dance_styles(dance_style_id);


-- ============================================================================
--  FAVORITES — un utente loggato salva un evento tra i preferiti
-- ============================================================================
CREATE TABLE favorites (
    user_id    UUID NOT NULL REFERENCES users(id)  ON DELETE CASCADE,
    event_id   UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, event_id)
);
CREATE INDEX idx_favorites_event ON favorites(event_id);


-- ============================================================================
--  EVENT_ATTENDANCE — "mi interessa" / "ci vado" (utile per contatori sociali)
-- ============================================================================
CREATE TABLE event_attendance (
    user_id    UUID NOT NULL REFERENCES users(id)  ON DELETE CASCADE,
    event_id   UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, event_id)
);
CREATE INDEX idx_attendance_event ON event_attendance(event_id);


-- ============================================================================
--  REVOKED_TOKENS — JWT invalidados por logout antes de su expiracion natural
-- ============================================================================
CREATE TABLE revoked_tokens (
    jti        UUID PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_revoked_tokens_expires_at ON revoked_tokens(expires_at);

-- ============================================================================
--  ORGANIZER_REQUEST 
-- ============================================================================
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


-- ============================================================================
--  VISTA di comodo: prossimi eventi pubblicati con i dati principali
-- ============================================================================
CREATE VIEW upcoming_events AS
SELECT
    e.id, e.title, e.slug, e.start_at, e.end_at,
    e.latitude, e.longitude, e.address, e.flyer_url,
    e.is_free, e.price, e.currency,
    COALESCE(o.name, 'Organizzatore eliminato') AS organizer_name,
    c.name  AS city_name,
    c.slug  AS city_slug
FROM events e
LEFT JOIN organizers o ON o.id = e.organizer_id
JOIN cities     c ON c.id = e.city_id
WHERE e.status = 'PUBLISHED'
    AND COALESCE(e.end_at, e.start_at + INTERVAL '4 hours') > now()
ORDER BY e.start_at;


-- ============================================================================
--  SEED — dati iniziali
-- ============================================================================
INSERT INTO cities (name, province, region, country, latitude, longitude, slug)
VALUES ('Milano', 'MI', 'Lombardia', 'IT', 45.4642, 9.1900, 'milano');

INSERT INTO dance_styles (name, slug) VALUES
    ('Salsa',              'salsa'),
    ('Salsa Cubana',       'salsa-cubana'),
    ('Salsa Portoricana',  'salsa-portoricana'),
    ('Bachata',            'bachata'),
    ('Bachata Sensual',    'bachata-sensual'),
    ('Kizomba',            'kizomba'),
    ('Merengue',           'merengue'),
    ('Cha Cha Cha',        'cha-cha-cha'),
    ('Rueda de Casino',    'rueda-de-casino'),
    ('Zouk',               'zouk'),
    ('Forró',              'forro');


-- ============================================================================
--  SEZIONE GEO OPZIONALE (PostGIS) — per query "eventi vicino a me" efficienti
--  Attiva PostGIS e usa questa colonna + indice GiST al posto del solo lat/lng.
-- ----------------------------------------------------------------------------
--  ALTER TABLE events
--      ADD COLUMN geom geography(Point, 4326)
--      GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography) STORED;
--  CREATE INDEX idx_events_geom ON events USING GIST (geom);
--
--  -- Esempio: eventi entro 5 km da un punto, ordinati per distanza
--  -- SELECT * FROM events
--  --  WHERE ST_DWithin(geom, ST_MakePoint(9.19, 45.46)::geography, 5000)
--  --  ORDER BY geom <-> ST_MakePoint(9.19, 45.46)::geography;
-- ============================================================================