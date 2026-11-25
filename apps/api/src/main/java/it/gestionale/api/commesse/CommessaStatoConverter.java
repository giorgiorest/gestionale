package it.gestionale.api.commesse;

import java.util.Locale;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Converte {@link CommessaStato} nei valori minuscoli usati dal CHECK del database. */
@Converter
class CommessaStatoConverter implements AttributeConverter<CommessaStato, String> {

    @Override
    public String convertToDatabaseColumn(CommessaStato value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public CommessaStato convertToEntityAttribute(String value) {
        return value == null ? null : CommessaStato.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
