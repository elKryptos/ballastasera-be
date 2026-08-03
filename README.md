# BallaStasera — Database (Postgres + PostGIS)

Setup in Docker. Non serve installare Postgres sul tuo PC: pensa a tutto il container.

## Prerequisito
Solo **Docker Desktop** installato e avviato (Windows/Mac/Linux).
Verifica: `docker --version` e `docker compose version`.

## File
- `docker-compose.yml` — definisce il container Postgres+PostGIS
- `ballastasera_schema.sql` — lo schema (tabelle, relazioni, dati iniziali)
- `02_postgis.sql` — attiva il geospaziale per la mappa (opzionale)
- `.env.example` — credenziali di esempio

## Avvio (3 passi)

```bash
# 1. crea il tuo .env con la password
cp .env.example .env      # poi apri .env e cambia la password

# 2. avvia il database (la prima volta scarica l'immagine, ~1 min)
docker compose -p ballastasera up -d
#Il flag -p ballastasera fissa il project name (evita ambiguità/conflitti col progetto announce, come dicevamo prima). Alla prima esecuzione #scaricherà l'immagine postgis/postgis:16-3.4 (~1 minuto).
docker compose up -d

#lista tutto che c'è
docker ps -a
#elimina tutto relativo a ballastasera-db rete, image, dati 
docker compose -p ballastasera down -v

# 3. guarda i log: devi vedere lo schema caricarsi senza errori
docker compose logs -f db
```

Quando nei log compare `database system is ready to accept connections`, è pronto.

## Verifica che lo schema sia partito

```bash
# entra nella console psql dentro il container
docker compose exec db psql -U ballastasera -d ballastasera

# dentro psql:
\dt                         -- elenco tabelle (devono essere 10)
SELECT * FROM cities;       -- deve mostrare Milano
SELECT * FROM dance_styles; -- deve mostrare salsa, bachata, ...
\q                          -- esci
```

## Connessione dall'app (Rust)

La stringa di connessione dal tuo host è:

```
postgres://ballastasera:LA_TUA_PASSWORD@localhost:5432/ballastasera
```

## Comandi utili

```bash
docker compose stop      # ferma (i dati restano)
docker compose start     # riavvia
docker compose down      # rimuove il container (i dati restano nel volume)
docker compose down -v   # rimuove ANCHE i dati (reset totale)
```

## IMPORTANTE — quando modifichi lo schema

Gli script in `docker-entrypoint-initdb.d` girano **solo al primo avvio**, cioè
quando il volume dati è vuoto. Se cambi `ballastasera_schema.sql` e vuoi
ricaricarlo da zero devi azzerare il volume:

```bash
docker compose down -v && docker compose up -d
```

(In produzione, invece, le modifiche allo schema si fanno con delle *migration*,
non ricaricando tutto — ma per lo sviluppo locale questo va benissimo.)
