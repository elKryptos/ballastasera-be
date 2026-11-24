# Social: seguirse, amigos, bloquear, avisos — plan

Actualizado: 24/11/2026 · Rama: `feature/social` (BE y FE, desde `main`).
Diseño: canvas https://claude.ai/artifact/JqQ8CdmVDhnZENtcaNfWfK, artboards 4 (tu perfil), 4b (modifica
perfil), 4c (perfil de otra persona) y 4d (Amici).

Estados: `[ ]` no empezado · `[~]` en progreso (con nota de qué falta) · `[x]` hecho y probado (tests de integración + prueba en el FE).

## 1. Estado

| Fase | Qué | Estado |
|---|---|---|
| 0 | Rate limit en todas las APIs | [~] BE hecho con tests (22/11), falta el aviso en el FE |
| 1 | Seguir + bloquear | [~] BE hecho con tests (22/11), falta el FE |
| 2 | Perfil: bio, ciudad, escuela, foto propia | [~] BE hecho con tests (22/11), falta el FE |
| 3 | Serate de otra persona y «Avete in comune» | [~] BE hecho con tests (22/11), falta el FE |
| 4 | Denuncias, ban, silenciar | [~] BE hecho con tests (22/11), falta el FE |
| 5 | Pestaña Amici: feed y sugerencias | [~] BE hecho con tests (22/11), falta el FE |
| 6 | Avisos (campanita + email) e invitaciones | [~] BE hecho con tests (24/11), falta el FE |
| 7 | Traguardi y @username | [~] BE hecho con tests (24/11), falta el FE |

**Siguiente paso:** el BE de las fases 0–7 está hecho. Ahora el SQL de todas las fases en develop y probar el BE
contra esa base; después todo el FE. El SQL de prod se va juntando en `social-prod-db.md` y se corre de una vez
antes del deploy.

**Tests:** desde la fase 0 hay tests de integración con Testcontainers (`support/IntegrationTest`): la app
entera contra un Postgres y un Redis reales en Docker, con el esquema de `ballastasera_schema.sql`.
Reemplazan las pruebas a mano con Postman; cada endpoint nuevo lleva el suyo. Hace falta Docker Desktop
abierto para `mvnw test`.

## 2. Lo que ya existe

El perfil que está en prod (`user-profile-plan.md`): Instagram, WhatsApp visible a todos o a nadie, fecha
de nacimiento, sexo, rol, estilos con nivel y perfil público sí/no (`GET`/`PATCH /rest/auth/me`). El perfil
de otra persona (`GET /rest/users/{id}`) hoy es un panel dentro de «Chi ci va», sin URL propia. «Ci vado»
y «Chi ci va» (`/rest/events/{id}/attendance`, `/attendees`).

Piezas que se reutilizan:
- Claves compuestas con `@EmbeddedId` + `@MapsId` (`Favorites`, `UserDanceStyles`).
- Paginación por cursor (`CursorPageDto`, como en `/users/me/attendance`).
- Subida a S3 (`S3ObjectStorageServiceImpl`, como el logo de los organizers).
- Emails con Resend: `OrganizerEmailListener` (`@TransactionalEventListener` después del commit) + `EmailService` `@Async`.
- `@EnableScheduling` activo (limpieza de avisos viejos).
- Redis ya en uso para la cache (sirve también para el rate limit).

## 3. Decisiones

**Seguir y amigos**
- Seguir es abierto: tocas «Segui» y listo, sin solicitud.
- Amigos = os seguís los dos. No hay tabla de amistad aparte.
- A un perfil privado no se le puede seguir (ya da 404).
- Los contadores (seguidores, seguidos, serate) se ven siempre, en los perfiles que se pueden ver.

**Privacidad**
- Privado = no te ve nadie, **tampoco tus amigos** (decidido el 23/11; la otra opción, «solo amigos», queda para más
  adelante). Los follows no se borran: sigue contando como amigo y, si vuelve a público, todo vuelve. Mientras es
  privado: su perfil y sus serate dan 404 a todos; no sale en «Chi ci va», en «ci vanno», en las serate del feed ni
  en las sugerencias; sí sale, sin enlace ni botón para seguirlo (`profilePublic = false`), en las listas, en «ti
  segue» de quien él siga y en «Avete in comune». Quien lo deje de seguir no puede volver a seguirlo hasta que
  vuelva a público.
