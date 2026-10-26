package com.kryptosystems.ballastasera.validations;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** NEW exige name + organizerType (y dirección si el tipo tiene local, D11); CLAIM exige targetOrganizerId. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = OrganizerRequestValidator.class)
public @interface ValidOrganizerRequest {
    String message() default "invalid organizer request";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
