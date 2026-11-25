package it.gestionale.api.dailyreports;

import it.gestionale.api.tenancy.LowercaseEnumConverter;
import jakarta.persistence.Converter;

/** Converte {@link DailyReportStatus} nei valori minuscoli usati dal CHECK del database. */
@Converter
class DailyReportStatusConverter extends LowercaseEnumConverter<DailyReportStatus> {

    @Override
    protected Class<DailyReportStatus> enumType() {
        return DailyReportStatus.class;
    }
}
