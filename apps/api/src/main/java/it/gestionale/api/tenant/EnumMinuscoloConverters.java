package it.gestionale.api.tenant;

import jakarta.persistence.Converter;

/** Converte gli enum del tenant nei valori minuscoli usati dai CHECK del database. */
final class EnumMinuscoloConverters {

    private EnumMinuscoloConverters() {
    }

    @Converter
    static class KindConverter extends EnumMinuscoloConverter<TipoTenant> {
        @Override
        protected Class<TipoTenant> enumType() {
            return TipoTenant.class;
        }
    }

    @Converter
    static class StatusConverter extends EnumMinuscoloConverter<StatoTenant> {
        @Override
        protected Class<StatoTenant> enumType() {
            return StatoTenant.class;
        }
    }
}
