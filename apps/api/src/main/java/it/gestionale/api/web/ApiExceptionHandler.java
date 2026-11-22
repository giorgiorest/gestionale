package it.gestionale.api.web;

import it.gestionale.api.ai.AiQuotaExceededException;
import it.gestionale.api.ai.AiUnavailableException;
import it.gestionale.api.licensing.FeatureNotEnabledException;
import it.gestionale.api.licensing.InvalidLicenseException;
import it.gestionale.api.tenancy.MissingTenantException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduce le eccezioni di dominio in risposte RFC 9457 (application/problem+json). */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(FeatureNotEnabledException.class)
    ProblemDetail featureNotEnabled(FeatureNotEnabledException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(MissingTenantException.class)
    ProblemDetail missingTenant(MissingTenantException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(AiQuotaExceededException.class)
    ProblemDetail quotaExceeded(AiQuotaExceededException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    @ExceptionHandler(AiUnavailableException.class)
    ProblemDetail aiUnavailable(AiUnavailableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(InvalidLicenseException.class)
    ProblemDetail invalidLicense(InvalidLicenseException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
