-- ============================================================================
--  Attiva il geospaziale per le query "eventi vicino a me" (mappa Leaflet).
--  Caricato dopo lo schema. Auto-contenuto: crea l'estensione se manca.
--  Se per ora NON vuoi PostGIS, togli la riga 02_postgis.sql da docker-compose.yml.
-- ============================================================================
CREATE EXTENSION IF NOT EXISTS postgis;

-- Colonna geografica calcolata automaticamente da longitude/latitude.
ALTER TABLE events
    ADD COLUMN geom geography(Point, 4326)
    GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography) STORED;

-- Indice spaziale: rende veloci le ricerche per distanza.
CREATE INDEX idx_events_geom ON events USING GIST (geom);

-- Esempio di query (eventi entro 5 km da un punto, ordinati per distanza):
--   SELECT id, title,
--          ST_Distance(geom, ST_MakePoint(9.19, 45.46)::geography) AS metri
--   FROM events
--   WHERE status = 'published'
--     AND ST_DWithin(geom, ST_MakePoint(9.19, 45.46)::geography, 5000)
--   ORDER BY metri;