- Privado = **aislado de todo** (24/11): tampoco invita ni lo invitan. Es a propósito: no queremos que se use como
  opción cómoda, y el FE lo explica claramente en cada sitio donde se nota (ver fase 6). Sus follows sí avisan a la
  otra persona, como en el feed: el aviso sale sin enlace y con «Profilo privato».
- WhatsApp: se suma la opción «solo amigos». Los usuarios nuevos arrancan ahí; a los que ya existen no se les toca.
- Las serate a las que vas las ven todos, solo tus amigos o nadie (por defecto, todos). Con «nadie» no sales en «Chi ci va» ni en tu perfil, pero sí cuentas en el número de los que van.
- Ciudad donde vives: se elige con Photon limitado a ciudades (comuni, nunca direcciones). En el perfil solo se ve si activas «Mostra la città nel profilo», y solo el nombre («Milano»).

**Menores de 18**
- WhatsApp siempre oculto y no salen en las sugerencias. Pueden seguir y ser seguidos. Solo los invitan sus amigos,
  como a todos (las invitaciones son solo entre amigos).

**Avisos**
- Todo sale en una campanita en la web. Los avisos se borran a los 60 días.
- Por email (Resend), solo «ora siete amici» y las invitaciones. «Ti segue» queda solo en la campanita.
- Toggle «Ricevi email» en Il tuo account. Sin push: no hay app.
- Cuando A sigue a B: B recibe «ti segue». Si B ya seguía a A, en cambio, B recibe «ora siete amici» (con email,
  salvo que A sea privado: el botón llevaría a un perfil que da 404); A no recibe nada, porque fue quien cerró la
  amistad.
- Sin avisos repetidos: dejar de seguir borra el «ti segue» sin leer, y la misma pareja no recibe el mismo aviso dos
  veces en 7 días. El «ora siete amici» no se borra: si se borrara, seguir y dejar de seguir en loop mandaría un email
  nuevo cada vez (corregido en el check del 24/11).
- Silenciados: sus avisos se filtran al leer (al reactivar, vuelven). Bloquear borra los avisos entre los dos, en los
  dos sentidos. Los de baneados no se muestran; las invitaciones a serate canceladas o terminadas, tampoco.
- Se marcan todos como leídos de una vez, al abrir la campanita.
- `email_notifications` arranca en true para todos (también los que ya existen): son avisos de su cuenta, no
  publicidad. El email lleva un link a «Il tuo account» para apagarlo (el link de baja de un clic, más adelante).

**Invitaciones** (24/11)
- Solo a amigos (se siguen los dos), para que no se preste a spam. Ni el que invita ni el invitado pueden ser privados.
- Frase fija a elegir entre 3–4 (enum, el FE las muestra en italiano), sin texto libre.
- Sin respuesta: el aviso lleva a la página de la serata y desde ahí se dice «Ci vado».
- Si el invitado ya dijo «Ci vado» se rechaza (`ALREADY_GOING`), salvo que haya elegido que nadie vea sus serate:
  entonces se deja invitar, para no revelar que va.
- Límite de 10 en las últimas 24 horas (móviles, contadas en la base), 1 por persona y serata.

**Seguridad**
- Bloquear sale en la misma entrega que seguir. Bloquear borra los follows en los dos sentidos, y ninguno ve el perfil del otro.
- Rate limit general en todas las APIs (Bucket4j + Redis), más un límite de negocio para las invitaciones.
- Denuncias: motivos fijos (perfil falso, molesto, foto inapropiada, otro + nota). Van a una cola en el admin con «Ignora» o «Banna». Nada se oculta solo.
- Silenciar quita a esa persona del feed y de la campanita, y ella no se entera. Solo se silencia a quien sigues; a un desconocido que molesta, se le bloquea.

**Perfil**
- Foto propia sin revisión previa (si es inapropiada, se denuncia). Máx. 5 MB, JPG/PNG/WebP. Se puede volver a la de Google o no poner ninguna.
- Escuela: enlace a un organizer de tipo SCHOOL o, si no está, texto libre.
- «Cerco qualcuno con cui ballare» se marca por serata, al decir «Ci vado», no en el perfil.
- Cada persona tiene su página `/persona/{id}`. El `@username` llega al final como alias para compartir.

**Feed**
- El feed se calcula en el momento a partir de los follows y los «Ci vado». No guardamos historial, así que no hay avisos tipo «ha cambiado de nivel».

