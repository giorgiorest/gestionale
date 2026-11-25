package it.gestionale.api.commesse;

import it.gestionale.api.tenancy.LowercaseEnumConverter;
import jakarta.persistence.Converter;

/** Converte {@link CommessaStato} nei valori minuscoli usati dal CHECK del database. */
@Converter
class CommessaStatoConverter extends LowercaseEnumConverter<CommessaStato> {

    @Override
    protected Class<CommessaStato> enumType() {
        return CommessaStato.class;
    }
}
