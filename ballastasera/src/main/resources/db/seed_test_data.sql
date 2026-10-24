-- ============================================================================
--  BallaStasera — datos ficticios para probar la API en Postman
--  Asume que ya corriste el schema base (cities Milano + dance_styles seed).
--  Los horarios de los eventos usan now() +/- INTERVAL, asi que siguen siendo
--  validos sin importar cuando ejecutes este script.
--
--  IDs fijos a proposito (no gen_random_uuid()) para poder pegarlos directo
--  en las requests de Postman sin tener que hacer un SELECT antes.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Usuario organizador (dueño del locale) + su perfil de Organizer
-- ----------------------------------------------------------------------------
INSERT INTO users (id, google_id, email, display_name, avatar_url, instagram, show_profile_public, role)
VALUES (
           '22222222-2222-2222-2222-222222222222', 'seed-google-organizer',
           'organizer.seed@ballastasera.test', 'Marco (Tropical Milano)', NULL,
           'tropicalmilano_marco', false, 'ORGANIZER'
       );

INSERT INTO organizers (id, user_id, name, slug, type, description, logo_url, website, phone, contact_email, instagram, facebook, is_verified)
VALUES (
           '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222',
           'Tropical Milano', 'tropical-milano', 'VENUE',
           'Locale storico di salsa e bachata a Milano.', NULL,
           'https://tropicalmilano.it', '+39 02 1234567', 'info@tropicalmilano.it',
           'tropicalmilano', 'tropicalmilano', true
       );

-- ----------------------------------------------------------------------------
-- Venue reutilizable
-- ----------------------------------------------------------------------------
INSERT INTO venues (id, name, type, organizer_id, city_id, address, latitude, longitude, description, created_by)
VALUES (
           '33333333-3333-3333-3333-333333333333', 'Sala Havana', 'CLUB',
           '11111111-1111-1111-1111-111111111111',
           (SELECT id FROM cities WHERE slug = 'milano'),
           'Via Tortona 12', 45.4522, 9.1620,
           'Sala da ballo nel cuore dei Navigli.', '22222222-2222-2222-2222-222222222222'
       );

-- ----------------------------------------------------------------------------
-- Usuarios "asistentes" (para testear attendance + attendees + privacidad)
--   Ana    -> opt-in (aparece en /attendees con su Instagram)
--   Marco2 -> opt-in
--   Lucia  -> NO opt-in (cuenta en goingCount pero NO aparece en /attendees)
-- ----------------------------------------------------------------------------
INSERT INTO users (id, google_id, email, display_name, avatar_url, instagram, show_profile_public, role) VALUES
    ('44444444-4444-4444-4444-444444444444', 'seed-google-ana',    'ana.seed@ballastasera.test',    'Ana',    NULL, 'ana.baila',     true,  'USER'),
    ('55555555-5555-5555-5555-555555555555', 'seed-google-marco2', 'marco2.seed@ballastasera.test', 'Marco',  NULL, 'marco_salsero', true,  'USER'),
    ('66666666-6666-6666-6666-666666666666', 'seed-google-lucia',  'lucia.seed@ballastasera.test',  'Lucia',  NULL, NULL,            false, 'USER');

