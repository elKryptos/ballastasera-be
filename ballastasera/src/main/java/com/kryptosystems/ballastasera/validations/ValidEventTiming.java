package com.kryptosystems.ballastasera.validations;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EventTimingValidator.class)
public @interface ValidEventTiming {
    String message() default "endAt must be greater than startAt";
    Class<?> [] groups() default {};
    Class<? extends Payload>[] payload() default {};
}