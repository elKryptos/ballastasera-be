package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.models.entities.DanceStyles;
import com.kryptosystems.ballastasera.repositories.DanceStylesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DanceStylesServiceImplTest {

    @Mock
    private DanceStylesRepository danceStylesRepository;

    @InjectMocks
    private DanceStylesServiceImpl danceStylesService;

    @Test
    void findAllReturnsDanceStylesOrderedByName() {
        DanceStyles milano = new DanceStyles();
        milano.setId(1L);
        milano.setName("Milonga");
        milano.setSlug("milonga");

        DanceStyles bachata = new DanceStyles();
        bachata.setId(2L);
        bachata.setName("Bachata");
        bachata.setSlug("bachata");

        when(danceStylesRepository.findAll())
                .thenReturn(List.of(milano, bachata));

        List<DanceStyles> result = danceStylesService.findAll();

        assertEquals(
                List.of("Bachata", "Milonga"),
                result.stream().map(DanceStyles::getName).toList()
        );
    }
}