-- ----------------------------------------------------------------------------
-- Eventos: cubren los casos que la API tiene que filtrar bien
--   aaaa = EN VIVO ahora            -> debe salir con liveNow=true
--   bbbb = empieza mas tarde hoy    -> debe salir con liveNow=false
--   cccc = dentro de unos dias      -> debe salir con liveNow=false
--   dddd = ya termino (2 dias atras)-> NO debe salir nunca en /rest/events
--   eeee = horario "en vivo" pero PENDING (sin publicar) -> NO debe salir
-- ----------------------------------------------------------------------------
INSERT INTO events (
    id, organizer_id, venue_id, city_id, title, slug, description,
    flyer_url, instagram_url, whatsapp_url,
    start_at, end_at, is_free, price, currency,
    address, latitude, longitude, status
) VALUES
      (
          'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111',
          '33333333-3333-3333-3333-333333333333', (SELECT id FROM cities WHERE slug = 'milano'),
          'Salsa al Tropical', 'salsa-al-tropical-seed', 'Serata sociale di salsa e bachata.',
          NULL, NULL, NULL,
          now() - INTERVAL '1 hour', now() + INTERVAL '2 hours', false, 10.00, 'EUR',
          'Via Tortona 12, Milano', 45.4522, 9.1620, 'PUBLISHED'
      ),
      (
          'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '11111111-1111-1111-1111-111111111111',
          NULL, (SELECT id FROM cities WHERE slug = 'milano'),
          'Bachata Sensual Night', 'bachata-sensual-night-seed', 'Workshop + social.',
          NULL, 'https://instagram.com/bachatanightmilano', NULL,
          now() + INTERVAL '3 hours', now() + INTERVAL '7 hours', true, NULL, 'EUR',
          'Corso Como 10, Milano', 45.4820, 9.1880, 'PUBLISHED'
      ),
      (
          'cccccccc-cccc-cccc-cccc-cccccccccccc', '11111111-1111-1111-1111-111111111111',
          NULL, (SELECT id FROM cities WHERE slug = 'milano'),
          'Rueda de Casino en Piazza', 'rueda-de-casino-piazza-seed', 'Rueda al aire libre.',
          NULL, NULL, NULL,
          now() + INTERVAL '3 days', now() + INTERVAL '3 days' + INTERVAL '3 hours', true, NULL, 'EUR',
          'Piazza Gae Aulenti, Milano', 45.4842, 9.1900, 'PUBLISHED'
      ),
      (
          'dddddddd-dddd-dddd-dddd-dddddddddddd', '11111111-1111-1111-1111-111111111111',
          NULL, (SELECT id FROM cities WHERE slug = 'milano'),
          'Evento ya terminado', 'evento-ya-terminado-seed', 'Deberia estar filtrado (esta en el pasado).',
          NULL, NULL, NULL,
          now() - INTERVAL '2 days', now() - INTERVAL '2 days' + INTERVAL '4 hours', true, NULL, 'EUR',
          'Via Tortona 12, Milano', 45.4522, 9.1620, 'PUBLISHED'
      ),
      (
          'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', '11111111-1111-1111-1111-111111111111',
          NULL, (SELECT id FROM cities WHERE slug = 'milano'),
          'Evento en revision', 'evento-en-revision-seed', 'Deberia estar filtrado (no esta PUBLISHED).',
          NULL, NULL, NULL,
          now() - INTERVAL '30 minutes', now() + INTERVAL '2 hours', true, NULL, 'EUR',
          'Via Tortona 12, Milano', 45.4522, 9.1620, 'PENDING'
      );

-- ----------------------------------------------------------------------------
-- Estilos de baile por evento
-- ----------------------------------------------------------------------------
INSERT INTO event_dance_styles (event_id, dance_style_id)
SELECT 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', id FROM dance_styles WHERE slug IN ('salsa', 'bachata');

INSERT INTO event_dance_styles (event_id, dance_style_id)
SELECT 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', id FROM dance_styles WHERE slug IN ('bachata-sensual');

INSERT INTO event_dance_styles (event_id, dance_style_id)
SELECT 'cccccccc-cccc-cccc-cccc-cccccccccccc', id FROM dance_styles WHERE slug IN ('rueda-de-casino', 'salsa-cubana');

-- ----------------------------------------------------------------------------
-- Asistencia sobre el evento EN VIVO (aaaa):
--   Ana y Marco2 -> GOING + opt-in      => deben aparecer en /attendees
--   Lucia        -> GOING sin opt-in    => cuenta en goingCount, NO aparece
--   Ana          -> INTERESTED en bbbb  => prueba que INTERESTED no cuenta como "going"
-- ----------------------------------------------------------------------------
INSERT INTO event_attendance (user_id, event_id) VALUES
    ('44444444-4444-4444-4444-444444444444', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
    ('55555555-5555-5555-5555-555555555555', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
    ('66666666-6666-6666-6666-666666666666', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'),
    ('44444444-4444-4444-4444-444444444444', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb');

-- ----------------------------------------------------------------------------
-- Favorito de ejemplo (no hay endpoint todavia, pero deja el dato listo)
-- ----------------------------------------------------------------------------
INSERT INTO favorites (user_id, event_id) VALUES
    ('44444444-4444-4444-4444-444444444444', 'cccccccc-cccc-cccc-cccc-cccccccccccc');


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