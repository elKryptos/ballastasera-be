package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.enums.ContactVisibility;
import com.kryptosystems.ballastasera.enums.DanceLevel;
import com.kryptosystems.ballastasera.enums.DanceRole;
import com.kryptosystems.ballastasera.enums.Gender;
import com.kryptosystems.ballastasera.enums.UserRole;
import com.kryptosystems.ballastasera.models.dtos.UserDanceStyleUpdateDto;
import com.kryptosystems.ballastasera.models.dtos.UserUpdateDto;
import com.kryptosystems.ballastasera.models.entities.DanceStyles;
import com.kryptosystems.ballastasera.models.entities.UserDanceStyles;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.models.mappers.UserMapper;
import com.kryptosystems.ballastasera.repositories.DanceStylesRepository;
import com.kryptosystems.ballastasera.repositories.EventAttendanceRepository;
import com.kryptosystems.ballastasera.repositories.EventsRepository;
import com.kryptosystems.ballastasera.repositories.FavoritesRepository;
import com.kryptosystems.ballastasera.repositories.OrganizersRepository;
import com.kryptosystems.ballastasera.repositories.UserBlocksRepository;
import com.kryptosystems.ballastasera.repositories.UsersRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsersServiceImplTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VIEWER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID EVENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_EVENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final Long SALSA_ID = 1L;
    private static final Long BACHATA_ID = 2L;
    private static final Long KIZOMBA_ID = 3L;

    @Mock
    private UsersRepository usersRepository;

    @Mock
    private DanceStylesRepository danceStylesRepository;

    /** Mapper real: updateProfile se prueba con el mapeo del formulario incluido. */
    @Spy
    private UserMapper userMapper = Mappers.getMapper(UserMapper.class);

    @Mock
    private FavoritesRepository favoritesRepository;

    @Mock
    private EventAttendanceRepository eventAttendanceRepository;

    @Mock
    private EventsRepository eventsRepository;

    @Mock
    private OrganizersRepository organizersRepository;

    @Mock
    private UserBlocksRepository userBlocksRepository;

    @InjectMocks
    private UsersServiceImpl usersService;

    /** Los contadores se bajan antes de borrar al usuario: despues la base de datos
     * ya borro en cascada sus favoritos y asistencias y no se sabe a que eventos restar. */
    @Test
    void deleteByIdDecrementsLikesAndGoingCountsBeforeDeletingUser() {
        when(favoritesRepository.findEventsIdsByUserId(USER_ID)).thenReturn(List.of(EVENT_ID));
        when(eventAttendanceRepository.findEventsIdsByUserId(USER_ID)).thenReturn(List.of(EVENT_ID, OTHER_EVENT_ID));

        usersService.deleteById(USER_ID);

        InOrder inOrder = inOrder(eventsRepository, usersRepository);
        inOrder.verify(eventsRepository).decrementLikesCountForEvents(List.of(EVENT_ID));
        inOrder.verify(eventsRepository).decrementGoingCountForEvents(List.of(EVENT_ID, OTHER_EVENT_ID));
        inOrder.verify(usersRepository).deleteById(USER_ID);
    }

    @Test
    void deleteByIdLeavesCountersAloneWhenUserHasNoLikesOrAttendance() {
        usersService.deleteById(USER_ID);

        verify(eventsRepository, never()).decrementLikesCountForEvents(any());
        verify(eventsRepository, never()).decrementGoingCountForEvents(any());
        verify(usersRepository).deleteById(USER_ID);
    }

    @Test
    void demoteToUserSetsUserRoleWhenOrganizerHasNoOrganizersLeft() {
        Users user = user(UserRole.ORGANIZER);
        when(usersRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(organizersRepository.countByUserId(USER_ID)).thenReturn(0L);

        usersService.demoteToUser(USER_ID);

        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        verify(usersRepository).save(user);
    }

    @Test
    void demoteToUserKeepsOrganizerRoleWhileUserStillOwnsAnOrganizer() {
        Users user = user(UserRole.ORGANIZER);
        when(usersRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(organizersRepository.countByUserId(USER_ID)).thenReturn(1L);

        usersService.demoteToUser(USER_ID);

        assertThat(user.getRole()).isEqualTo(UserRole.ORGANIZER);
    }

    /** Un ADMIN no se toca: ni siquiera se cuentan sus organizadores (el && corta antes). */
    @Test
    void demoteToUserNeverDemotesAnAdmin() {
        Users user = user(UserRole.ADMIN);
        when(usersRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        usersService.demoteToUser(USER_ID);

        assertThat(user.getRole()).isEqualTo(UserRole.ADMIN);
        verify(organizersRepository, never()).countByUserId(any());
    }

    /** Reemplazo completo del formulario: "" pasa a null y whatsappVisibility null queda MUTUALS. */
    @Test
    void updateProfileCopiesTheFormAndNormalizesEmptyInputs() {
        Users user = user(UserRole.USER);
        user.setInstagram("old.handle");
        user.setWhatsapp("+390000000000");
        user.setWhatsappVisibility(ContactVisibility.NOBODY);
        when(usersRepository.findByIdWithDanceStyles(USER_ID)).thenReturn(Optional.of(user));
        UserUpdateDto dto = form();
        dto.setInstagram("@juan.baila");
        dto.setWhatsapp("");
        dto.setBirthDate(LocalDate.of(1995, 4, 12));
        dto.setGender(Gender.MALE);
        dto.setDanceRole(DanceRole.LEADER);

        Users updated = usersService.updateProfile(USER_ID, dto);

        assertThat(updated.getInstagram()).isEqualTo("juan.baila");
        assertThat(updated.getWhatsapp()).isNull();
        assertThat(updated.getWhatsappVisibility()).isEqualTo(ContactVisibility.MUTUALS);
        assertThat(updated.getBirthDate()).isEqualTo(LocalDate.of(1995, 4, 12));
        assertThat(updated.getGender()).isEqualTo(Gender.MALE);
        assertThat(updated.getDanceRole()).isEqualTo(DanceRole.LEADER);
    }

    /** Por diferencia: Salsa sigue (mismo objeto, nivel nuevo), Bachata se quita, Kizomba se agrega
     * con los ids del embeddable puestos. Sin save(): se guarda al commit (ver javadoc de updateProfile). */
    @Test
    void updateProfileSyncsDanceStylesByDifferenceWithoutSave() {
        Users user = user(UserRole.USER);
        UserDanceStyles salsa = userStyle(user, style(SALSA_ID, "Salsa"), DanceLevel.BEGINNER);
        UserDanceStyles bachata = userStyle(user, style(BACHATA_ID, "Bachata"), DanceLevel.INTERMEDIATE);
        user.getDanceStyles().add(salsa);
        user.getDanceStyles().add(bachata);
        when(usersRepository.findByIdWithDanceStyles(USER_ID)).thenReturn(Optional.of(user));
        DanceStyles kizomba = style(KIZOMBA_ID, "Kizomba");
        when(danceStylesRepository.findAllById(Set.of(KIZOMBA_ID))).thenReturn(List.of(kizomba));
        UserUpdateDto dto = form();
        dto.setDanceStyles(List.of(styleForm(SALSA_ID, DanceLevel.ADVANCED), styleForm(KIZOMBA_ID, DanceLevel.BEGINNER)));

        Users updated = usersService.updateProfile(USER_ID, dto);

        assertThat(updated.getDanceStyles()).hasSize(2).contains(salsa).doesNotContain(bachata);
        assertThat(salsa.getLevel()).isEqualTo(DanceLevel.ADVANCED);
        UserDanceStyles added = updated.getDanceStyles().stream()
                .filter(uds -> uds != salsa)
                .findFirst()
                .orElseThrow();
        assertThat(added.getUser()).isSameAs(user);
        assertThat(added.getDanceStyle()).isSameAs(kizomba);
        assertThat(added.getLevel()).isEqualTo(DanceLevel.BEGINNER);
        assertThat(added.getId().getUserId()).isEqualTo(USER_ID);
        assertThat(added.getId().getDanceStyleId()).isEqualTo(KIZOMBA_ID);
        verify(usersRepository, never()).save(any());
    }

    @Test
    void updateProfileKeepsTheLastLevelWhenAStyleIsRepeated() {
        Users user = user(UserRole.USER);
        UserDanceStyles salsa = userStyle(user, style(SALSA_ID, "Salsa"), DanceLevel.BEGINNER);
        user.getDanceStyles().add(salsa);
        when(usersRepository.findByIdWithDanceStyles(USER_ID)).thenReturn(Optional.of(user));
        UserUpdateDto dto = form();
        dto.setDanceStyles(List.of(styleForm(SALSA_ID, DanceLevel.INTERMEDIATE), styleForm(SALSA_ID, DanceLevel.TEACHER)));

        Users updated = usersService.updateProfile(USER_ID, dto);

        assertThat(updated.getDanceStyles()).containsExactly(salsa);
        assertThat(salsa.getLevel()).isEqualTo(DanceLevel.TEACHER);
        verifyNoInteractions(danceStylesRepository);
    }

    /** danceStyles null en el formulario completo = sin estilos. */
    @Test
    void updateProfileWithoutDanceStylesRemovesThemAll() {
        Users user = user(UserRole.USER);
        user.getDanceStyles().add(userStyle(user, style(SALSA_ID, "Salsa"), DanceLevel.BEGINNER));
        when(usersRepository.findByIdWithDanceStyles(USER_ID)).thenReturn(Optional.of(user));

        Users updated = usersService.updateProfile(USER_ID, form());

        assertThat(updated.getDanceStyles()).isEmpty();
        verifyNoInteractions(danceStylesRepository);
    }

    @Test
    void updateProfileRejectsAnUnknownDanceStyle() {
        when(usersRepository.findByIdWithDanceStyles(USER_ID)).thenReturn(Optional.of(user(UserRole.USER)));
        when(danceStylesRepository.findAllById(Set.of(KIZOMBA_ID))).thenReturn(List.of());
        UserUpdateDto dto = form();
        dto.setDanceStyles(List.of(styleForm(KIZOMBA_ID, DanceLevel.BEGINNER)));

        assertThatThrownBy(() -> usersService.updateProfile(USER_ID, dto))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void findPublicProfileReturnsAPublicUserToOthers() {
        Users user = user(UserRole.USER);
        when(usersRepository.findByIdWithDanceStyles(USER_ID)).thenReturn(Optional.of(user));

        assertThat(usersService.findPublicProfile(VIEWER_ID, USER_ID)).isSameAs(user);
    }

    @Test
    void findPublicProfileHidesAPrivateProfileFromOthers() {
        Users user = user(UserRole.USER);
        user.setShowProfilePublic(false);
        when(usersRepository.findByIdWithDanceStyles(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> usersService.findPublicProfile(VIEWER_ID, USER_ID))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void findPublicProfileHidesABannedUserFromOthers() {
        Users user = user(UserRole.USER);
        user.setBanned(true);
        when(usersRepository.findByIdWithDanceStyles(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> usersService.findPublicProfile(VIEWER_ID, USER_ID))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void findPublicProfileHidesADeletedUserFromOthers() {
        Users user = user(UserRole.USER);
        user.setDeleted(true);
        when(usersRepository.findByIdWithDanceStyles(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> usersService.findPublicProfile(VIEWER_ID, USER_ID))
                .isInstanceOf(EntityNotFoundException.class);
    }

    /** Mismo 404 que un perfil privado: no se revela el bloqueo. */
    @Test
    void findPublicProfileHidesAUserWithABlockInEitherDirection() {
        when(usersRepository.findByIdWithDanceStyles(USER_ID)).thenReturn(Optional.of(user(UserRole.USER)));
        when(userBlocksRepository.existsBetween(VIEWER_ID, USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> usersService.findPublicProfile(VIEWER_ID, USER_ID))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void findPublicProfileShowsTheOwnerTheirPrivateProfile() {
        Users user = user(UserRole.USER);
        user.setShowProfilePublic(false);
        when(usersRepository.findByIdWithDanceStyles(USER_ID)).thenReturn(Optional.of(user));

        assertThat(usersService.findPublicProfile(USER_ID, USER_ID)).isSameAs(user);
    }

    private static Users user(UserRole role) {
        Users user = new Users();
        user.setId(USER_ID);
        user.setRole(role);
        return user;
    }

    /** Formulario minimo valido: showProfilePublic es lo unico obligatorio. */
    private static UserUpdateDto form() {
        UserUpdateDto dto = new UserUpdateDto();
        dto.setShowProfilePublic(true);
        return dto;
    }

    private static DanceStyles style(Long id, String name) {
        DanceStyles style = new DanceStyles();
        style.setId(id);
        style.setName(name);
        style.setSlug(name.toLowerCase());
        return style;
    }

    /** Como llega de la DB: con los ids del embeddable ya puestos. */
    private static UserDanceStyles userStyle(Users user, DanceStyles style, DanceLevel level) {
        UserDanceStyles userStyle = new UserDanceStyles();
        userStyle.getId().setUserId(user.getId());
        userStyle.getId().setDanceStyleId(style.getId());
        userStyle.setUser(user);
        userStyle.setDanceStyle(style);
        userStyle.setLevel(level);
        return userStyle;
    }

    private static UserDanceStyleUpdateDto styleForm(Long danceStyleId, DanceLevel level) {
        UserDanceStyleUpdateDto dto = new UserDanceStyleUpdateDto();
        dto.setDanceStyleId(danceStyleId);
        dto.setLevel(level);
        return dto;
    }
}
