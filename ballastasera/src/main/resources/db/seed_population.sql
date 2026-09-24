-- ============================================================================
-- BallaStasera - dataset locale esteso
--
-- Eseguire dopo seed_test_data.sql.
-- Il file e idempotente: gli identificativi sono fissi e gli inserimenti usano
-- ON CONFLICT DO NOTHING. Le date relative a now() mantengono utili i dati.
-- Compatibile con il database locale attuale: non usa events.type.
-- ============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- Citta aggiuntive
-- ---------------------------------------------------------------------------
INSERT INTO cities (name, province, region, country, latitude, longitude, slug)
VALUES
    ('Roma', 'RM', 'Lazio', 'IT', 41.9028, 12.4964, 'roma'),
    ('Torino', 'TO', 'Piemonte', 'IT', 45.0703, 7.6869, 'torino')
ON CONFLICT (slug) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Utenti fittizi
-- ---------------------------------------------------------------------------
INSERT INTO users (id, google_id, email, display_name, avatar_url, instagram,
                   show_profile_public, role)
VALUES
    ('77777777-7777-7777-7777-777777777777', 'seed-google-giulia',
     'giulia.seed@ballastasera.test', 'Giulia Balla', NULL, 'giulia.balla', true, 'USER'),
    ('88888888-8888-8888-8888-888888888888', 'seed-google-alessandro',
     'alessandro.seed@ballastasera.test', 'Alessandro Ritmo', NULL, 'ale.ritmo', true, 'USER'),
    ('99999999-9999-9999-9999-999999999999', 'seed-google-sofia',
     'sofia.seed@ballastasera.test', 'Sofia Latina', NULL, NULL, false, 'USER'),
    ('12121212-1212-1212-1212-121212121212', 'seed-google-matteo',
     'matteo.seed@ballastasera.test', 'Matteo Salsa', NULL, 'matteo.salsa', true, 'USER'),
    ('13131313-1313-1313-1313-131313131313', 'seed-google-elena',
     'elena.seed@ballastasera.test', 'Elena Bachata', NULL, 'elena.bachata', true, 'USER'),
    ('14141414-1414-1414-1414-141414141414', 'seed-google-davide',
     'davide.seed@ballastasera.test', 'Davide Kizomba', NULL, NULL, false, 'USER'),
    ('15151515-1515-1515-1515-151515151515', 'seed-google-chiara',
     'chiara.seed@ballastasera.test', 'Chiara Dance', NULL, 'chiara.dance', true, 'USER'),
    ('16161616-1616-1616-1616-161616161616', 'seed-google-lorenzo',
     'lorenzo.seed@ballastasera.test', 'Lorenzo Rueda', NULL, NULL, false, 'USER'),
    ('17171717-1717-1717-1717-171717171717', 'seed-google-admin',
     'admin.seed@ballastasera.test', 'Admin BallaStasera', NULL, NULL, false, 'ADMIN')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Organizzatori
-- ---------------------------------------------------------------------------
INSERT INTO organizers (id, user_id, name, slug, type, description, logo_url,
                        website, phone, contact_email, instagram, facebook,
                        is_verified, claimed)
VALUES
    ('a1000000-0000-0000-0000-000000000001',
     '77777777-7777-7777-7777-777777777777', 'Roma Salsa Collective',
     'roma-salsa-collective', 'CLUB',
     'Serate sociali e corsi di salsa nel centro di Roma.', NULL,
     'https://example.test/roma-salsa', '+39 06 1000001', 'roma@example.test',
     'romasalsacollective', NULL, true, true),
    ('a1000000-0000-0000-0000-000000000002',
     '88888888-8888-8888-8888-888888888888', 'Torino Bachata Lab',
     'torino-bachata-lab', 'SCHOOL',
     'Scuola con workshop di bachata sensual e zouk.', NULL,
     'https://example.test/torino-bachata', '+39 011 1000002', 'torino@example.test',
     'torinobachatalab', NULL, false, true),
    ('a1000000-0000-0000-0000-000000000003',
     '12121212-1212-1212-1212-121212121212', 'Navigli Dance Hub',
     'navigli-dance-hub', 'VENUE',
     'Spazio per danza, social e musica latina a Milano.', NULL,
     'https://example.test/navigli-dance', '+39 02 1000003', 'navigli@example.test',
     'naviglidancehub', NULL, true, true),
    ('a1000000-0000-0000-0000-000000000004', NULL,
     'Danza Latina Italia', 'danza-latina-italia', 'ASSOCIATION',
     'Associazione indipendente per eventi di ballo sociale.', NULL,
     NULL, NULL, 'associazione@example.test', NULL, NULL, false, false)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Luoghi
