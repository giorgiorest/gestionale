package it.gestionale.api.condiviso;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Risposta esplicita per le liste paginate (ADR 0014): mai la serializzazione di {@link Page} di
 * Spring, il cui formato JSON non è stabile tra le versioni. Pensato per essere riusato da ogni
 * entità con una lista (customers, commesse, risorse, …).
 */
public record PaginaDto<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PaginaDto<T> of(Page<E> source, Function<E, T> mapper) {
        return new PaginaDto<>(source.getContent().stream().map(mapper).toList(), source.getNumber(), source.getSize(),
                source.getTotalElements(), source.getTotalPages());
    }
}
