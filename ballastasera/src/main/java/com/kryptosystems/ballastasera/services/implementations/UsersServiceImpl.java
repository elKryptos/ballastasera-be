package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.config.RedisCacheConfig;
import com.kryptosystems.ballastasera.enums.AvatarSource;
import com.kryptosystems.ballastasera.enums.DanceLevel;
import com.kryptosystems.ballastasera.enums.OrganizerType;
import com.kryptosystems.ballastasera.enums.UserRole;
import com.kryptosystems.ballastasera.enums.UsernameStatus;
import com.kryptosystems.ballastasera.events.UserAvatarDeletedEvent;
import com.kryptosystems.ballastasera.exceptions.FileTooLargeException;
import com.kryptosystems.ballastasera.exceptions.MediaStorageException;
import com.kryptosystems.ballastasera.exceptions.UsernameUnavailableException;
import com.kryptosystems.ballastasera.models.dtos.UserDanceStyleUpdateDto;
import com.kryptosystems.ballastasera.models.dtos.UserUpdateDto;
import com.kryptosystems.ballastasera.models.entities.DanceStyles;
import com.kryptosystems.ballastasera.models.entities.Organizers;
import com.kryptosystems.ballastasera.models.entities.UserDanceStyles;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.models.mappers.UserMapper;
import com.kryptosystems.ballastasera.repositories.*;
import com.kryptosystems.ballastasera.services.manager.ObjectStorageService;
import com.kryptosystems.ballastasera.services.manager.UsersService;
import com.kryptosystems.ballastasera.services.manager.WebpConverterService;
import com.kryptosystems.ballastasera.utilities.ImageTypeValidator;
import com.kryptosystems.ballastasera.utilities.InstagramUtils;
import com.kryptosystems.ballastasera.utilities.UsernameUtils;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UsersServiceImpl implements UsersService {

    private static final long AVATAR_MAX_BYTES = 5L * 1024 * 1024;
    private static final int AVATAR_MAX_LONG_EDGE = 512;
    /** El UNIQUE de users.username (ballastasera_schema.sql): Postgres le da este nombre. */
    private static final String USERNAME_UNIQUE_CONSTRAINT = "users_username_key";

    private final UsersRepository usersRepository;
    private final DanceStylesRepository danceStylesRepository;
    private final UserMapper userMapper;
    private final FavoritesRepository favoritesRepository;
    private final EventAttendanceRepository eventAttendanceRepository;
    private final EventsRepository eventsRepository;
    private final OrganizersRepository organizersRepository;
    private final UserBlocksRepository userBlocksRepository;
    private final WebpConverterService webpConverterService;
    private final ObjectStorageService objectStorageService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public List<Users> findAll() {
        return usersRepository.findAll();
    }

    @Override
    public Users findById(UUID id) {
        return usersRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id " + id));
    }

    @Override
    public Users findByIdWithDanceStyles(UUID id) {
        return usersRepository.findByIdWithDanceStyles(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id " + id));
    }

    @Override
    public Users findByEmail(String email) {
        return usersRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found with email " + email));
    }

    @Override
    public Users findByGoogleId(String googleId) {
        return usersRepository.findByGoogleId(googleId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with googleId " + googleId));
    }

    @Override
    public Users save(Users user) {
        return usersRepository.save(user);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = RedisCacheConfig.EVENT_DETAIL, allEntries = true)
    public void deleteById(UUID id) {
        List<UUID> favoriteEventIds = favoritesRepository.findEventsIdsByUserId(id);
        if (!favoriteEventIds.isEmpty()) {
            eventsRepository.decrementLikesCountForEvents(favoriteEventIds);
        }
        List<UUID> attendedEventIds = eventAttendanceRepository.findEventsIdsByUserId(id);
        if (!attendedEventIds.isEmpty()) {
            eventsRepository.decrementGoingCountForEvents(attendedEventIds);
        }
        // La foto subida se borra del bucket despues del commit (RGPD: no queda nada suyo)
        usersRepository.findById(id).map(Users::getCustomAvatarUrl).ifPresent(this::discardAvatar);
        usersRepository.deleteById(id);
    }

    /**
     * OJO: aca NO hay usersRepository.save(user) a propósito. NO quitar el @Transactional.
     *
     * Como se guarda: user se carga dentro de esta transaccion, asi que queda "managed".
     * Al terminar el método, el proxy de @Transactional hace flush + commit y Hibernate
     * compara user con como estaba al cargarlo (dirty checking):
     *  - campos cambiados en user          -> UPDATE users
     *  - estilo nuevo en user.danceStyles  -> INSERT user_dance_styles (cascade ALL)
     *  - estilo quitado (orphanRemoval)    -> DELETE user_dance_styles
     *  - nivel cambiado                    -> UPDATE user_dance_styles
     * Si el commit falla (ej. PK duplicada), la excepción llega igual al controller.
     *
     * Por que sin save(): user ya tiene id, asi que save() hace merge(). El merge se propaga
     * a los estilos nuevos y, como su id (user_id, dance_style_id) ya viene seteado,
     * Hibernate no sabe si existen y hace un SELECT por cada uno antes del INSERT.
     * Sin save() los persiste directo, sin esos SELECT.
     *
     * Si un dia se quita el @Transactional o user se carga fuera de este método,
     * los cambios se pierden SIN ERROR: en ese caso hay que volver a poner el save().
     */
    @Override
    @Transactional
    public Users updateProfile(UUID userId, UserUpdateDto dto) {
        Users user = findByIdWithDanceStyles(userId);
        userMapper.updateProfileFromDto(dto, user);
        // Si el input viene vacio ("") en la DB queda null
        user.setInstagram(InstagramUtils.normalizeHandle(user.getInstagram()));
        user.setWhatsapp(StringUtils.hasText(user.getWhatsapp()) ? user.getWhatsapp() : null);
        user.setBio(StringUtils.hasText(user.getBio()) ? user.getBio().trim() : null);
        user.setSchoolName(StringUtils.hasText(user.getSchoolName()) ? user.getSchoolName().trim() : null);
        syncSchool(user, dto.getSchoolOrganizerId());
        syncDanceStyles(user, dto.getDanceStyles() != null ? dto.getDanceStyles() : List.of());
        return user; // sin save(): se guarda al commit, ver javadoc
    }

    /** Sin save(), como updateProfile. La foto anterior se borra del bucket despues del commit. */
    @Override
    @Transactional
    public Users uploadAvatar(UUID userId, MultipartFile file) {
        if (file.getSize() > AVATAR_MAX_BYTES) {
            throw new FileTooLargeException("Avatar must be at most 5 MB");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new MediaStorageException("Failed to read avatar file", e);
        }
        ImageTypeValidator.detectExtension(content);
        byte[] webpContent = webpConverterService.convertToWebp(content, AVATAR_MAX_LONG_EDGE);

        Users user = findByIdWithDanceStyles(userId);
        String previousAvatarUrl = user.getCustomAvatarUrl();
        String avatarUrl = objectStorageService.uploadUserAvatar(userId, webpContent);
        user.setCustomAvatarUrl(avatarUrl);
        user.setAvatarSource(AvatarSource.CUSTOM);
        user.setAvatarUrl(avatarUrl);
        discardAvatar(previousAvatarUrl);
        return user;
    }

    /** Sin save(), como updateProfile: el dirty checking hace el UPDATE en el commit. */
    @Override
    @Transactional
    public void setEmailNotifications(UUID userId, boolean enabled) {
        usersRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id " + userId))
                .setEmailNotifications(enabled);
    }

    /** Sin save(), como updateProfile. La foto propia no se guarda para despues: se borra del bucket. */
    @Override
    @Transactional
    public Users changeAvatarSource(UUID userId, AvatarSource source) {
        Users user = findByIdWithDanceStyles(userId);
        String previousAvatarUrl = user.getCustomAvatarUrl();
        user.setCustomAvatarUrl(null);
        user.setAvatarSource(source);
        user.setAvatarUrl(source == AvatarSource.GOOGLE ? user.getGoogleAvatarUrl() : null);
        discardAvatar(previousAvatarUrl);
        return user;
    }

    @Override
    public Users findPublicProfile(UUID viewerId, UUID userId) {
        Users user = findByIdWithDanceStyles(userId);
        if (!isVisibleTo(viewerId, user)) {
            throw new EntityNotFoundException("User not found with id " + userId);
        }
        return user;
    }

    /** Mismo 404 si nadie lo tiene o si no se puede ver. El mensaje no lleva el id: no se revela de quien es. */
    @Override
    public Users findPublicProfileByUsername(UUID viewerId, String username) {
        return usersRepository.findByUsernameWithDanceStyles(username.toLowerCase(Locale.ROOT))
                .filter(user -> isVisibleTo(viewerId, user))
                .orElseThrow(() -> new EntityNotFoundException("User not found with username " + username));
    }

    @Override
    public UsernameStatus checkUsername(UUID userId, String username) {
        if (!UsernameUtils.isValid(username)) {
            return UsernameStatus.INVALID;
        }
        if (UsernameUtils.isReserved(username)) {
            return UsernameStatus.RESERVED;
        }
        if (usersRepository.existsByUsernameAndIdNot(username, userId)) {
            return UsernameStatus.TAKEN;
        }
        return UsernameStatus.AVAILABLE;
    }

    /**
     * El formato ya lo valido UsernameUpdateDto. Se comprueba antes para dar el 409 con su code, y el indice unico
     * cubre a dos personas que piden el mismo a la vez: saveAndFlush hace salir el UPDATE aqui para traducir la
     * violacion, como OrganizerRequestsServiceImpl.create.
     */
    @Override
    @Transactional
    public void changeUsername(UUID userId, String username) {
        String wanted = StringUtils.hasText(username) ? username : null;
        if (wanted != null) {
            UsernameStatus status = checkUsername(userId, wanted);
            if (status == UsernameStatus.RESERVED) {
                throw new UsernameUnavailableException(UsernameUnavailableException.USERNAME_RESERVED,
                        "Username " + wanted + " is reserved");
            }
            if (status == UsernameStatus.TAKEN) {
                throw new UsernameUnavailableException(UsernameUnavailableException.USERNAME_TAKEN,
                        "Username " + wanted + " is taken");
            }
        }
        Users user = findById(userId);
        user.setUsername(wanted);
        try {
            usersRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            if (isUsernameTakenViolation(ex)) {
                throw new UsernameUnavailableException(UsernameUnavailableException.USERNAME_TAKEN,
                        "Username " + wanted + " is taken");
            }
            throw ex;
        }
    }

    /** Con un bloqueo en cualquier sentido da el mismo 404 que un perfil privado: no se revela el bloqueo. */
    private boolean isVisibleTo(UUID viewerId, Users user) {
        if (user.getId().equals(viewerId)) {
            return true;
        }
        return user.isShowProfilePublic() && !user.isDeleted() && !user.isBanned()
                && !userBlocksRepository.existsBetween(viewerId, user.getId());
    }

    private boolean isUsernameTakenViolation(DataIntegrityViolationException ex) {
        String cause = ex.getMostSpecificCause().getMessage();
        return cause != null && cause.contains(USERNAME_UNIQUE_CONSTRAINT);
    }

    /**
     * Escuela enlazada: tiene que ser un organizer SCHOOL verificado (los mismos que salen en el buscador).
     * Si viene, el texto libre se borra: o una o la otra.
     */
    private void syncSchool(Users user, UUID schoolOrganizerId) {
        if (schoolOrganizerId == null) {
            user.setSchoolOrganizer(null);
            return;
        }
        Organizers school = organizersRepository.findById(schoolOrganizerId)
                .filter(organizer -> organizer.getType() == OrganizerType.SCHOOL && organizer.isVerified())
                .orElseThrow(() -> new EntityNotFoundException("School not found with id " + schoolOrganizerId));
        user.setSchoolOrganizer(school);
        user.setSchoolName(null);
    }

    private void discardAvatar(String avatarUrl) {
        if (avatarUrl != null) {
            eventPublisher.publishEvent(new UserAvatarDeletedEvent(avatarUrl));
        }
    }

    /**
     * Sincroniza por diferencia y no con clear() + add(): si un estilo se quita y se vuelve a poner
     * en el mismo flush, Hibernate puede hacer el INSERT antes del DELETE y saltar la PK (user_id, dance_style_id).
     * requested vacio = borra todos.
     */
    private void syncDanceStyles(Users user, List<UserDanceStyleUpdateDto> requested) {
        Map<Long, DanceLevel> levelByStyleId = requested.stream().collect(Collectors.toMap(
                UserDanceStyleUpdateDto::getDanceStyleId,
                UserDanceStyleUpdateDto::getLevel,
                (first, last) -> last));

        // 1. Quita los que ya no vienen y actualiza el nivel de los que siguen
        user.getDanceStyles().removeIf(uds -> !levelByStyleId.containsKey(uds.getId().getDanceStyleId()));
        user.getDanceStyles().forEach(uds -> uds.setLevel(levelByStyleId.get(uds.getId().getDanceStyleId())));

        // 2. Agrega los nuevos
        Set<Long> newStyleIds = new HashSet<>(levelByStyleId.keySet());
        user.getDanceStyles().forEach(uds -> newStyleIds.remove(uds.getId().getDanceStyleId()));
        if (newStyleIds.isEmpty()) {
            return;
        }
        List<DanceStyles> styles = danceStylesRepository.findAllById(newStyleIds);
        if (styles.size() != newStyleIds.size()) {
            throw new EntityNotFoundException("Dance style not found among ids " + newStyleIds);
        }
        for (DanceStyles style : styles) {
            UserDanceStyles userStyle = new UserDanceStyles();
            // Ids a mano: no depender de que @MapsId los rellene en el merge por cascade
            userStyle.getId().setUserId(user.getId());
            userStyle.getId().setDanceStyleId(style.getId());
            userStyle.setUser(user);
            userStyle.setDanceStyle(style);
            userStyle.setLevel(levelByStyleId.get(style.getId()));
            user.getDanceStyles().add(userStyle);
        }
    }

    @Override
    public Users promoteToOrganizer(UUID userId){
        Users user = findById(userId);
        /** Usar == para comparar el user.getRole() es mas seguro, si en caso user.getRole() es null,
         *  entonces da false, mientras que al usar equals podria lanzar una NPE */
        if (user.getRole() == UserRole.USER){
            user.setRole(UserRole.ORGANIZER);
        }
        return usersRepository.save(user);
    }

    @Override
    public Users demoteToUser(UUID userId) {
        Users user = findById(userId);
        if (user.getRole() == UserRole.ORGANIZER && organizersRepository.countByUserId(userId) == 0) {
            user.setRole(UserRole.USER);
        }
        return usersRepository.save(user);
    }
}
