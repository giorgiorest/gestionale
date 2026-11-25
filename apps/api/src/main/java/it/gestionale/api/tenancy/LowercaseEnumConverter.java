package it.gestionale.api.tenancy;

import java.util.Locale;

import jakarta.persistence.AttributeConverter;

/**
 * Converte un enum nei valori minuscoli usati dai CHECK del database: {@code NOME_VALORE} ↔
 * {@code "nome_valore"}. Le sottoclassi forniscono solo {@link #enumType()}.
 */
public abstract class LowercaseEnumConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

    protected abstract Class<E> enumType();

    @Override
    public String convertToDatabaseColumn(E value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public E convertToEntityAttribute(String value) {
        return value == null ? null : Enum.valueOf(enumType(), value.toUpperCase(Locale.ROOT));
    }
}
