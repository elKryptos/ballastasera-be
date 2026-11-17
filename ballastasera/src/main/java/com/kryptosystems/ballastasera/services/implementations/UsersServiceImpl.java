package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.config.RedisCacheConfig;
import com.kryptosystems.ballastasera.enums.DanceLevel;
import com.kryptosystems.ballastasera.enums.UserRole;
import com.kryptosystems.ballastasera.models.dtos.UserDanceStyleUpdateDto;
import com.kryptosystems.ballastasera.models.dtos.UserUpdateDto;
import com.kryptosystems.ballastasera.models.entities.DanceStyles;
import com.kryptosystems.ballastasera.models.entities.UserDanceStyles;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.models.mappers.UserMapper;
import com.kryptosystems.ballastasera.repositories.*;
import com.kryptosystems.ballastasera.services.manager.UsersService;
import com.kryptosystems.ballastasera.utilities.InstagramUtils;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UsersServiceImpl implements UsersService {

    private final UsersRepository usersRepository;
    private final DanceStylesRepository danceStylesRepository;
    private final UserMapper userMapper;
    private final FavoritesRepository favoritesRepository;
    private final EventAttendanceRepository eventAttendanceRepository;
    private final EventsRepository eventsRepository;
    private final OrganizersRepository organizersRepository;

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
        syncDanceStyles(user, dto.getDanceStyles() != null ? dto.getDanceStyles() : List.of());
        return user; // sin save(): se guarda al commit, ver javadoc
    }

    @Override
    public Users findPublicProfile(UUID viewerId, UUID userId) {
        Users user = findByIdWithDanceStyles(userId);
        boolean isOwner = userId.equals(viewerId);
        if (!isOwner && (!user.isShowProfilePublic() || user.isDeleted() || user.isBanned())) {
            throw new EntityNotFoundException("User not found with id " + userId);
        }
        return user;
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
