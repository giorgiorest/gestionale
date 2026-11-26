package it.gestionale.api.web;

import it.gestionale.api.ai.AiQuotaExceededException;
import it.gestionale.api.ai.AiUnavailableException;
import it.gestionale.api.attachments.AttachmentNotFoundException;
import it.gestionale.api.attachments.InvalidAttachmentException;
import it.gestionale.api.commesse.UnknownCustomerException;
import it.gestionale.api.dailyreportmaterials.UnknownDailyReportMaterialReferenceException;
import it.gestionale.api.dailyreports.DailyReportNotFoundException;
import it.gestionale.api.dailyreports.InvalidDailyReportStateException;
import it.gestionale.api.dailyreports.UnknownCommessaReferenceException;
import it.gestionale.api.hoursreport.InvalidHoursReportRangeException;
import it.gestionale.api.laborcostreport.InvalidLaborCostReportRangeException;
import it.gestionale.api.licensing.FeatureNotEnabledException;
import it.gestionale.api.licensing.InvalidLicenseException;
import it.gestionale.api.notifications.NotificationNotFoundException;
import it.gestionale.api.notifications.UnknownRecipientException;
import it.gestionale.api.resourceassignments.UnknownAssignmentReferenceException;
import it.gestionale.api.resources.UnknownResourceException;
import it.gestionale.api.resources.UnknownTeamLeadException;
import it.gestionale.api.sync.InvalidSyncCursorException;
import it.gestionale.api.tenancy.MissingTenantException;
import it.gestionale.api.workactivities.UnknownWorkActivityReferenceException;
import it.gestionale.api.workentries.UnknownWorkEntryReferenceException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
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

    /** Es. id già usato da un record non visibile (altro tenant): conflitto, senza rivelare altro. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Operazione in conflitto con i dati esistenti");
    }

    /** Blocco ottimistico (@Version): un altro utente ha modificato il record nel frattempo. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail optimisticLock(OptimisticLockingFailureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Il record è stato modificato da un altro utente: ricarica i dati e riprova");
    }

    @ExceptionHandler(UnknownRecipientException.class)
    ProblemDetail unknownRecipient(UnknownRecipientException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UnknownTeamLeadException.class)
    ProblemDetail unknownTeamLead(UnknownTeamLeadException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UnknownResourceException.class)
    ProblemDetail unknownResource(UnknownResourceException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(UnknownCustomerException.class)
    ProblemDetail unknownCustomer(UnknownCustomerException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UnknownAssignmentReferenceException.class)
    ProblemDetail unknownAssignmentReference(UnknownAssignmentReferenceException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UnknownWorkActivityReferenceException.class)
    ProblemDetail unknownWorkActivityReference(UnknownWorkActivityReferenceException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    ProblemDetail notificationNotFound(NotificationNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(DailyReportNotFoundException.class)
    ProblemDetail dailyReportNotFound(DailyReportNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(UnknownCommessaReferenceException.class)
    ProblemDetail unknownCommessaReference(UnknownCommessaReferenceException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** Transizione di stato non valida sul rapportino, o scrittura bloccata perché non è DRAFT. */
    @ExceptionHandler(InvalidDailyReportStateException.class)
    ProblemDetail invalidDailyReportState(InvalidDailyReportStateException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(UnknownWorkEntryReferenceException.class)
    ProblemDetail unknownWorkEntryReference(UnknownWorkEntryReferenceException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UnknownDailyReportMaterialReferenceException.class)
    ProblemDetail unknownDailyReportMaterialReference(UnknownDailyReportMaterialReferenceException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(InvalidHoursReportRangeException.class)
    ProblemDetail invalidHoursReportRange(InvalidHoursReportRangeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(InvalidLaborCostReportRangeException.class)
    ProblemDetail invalidLaborCostReportRange(InvalidLaborCostReportRangeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(InvalidSyncCursorException.class)
    ProblemDetail invalidSyncCursor(InvalidSyncCursorException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(it.gestionale.api.platform.PlatformException.class)
    ProblemDetail platform(it.gestionale.api.platform.PlatformException e) {
        return ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
    }

    @ExceptionHandler(it.gestionale.api.platform.identity.IdentityAdminException.class)
    ProblemDetail identity(it.gestionale.api.platform.identity.IdentityAdminException e) {
        HttpStatus status = switch (e.kind()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNAVAILABLE -> HttpStatus.BAD_GATEWAY;
        };
        return ProblemDetail.forStatusAndDetail(status, e.getMessage());
    }

    @ExceptionHandler(InvalidLicenseException.class)
    ProblemDetail invalidLicense(InvalidLicenseException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(AttachmentNotFoundException.class)
    ProblemDetail attachmentNotFound(AttachmentNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(InvalidAttachmentException.class)
    ProblemDetail invalidAttachment(InvalidAttachmentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
