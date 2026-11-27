package it.gestionale.api.lavorazioni;

import it.gestionale.api.tenant.EnumMinuscoloConverter;
import jakarta.persistence.Converter;

/** Converte {@link StatoLavorazione} nei valori minuscoli usati dal CHECK del database. */
@Converter
class StatoLavorazioneConverter extends EnumMinuscoloConverter<StatoLavorazione> {

    @Override
    protected Class<StatoLavorazione> enumType() {
        return StatoLavorazione.class;
    }
}
