package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.enums.AttendanceStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AttendanceRequestDto {

    @NotNull
    private AttendanceStatus status;
}