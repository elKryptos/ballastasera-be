package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.repositories.EventAttendanceRepository;
import com.kryptosystems.ballastasera.repositories.EventsRepository;
import com.kryptosystems.ballastasera.repositories.FavoritesRepository;
import com.kryptosystems.ballastasera.repositories.UsersRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsersServiceImplTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID EVENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_EVENT_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Mock
    private UsersRepository usersRepository;

    @Mock
    private FavoritesRepository favoritesRepository;

    @Mock
    private EventAttendanceRepository eventAttendanceRepository;

    @Mock
    private EventsRepository eventsRepository;

    @InjectMocks
    private UsersServiceImpl usersService;

    /** Los contadores se bajan antes de borrar al usuario: despues la base de datos
     * ya borro en cascada sus favoritos y asistencias y no se sabe a que eventos restar. */
    @Test
    void deleteByIdDecrementsLikesAndGoingCountsBeforeDeletingUser() {
        when(favoritesRepository.findEventIdsByUserId(USER_ID)).thenReturn(List.of(EVENT_ID));
        when(eventAttendanceRepository.findEventIdsByUserId(USER_ID)).thenReturn(List.of(EVENT_ID, OTHER_EVENT_ID));

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
}
