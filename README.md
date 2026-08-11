# BallaStasera - Database (Postgres + PostGIS)

Local BallaStasera database. You only need Docker Desktop installed and
running.

## Files

- `infra/docker-compose.yml`: defines PostgreSQL with PostGIS.
- `docs/ballastasera_schema.sql`: tables, relationships and seed data.
- `docs/02_postgis.sql`: enables PostGIS and creates the geographic index.
- `ballastasera/.env.example`: example variables for Spring Boot and Docker.

## Initial setup

```powershell
Copy-Item ballastasera/.env.example ballastasera/.env
```

Edit `ballastasera/.env` and change `DB_PASSWORD` if needed. The `.env` file
is ignored by Git.

## Startup

Run this command from the repository root:

```powershell
docker compose --env-file ballastasera/.env -p ballastasera -f infra/docker-compose.yml up -d --wait
```

The SQL scripts run automatically only when the volume is empty.

## Verification

```powershell
docker compose --env-file ballastasera/.env -p ballastasera -f infra/docker-compose.yml ps
docker compose --env-file ballastasera/.env -p ballastasera -f infra/docker-compose.yml logs postgres
docker compose --env-file ballastasera/.env -p ballastasera -f infra/docker-compose.yml exec postgres psql -U ballastasera -d ballastasera
```

Inside `psql`:

```sql
\dt
SELECT * FROM cities;
SELECT * FROM dance_styles;
SELECT extname FROM pg_extension WHERE extname = 'postgis';
\q
```

## Full reset

`down -v` also removes local data because it deletes the volume.

```powershell
docker compose --env-file ballastasera/.env -p ballastasera -f infra/docker-compose.yml down -v
docker compose --env-file ballastasera/.env -p ballastasera -f infra/docker-compose.yml up -d --wait
```

## Spring Boot connection

Spring Boot uses the same configuration from `ballastasera/.env`:

```text
jdbc:postgresql://localhost:5432/ballastasera
```