-- ---------------------------------------------------------------------------
INSERT INTO venues (id, name, organizer_id, city_id, address, postal_code,
                    latitude, longitude, description, created_by)
VALUES
    ('b1000000-0000-0000-0000-000000000001', 'Sala Trastevere',
     'a1000000-0000-0000-0000-000000000001',
     (SELECT id FROM cities WHERE slug = 'roma'), 'Via della Lungara 18', '00165',
     41.8912, 12.4688, 'Sala per serate latine e workshop.',
     '77777777-7777-7777-7777-777777777777'),
    ('b1000000-0000-0000-0000-000000000002', 'Club Testaccio',
     'a1000000-0000-0000-0000-000000000001',
     (SELECT id FROM cities WHERE slug = 'roma'), 'Via di Monte Testaccio 40', '00153',
     41.8805, 12.4767, 'Locale con pista principale e area esterna.',
     '77777777-7777-7777-7777-777777777777'),
    ('b1000000-0000-0000-0000-000000000003', 'PalaRuffini Dance Room',
     'a1000000-0000-0000-0000-000000000002',
     (SELECT id FROM cities WHERE slug = 'torino'), 'Viale Leonardo Bistolfi 19', '10134',
     45.0412, 7.6437, 'Ampia sala per lezioni e congressi di danza.',
     '88888888-8888-8888-8888-888888888888'),
    ('b1000000-0000-0000-0000-000000000004', 'San Salvario Social',
     'a1000000-0000-0000-0000-000000000002',
     (SELECT id FROM cities WHERE slug = 'torino'), 'Via Baretti 15', '10125',
     45.0557, 7.6816, 'Pista raccolta nel quartiere San Salvario.',
     '88888888-8888-8888-8888-888888888888'),
    ('b1000000-0000-0000-0000-000000000005', 'Navigli Loft',
     'a1000000-0000-0000-0000-000000000003',
     (SELECT id FROM cities WHERE slug = 'milano'), 'Via Savona 35', '20144',
     45.4508, 9.1613, 'Loft per corsi e social serali.',
     '12121212-1212-1212-1212-121212121212'),
    ('b1000000-0000-0000-0000-000000000006', 'Parco Sempione Dance Spot',
     'a1000000-0000-0000-0000-000000000004',
     (SELECT id FROM cities WHERE slug = 'milano'), 'Viale Emilio Alemagna 6', '20121',
     45.4721, 9.1750, 'Punto di incontro all aperto per rueda e salsa.',
     '12121212-1212-1212-1212-121212121212')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Serie ricorrenti
-- ---------------------------------------------------------------------------
INSERT INTO event_series (id, organizer_id, venue_id, city_id, title, rrule,
                          description, flyer_url, instagram_url, whatsapp_url,
                          is_free, price, currency, address, latitude, longitude,
                          start_time, end_time)
VALUES
    ('c1000000-0000-0000-0000-000000000001',
     'a1000000-0000-0000-0000-000000000003',
     'b1000000-0000-0000-0000-000000000005',
     (SELECT id FROM cities WHERE slug = 'milano'), 'Giovedi Salsa ai Navigli',
     'FREQ=WEEKLY;BYDAY=TH', 'Serata settimanale di salsa e bachata.', NULL,
     'https://instagram.com/naviglidancehub', NULL, false, 12.00, 'EUR',
     'Via Savona 35, Milano', 45.4508, 9.1613, '21:00', '01:00'),
    ('c1000000-0000-0000-0000-000000000002',
     'a1000000-0000-0000-0000-000000000002',
     'b1000000-0000-0000-0000-000000000004',
     (SELECT id FROM cities WHERE slug = 'torino'), 'Domenica Bachata Lab',
     'FREQ=WEEKLY;BYDAY=SU', 'Workshop e pratica guidata di bachata.', NULL,
     'https://instagram.com/torinobachatalab', NULL, true, NULL, 'EUR',
     'Via Baretti 15, Torino', 45.0557, 7.6816, '18:00', '22:00')
ON CONFLICT DO NOTHING;

INSERT INTO event_series_dance_styles (series_id, dance_style_id)
SELECT 'c1000000-0000-0000-0000-000000000001', id
FROM dance_styles WHERE slug IN ('salsa', 'bachata')
ON CONFLICT DO NOTHING;

