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

@RestController
@RequestMapping("/api/dance-styles")
@RequiredArgsConstructor
public class DanceStylesController {
    private final DanceStylesService danceStylesService;
    private final DanceStylesMapper danceStylesMapper;

    @GetMapping
    public ResponseEntity<List<DanceStyleDto>> getAllDanceStyles() {
        List<DanceStyleDto> danceStyle = danceStylesService.findAll()
                .stream()
                .map(danceStylesMapper::toDto)
                .toList();
        return ResponseEntity.ok(danceStyle);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DanceStyleDto> getDanceStyle(@PathVariable Long id) {
        DanceStyleDto danceStyle = danceStylesMapper.toDto(danceStylesService.findById(id));
        return ResponseEntity.ok(danceStyle);
    }
}