**Traguardi** (24/11)
- Cuentan los «Ci vado» de serate publicadas que ya terminaron (lo mismo que `eventsCount`), y el texto lo dice así.
  Locales: los distintos de esas serate; las serate sin local no cuentan.
- Sin ciudad: muchos locales están en la periferia o fuera de la ciudad, y si te mudas de región deja de tener
  sentido. El FE pone «dove hai ballato».
- «Prima serata» (y el «da marzo 2026» de las serate) solo en tu perfil: a los demás no se les dice la fecha.
- Se ven aunque nadie vea tus serate: son contadores, como `eventsCount`.
- La meta siguiente («50 serate, ne mancano 23») la calcula el FE con umbrales fijos (10, 25, 50, 100, 200).
- «Su ballastasera da…» = fecha de registro, en los dos perfiles.

**@username** (24/11)
- Opcional y único. Minúsculas, `a-z0-9._`, 3–30, sin punto al principio ni al final ni dos seguidos. Reservados:
  `admin`, `ballastasera`, `staff`, `support`, `help`, `info`.
- Se cambia cuando quieras: el viejo queda libre en el momento y su link da 404 (sin historial).
- El FE lo propone desde el Instagram si está libre. A los usuarios que ya existen no se les rellena: alguien pudo
  escribir el Instagram de otra persona.
- `/@username` pide login, como `/persona/{id}`: «Profilo pubblico» sigue siendo «visible a quien tiene cuenta».
  Quien llega desde una story entra con Google y lo ve.
- Borrar la cuenta lo deja en `null` (queda libre); el baneado lo conserva.
- «Condividi» (canvas 4): tarjeta para stories y «Copia il link» con `ballastasera.it/@username`. La tarjeta lleva el
  logo y la foto de perfil (sin foto, la inicial).

**Datos por usuario y cache:** nada que dependa de quién mira (relación, amigos que van) entra en la cache
`EVENT_DETAIL`. Va en endpoints aparte.

## 4. Fases

### Fase 0 — Rate limit
El usuario no ve nada, salvo «Troppe richieste, riprova tra poco» si abusa.
- [x] `bucket4j_jdk17-core` + `bucket4j_jdk17-lettuce` en el `pom.xml`
- [x] `RateLimitFilter` después de `JwtAuthenticationFilter`. Clave: usuario con login, IP sin login. Grupos: lectura 120/min · login 20/min · escrituras 60/min · social 30/min y 300/día · uploads 10/hora
- [x] 429 con `Retry-After` y `code: RATE_LIMITED`. Si Redis no responde se deja pasar (como la cache)
- [x] `app.rate-limit.*` en properties, con variables de entorno
- [x] Tests: filtro, servicio con Redis caído, integración con Redis real (Testcontainers). Sección en `API.md`
- [ ] FE: aviso en 429 desde el interceptor

### Fase 1 — Seguir + bloquear
Botón Segui / Ricambia / Amici, listas de amigos, WhatsApp «solo amigos», Blocca.
- [x] SQL: `MUTUALS` en `contact_visibility`, tablas `user_follows` y `user_blocks`, default de WhatsApp a `MUTUALS` (`social-prod-db.md`; dev ☐)
- [x] `POST`/`DELETE /rest/users/{id}/follow` (idempotentes, no a ti mismo, 404 si es privado o hay bloqueo)
- [x] `GET /rest/users/me/followers`, `/me/following`, `/me/friends?q=` (cursor, `UserSummaryDto`)
- [x] `POST`/`DELETE /rest/users/{id}/block`, `GET /rest/users/me/blocks`
- [x] `GET /rest/users/{id}` y `/auth/me` suman `relation` (`NONE`/`FOLLOWING`/`FOLLOWED_BY`/`MUTUAL`) y contadores
- [x] `UserMapper`: WhatsApp entre amigos; menores siempre ocultos
- [x] Bloqueos filtrados en perfil, follow, «Chi ci va» y listas
- [x] Tests de integración (uno por sitio donde un bloqueo esconde a alguien) y del mapper
- [ ] FE: página `/persona/{id}` (reutiliza `attendee-profile`), botón de follow, contadores, lista de amigos, Blocca
- [ ] FE: en las listas, con `profilePublic = false` la fila va sin enlace y con «Profilo privato» en vez de «Segui»/«Ricambia» (dejar de seguir y desbloquear sí). Al dejar de seguir a alguien privado, confirmar: «È un profilo privato: non potrai seguirlo di nuovo finché non lo rende pubblico.»