INSERT INTO event_series_dance_styles (series_id, dance_style_id)
SELECT 'c1000000-0000-0000-0000-000000000002', id
FROM dance_styles WHERE slug IN ('bachata-sensual', 'zouk')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Eventi: 15 righe, con quattro stati e tre citta
-- ---------------------------------------------------------------------------
INSERT INTO events (
    id, organizer_id, venue_id, series_id, city_id, title, slug, description,
    flyer_url, instagram_url, whatsapp_url, start_at, end_at, is_free, price,
    currency, address, latitude, longitude, status
)
VALUES
    ('d1000000-0000-0000-0000-000000000001',
     'a1000000-0000-0000-0000-000000000003',
     'b1000000-0000-0000-0000-000000000005',
     'c1000000-0000-0000-0000-000000000001',
     (SELECT id FROM cities WHERE slug = 'milano'), 'Giovedi Salsa ai Navigli',
     'giovedi-salsa-ai-navigli-seed', 'Salsa e bachata con pista libera.', NULL,
     'https://instagram.com/naviglidancehub', NULL, now() + INTERVAL '1 day',
     now() + INTERVAL '1 day 5 hours', false, 12.00, 'EUR',
     'Via Savona 35, Milano', 45.4508, 9.1613, 'PUBLISHED'),
    ('d1000000-0000-0000-0000-000000000002',
     'a1000000-0000-0000-0000-000000000003',
     'b1000000-0000-0000-0000-000000000006', NULL,
     (SELECT id FROM cities WHERE slug = 'milano'), 'Rueda al Parco',
     'rueda-al-parco-seed', 'Rueda di casino all aperto, ingresso libero.', NULL,
     NULL, NULL, now() + INTERVAL '3 days', now() + INTERVAL '3 days 3 hours',
     true, NULL, 'EUR', 'Viale Emilio Alemagna 6, Milano', 45.4721, 9.1750, 'PUBLISHED'),
    ('d1000000-0000-0000-0000-000000000003',
     'a1000000-0000-0000-0000-000000000004', NULL, NULL,
     (SELECT id FROM cities WHERE slug = 'milano'), 'Milano Latin Sunset',
     'milano-latin-sunset-seed', 'Social di salsa, merengue e cha cha cha.', NULL,
     NULL, NULL, now() + INTERVAL '6 days', now() + INTERVAL '6 days 4 hours',
     true, NULL, 'EUR', 'Darsena, Milano', 45.4527, 9.1740, 'PUBLISHED'),
    ('d1000000-0000-0000-0000-000000000004',
     'a1000000-0000-0000-0000-000000000003',
     'b1000000-0000-0000-0000-000000000005', NULL,
     (SELECT id FROM cities WHERE slug = 'milano'), 'Bachata Friday Loft',
     'bachata-friday-loft-seed', 'Serata bachata sensual per tutti i livelli.', NULL,
     'https://instagram.com/naviglidancehub', NULL, now() + INTERVAL '10 days',
     now() + INTERVAL '10 days 5 hours', false, 15.00, 'EUR',
     'Via Savona 35, Milano', 45.4508, 9.1613, 'PUBLISHED'),
    ('d1000000-0000-0000-0000-000000000005',
     'a1000000-0000-0000-0000-000000000003',
     'b1000000-0000-0000-0000-000000000005',
     'c1000000-0000-0000-0000-000000000001',
     (SELECT id FROM cities WHERE slug = 'milano'), 'Milano Dance Marathon',
     'milano-dance-marathon-seed', 'Una giornata intera di danza e social.', NULL,
     NULL, NULL, now() + INTERVAL '25 days', now() + INTERVAL '25 days 10 hours',
     false, 25.00, 'EUR', 'Via Savona 35, Milano', 45.4508, 9.1613, 'PUBLISHED'),
    ('d1000000-0000-0000-0000-000000000006',
     'a1000000-0000-0000-0000-000000000001',
     'b1000000-0000-0000-0000-000000000001', NULL,
     (SELECT id FROM cities WHERE slug = 'roma'), 'Salsa Roma Social',
     'salsa-roma-social-seed', 'Serata sociale con le migliori scuole romane.', NULL,
     'https://instagram.com/romasalsacollective', NULL, now() + INTERVAL '2 days',
     now() + INTERVAL '2 days 5 hours', false, 10.00, 'EUR',
     'Via della Lungara 18, Roma', 41.8912, 12.4688, 'PUBLISHED'),
    ('d1000000-0000-0000-0000-000000000007',
     'a1000000-0000-0000-0000-000000000001',
     'b1000000-0000-0000-0000-000000000002', NULL,
     (SELECT id FROM cities WHERE slug = 'roma'), 'Bachata sotto le stelle',
     'bachata-sotto-le-stelle-seed', 'Bachata e kizomba in una location estiva.', NULL,
     NULL, NULL, now() + INTERVAL '7 days', now() + INTERVAL '7 days 4 hours',
     true, NULL, 'EUR', 'Via di Monte Testaccio 40, Roma', 41.8805, 12.4767, 'PUBLISHED'),
    ('d1000000-0000-0000-0000-000000000008',
     'a1000000-0000-0000-0000-000000000001',
     'b1000000-0000-0000-0000-000000000001', NULL,
     (SELECT id FROM cities WHERE slug = 'roma'), 'Rueda Workshop Roma',
     'rueda-workshop-roma-seed', 'Workshop intensivo di rueda de casino.', NULL,
     NULL, NULL, now() + INTERVAL '14 days', now() + INTERVAL '14 days 6 hours',
     false, 18.00, 'EUR', 'Via della Lungara 18, Roma', 41.8912, 12.4688, 'PUBLISHED'),
    ('d1000000-0000-0000-0000-000000000009',
     'a1000000-0000-0000-0000-000000000004', NULL, NULL,
     (SELECT id FROM cities WHERE slug = 'roma'), 'Roma Latin Picnic',
     'roma-latin-picnic-seed', 'Evento gratuito con musica e ballo al tramonto.', NULL,
     NULL, NULL, now() + INTERVAL '22 days', now() + INTERVAL '22 days 4 hours',
     true, NULL, 'EUR', 'Villa Borghese, Roma', 41.9142, 12.4922, 'PUBLISHED'),
    ('d1000000-0000-0000-0000-000000000010',
     'a1000000-0000-0000-0000-000000000002',
     'b1000000-0000-0000-0000-000000000003', NULL,
     (SELECT id FROM cities WHERE slug = 'torino'), 'Torino Bachata Lab',
     'torino-bachata-lab-seed', 'Lezione e social di bachata sensual.', NULL,
     'https://instagram.com/torinobachatalab', NULL, now() + INTERVAL '3 days',
     now() + INTERVAL '3 days 4 hours', true, NULL, 'EUR',
     'Viale Leonardo Bistolfi 19, Torino', 45.0412, 7.6437, 'PUBLISHED'),
    ('d1000000-0000-0000-0000-000000000011',
     'a1000000-0000-0000-0000-000000000002',
     'b1000000-0000-0000-0000-000000000004',
     'c1000000-0000-0000-0000-000000000002',
     (SELECT id FROM cities WHERE slug = 'torino'), 'Domenica Bachata Lab',
     'domenica-bachata-lab-seed', 'Pratica guidata e pista libera.', NULL,
     'https://instagram.com/torinobachatalab', NULL, now() + INTERVAL '8 days',
     now() + INTERVAL '8 days 4 hours', true, NULL, 'EUR',
     'Via Baretti 15, Torino', 45.0557, 7.6816, 'PUBLISHED'),
    ('d1000000-0000-0000-0000-000000000012',
     'a1000000-0000-0000-0000-000000000002',
     'b1000000-0000-0000-0000-000000000003', NULL,
     (SELECT id FROM cities WHERE slug = 'torino'), 'Kizomba e Zouk Night',
     'kizomba-zouk-night-seed', 'Una notte dedicata ai balli afro-latini.', NULL,
     NULL, NULL, now() + INTERVAL '16 days', now() + INTERVAL '16 days 5 hours',
     false, 14.00, 'EUR', 'Viale Leonardo Bistolfi 19, Torino', 45.0412, 7.6437, 'PUBLISHED'),
    ('d1000000-0000-0000-0000-000000000013',
     'a1000000-0000-0000-0000-000000000001',
     'b1000000-0000-0000-0000-000000000001', NULL,
     (SELECT id FROM cities WHERE slug = 'roma'), 'Roma Evento in Revisione',
     'roma-evento-in-revisione-seed', 'Evento non ancora approvato.', NULL,
     NULL, NULL, now() + INTERVAL '5 days', now() + INTERVAL '5 days 4 hours',
     true, NULL, 'EUR', 'Via della Lungara 18, Roma', 41.8912, 12.4688, 'PENDING'),
    ('d1000000-0000-0000-0000-000000000014',
     'a1000000-0000-0000-0000-000000000002',
     'b1000000-0000-0000-0000-000000000004', NULL,
     (SELECT id FROM cities WHERE slug = 'torino'), 'Torino Evento Annullato',
     'torino-evento-annullato-seed', 'Evento mantenuto per verificare lo stato.', NULL,
     NULL, NULL, now() + INTERVAL '12 days', now() + INTERVAL '12 days 4 hours',
     false, 9.00, 'EUR', 'Via Baretti 15, Torino', 45.0557, 7.6816, 'CANCELLED'),
    ('d1000000-0000-0000-0000-000000000015',
     'a1000000-0000-0000-0000-000000000004', NULL, NULL,
     (SELECT id FROM cities WHERE slug = 'milano'), 'Milano Evento in Bozza',
     'milano-evento-in-bozza-seed', 'Bozza non visibile nel catalogo pubblico.', NULL,
     NULL, NULL, now() + INTERVAL '30 days', now() + INTERVAL '30 days 4 hours',
     true, NULL, 'EUR', 'Piazza del Duomo, Milano', 45.4642, 9.1900, 'DRAFT')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Stili per evento
