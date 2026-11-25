# Perfil de usuario ampliado — plan de implementación

Fecha: 16/11/2026 · Rama: `feature/user-profile` (desde `main`, `3e065f0`).
SQL de dev/prod paso a paso: `user-profile-prod-db.md`. Esquema final de referencia: `ballastasera_schema.sql`.

Objetivo: en el detalle de un evento, en «parteciperò», se tocan los avatares → lista de asistentes →
se toca un usuario → se ve su perfil: edad, sexo, rol de baile, estilos con nivel, Instagram y WhatsApp
(si el usuario lo hace visible).

Esta es la **fase 1**. Los follows entre usuarios son la **fase 2** y no entran acá; esta fase deja el
esquema listo para agregarlos (`contact_visibility` recibe `MUTUALS` con `ALTER TYPE ... ADD VALUE`).

Convención de estado (la misma que `ROADMAP.md`):
- `[ ]` no empezado
- `[~]` en progreso (dejar nota de qué falta)
- `[x]` hecho y probado (tests + Postman)

## ▶ Dónde retomar (actualizado 22/11/2026)

**Estado: en prod (BE, FE y SQL). Prod = develop al 22/11.** La fase 2 (social) sigue en `social-plan.md`.
La tabla de abajo es el historial del 16–17/11.

