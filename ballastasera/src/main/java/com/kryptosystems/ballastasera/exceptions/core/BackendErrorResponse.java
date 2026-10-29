package com.kryptosystems.ballastasera.exceptions.core;

import com.kryptosystems.ballastasera.exceptions.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import tools.jackson.databind.exc.MismatchedInputException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class BackendErrorResponse {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorDetails> handleNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        log.warn(ex.getMessage());
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getRequestURI(),
                "Resource not found",
                HttpStatus.NOT_FOUND
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorDetails);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDetails> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "Validation failed",
                request.getRequestURI(),
                "One or more fields are invalid",
                HttpStatus.BAD_REQUEST,
                errors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDetails);
    }

    /** @RequestParam/@PathVariable con @Size, @Min, @Max...: Spring los valida sin @Validated y lanza esta
     * excepción. Sin este handler acabaría en el genérico como 500. Mismo formato que handleValidation. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorDetails> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            errors.put(result.getMethodParameter().getParameterName(),
                    result.getResolvableErrors().getFirst().getDefaultMessage());
        }
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "Validation failed",
                request.getRequestURI(),
                "One or more parameters are invalid",
                HttpStatus.BAD_REQUEST,
                errors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDetails);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorDetails> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        log.warn(ex.getMessage());
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getRequestURI(),
                "Access denied",
                HttpStatus.FORBIDDEN
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorDetails);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorDetails> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn(ex.getMessage());
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "Missing request parameter: " + ex.getParameterName(),
                request.getRequestURI(),
                "Required parameter '" + ex.getParameterName() + "' of type " + ex.getParameterType() + " is missing",
                HttpStatus.BAD_REQUEST
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDetails);
    }

    /** Multipart sin la parte esperada (ej. "file" en vez de "logo"). Sin este handler caia en el generico como 500. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorDetails> handleMissingPart(MissingServletRequestPartException ex, HttpServletRequest request) {
        log.warn(ex.getMessage());
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "Missing request part: " + ex.getRequestPartName(),
                request.getRequestURI(),
                "Required multipart part '" + ex.getRequestPartName() + "' is missing",
                HttpStatus.BAD_REQUEST
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDetails);
    }

    /** Ruta que existe pero no con ese método (ej. POST /rest/organizers, borrado en D10). Sin este handler
     * caia en el generico como 500. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorDetails> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn(ex.getMessage());
        String supported = ex.getSupportedMethods() != null ? String.join(", ", ex.getSupportedMethods()) : "none";
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "Method " + ex.getMethod() + " not supported",
                request.getRequestURI(),
                "Supported methods: " + supported,
                HttpStatus.METHOD_NOT_ALLOWED
        );
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(errorDetails);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorDetails> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn(ex.getMessage());
        String requiredType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "Invalid value for parameter: " + ex.getName(),
                request.getRequestURI(),
                "Parameter '" + ex.getName() + "' should be of type " + requiredType,
                HttpStatus.BAD_REQUEST
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDetails);
    }

    /** Body ilegible: falta, no es JSON válido o un valor no encaja con su tipo (ej. "reason": "FOO").
     * Sin este handler caía en el genérico como 500. No se devuelve ex.getMessage(): lleva nombres de
     * clases Java. Si se sabe el campo, va en errors.<campo>, como en handleValidation. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorDetails> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn(ex.getMessage());
        Map<String, String> errors = null;
        if (ex.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            // El último tramo del path es el campo que falla; en un array no tiene nombre (null)
            String field = mismatch.getPath().getLast().getPropertyName();
            if (field != null) {
                Class<?> type = mismatch.getTargetType();
                errors = Map.of(field, type != null && type.isEnum()
                        ? "must be one of " + Arrays.toString(type.getEnumConstants())
                        : "has an invalid value");
            }
        }
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "Malformed request body",
                request.getRequestURI(),
                "The request body is missing, is not valid JSON or has a value of the wrong type",
                HttpStatus.BAD_REQUEST,
                errors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDetails);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDetails> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception", ex);
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "An unexpected error occurred",
                request.getRequestURI(),
                ex.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorDetails);
    }

    @ExceptionHandler(AddressNotFoundException.class)
    public ResponseEntity<ErrorDetails> handleAddressNotFound(AddressNotFoundException ex, HttpServletRequest request) {
        log.warn(ex.getMessage());
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getRequestURI(),
                "Address not found",
                HttpStatus.BAD_REQUEST
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDetails);
    }

    @ExceptionHandler(InvalidEventTimingException.class)
    public ResponseEntity<ErrorDetails> handleInvalidEventTiming(InvalidEventTimingException ex, HttpServletRequest request) {
        log.warn(ex.getMessage());
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getRequestURI(),
                "Date range is invalid",
                HttpStatus.BAD_REQUEST
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDetails);
    }

    @ExceptionHandler(VenueCityMismatchException.class)
    public ResponseEntity<ErrorDetails> handleVenueCityMismatch(VenueCityMismatchException ex, HttpServletRequest request) {
        log.warn(ex.getMessage());
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getRequestURI(),
                "Venue does not belong to the selected city",
                HttpStatus.BAD_REQUEST
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorDetails);
    }

    @ExceptionHandler(DuplicateVenueException.class)
    public ResponseEntity<ErrorDetails> handleDuplicateVenue(DuplicateVenueException ex, HttpServletRequest request) {
        log.warn(ex.getMessage());
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getRequestURI(),
                "Venue already exists",
                HttpStatus.CONFLICT,
                Map.of("existingVenueId", ex.getExistingVenueId().toString())
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorDetails);
    }

    @ExceptionHandler(VenueHasActiveEventsException.class)
    public ResponseEntity<ErrorDetails> handleVenueHasActiveEvents(VenueHasActiveEventsException ex, HttpServletRequest request) {
        log.warn(ex.getMessage());
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getRequestURI(),
                "Venue has active events",
                HttpStatus.CONFLICT
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorDetails);
    }

    @ExceptionHandler(InvalidMediaTypeException.class)
    public ResponseEntity<ErrorDetails> handleInvalidMediaType(InvalidMediaTypeException ex, HttpServletRequest request) {
        log.warn(ex.getMessage());
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getRequestURI(),
                "Unsupported media type",
                HttpStatus.UNSUPPORTED_MEDIA_TYPE
        );
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(errorDetails);
    }

    @ExceptionHandler(MediaStorageException.class)
    public ResponseEntity<ErrorDetails> handleMediaStorage(MediaStorageException ex, HttpServletRequest request) {
        log.error(ex.getMessage(), ex);
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getRequestURI(),
                "Media storage temporarily unavailable",
                HttpStatus.SERVICE_UNAVAILABLE             // 503
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorDetails);
    }

    @ExceptionHandler(EventSeriesInactiveException.class)
    public ResponseEntity<ErrorDetails> handleEventSeriesInactive(EventSeriesInactiveException ex, HttpServletRequest request) {
        log.warn(ex.getMessage());
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getRequestURI(),
                "Event series is not active",
                HttpStatus.CONFLICT
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorDetails);
    }

    /** El organizador ya tiene dueño. code para que el frontend traduzca el mensaje (organizer-requests-plan.md §4.6). */
    @ExceptionHandler(OrganizerAlreadyClaimedException.class)
    public ResponseEntity<ErrorDetails> handleOrganizerAlreadyClaimed(OrganizerAlreadyClaimedException ex, HttpServletRequest request) {
        log.warn(ex.getMessage());
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getRequestURI(),
                "Organizer already claimed",
                HttpStatus.CONFLICT,
                Map.of("code", "ORGANIZER_ALREADY_CLAIMED")
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorDetails);
    }

    /** Conflictos de las solicitudes de organizador. code para que el frontend traduzca el mensaje (§4.6). */
    @ExceptionHandler(OrganizerRequestConflictException.class)
    public ResponseEntity<ErrorDetails> handleOrganizerRequestConflict(OrganizerRequestConflictException ex, HttpServletRequest request) {
        log.warn(ex.getMessage());
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                ex.getMessage(),
                request.getRequestURI(),
                "Organizer request conflict",
                HttpStatus.CONFLICT,
                Map.of("code", ex.getCode())
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorDetails);
    }

    /** @Version (hoy solo OrganizerRequests): otra petición la modificó entre la lectura y el commit. Salta
     * en el commit, fuera del servicio, por eso se traduce aquí. Mismo formato que los otros 409 con code. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorDetails> handleOptimisticLock(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn(ex.getMessage());
        ErrorDetails errorDetails = new ErrorDetails(
                LocalDateTime.now(),
                "The resource was modified by another request",
                request.getRequestURI(),
                "Concurrent update",
                HttpStatus.CONFLICT,
                Map.of("code", OrganizerRequestConflictException.CONCURRENT_UPDATE)
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorDetails);
    }

}