### Fase 2 — Perfil
Canvas 4b: bio, quién ve tus serate, escuela, ciudad y país de origen, foto propia.
- [x] **Antes**: `CustomOidcUserService` pisa la foto en cada login. Guardar la de Google aparte (`google_avatar_url`) y resolver `avatarUrl` según `avatar_source` (`GOOGLE`/`CUSTOM`/`NONE`)
- [x] SQL: `bio` (≤ 150), `attendance_visibility` (default `EVERYONE`), `school_organizer_id` + `school_name`, `origin_country`, `home_city`/`home_region`/`home_country`/`home_latitude`/`home_longitude` (los de `citta-plan.md`) + `show_home_city` (default false), columnas de avatar (`social-prod-db.md`; dev ☐)
- [x] `PATCH /auth/me` y `UserDto` con los campos nuevos y sus validaciones
- [x] `UserProfileDto` suma `bio`, escuela en campos planos (`schoolOrganizerId`, `schoolName`, `schoolSlug`), `originCountry` y `homeCityName` solo si `show_home_city`
- [x] «Chi ci va» respeta `attendance_visibility`: `NOBODY` no sale, `MUTUALS` solo lo ven sus amigos (sigue contando en `goingCount`). Las caritas del mapa, solo `EVERYONE`
- [x] `PATCH /rest/users/me/avatar` (multipart como los otros uploads, máx. 5 MB, JPG/PNG/WebP, webp en el bucket de logos con prefijo `avatars/`) y `PATCH /rest/users/me/avatar/source` `{ GOOGLE | NONE }`
- [x] `GET /rest/organizers/schools?name=` para el autocompletado
- [x] Tests de integración (perfil, escuela, «Chi ci va», foto con S3 mockeado) y del login
- [ ] FE canvas 4b. En el toggle «Profilo pubblico», explicar bien qué pasa al apagarlo: «Nessuno, nemmeno i tuoi
  amici, potrà vedere il tuo profilo e le tue serate. Non comparirai in "Chi ci va" e le tue serate non usciranno
  nel feed degli amici. Non potrai invitare né essere invitato a ballare. Nessuno potrà iniziare a seguirti; le
  persone che ti seguono restano.»

### Fase 3 — En común
Canvas 4c: «Le sue prossime serate», «Avete in comune», «Sara e Luca ci vanno».
- [x] `GET /rest/users/{id}/attendance` (respeta visibilidad y bloqueos; si no se pueden ver, lista vacía)
- [x] `GET /rest/users/{id}` suma «Avete in comune» en campos planos: hasta 3 amigos en común con el total, serate pasadas juntos y la última (solo si sus serate son visibles para quien mira)
- [x] `GET /rest/events/{id}/friends-going` (fuera de la cache)
- [x] «Cerco qualcuno»: columna `looking_for_partner` en `event_attendance`; `POST /attendance` acepta `{ lookingForPartner }` opcional (sin body no cambia) y `GET /events/{id}/attendance/me` → `{ going, lookingForPartner }`
- [x] Pallino del perfil `lookingForPartnerToday`: serata en curso o de las próximas 24 horas
- [x] `AttendeeDto` suma `relation` y `lookingForPartner` (sin login, `relation` null)
- [x] Tests de integración (SQL en `social-prod-db.md`; dev ☐)
- [ ] FE canvas 4c

### Fase 4 — Denuncias, ban, silenciar
Menú ⋯ del perfil y cola en el admin.
- [x] SQL: tabla `user_reports` (enums `report_reason`, `report_status`), `muted` en `user_follows`, `banned_at` en `users` (`social-prod-db.md`; dev ☐)
- [x] `POST /rest/users/{id}/report { reason, note }` (una abierta por pareja, idempotente; nota obligatoria con `OTHER`)
- [x] Admin: `GET /rest/admin/reports?status=`, `PATCH /rest/admin/reports/{id} { DISMISSED | BANNED }`, `PATCH /rest/admin/users/{id}/ban` y `/unban`, `GET /rest/admin/users/banned`. Banear (desde una denuncia o directo) cierra todas las abiertas contra esa persona. `ModerationService` junta denuncias y ban
- [x] El ban corta la sesión abierta: `JwtAuthenticationFilter` rechaza a los baneados (antes solo lo hacía el login)
- [x] Silenciar: `PATCH /rest/users/{id}/follow { muted }` (404 si no lo sigues); `GET /users/{id}` suma `muted`. A un desconocido no se le silencia: para eso está Blocca
- [x] Tests de integración (`ModerationIntegrationTest`, silenciar en `FollowsIntegrationTest`)
- [ ] FE menú ⋯ (Silenzia, Segnala, Blocca) y en el admin la cola de denuncias y «Utenti bannati»

