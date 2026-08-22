package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.models.dtos.DanceStyleDto;
import com.kryptosystems.ballastasera.models.mappers.DanceStylesMapper;
import com.kryptosystems.ballastasera.services.manager.DanceStylesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.kryptosystems.ballastasera.utilities.RestConstants.DANCE_STYLES;

@RestController
@RequestMapping(DANCE_STYLES)
@RequiredArgsConstructor
public class DanceStylesController {

    private static final String GET_ALL_DANCE_STYLES = "";
    private static final String GET_DANCE_STYLE = "/{id}";

    private final DanceStylesService danceStylesService;
    private final DanceStylesMapper danceStylesMapper;

    @GetMapping(GET_ALL_DANCE_STYLES)
    public ResponseEntity<List<DanceStyleDto>> getAllDanceStyles() {
        List<DanceStyleDto> danceStyle = danceStylesService.findAll()
                .stream()
                .map(danceStylesMapper::toDto)
                .toList();
        return ResponseEntity.ok(danceStyle);
    }

    @GetMapping(GET_DANCE_STYLE)
    public ResponseEntity<DanceStyleDto> getDanceStyle(@PathVariable Long id) {
        DanceStyleDto danceStyle = danceStylesMapper.toDto(danceStylesService.findById(id));
        return ResponseEntity.ok(danceStyle);
    }
}
