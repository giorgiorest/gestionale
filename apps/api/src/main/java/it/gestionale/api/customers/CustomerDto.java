package it.gestionale.api.customers;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Rappresentazione di un cliente nell'API.
 *
 * @param version versione del record per il blocco ottimistico
 */
public record CustomerDto(UUID id, String name, String email, String partitaIva, String codiceFiscale,
                           String indirizzo, String pec, String codiceSdi, String condizioniPagamento, long version,
                           Instant updatedAt) {

    public static CustomerDto from(Customer customer) {
        return new CustomerDto(customer.getId(), customer.getName(), customer.getEmail(), customer.getPartitaIva(),
                customer.getCodiceFiscale(), customer.getIndirizzo(), customer.getPec(), customer.getCodiceSdi(),
                customer.getCondizioniPagamento(), customer.getVersion(), customer.getUpdatedAt());
    }

    /**
     * Dati in ingresso per creare o aggiornare un cliente. I campi oltre a {@code name} sono
     * opzionali: un cliente privato può non avere partita IVA, e non tutti i dati fiscali sono
     * sempre noti al momento dell'inserimento.
     */
    public record Input(
            @NotBlank @Size(max = 200) String name,
            @Email @Size(max = 255) String email,
            @Pattern(regexp = "\\d{11}", message = "la partita IVA deve avere 11 cifre") String partitaIva,
            @Pattern(regexp = "[A-Za-z0-9]{11}|[A-Za-z0-9]{16}",
                    message = "il codice fiscale deve avere 11 o 16 caratteri alfanumerici") String codiceFiscale,
            @Size(max = 255) String indirizzo,
            @Email @Size(max = 255) String pec,
            @Pattern(regexp = "[A-Za-z0-9]{7}",
                    message = "il codice destinatario SDI deve avere 7 caratteri alfanumerici") String codiceSdi,
            @Size(max = 255) String condizioniPagamento) {
    }
}