### Fase 5 — Pestaña Amici
Canvas 4d.
Decidido (22/11): sale la gente que sigues (no solo amigos); «stasera» = en curso o empieza antes de las próximas
06:00 hora de Italia; el subtítulo «Stasera a…» de la lista de amigos lo saca el FE de `tonight`; «stessa scuola»
como motivo de sugerencia queda para después. Sin SQL: alcanzan los índices que ya hay.
Los seguidores privados salen (en «ti segue» y en `/me/followers`): `UserSummaryDto.profilePublic = false` y el FE
los muestra sin enlace ni «Ricambia» («Profilo privato»), porque seguir a un privado sigue dando 404.
- [x] `GET /rest/users/me/feed`: `tonight` («Stasera escono», por serata) y `news` de 7 días («ti segue», «va a X» a serate de los próximos 7 días). Sin silenciados; respeta `attendanceVisibility`
- [x] `GET /rest/users/me/suggestions?limit=`: gente de tus próximas serate (`EVERYONE`) y amigos de amigos. Sin seguidos, bloqueados, menores ni privados. Orden: serata compartida, «Cerco qualcuno», amigos en común
- [x] `FeedService` devuelve `FeedActivity` y `SuggestionsActivity` (entidades, como `ProfileActivity`) y `FeedMapper` arma los DTOs
- [x] Tests de integración (`FeedIntegrationTest`) y del corte de «stasera» con horario de verano (`FeedServiceImplTest`)
- [ ] FE canvas 4d

### Fase 6 — Avisos e invitaciones
Campanita del Home e «Invita a ballare». Decisiones en §3 (Avisos e Invitaciones).
- [x] Tabla `notifications` (`social-prod-db.md`; dev ☐); `GET /me/notifications`, `/me/notifications/unread-count`, `PATCH /me/notifications/read`
- [x] Tipos: nuevo seguidor, nuevo amigo, invitación. Limpieza a los 60 días con `@Scheduled` (`NotificationsCleanupJob`, 4:30 hora de Italia)
- [x] Sin repetidos (dejar de seguir borra el «ti segue» sin leer, 7 días por pareja), silenciados filtrados al leer (y sin email), bloquear borra los avisos entre los dos
- [x] Emails de nuevo amigo e invitación (`SocialEmailListener`, como `OrganizerEmailListener`) + columna `email_notifications` (default true) con `PATCH /me/email-notifications`, aparte de `PATCH /auth/me` para que un FE viejo no los apague
- [x] `POST /rest/users/{id}/invites { eventId, phrase }`: solo amigos, ninguno privado, frase fija (enum); 10 en 24 horas, 1 por persona y serata (`ON CONFLICT DO NOTHING`), solo serate no terminadas a las que vas; `ALREADY_GOING` salvo serate ocultas; 429 `INVITE_LIMIT` con `Retry-After` si te pasas
- [x] `GET /rest/events/{id}/invites/friends?q=`: el diálogo, con el estado de cada amigo e `invitesLeft`
- [x] Tests de integración (`NotificationsIntegrationTest`, `InvitesIntegrationTest`). `EmailService` mockeado en `IntegrationTest`: ningún test manda emails reales
- [ ] FE campanita y diálogo «Invita a ballare». El botón solo sale en serate no terminadas a las que dijiste «Ci vado»:
  si no, el diálogo da `400` (`EVENT_NOT_INVITABLE`, `NOT_GOING`)
- [ ] FE, consecuencias del perfil privado explicadas claramente donde se notan:
  - Toggle «Profilo pubblico»: el texto de la fase 2 (ya dice que no podrá invitar ni ser invitado).
  - Con el perfil privado, «Invita a ballare» se ve deshabilitado con «Il tuo profilo è privato: rendilo pubblico per
    invitare i tuoi amici», y un enlace a la configuración.
  - En el diálogo, el amigo privado sale deshabilitado con «Profilo privato: non può essere invitato».
  - En la campanita, el aviso de alguien privado sale sin enlace y con «Profilo privato».
