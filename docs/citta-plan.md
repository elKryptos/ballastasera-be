# Ciudad del mapa, ciudad de residencia y país de origen — plan

Fecha: 17/11/2026 · Estado: **plan escrito, sin código**. Se empieza **después** de cerrar y desplegar el
perfil de usuario (`user-profile-plan.md`), en una rama propia.

Objetivo:
1. **El mapa abre donde el usuario lo dejó**: en la última ciudad que eligió en el filtro «Città». Con
   login, en cualquier dispositivo; sin login, en ese navegador; la primera vez, en Milano.
2. **El perfil guarda la ciudad donde vive y su país de origen.** La ciudad es solo un dato (no mueve el
   mapa) y sirve para saber dónde hay demanda antes de abrir una ciudad nueva. El país de origen se
   muestra en el perfil.
3. **Dejar preparada la expansión**: abrir una ciudad nueva es activarla en la base, sin tocar código.

Convención de estado (la misma que `ROADMAP.md`):
- `[ ]` no empezado
- `[~]` en progreso (dejar nota de qué falta)
- `[x]` hecho y probado (tests + Postman)

---

## 1. Decisiones cerradas

| # | Tema | Decisión |
|---|---|---|
| C1 | Qué decide el mapa | La **última ciudad elegida en el filtro «Città»** del mapa, no la residencia. La residencia es solo un dato |
| C2 | Dónde se guarda la ciudad del mapa | Con login: en el backend (`users.map_city_id`), así sigue al usuario en cualquier dispositivo. Sin login: en `localStorage` |
| C3 | Primera visita | Milano (hoy es donde hay serate). Más adelante: la ciudad activa más cercana si el usuario da su ubicación |
| C4 | Endpoint de la ciudad del mapa | Propio: `PUT /rest/users/me/map-city`. **No** en `PATCH /auth/me`, que reemplaza el perfil entero (P5 del perfil): no se puede mandar el formulario cada vez que alguien toca una ciudad del mapa |
| C5 | Origen | **País de origen**, ISO 3166-1 alpha-2 (`CO`, `VE`, `IT`…). Opcional. Público en el perfil |
| C6 | Ciudad de residencia | Opcional, cualquier comune (no solo las ciudades activas). Se elige con Photon (`GeocodingService`), no texto libre, para que los conteos salgan limpios («torino», «Turín» → Torino). **Privada por defecto**: solo para el usuario y para las estadísticas del admin. Desde el plan social (22/11), el usuario puede activar «Mostra la città nel profilo» y entonces se ve solo el nombre de la ciudad. La ciudad y el país de origen se hacen en la **fase 2 de `social-plan.md`**; aquí queda la ciudad del mapa |
| C7 | Expansión | `MAP_BOUNDS` (hoy Lombardía escrita a mano en `map.ts`) pasa a calcularse de las ciudades activas. Abrir una ciudad = `INSERT`/`is_active = true` en `cities` |

## 2. Lo que existe y se reutiliza

- **`cities`** ya tiene `name`, `province`, `region`, `country`, centro `latitude/longitude`, `slug` e
  `is_active`. Hoy solo está Milano (seed de `ballastasera_schema.sql`).
- **Filtro «Città» del mapa** (`map.html`, `goToCity` en `map.ts`) y `nearestCity` (`viewCity`): ya
  existen; hoy la elección solo dura la pestaña (`MapViewStateService`).
- **`GeocodingService`** (FE) usa Photon (`photon.komoot.io`), sin clave.
- **Perfil**: `PATCH /auth/me` con el formulario completo, `UserProfileDto` para el perfil público, la
  hoja «Chi ci va» y «Il tuo account» (rama `feature/user-profile`).

## 3. Esquema SQL

```sql
ALTER TABLE users
    ADD COLUMN origin_country CHAR(2),                                      -- ISO 3166-1 alpha-2
    ADD COLUMN home_city      TEXT,                                         -- 'Torino'
    ADD COLUMN home_region    TEXT,                                         -- 'Piemonte'
    ADD COLUMN home_country   CHAR(2),                                      -- 'IT'
    ADD COLUMN home_latitude  DOUBLE PRECISION,                             -- para agrupar por zona
    ADD COLUMN home_longitude DOUBLE PRECISION,
    ADD COLUMN map_city_id    BIGINT REFERENCES cities(id) ON DELETE SET NULL;

CREATE INDEX idx_users_home_city ON users(home_country, home_city);
```

- Aditivo, como el del perfil: se puede correr antes del deploy; el backend nuevo no puede salir sin él.
- `map_city_id` con `ON DELETE SET NULL`: si una ciudad se borra, el usuario vuelve a Milano.

## 4. Contrato API

