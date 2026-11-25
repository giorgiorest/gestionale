package it.gestionale.api.tenancy;

import jakarta.persistence.Converter;

/** Converte gli enum del tenant nei valori minuscoli usati dai CHECK del database. */
final class LowercaseEnumConverters {

    private LowercaseEnumConverters() {
    }

    @Converter
    static class KindConverter extends LowercaseEnumConverter<TenantKind> {
        @Override
        protected Class<TenantKind> enumType() {
            return TenantKind.class;
        }
    }

    @Converter
    static class StatusConverter extends LowercaseEnumConverter<TenantStatus> {
        @Override
        protected Class<TenantStatus> enumType() {
            return TenantStatus.class;
        }
    }
}
