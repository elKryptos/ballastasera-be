package com.kryptosystems.ballastasera.validations;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class EventTimingValidator implements ConstraintValidator<ValidEventTiming, EventTimeRange> {

    @Override
    public boolean isValid(EventTimeRange value, ConstraintValidatorContext context) {
        // Datos obligatorios que los gestionan el @NotNull en el dto con la validation
        if (value == null || value.getStartAt() == null || value.getEndAt() == null) return true;
        if (value.getStartAt().isBefore(value.getEndAt())) return true;
        // Este bloque es opcional, pero mejor si gestionado ya que sin esto, el error resulta asociado al
        // al entero DTO de input y no al campo endAt, lo que es menos claro para el usuario.
        // y en la response con el 400 el frontend puede mostrar el error en el campo endAt.
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("endAt")
                .addConstraintViolation();
        return false;
    }
}