- `PATCH /rest/auth/me` (`UserUpdateDto`) suma:
  ```json
  { "originCountry": "CO",
    "homeCity": { "name": "Torino", "region": "Piemonte", "country": "IT",
                  "latitude": 45.07, "longitude": 7.69 } }
  ```
  `homeCity` null = sin ciudad. Validación: `originCountry` `^[A-Z]{2}$`; `homeCity.name` no vacío,
  `country` `^[A-Z]{2}$`, lat/lng en rango.
- `GET /rest/auth/me` (`UserDto`) suma `originCountry`, `homeCity` (el mismo objeto) y `mapCityId`.
- `GET /rest/users/{id}` (`UserProfileDto`) suma solo `originCountry` (C5). La ciudad no (C6).
- `PUT /rest/users/me/map-city` → `204`. Body `{ "cityId": 3 }`; `null` la borra. `404` si la ciudad no
  existe o no está activa.
- Admin: `GET /rest/admin/stats/home-cities` → `[{ city, region, country, users, activeCityId }]`,
  ordenado por usuarios. `activeCityId` null = ciudad todavía sin abrir: ahí está la demanda.

## 5. Frontend

### Mapa (`features/map`)
- Al abrir, en este orden: la vista de la sesión (`MapViewStateService`, como hoy) → `mapCityId` del
  usuario → la ciudad guardada en `localStorage` → Milano.
- `goToCity(city)`: además de mover el mapa, guarda la ciudad. Con login, `PUT /users/me/map-city` (sin
  esperar la respuesta; si falla, no pasa nada visible); sin login, `localStorage`.
- Al iniciar sesión con una ciudad en `localStorage` y ninguna en el backend: se sube una vez.
- `MAP_BOUNDS` y `MAP_MIN_ZOOM`: de las ciudades activas (su bbox más un margen), no Lombardía fija.
  `map-placeholder` sigue en Milano (es la primera visita).

### «Il tuo account»
- **Paese d'origine**: selector con buscador. Nombres en italiano con `Intl.DisplayNames(['it'],
  { type: 'region' })` (sin lista propia de 250 países). Arriba los más frecuentes en la escena (Italia,
  Colombia, Venezuela, Perú, Cuba, República Dominicana, Brasil…).
- **Città dove vivi**: autocompletado con Photon limitado a ciudades (`layer=city`, `lang=it`); se guarda
  el objeto normalizado. Texto: «Non la vede nessuno: ci serve per capire dove aprire».
- Los dos van en el mismo formulario y el mismo «Salva»; entran en `toUserUpdate` / `sameProfile`.

### Perfil de una persona (`attendee-profile`)
- Etiqueta nueva con el país de origen, junto a edad, sexo y ruolo.

### Admin
- Página «Dove ci aspettano»: la tabla de `stats/home-cities`, las ciudades sin abrir arriba.

## 6. Riesgos y puntos abiertos

1. **Banderas en Windows**: los emoji de bandera (🇨🇴) en Windows se ven como dos letras («CO»). Opciones:
   un set de SVG de banderas como asset, o mostrar solo el nombre del país. **A decidir con el canvas.**
2. **Photon** es un servicio público sin SLA. Si no responde, el campo de ciudad muestra un error y el
   resto del formulario se guarda igual.
3. **PATCH completo** (P5 del perfil): los campos nuevos entran en el formulario completo, y un FE viejo
   los vaciaría. Mismo remedio: BE y FE juntos.
4. **RGPD**: `user-account-deletion-plan.md` suma `origin_country` y los `home_*` a anonimizar.
   `map_city_id` es una preferencia, se borra con el usuario.
5. **Privacidad de la ciudad**: no se muestra a otros usuarios, y las estadísticas del admin son
   conteos, nunca listas de personas.

## 7. Checklist

### Diseño
- [ ] Canvas: «Il tuo account» con país de origen y ciudad; perfil con el país; filtro «Città» que recuerda
- [ ] Decidir banderas (riesgo 1)

### Backend
- [ ] SQL en dev (`citta-prod-db.md`, como el del perfil) y `ballastasera_schema.sql`
- [ ] `Users`: columnas nuevas; `UserUpdateDto`, `UserDto`, `UserProfileDto` + validaciones
- [ ] `PUT /users/me/map-city` (ciudad activa o 404)
- [ ] `GET /admin/stats/home-cities`
- [ ] Tests + docs (`API.md`, `POSTMAN_TESTING.md`, plan de borrado)

### Frontend
- [ ] Modelos, endpoints, `toUserUpdate` / `sameProfile` con los campos nuevos (+ specs)
- [ ] Mapa: ciudad inicial, guardar la elección, `MAP_BOUNDS` desde las ciudades activas
- [ ] «Il tuo account»: país de origen y ciudad
- [ ] Perfil: país de origen
- [ ] Admin: «Dove ci aspettano»

### Base de datos prod
- [ ] SQL **antes** del deploy del backend
