package it.gestionale.api.tenancy;

import java.util.Locale;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Converte gli enum del tenant nei valori minuscoli usati dai CHECK del database. */
final class LowercaseEnumConverters {

    private LowercaseEnumConverters() {
    }

    @Converter
    static class KindConverter implements AttributeConverter<TenantKind, String> {
        @Override
        public String convertToDatabaseColumn(TenantKind value) {
            return value == null ? null : value.name().toLowerCase(Locale.ROOT);
        }

        @Override
        public TenantKind convertToEntityAttribute(String value) {
            return value == null ? null : TenantKind.valueOf(value.toUpperCase(Locale.ROOT));
        }
    }

    @Converter
    static class StatusConverter implements AttributeConverter<TenantStatus, String> {
        @Override
        public String convertToDatabaseColumn(TenantStatus value) {
            return value == null ? null : value.name().toLowerCase(Locale.ROOT);
        }

        @Override
        public TenantStatus convertToEntityAttribute(String value) {
            return value == null ? null : TenantStatus.valueOf(value.toUpperCase(Locale.ROOT));
        }
    }
}
