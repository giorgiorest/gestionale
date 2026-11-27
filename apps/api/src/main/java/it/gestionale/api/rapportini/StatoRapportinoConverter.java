package it.gestionale.api.rapportini;

import it.gestionale.api.tenant.EnumMinuscoloConverter;
import jakarta.persistence.Converter;

/** Converte {@link StatoRapportino} nei valori minuscoli usati dal CHECK del database. */
@Converter
class StatoRapportinoConverter extends EnumMinuscoloConverter<StatoRapportino> {

    @Override
    protected Class<StatoRapportino> enumType() {
        return StatoRapportino.class;
    }
}
