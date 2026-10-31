package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.enums.OrganizerRequestStatus;
import com.kryptosystems.ballastasera.enums.OrganizerRequestType;
import com.kryptosystems.ballastasera.models.entities.OrganizerRequests;
import com.kryptosystems.ballastasera.models.entities.Organizers;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.models.mappers.OrganizerMapper;
import com.kryptosystems.ballastasera.repositories.EventSeriesRepository;
import com.kryptosystems.ballastasera.repositories.EventsRepository;
import com.kryptosystems.ballastasera.repositories.OrganizerRequestsRepository;
import com.kryptosystems.ballastasera.repositories.OrganizersRepository;
import com.kryptosystems.ballastasera.services.manager.UsersService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizersServiceImplTest {

    private static final UUID ORGANIZER_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock
    private OrganizersRepository organizersRepository;

    @Mock
    private OrganizerRequestsRepository organizerRequestsRepository;

    @Mock
    private EventsRepository eventsRepository;

    @Mock
    private EventSeriesRepository eventSeriesRepository;

    @Mock
    private UsersService usersService;

    @Mock
    private OrganizerMapper organizerMapper;

    @InjectMocks
    private OrganizersServiceImpl organizersService;

    /** El rol se baja después de borrar: si no, countByUserId todavía vería el organizador. */
    @Test
    void deleteRemovesOrganizerAndThenDemotesOwner() {
        Organizers organizer = organizer(user(OWNER_ID));
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));
        when(organizerRequestsRepository.existsByOrganizerIdAndUserIdAndTypeAndStatus(
                ORGANIZER_ID, OWNER_ID, OrganizerRequestType.NEW, OrganizerRequestStatus.APPROVED)).thenReturn(true);

        organizersService.delete(ORGANIZER_ID, OWNER_ID);

        InOrder inOrder = inOrder(organizersRepository, usersService);
        inOrder.verify(organizersRepository).delete(organizer);
        inOrder.verify(usersService).demoteToUser(OWNER_ID);
    }

    @Test
    void deleteByNonOwnerIsRejectedAndDeletesNothing() {
        Organizers organizer = organizer(user(OWNER_ID));
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));

        assertThatThrownBy(() -> organizersService.delete(ORGANIZER_ID, OTHER_USER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(organizersRepository, never()).delete(any());
        verifyNoInteractions(usersService);
    }

    /** D13: llegó por CLAIM, no hay NEW APPROVED de ese usuario (false por defecto en Mockito). */
    @Test
    void deleteOfOrganizerNotCreatedByOwnerIsRejectedAndDeletesNothing() {
        Organizers organizer = organizer(user(OWNER_ID));
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));

        assertThatThrownBy(() -> organizersService.delete(ORGANIZER_ID, OWNER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(organizersRepository, never()).delete(any());
        verifyNoInteractions(usersService);
    }

    /** Sin CLAIM PENDING: Mockito devuelve una lista vacía por defecto, no hace falta stub. */
    @Test
    void deleteAsAdminRemovesOrganizerAndThenDemotesOwner() {
        Organizers organizer = organizer(user(OWNER_ID));
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));

        organizersService.deleteAsAdmin(ORGANIZER_ID);

        InOrder inOrder = inOrder(organizersRepository, usersService);
        inOrder.verify(organizersRepository).delete(organizer);
        inOrder.verify(usersService).demoteToUser(OWNER_ID);
    }

    /** Problema 9 del plan: las CLAIM PENDING pasan a CANCELLED. Si no, ON DELETE SET NULL las dejaría
     * PENDING sin organizer_id y la base de datos rechazaría el borrado (chk_org_request_claim). */
    @Test
    void deleteAsAdminCancelsPendingClaimsOfUnclaimedOrganizer() {
        Organizers organizer = organizer(null);
        OrganizerRequests anaClaim = pendingClaim(organizer);
        OrganizerRequests marcoClaim = pendingClaim(organizer);
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.of(organizer));
        when(organizerRequestsRepository.findByOrganizerIdAndTypeAndStatus(
                ORGANIZER_ID, OrganizerRequestType.CLAIM, OrganizerRequestStatus.PENDING))
                .thenReturn(List.of(anaClaim, marcoClaim));

        organizersService.deleteAsAdmin(ORGANIZER_ID);

        assertThat(List.of(anaClaim, marcoClaim)).allSatisfy(claim -> {
            assertThat(claim.getStatus()).isEqualTo(OrganizerRequestStatus.CANCELLED);
            assertThat(claim.getRejectionMessage()).isEqualTo("La pagina è stata rimossa.");
        });
        verify(organizersRepository).delete(organizer);
        verifyNoInteractions(usersService);
    }

    /** Antes deleteById ignoraba un id inexistente; ahora hay que cargar el organizador para saber su dueño. */
    @Test
    void deleteAsAdminThrowsWhenOrganizerDoesNotExist() {
        when(organizersRepository.findById(ORGANIZER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizersService.deleteAsAdmin(ORGANIZER_ID))
                .isInstanceOf(EntityNotFoundException.class);

        verify(organizersRepository, never()).delete(any());
    }

    private static Organizers organizer(Users owner) {
        Organizers organizer = new Organizers();
        organizer.setId(ORGANIZER_ID);
        organizer.setUser(owner);
        organizer.setClaimed(owner != null);
        return organizer;
    }

    private static Users user(UUID id) {
        Users user = new Users();
        user.setId(id);
        return user;
    }

    /** status = PENDING por defecto en la entidad. */
    private static OrganizerRequests pendingClaim(Organizers organizer) {
        OrganizerRequests claim = new OrganizerRequests();
        claim.setType(OrganizerRequestType.CLAIM);
        claim.setOrganizer(organizer);
        return claim;
    }
}
