package it.gestionale.api.errori;

import it.gestionale.api.allegati.AllegatoNonTrovatoException;
import it.gestionale.api.allegati.AllegatoNonValidoException;
import it.gestionale.api.assegnazioni.RiferimentoAssegnazioneSconosciutoException;
import it.gestionale.api.commesse.ClienteSconosciutoException;
import it.gestionale.api.costimanodopera.IntervalloCostiManodoperaNonValidoException;
import it.gestionale.api.ia.IaNonDisponibileException;
import it.gestionale.api.ia.QuotaIaSuperataException;
import it.gestionale.api.lavorazioni.RiferimentoLavorazioneSconosciutoException;
import it.gestionale.api.licenze.FunzionalitaNonAbilitataException;
import it.gestionale.api.licenze.LicenzaNonValidaException;
import it.gestionale.api.notifiche.DestinatarioSconosciutoException;
import it.gestionale.api.notifiche.NotificaNonTrovataException;
import it.gestionale.api.piattaforma.PiattaformaException;
import it.gestionale.api.piattaforma.identita.AmministrazioneIdentitaException;
import it.gestionale.api.rapportini.RapportinoNonTrovatoException;
import it.gestionale.api.rapportini.RiferimentoCommessaSconosciutoException;
import it.gestionale.api.rapportini.RiferimentoMaterialeSconosciutoException;
import it.gestionale.api.rapportini.RiferimentoRigaOreSconosciutoException;
import it.gestionale.api.rapportini.StatoRapportinoNonValidoException;
import it.gestionale.api.riepilogoore.IntervalloRiepilogoOreNonValidoException;
import it.gestionale.api.risorse.CapoSquadraSconosciutoException;
import it.gestionale.api.risorse.RisorsaSconosciutaException;
import it.gestionale.api.sincronizzazione.CursoreNonValidoException;
import it.gestionale.api.tenant.TenantMancanteException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduce le eccezioni di dominio in risposte RFC 9457 (application/problem+json). */
@RestControllerAdvice
public class GestoreErroriApi {

    @ExceptionHandler(FunzionalitaNonAbilitataException.class)
    ProblemDetail featureNotEnabled(FunzionalitaNonAbilitataException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(TenantMancanteException.class)
    ProblemDetail missingTenant(TenantMancanteException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(QuotaIaSuperataException.class)
    ProblemDetail quotaExceeded(QuotaIaSuperataException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
    }

    @ExceptionHandler(IaNonDisponibileException.class)
    ProblemDetail aiUnavailable(IaNonDisponibileException e) {
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

    @ExceptionHandler(DestinatarioSconosciutoException.class)
    ProblemDetail unknownRecipient(DestinatarioSconosciutoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(CapoSquadraSconosciutoException.class)
    ProblemDetail unknownTeamLead(CapoSquadraSconosciutoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(RisorsaSconosciutaException.class)
    ProblemDetail unknownResource(RisorsaSconosciutaException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ClienteSconosciutoException.class)
    ProblemDetail unknownCustomer(ClienteSconosciutoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(RiferimentoAssegnazioneSconosciutoException.class)
    ProblemDetail unknownAssignmentReference(RiferimentoAssegnazioneSconosciutoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(RiferimentoLavorazioneSconosciutoException.class)
    ProblemDetail unknownWorkActivityReference(RiferimentoLavorazioneSconosciutoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(NotificaNonTrovataException.class)
    ProblemDetail notificationNotFound(NotificaNonTrovataException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(RapportinoNonTrovatoException.class)
    ProblemDetail dailyReportNotFound(RapportinoNonTrovatoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(RiferimentoCommessaSconosciutoException.class)
    ProblemDetail unknownCommessaReference(RiferimentoCommessaSconosciutoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** Transizione di stato non valida sul rapportino, o scrittura bloccata perché non è DRAFT. */
    @ExceptionHandler(StatoRapportinoNonValidoException.class)
    ProblemDetail invalidDailyReportState(StatoRapportinoNonValidoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(RiferimentoRigaOreSconosciutoException.class)
    ProblemDetail unknownWorkEntryReference(RiferimentoRigaOreSconosciutoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(RiferimentoMaterialeSconosciutoException.class)
    ProblemDetail unknownDailyReportMaterialReference(RiferimentoMaterialeSconosciutoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(IntervalloRiepilogoOreNonValidoException.class)
    ProblemDetail invalidHoursReportRange(IntervalloRiepilogoOreNonValidoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(IntervalloCostiManodoperaNonValidoException.class)
    ProblemDetail invalidLaborCostReportRange(IntervalloCostiManodoperaNonValidoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(CursoreNonValidoException.class)
    ProblemDetail invalidSyncCursor(CursoreNonValidoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(it.gestionale.api.piattaforma.PiattaformaException.class)
    ProblemDetail platform(it.gestionale.api.piattaforma.PiattaformaException e) {
        return ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
    }

    @ExceptionHandler(it.gestionale.api.piattaforma.identita.AmministrazioneIdentitaException.class)
    ProblemDetail identity(it.gestionale.api.piattaforma.identita.AmministrazioneIdentitaException e) {
        HttpStatus status = switch (e.kind()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNAVAILABLE -> HttpStatus.BAD_GATEWAY;
        };
        return ProblemDetail.forStatusAndDetail(status, e.getMessage());
    }

    @ExceptionHandler(LicenzaNonValidaException.class)
    ProblemDetail invalidLicense(LicenzaNonValidaException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(AllegatoNonTrovatoException.class)
    ProblemDetail attachmentNotFound(AllegatoNonTrovatoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(AllegatoNonValidoException.class)
    ProblemDetail invalidAttachment(AllegatoNonValidoException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
