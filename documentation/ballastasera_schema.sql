-- ============================================================================
--  BallaStasera — Schema database  (PostgreSQL 14+)
--  Piattaforma di eventi di ballo latino/social (salsa, bachata, kizomba, ...)
--
--  Idee guida:
--   * Utenti con login Google (OAuth) — ma la consultazione è pubblica.
--   * Chi pubblica ("organizer") può essere una persona, un locale, una
--     discoteca, una scuola o un'associazione.
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
CREATE TYPE user_role         AS ENUM ('USER', 'ORGANIZER', 'ADMIN');
CREATE TYPE organizer_type    AS ENUM ('PERSON', 'VENUE', 'CLUB', 'SCHOOL', 'ASSOCIATION');
CREATE TYPE event_status      AS ENUM ('DRAFT', 'PENDING', 'PUBLISHED', 'CANCELLED');
CREATE TYPE attendance_status AS ENUM ('INTERESTED', 'GOING');


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
    slug        TEXT       NOT NULL,              -- es. 'milano' (per URL /milano)
    is_active   BOOLEAN    NOT NULL DEFAULT TRUE, -- città già "aperta" sulla piattaforma
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (slug),
    UNIQUE (name, province)
);


-- ============================================================================
--  USERS — account (identità + autenticazione Google)
-- ============================================================================
CREATE TABLE users (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    google_id    TEXT UNIQUE,                     -- 'sub' restituito da Google OAuth
    email        TEXT        NOT NULL UNIQUE,
    display_name TEXT        NOT NULL,
    avatar_url   TEXT,
    role         user_role   NOT NULL DEFAULT 'USER',
    is_active    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- ============================================================================
--  ORGANIZERS — profilo pubblico di chi pubblica eventi
--  (una persona, un locale, una discoteca, una scuola, un'associazione)
--  Un utente può gestire più profili (es. gestisce 2 locali).
-- ============================================================================
CREATE TABLE organizers (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
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
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_organizers_user ON organizers(user_id);
CREATE INDEX idx_organizers_type ON organizers(type);


-- ============================================================================
--  VENUES — luoghi fisici riutilizzabili (locali, discoteche, scuole...)
--  Separato dagli eventi: lo stesso locale può ospitare tanti eventi,
--  anche di organizzatori diversi.
-- ============================================================================
CREATE TABLE venues (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name         TEXT NOT NULL,
    organizer_id UUID REFERENCES organizers(id) ON DELETE SET NULL, -- se il locale ha un profilo proprio
    city_id      BIGINT NOT NULL REFERENCES cities(id),
    address      TEXT NOT NULL,                   -- via e numero civico
    postal_code  TEXT,
    latitude     DOUBLE PRECISION NOT NULL,
    longitude    DOUBLE PRECISION NOT NULL,
    description  TEXT,
    created_by   UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_venues_city   ON venues(city_id);
CREATE INDEX idx_venues_coords ON venues(latitude, longitude);


-- ============================================================================
--  DANCE_STYLES — tipi di ballo (tabella di lookup, facile da estendere)
-- ============================================================================
CREATE TABLE dance_styles (
    id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,                    -- 'Salsa', 'Bachata', ...
    slug TEXT NOT NULL UNIQUE
);


-- ============================================================================
--  EVENT_SERIES — (opzionale) raggruppa eventi ricorrenti
--  Utile per i social settimanali: "ogni giovedì salsa al locale X".
--  L'app genera le singole occorrenze nella tabella `events` a partire da rrule.
-- ============================================================================
CREATE TABLE event_series (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organizer_id UUID NOT NULL REFERENCES organizers(id) ON DELETE CASCADE,
    title        TEXT NOT NULL,
    rrule        TEXT,          -- regola ricorrenza formato iCalendar, es. 'FREQ=WEEKLY;BYDAY=TH'
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- ============================================================================
--  EVENTS — la tabella centrale: una riga = una serata/occorrenza
--
--  Nota geo: le coordinate sono SEMPRE presenti sull'evento (denormalizzate).
--  Se c'è un venue, l'app le copia dal venue; altrimenti sono inserite a mano.
--  Così la query per la mappa legge una sola tabella indicizzata.
-- ============================================================================
CREATE TABLE events (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organizer_id UUID   NOT NULL REFERENCES organizers(id)  ON DELETE CASCADE,
    venue_id     UUID            REFERENCES venues(id)       ON DELETE SET NULL,
    series_id    UUID            REFERENCES event_series(id) ON DELETE SET NULL,
    city_id      BIGINT NOT NULL REFERENCES cities(id),

    title        TEXT   NOT NULL,
    slug         TEXT   NOT NULL UNIQUE,
    description  TEXT,
    flyer_url    TEXT,                            -- immagine/volantino (URL, upload gestito da backend)

    start_at     TIMESTAMPTZ NOT NULL,           -- inizio (data + ora, con timezone)
    end_at       TIMESTAMPTZ,                    -- fine (opzionale)

    is_free      BOOLEAN NOT NULL DEFAULT TRUE,
    price        NUMERIC(8,2),                   -- prezzo ingresso (NULL se gratis)
    currency     CHAR(3) NOT NULL DEFAULT 'EUR',

    -- posizione sulla mappa (obbligatoria)
    address      TEXT   NOT NULL,
    latitude     DOUBLE PRECISION NOT NULL,
    longitude    DOUBLE PRECISION NOT NULL,

    status       event_status NOT NULL DEFAULT 'PENDING', -- moderazione: pending -> published
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_event_time  CHECK (end_at IS NULL OR end_at > start_at),
    CONSTRAINT chk_event_price CHECK (is_free OR price IS NOT NULL)
);
CREATE INDEX idx_events_start     ON events(start_at);
CREATE INDEX idx_events_city      ON events(city_id);
CREATE INDEX idx_events_status    ON events(status);
CREATE INDEX idx_events_organizer ON events(organizer_id);
CREATE INDEX idx_events_venue     ON events(venue_id);
CREATE INDEX idx_events_coords    ON events(latitude, longitude);
-- indice utile per la vista "prossimi eventi pubblicati di una città"
CREATE INDEX idx_events_city_time ON events(city_id, start_at) WHERE status = 'PUBLISHED';


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
    status     attendance_status NOT NULL DEFAULT 'INTERESTED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, event_id)
);
CREATE INDEX idx_attendance_event ON event_attendance(event_id);


-- ============================================================================
--  VISTA di comodo: prossimi eventi pubblicati con i dati principali
-- ============================================================================
CREATE VIEW upcoming_events AS
SELECT
    e.id, e.title, e.slug, e.start_at, e.end_at,
    e.latitude, e.longitude, e.address, e.flyer_url,
    e.is_free, e.price, e.currency,
    o.name  AS organizer_name,
    c.name  AS city_name,
    c.slug  AS city_slug
FROM events e
JOIN organizers o ON o.id = e.organizer_id
JOIN cities     c ON c.id = e.city_id
WHERE e.status = 'PUBLISHED'
  AND e.start_at >= now()
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