-- ---------------------------------------------------------------------------
INSERT INTO event_dance_styles (event_id, dance_style_id)
SELECT event_id, style_id
FROM (VALUES
    ('d1000000-0000-0000-0000-000000000001'::uuid, 'salsa'),
    ('d1000000-0000-0000-0000-000000000001'::uuid, 'bachata'),
    ('d1000000-0000-0000-0000-000000000002'::uuid, 'rueda-de-casino'),
    ('d1000000-0000-0000-0000-000000000002'::uuid, 'salsa-cubana'),
    ('d1000000-0000-0000-0000-000000000003'::uuid, 'salsa'),
    ('d1000000-0000-0000-0000-000000000003'::uuid, 'merengue'),
    ('d1000000-0000-0000-0000-000000000004'::uuid, 'bachata-sensual'),
    ('d1000000-0000-0000-0000-000000000005'::uuid, 'salsa'),
    ('d1000000-0000-0000-0000-000000000006'::uuid, 'salsa'),
    ('d1000000-0000-0000-0000-000000000007'::uuid, 'bachata-sensual'),
    ('d1000000-0000-0000-0000-000000000007'::uuid, 'kizomba'),
    ('d1000000-0000-0000-0000-000000000008'::uuid, 'rueda-de-casino'),
    ('d1000000-0000-0000-0000-000000000009'::uuid, 'merengue'),
    ('d1000000-0000-0000-0000-000000000010'::uuid, 'bachata-sensual'),
    ('d1000000-0000-0000-0000-000000000011'::uuid, 'bachata-sensual'),
    ('d1000000-0000-0000-0000-000000000012'::uuid, 'kizomba'),
    ('d1000000-0000-0000-0000-000000000012'::uuid, 'zouk'),
    ('d1000000-0000-0000-0000-000000000013'::uuid, 'salsa'),
    ('d1000000-0000-0000-0000-000000000014'::uuid, 'bachata'),
    ('d1000000-0000-0000-0000-000000000015'::uuid, 'salsa')
) AS requested(event_id, style_slug)
JOIN dance_styles ds ON ds.slug = requested.style_slug
CROSS JOIN LATERAL (SELECT ds.id AS style_id) styles
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- Presenze e preferiti
-- ---------------------------------------------------------------------------
INSERT INTO event_attendance (user_id, event_id, status)
VALUES
    ('77777777-7777-7777-7777-777777777777', 'd1000000-0000-0000-0000-000000000001', 'GOING'),
    ('88888888-8888-8888-8888-888888888888', 'd1000000-0000-0000-0000-000000000001', 'INTERESTED'),
    ('99999999-9999-9999-9999-999999999999', 'd1000000-0000-0000-0000-000000000006', 'GOING'),
    ('12121212-1212-1212-1212-121212121212', 'd1000000-0000-0000-0000-000000000007', 'GOING'),
    ('13131313-1313-1313-1313-131313131313', 'd1000000-0000-0000-0000-000000000010', 'INTERESTED'),
    ('14141414-1414-1414-1414-141414141414', 'd1000000-0000-0000-0000-000000000012', 'GOING'),
    ('15151515-1515-1515-1515-151515151515', 'd1000000-0000-0000-0000-000000000011', 'INTERESTED'),
    ('16161616-1616-1616-1616-161616161616', 'd1000000-0000-0000-0000-000000000003', 'GOING')
ON CONFLICT DO NOTHING;

INSERT INTO favorites (user_id, event_id)
VALUES
    ('77777777-7777-7777-7777-777777777777', 'd1000000-0000-0000-0000-000000000006'),
    ('88888888-8888-8888-8888-888888888888', 'd1000000-0000-0000-0000-000000000010'),
    ('13131313-1313-1313-1313-131313131313', 'd1000000-0000-0000-0000-000000000004'),
    ('15151515-1515-1515-1515-151515151515', 'd1000000-0000-0000-0000-000000000012')
ON CONFLICT DO NOTHING;

COMMIT;