- [ ] FE, resto del diálogo: «Ci va già», «Già invitato», «Ti restano N inviti oggi» y, a 0, «Hai finito gli inviti per oggi»
- [ ] FE, serate ocultas: si quien invita eligió «Chi vede le tue serate: nessuno» (`attendanceVisibility = NOBODY`, perfil
  público), el diálogo avisa antes de mandar «Il tuo amico vedrà che ci vai», porque la invitación (aviso y email) dice
  a qué serata va. No es el caso del perfil privado: ese no puede invitar (`PROFILE_PRIVATE`)
- [ ] FE toggle «Ricevi email»: «Ti scriviamo solo quando diventi amico di qualcuno o ricevi un invito.»

### Fase 7 — Traguardi y @username
Canvas 4 (Traguardi y «Condividi»), 4b («Nome utente» en «Chi sei») y 4c. Decisiones en §3.
- [x] SQL: `username VARCHAR(30) UNIQUE` en `users`, sin `CHECK`: el formato lo valida solo el BE (`UsernameUtils`), como
  `instagram` y `whatsapp` (`social-prod-db.md`; dev ☐)
- [x] `UserDto` y `UserProfileDto` suman `username`, `memberSince` y `venuesCount`; `firstEventAt` solo en `UserDto`
  (`/auth/me`). `SocialCounts` suma `venues` y `firstEventAt`, y `UserMapper` decide dónde va cada uno
- [x] `GET /rest/users/username-available?u=` → `{ available, reason }` (`INVALID` / `RESERVED` / `TAKEN`; el tuyo cuenta como libre)
- [x] `PATCH /rest/users/me/username { username }`, aparte de `PATCH /auth/me` (un FE viejo lo borraría). `null` o vacío
  lo quita; `409` con `USERNAME_TAKEN` (también si dos lo piden a la vez: índice único) o `USERNAME_RESERVED`
- [x] `GET /rest/users/by-username/{username}` → el mismo `UserProfileDto` que `/users/{id}`, con los mismos 404
  (privado, bloqueo, baneado, borrado)
- [x] Tests de integración (`ProfileStatsIntegrationTest`, `UsernameIntegrationTest`), del mapper y de
  `UsersServiceImplTest` (dos personas que piden el mismo username a la vez: el índice único da `USERNAME_TAKEN`)
- [x] `user-account-deletion-plan.md`: `username` a `null` al borrar
- [ ] FE: traguardi (4 y 4c), campo «Nome utente» (4b), ruta `/@username`, «Condividi» (tarjeta para stories como
  `social-posts/cards.ts`, con el logo y la foto, cargada con CORS como los flyers; sin foto, la inicial) y «Copia il link»

### Docs al cerrar cada fase
`API.md`, `ballastasera_schema.sql` (lo cargan los tests de integración), `user-account-deletion-plan.md`
(bio, ciudad, foto propia, denuncias, avisos y username ya están). El SQL de prod va en
`social-prod-db.md`, por fase.

## 5. Fuera por ahora
- Fotos de las serate (para eso está Instagram).
- Chat dentro de la app: el contacto sigue por WhatsApp o Instagram.
- Notificaciones push (no hay app).
- Check-in con GPS en el local, para que los traguardi cuenten asistencia real.
- Invitar a amigos que no están en ballastasera con follow automático al registrarse.
- Cambios de nivel en el feed.

## 6. A recordar
1. **Bloqueos en todas partes**: un solo método (`isBlockedEitherWay`) y un test por cada endpoint que muestra gente.
2. **Sin N+1** al calcular `relation` en listas: una query con los ids de la página.
3. **Cache**: nada del que mira en `EVENT_DETAIL`.
4. **BE y FE salen juntos**: `PATCH /auth/me` reemplaza el perfil entero, y un FE viejo borraría los campos nuevos.
5. **SQL en prod antes de cada deploy.** El `ALTER TYPE ... ADD VALUE` va solo, en su propia transacción, antes de usar el valor nuevo.
6. **Borrado de cuenta**: con borrado lógico, follows, bloqueos, avisos y denuncias se borran a mano (el `ON DELETE CASCADE` no actúa).
