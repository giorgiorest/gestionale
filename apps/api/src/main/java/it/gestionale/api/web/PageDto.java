package it.gestionale.api.web;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Risposta esplicita per le liste paginate (ADR 0014): mai la serializzazione di {@link Page} di
 * Spring, il cui formato JSON non è stabile tra le versioni. Pensato per essere riusato da ogni
 * entità con una lista (customers, commesse, risorse, …).
 */
public record PageDto<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageDto<T> of(Page<E> source, Function<E, T> mapper) {
        return new PageDto<>(source.getContent().stream().map(mapper).toList(), source.getNumber(), source.getSize(),
                source.getTotalElements(), source.getTotalPages());
    }
}
