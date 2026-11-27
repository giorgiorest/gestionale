package it.gestionale.api.commesse;

import it.gestionale.api.tenant.EnumMinuscoloConverter;
import jakarta.persistence.Converter;

/** Converte {@link StatoCommessa} nei valori minuscoli usati dal CHECK del database. */
@Converter
class StatoCommessaConverter extends EnumMinuscoloConverter<StatoCommessa> {

    @Override
    protected Class<StatoCommessa> enumType() {
        return StatoCommessa.class;
    }
}
