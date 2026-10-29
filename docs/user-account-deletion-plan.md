# Borrar la cuenta de un usuario: qué tener en cuenta

Nota escrita el 29/10/2026, durante D13 de `organizer-requests-plan.md`. **No es un plan de implementación
cerrado:** hoy no hay endpoint para borrar una cuenta. Recoge lo que se rompería si se implementa sin
cuidado y el orden que tiene que seguir el servicio. Revisar contra el código antes de empezar.

---

## 1. Estado actual (29/10/2026)

- No hay endpoint. `UsersServiceImpl.deleteById` existe (resta los contadores de favoritos y «ci vado» y
  hace `usersRepository.deleteById`), pero ningún controller lo llama.
- `users` ya tiene `is_deleted` y `deleted_at`. `CustomOidcUserService` rechaza el login de un usuario con
  `isDeleted` (`account_deleted`) y de uno baneado (`account_banned`). Nadie los marca todavía.
- `JwtAuthenticationFilter` solo comprueba si el `jti` está en `revoked_tokens`. **No mira `isDeleted` ni
  `isBanned`:** un token emitido antes sigue valiendo hasta que caduca.
- `EventAttendanceRepository` (previews de avatares) ya filtra `u.isDeleted = false AND u.isBanned = false`.

## 2. Qué hace hoy la base de datos con un `DELETE FROM users`

| Tabla | FK | Efecto | ¿Problema? |
|---|---|---|---|
| `organizers.user_id` | `ON DELETE CASCADE` | **Borra sus organizadores** directamente en la base | **Sí.** Se salta D13 (un dueño por CLAIM haría desaparecer una página del admin) y la retirada de eventos futuros: sus eventos y series quedan publicados sin organizador (`ON DELETE SET NULL`), y el venue sin organizador |
| `organizer_requests.user_id` | `ON DELETE CASCADE` | Borra sus solicitudes | Se pierde el historial de revisiones. Aceptable si se decide así |
| `organizer_requests.reviewed_by` | `ON DELETE SET NULL` | Si se borra un admin, sus revisiones quedan sin revisor | No |
| `venues.created_by` | `ON DELETE SET NULL` | Solo informativo | No |
| `favorites.user_id` | `ON DELETE CASCADE` (y `CascadeType.ALL` en `Users`) | Se borran | No, si antes se restan los contadores (ya lo hace `deleteById`) |
| `event_attendance.user_id` | `ON DELETE CASCADE` (y `CascadeType.ALL` en `Users`) | Se borran | Igual que favoritos |

`events`, `event_series` y `revoked_tokens` no tienen FK a `users`.

**No cambiar `organizers.user_id` a `SET NULL`:** dejaría páginas con `claimed = true` y sin dueño, que no
salen en `claimable` y nadie gestiona. La solución es que el servicio deje al usuario sin organizadores
**antes** de borrarlo o marcarlo. Así el `CASCADE` no tiene nada que borrar y queda solo como red de
seguridad.

## 3. Decisión previa: soft delete o borrado real

- **Soft delete** (`is_deleted = true`, `deleted_at`): el esquema y el login ya lo contemplan. Las FK no se
  disparan, así que todo lo de la sección 4 hay que hacerlo a mano igualmente. Para cumplir el derecho al
  olvido (RGPD) hay que anonimizar los datos personales: `email`, `displayName`, `avatarUrl`, `instagram` y
  `googleId`.
- **Borrado real:** más simple con los datos personales, pero dispara las FK de la sección 2.
- En los dos casos: con soft delete y el `email`/`googleId` intactos, `CustomOidcUserService` encuentra la
  cuenta y devuelve `account_deleted` para siempre, así que el usuario no puede volver con la misma cuenta
  de Google. Hay que decidir si eso es lo que se quiere. Si se anonimiza, puede volver como usuario nuevo.

## 4. Orden en el servicio (antes de borrar o marcar al usuario)

Todo en una sola transacción.

1. **Sus organizadores**, uno por uno, reutilizando D13 (no a mano y no con las FK):
   - Los creó él (NEW APPROVED, `isCreatedByOwner`) → el camino de `delete`: retirar los eventos futuros
     (`retireUpcomingEvents`) y borrar.
   - Le llegaron por CLAIM o se los asignó el admin → el camino de `release` (`detachOwner`): la página, el
     venue y los eventos se quedan y la página vuelve a `claimable`.
   - Sin emails de «has dejado la página»: el usuario se está yendo.
2. **Sus solicitudes PENDING** → `CANCELLED`. Con el borrado real desaparecen con el `CASCADE`. Con soft
   delete hay que cancelarlas a mano, si no se quedan en la cola del admin.
3. **Contadores:** restar `likesCount` y `goingCount` (ya lo hace `deleteById`). Con soft delete, borrar
   también sus filas de `favorites` y `event_attendance`, o los contadores incluirían a un usuario que ya no
   existe.
4. **Tokens:** revocar el JWT actual (como el logout: `JwtService` lo guarda en `revoked_tokens`) y hacer
   que `JwtAuthenticationFilter` rechace a los usuarios con `isDeleted` (y de paso con `isBanned`). Si no,
   los tokens que tenga en otros dispositivos siguen valiendo hasta que caducan.
5. **Archivos:** cuando existan los logos de organizador (D7, §5.3), borrar el logo de cada organizador que
   se borra en el punto 1. `avatarUrl` hoy viene de Google: no hay nada propio que borrar.
6. **Caché:** `EVENT_DETAIL` (ya lo vacía `deleteById` y el borrado de organizadores) y las de venues si se
   borra algún organizador con venue.
7. **Admins:** decidir si un ADMIN puede borrar su cuenta, y no dejar nunca la app sin ningún admin.

## 5. Tests mínimos

- Usuario con un organizador de su NEW → organizador borrado, eventos futuros `CANCELLED`, series inactivas.
- Usuario con un organizador por CLAIM → el organizador sigue, sin dueño y en `claimable`; sus eventos
  siguen `PUBLISHED`.
- Usuario con una solicitud PENDING → `CANCELLED` (soft delete) o borrada (borrado real).
- Contadores de favoritos y «ci vado» restados.
- Token emitido antes del borrado → 401.
- El borrado real después de los pasos de la sección 4 no borra ningún organizador por `CASCADE`.
