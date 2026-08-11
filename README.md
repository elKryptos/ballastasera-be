# BallaStasera - Database (Postgres + PostGIS)

Database local de BallaStasera. Solo necesitas Docker Desktop instalado y
ejecutandose.

## Archivos

- `infra/docker-compose.yml`: define PostgreSQL con PostGIS.
- `docs/ballastasera_schema.sql`: tablas, relaciones y datos iniciales.
- `docs/02_postgis.sql`: activa PostGIS y crea el indice geografico.
- `ballastasera/.env.example`: variables de ejemplo para Spring Boot y Docker.

## Configuracion inicial

```powershell
Copy-Item ballastasera/.env.example ballastasera/.env
```

Edita `ballastasera/.env` y cambia `DB_PASSWORD` si lo necesitas. El archivo
`.env` esta ignorado por Git.

## Arranque

Desde la raiz del repositorio:

```powershell
docker compose --env-file ballastasera/.env -p ballastasera -f infra/docker-compose.yml up -d --wait
```

Los scripts SQL se ejecutan automaticamente solo cuando el volumen esta vacio.

## Verificacion

```powershell
docker compose --env-file ballastasera/.env -p ballastasera -f infra/docker-compose.yml ps
docker compose --env-file ballastasera/.env -p ballastasera -f infra/docker-compose.yml logs postgres
docker compose --env-file ballastasera/.env -p ballastasera -f infra/docker-compose.yml exec postgres psql -U ballastasera -d ballastasera
```

Dentro de `psql`:

```sql
\dt
SELECT * FROM cities;
SELECT * FROM dance_styles;
SELECT extname FROM pg_extension WHERE extname = 'postgis';
\q
```

## Reinicio completo

`down -v` elimina tambien los datos locales porque borra el volumen.

```powershell
docker compose --env-file ballastasera/.env -p ballastasera -f infra/docker-compose.yml down -v
docker compose --env-file ballastasera/.env -p ballastasera -f infra/docker-compose.yml up -d --wait
```

## Conexion desde Spring Boot

Spring Boot usa la misma configuracion de `ballastasera/.env`:

```text
jdbc:postgresql://localhost:5432/ballastasera
```
