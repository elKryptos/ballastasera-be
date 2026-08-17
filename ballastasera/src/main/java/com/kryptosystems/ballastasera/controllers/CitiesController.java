package com.kryptosystems.ballastasera.controllers;

import com.kryptosystems.ballastasera.models.dtos.CityDto;
import com.kryptosystems.ballastasera.models.mappers.CitiesMapper;
import com.kryptosystems.ballastasera.services.manager.CitiesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cities")
@RequiredArgsConstructor
public class CitiesController {
    private final CitiesService citiesService;
    private final CitiesMapper citiesMapper;

    @GetMapping
    public ResponseEntity<List<CityDto>> getCities() {
        List<CityDto> cities = citiesService.findActive()
                .stream()
                .map(citiesMapper::toDto)
                .toList();

        return ResponseEntity.ok(cities);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CityDto> getCity(@PathVariable Long id) {
        CityDto city = citiesMapper.toDto(citiesService.findActiveById(id));

        return ResponseEntity.ok(city);
    }
}
