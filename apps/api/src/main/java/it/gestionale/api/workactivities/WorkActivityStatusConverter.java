package it.gestionale.api.workactivities;

import it.gestionale.api.tenancy.LowercaseEnumConverter;
import jakarta.persistence.Converter;

/** Converte {@link WorkActivityStatus} nei valori minuscoli usati dal CHECK del database. */
@Converter
class WorkActivityStatusConverter extends LowercaseEnumConverter<WorkActivityStatus> {

    @Override
    protected Class<WorkActivityStatus> enumType() {
        return WorkActivityStatus.class;
    }
}
