package com.smartroute.smartroute1.endpoint.exceptionhandler;

import com.smartroute.smartroute1.exception.ConflictException;
import com.smartroute.smartroute1.exception.ErrorListException;
import com.smartroute.smartroute1.exception.NotFoundException;
import com.smartroute.smartroute1.exception.StravaAuthorizationException;
import com.smartroute.smartroute1.exception.RateLimitExceededException;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.exception.garmin.GarminAuthenticationException;
import com.smartroute.smartroute1.exception.garmin.GarminException;
import com.smartroute.smartroute1.exception.garmin.GarminNoDataException;
import com.smartroute.smartroute1.exception.garmin.GarminScriptException;
import com.smartroute.smartroute1.exception.StopTooFarFromRouteException;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.lang.invoke.MethodHandles;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Register all your Java exceptions here to map them into meaningful HTTP
 * exceptions
 * If you have special cases which are only important for specific endpoints,
 * use ResponseStatusExceptions
 * https://www.baeldung.com/exception-handling-for-rest-with-spring#responsestatusexception
 */
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


    @ExceptionHandler(GarminNoDataException.class)
    public ResponseEntity<Object> handleGarminNoData(GarminNoDataException ex, WebRequest request) {
        LOGGER.info("No Garmin data found: {}", ex.getMessage());
        return handleExceptionInternal(ex, ex.getMessage(), new HttpHeaders(), HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(GarminAuthenticationException.class)
    public ResponseEntity<Object> handleGarminAuth(GarminAuthenticationException ex, WebRequest request) {
        LOGGER.warn("Garmin authentication failed: {}", ex.getMessage());
        return handleExceptionInternal(ex, ex.getMessage(), new HttpHeaders(), HttpStatus.UNAUTHORIZED, request);
    }

    @ExceptionHandler(GarminScriptException.class)
    public ResponseEntity<Object> handleGarminScript(GarminScriptException ex, WebRequest request) {
        LOGGER.error("Garmin script error: {}", ex.getMessage(), ex);
        return handleExceptionInternal(ex, "Failed to execute Garmin sync", new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @ExceptionHandler(GarminException.class)
    public ResponseEntity<Object> handleGarminGeneric(GarminException ex, WebRequest request) {
        LOGGER.error("Garmin error: {}", ex.getMessage(), ex);
        return handleExceptionInternal(ex, "An error occurred while syncing with Garmin", new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }


    /**
     * Use the @ExceptionHandler annotation to write handler for custom exceptions.
     */
    @ExceptionHandler(value = {NotFoundException.class})
    protected ResponseEntity<Object> handleNotFound(RuntimeException ex, WebRequest request) {
        LOGGER.warn(ex.getMessage());
        return handleExceptionInternal(ex, ex.getMessage(), new HttpHeaders(), HttpStatus.NOT_FOUND, request);
    }

    /**
     * Override methods from ResponseEntityExceptionHandler to send a customized
     * HTTP response for a know exception
     * from e.g. Spring
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        // Get all errors
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .toList();
        body.put("Validation errors", errors);

        return new ResponseEntity<>(body.toString(), headers, status);
    }


    /**
     * Handles {@link RateLimitExceededException} by returning an HTTP 429 (Too Many Requests)
     * response when a user exceeds the configured rate limit for specific actions
     * (such as resending verification or password reset emails).
     *
     * @param ex      the thrown {@code RateLimitExceededException}
     * @param request the current web request
     * @return a {@link ResponseEntity} with the exception message and status 429
     */
    @ExceptionHandler(value = {RateLimitExceededException.class})
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    @ApiResponse(responseCode = "429", description = "Too many requests", content = @Content)
    protected ResponseEntity<Object> handleRateLimitExceededException(RateLimitExceededException ex, WebRequest request) {
        LOGGER.warn(ex.getMessage());
        return handleExceptionInternal(ex, ex.getMessage(), new HttpHeaders(), HttpStatus.TOO_MANY_REQUESTS, request);
    }

    /**
     * Handles {@link ValidationException} and other {@link ErrorListException}-based exceptions
     * by returning an HTTP 422 (Unprocessable Entity) response.
     * <p>
     * This is used for custom business validation errors where the request structure is valid,
     * but its content violates domain-specific rules.
     * </p>
     *
     * @param ex      the thrown {@code ErrorListException}
     * @param request the current web request
     * @return a {@link ResponseEntity} with the validation message and status 422
     */
    @ExceptionHandler(value = {ValidationException.class})
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    @ApiResponse(responseCode = "422", description = "Unprocessable Entity", content = @Content)
    protected ResponseEntity<Object> handleValidation(ErrorListException ex, WebRequest request) {
        LOGGER.warn(ex.getMessage());
        return handleExceptionInternal(ex, ex.getMessage(), new HttpHeaders(), HttpStatus.UNPROCESSABLE_ENTITY, request);
    }

    @ExceptionHandler(value = {ConflictException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    protected ResponseEntity<Object> handleConflict(ConflictException ex, WebRequest request) {
        LOGGER.warn("Conflict occurred: {}", ex.getMessage());
        return handleExceptionInternal(ex, ex.getMessage(), new HttpHeaders(), HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(value = {StravaAuthorizationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    protected ResponseEntity<Object> handleStravaAuth(StravaAuthorizationException ex, WebRequest request) {
        LOGGER.warn("Strava authorization failed: {}", ex.getMessage());
        return handleExceptionInternal(ex, ex.getMessage(), new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(value = {ResponseStatusException.class})
    protected ResponseEntity<Object> handleResponseStatus(ResponseStatusException ex, WebRequest request) {
        LOGGER.warn("Request failed: {} - {}", ex.getStatusCode(), ex.getReason());
        return handleExceptionInternal(ex, ex.getReason(), new HttpHeaders(), ex.getStatusCode(), request);
    }

    @ExceptionHandler(StopTooFarFromRouteException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    protected ResponseEntity<Object> handleStopTooFar(StopTooFarFromRouteException ex, WebRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", ex.getMessage());
        body.put("code", "STOP_TOO_FAR_FROM_ROUTE");
        body.put("details", ex.toDetails());

        return handleExceptionInternal(ex, body, new HttpHeaders(), HttpStatus.UNPROCESSABLE_ENTITY, request);
    }

}