| Parte | Estado |
|---|---|
| Backend: código | ✅ Hecho (rama `feature/user-profile`) |
| Backend: tests | ✅ `mvn test` 343 en verde (34 nuevos) |
| Base de datos dev | ✅ SQL corrido + paso opcional 2b |
| `ballastasera_schema.sql` | ✅ Actualizado |
| Docs (`API.md`, `POSTMAN_TESTING.md`, `user-account-deletion-plan.md`) | ✅ Actualizados |
| Prueba con Postman contra la DB local | ☐ Pendiente (casos en `POSTMAN_TESTING.md`) |
| Commits | ✅ En `feature/user-profile` (17/11), sin push: el merge a `main` y el push los hace el usuario, después del SQL en prod |
| Base de datos prod | ☐ Pendiente: `user-profile-prod-db.md`, **antes** del deploy del backend |
| Frontend | ◐ Diseño aprobado y código escrito (17/11), compila y tiene tests. Falta probarlo contra este BE: `ballastasera-fe/docs/perfil-usuario-fe.md` (rama `feature/user-profile` del FE, canvas https://claude.ai/artifact/JqQ8CdmVDhnZENtcaNfWfK) |
| Deploy | ☐ BE y FE juntos (P5: un FE viejo vaciaría los campos nuevos al guardar) |

**Siguiente feature, después de desplegar esta:** ciudad del mapa, ciudad de residencia y país de origen
(`citta-plan.md`). Sus campos nuevos entran en el mismo `PATCH /auth/me`.

**Commits (hechos el 17/11, los dos en `feature/user-profile`):**
1. Arreglo de `S3ObjectStorageServiceImplTest`, ajeno a esta feature: fallaba en `main` desde `7d5d270`
   (la URL del flyer ahora lleva `?v=`).
2. La feature: código, tests y docs (incluido `ballastasera_schema.sql`). `docs/passaggi-plan.md` no va,
   no es de esta rama.

---

## 1. Decisiones cerradas

| # | Tema | Decisión |
|---|---|---|
| P1 | Nivel | **Por estilo**: tabla `user_dance_styles(user_id, dance_style_id, level)`. Reutiliza `dance_styles` |
| P2 | WhatsApp | Columna `whatsapp_visibility` con `NOBODY` / `EVERYONE` (default `EVERYONE`, cambiado el 16/11: quien no quiere mostrarlo elige `NOBODY`). `MUTUALS` en la fase 2 |
| P3 | Sexo / rol | Las dos, opcionales: `gender` (MALE/FEMALE/OTHER/UNDISCLOSED) y `dance_role` (LEADER/FOLLOWER/BOTH) |
| P4 | Edad | Se guarda `birth_date`. El perfil público devuelve solo `age`. Mínimo 16 años, máximo 100 |
| P5 | Guardado | Todo en `PATCH /rest/auth/me`, extendiendo `UserUpdateDto`. Reemplaza el formulario completo, igual que hoy con instagram |
| P6 | Perfil público | `GET /rest/users/{id}`, **requiere login** (ya lo cubre `anyRequest().authenticated()`, no se toca `SecurityConfig`) |
| P7 | Privacidad | 404 si el perfil no es público, está borrado o baneado, salvo que sea el propio usuario |
| P8 | Lista de asistentes | Sigue ligera (avatar, nombre, instagram). Sin teléfono |
| P9 | Máx. estilos | 10 por usuario (`@Size(max = 10)`) |
| P10 | Mapeo del PATCH | `UserMapper.updateProfileFromDto` (`@MappingTarget`), **sin** `nullValuePropertyMappingStrategy = IGNORE` (a diferencia de `OrganizerMapper`/`EventsMapper`): null pisa con null, por P5. Se probó el PATCH parcial y se descartó: `birthDate` y `danceRole` no se podían vaciar |
| P11 | Sin `save()` en `updateProfile` | Se guarda al commit del `@Transactional` (dirty checking). `save()` hacía `merge` y un SELECT por cada estilo nuevo. Explicado en el javadoc del método: **no quitar el `@Transactional`** |
| P12 | Mensajes de validación | En inglés y técnicos, como el resto del backend. El front muestra sus propios textos en italiano según la clave de `errors` |
| P13 | Doble click en «Salva» | Dos PATCH a la vez que agregan el mismo estilo → el segundo da 500 (PK duplicada). No se maneja en el backend: **el FE deshabilita el botón mientras guarda** |
| P14 | Constantes de path | `AuthController`: `GET_ME` y `UPDATE_ME` (los dos `"/me"`), una por operación como en `EventsController`. La URL no cambia, el FE no se toca |

## 2. Factibilidad: puntos verificados

- **Enums de Postgres**: el patrón ya existe (`user_role` con `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` en `Users.java`).
- **Clave compuesta**: el patrón ya existe (`Favorites` + `keys/UserEventId` con `@EmbeddedId` + `@MapsId`).
- **Services reciben DTO de entrada y devuelven entidad**: igual que `EventsService.update(UUID, UUID, EventUpdateDto)`.
- **Sin Flyway/Liquibase** (`ddl-auto` no está seteado): el SQL se corre a mano siguiendo `user-profile-prod-db.md`.
- **Cache**: `GET /auth/me` y `GET /users/{id}` no pasan por Redis. `EVENT_DETAIL` no incluye datos de perfil.
- **Lazy loading**: `open-in-view` está activo por default, pero se usa `@Query` + `LEFT JOIN FETCH` para evitar N+1.
- **Tests existentes**: ninguno usa `updateSocialProfile`, `UserUpdateDto` ni `UserMapper`.

## 3. Riesgos y cómo se cubren

1. **Orden del flush de Hibernate**: con `clear()` + `add()` del mismo `(user_id, dance_style_id)`, Hibernate
   puede ejecutar el INSERT antes del DELETE y saltar la PK duplicada. **Solución**: sincronizar por diferencia.
2. **`@MapsId` con hijo nuevo vía cascade**: se setean a mano `id.userId` e `id.danceStyleId` al crear `UserDanceStyles`.
3. **PATCH /auth/me reemplaza todo**: un front viejo que solo mande `instagram` + `showProfilePublic` borraría
   los campos nuevos. **Solución**: desplegar BE y FE juntos. `whatsappVisibility` null → `EVERYONE` (no da 400).
4. **Ids de estilo inexistentes**: `EventResolverServiceImpl.resolveDanceStyles` los ignora sin avisar. Para el
   perfil se lanza `EntityNotFoundException` (404, handler ya existente).
5. **Bug existente**: `EventAttendanceRepository.findByEventIdAndStatusAndUserShowProfilePublicTrue` no filtra
   `isDeleted` / `isBanned`, así que aparecerían usuarios cuyo perfil da 404. Se corrige acá y se renombra a
   `findPublicByEventId`.
6. **RGPD**: `user-account-deletion-plan.md` tiene que sumar `whatsapp`, `birth_date` y `gender` a los datos a
   anonimizar. `user_dance_styles` cae con `ON DELETE CASCADE`.

## 4. Esquema SQL

```sql
CREATE TYPE gender             AS ENUM ('MALE', 'FEMALE', 'OTHER', 'UNDISCLOSED');
CREATE TYPE dance_role         AS ENUM ('LEADER', 'FOLLOWER', 'BOTH');
CREATE TYPE dance_level        AS ENUM ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'TEACHER');
CREATE TYPE contact_visibility AS ENUM ('NOBODY', 'EVERYONE');   -- MUTUALS en fase 2

ALTER TABLE users
    ADD COLUMN whatsapp            TEXT,                -- E.164: +393331234567
    ADD COLUMN whatsapp_visibility contact_visibility NOT NULL DEFAULT 'EVERYONE',
    ADD COLUMN birth_date          DATE,
    ADD COLUMN gender              gender,
    ADD COLUMN dance_role          dance_role;

CREATE TABLE user_dance_styles (
    user_id        UUID   NOT NULL REFERENCES users(id)        ON DELETE CASCADE,
    dance_style_id BIGINT NOT NULL REFERENCES dance_styles(id) ON DELETE CASCADE,
    level          dance_level NOT NULL,
    PRIMARY KEY (user_id, dance_style_id)
);
CREATE INDEX idx_user_dance_styles_style ON user_dance_styles(dance_style_id);
```

## 5. Contrato API

- `PATCH /rest/auth/me` → `UserDto`
  ```json
  { "instagram": "juan.baila", "showProfilePublic": true,
    "whatsapp": "+393331234567", "whatsappVisibility": "EVERYONE",
    "birthDate": "1995-04-12", "gender": "MALE", "danceRole": "LEADER",
    "danceStyles": [ { "danceStyleId": 1, "level": "ADVANCED" },
                     { "danceStyleId": 2, "level": "BEGINNER" } ] }
  ```
- `GET /rest/auth/me` → `UserDto` con los campos nuevos y `danceStyles[{danceStyleId, name, slug, level}]`
- `GET /rest/users/{id}` (login) → `UserProfileDto` con `age` en vez de fecha; `whatsapp` null si no es
  visible; 404 si es privado, borrado o baneado
- `GET /rest/events/{id}/attendees`: no cambia el contrato; solo filtra borrados y baneados

## 6. Checklist

### Base de datos
- [x] SQL en dev (`user-profile-prod-db.md`), con default `EVERYONE` en `whatsapp_visibility`
- [x] `ballastasera_schema.sql` actualizado (lo hizo el usuario)
- [x] SQL en prod, **antes** del deploy del backend (y el paso opcional 2b)

### Código
`[~]` = escrito y con tests unitarios en verde; pasa a `[x]` cuando se pruebe con Postman.

- [~] Nuevos: `enums/Gender`, `DanceRole`, `DanceLevel`, `ContactVisibility`
- [~] Nuevo: `models/entities/keys/UserDanceStyleId.java` (`@Embeddable @EqualsAndHashCode`, como `UserEventId`)
- [~] Nuevo: `models/entities/UserDanceStyles.java` (`@EmbeddedId`, `@MapsId` a `Users` y `DanceStyles`, `level` `NAMED_ENUM`)
- [~] Nuevos: `UserDanceStyleUpdateDto` (entrada), `UserDanceStyleDto` (salida), `UserProfileDto` (perfil público)
- [~] `Users.java`: 5 columnas + `Set<UserDanceStyles> danceStyles`
- [~] `UserUpdateDto.java`: campos nuevos + validaciones (`@Pattern` whatsapp, `@Past` + `@AssertTrue` edad, `@Size(max=10)` estilos), mensajes en inglés (P12)
- [~] `UserDto.java`: campos nuevos + `danceStyles`
- [~] `UsersRepository.java`: `findByIdWithDanceStyles` (`@Query` + `LEFT JOIN FETCH`)
- [~] `UsersService` / `UsersServiceImpl`: `updateProfile` (reemplaza `updateSocialProfile`, sin `save()`: P11) con
      `syncDanceStyles` por diferencia, `findByIdWithDanceStyles` y `findPublicProfile`
- [~] `UserMapper.java`: `updateProfileFromDto` (P10), `toDanceStyleDto`, `toProfileDtoBase` (+ `age`),
      `default toProfileDto(user, viewerId)`, `default toAge`
- [~] `AuthController.java`: `GET_ME` con estilos, `UPDATE_ME` → `updateProfile` (P14)
- [~] `UsersController.java`: `GET /{id}`
- [~] `EventAttendanceRepository.java`: filtro `isDeleted`/`isBanned`, rename a `findPublicByEventId` y `countQuery` explícito
- [~] `EventAttendanceServiceImpl.java`: llamada renombrada
- [~] `AttendeeDto.java`: javadoc

### Docs
- [x] `user-account-deletion-plan.md`: campos a anonimizar + fila de `user_dance_styles`
- [x] `API.md`: `GET`/`PATCH /auth/me` y `GET /users/{id}`
- [x] `POSTMAN_TESTING.md`: casos de esta feature

### Tests
- [x] `UsersServiceImplTest` (+10): copia del formulario y normalización, sync agrega/quita/cambia nivel sin
      `save()`, estilo repetido gana el último, `null` borra todos, id de estilo inexistente → 404, visibilidad del
      perfil (público, privado, baneado, borrado, propio)
- [x] `UserMapperTest` (8): `age`, whatsapp oculto/visible/dueño, estilos, `UserDto`, `updateProfileFromDto`
- [x] `UserUpdateDtoValidationTest` (11): WhatsApp E.164, edad 16–100, máx. 10 estilos, `level` obligatorio, `showProfilePublic` obligatorio
- [x] `EventsCacheTest`: mocks `DanceStylesRepository` y `UserMapper` (los pide `UsersServiceImpl`)
- [x] `mvn test` en verde (343)

### Frontend (cuando se empiece)
- [ ] Formulario de perfil: mandar siempre el formulario completo (P5), cargado desde `GET /auth/me`
- [ ] Validar en el cliente con las mismas reglas y textos en italiano; traducir las claves de `errors` del 400 (P12)
- [ ] Switch «mostrar WhatsApp» junto al campo (default `EVERYONE`)
- [ ] Deshabilitar «Salva» mientras guarda (P13)
- [ ] Lista de «parteciperò» → tocar un usuario → `GET /users/{id}`

### Postman
- [ ] PATCH /auth/me completo
- [ ] Repetir el PATCH cambiando un nivel y sacando un estilo: sin error de PK duplicada
- [ ] GET /auth/me
- [ ] GET /users/{id} de otro usuario con whatsapp `NOBODY` y `EVERYONE`
- [ ] GET /users/{id} de un perfil privado → 404
- [ ] GET /users/{id} sin token → 401
- [ ] birthDate de 10 años → 400; whatsapp `333 123` → 400

## 7. Fuera de alcance (fase 2)

`user_follows`, el valor `MUTUALS` en `contact_visibility`, contadores de seguidores y ver el WhatsApp entre mutuos.