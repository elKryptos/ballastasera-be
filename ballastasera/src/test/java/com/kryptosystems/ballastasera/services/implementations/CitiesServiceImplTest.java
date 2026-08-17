package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.models.entities.Cities;
import com.kryptosystems.ballastasera.repositories.CitiesRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CitiesServiceImplTest {

    @Mock
    private CitiesRepository citiesRepository;

    @InjectMocks
    private CitiesServiceImpl citiesService;

    @Test
    void deactivateMarksExistingCityAsInactive() {
        Cities city = new Cities();
        city.setId(1L);
        city.setName("Milano");
        city.setActive(true);

        when(citiesRepository.findById(1L))
                .thenReturn(Optional.of(city));

        citiesService.deactivate(1L);

        assertFalse(city.isActive());
    }

    @Test
    void findActiveReturnsActiveCitiesOrderedByName() {
        Cities bergamo = new Cities();
        bergamo.setId(1L);
        bergamo.setName("Bergamo");
        bergamo.setActive(true);

        Cities milano = new Cities();
        milano.setId(2L);
        milano.setName("Milano");
        milano.setActive(true);

        when(citiesRepository.findAllByIsActiveTrueOrderByNameAsc())
                .thenReturn(List.of(bergamo, milano));

        List<Cities> result = citiesService.findActive();

        assertEquals(
                List.of("Bergamo", "Milano"),
                result.stream().map(Cities::getName).toList()
        );
    }

    @Test
    void findActiveByIdDoesNotReturnInactiveCity() {
        when(citiesRepository.findByIdAndIsActiveTrue(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                EntityNotFoundException.class,
                () -> citiesService.findActiveById(1L)
        );
    }
}
