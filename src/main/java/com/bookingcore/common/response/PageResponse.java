package com.bookingcore.common.response;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Stable pagination envelope. Deliberately decoupled from Spring Data's {@link Page} serialization,
 * which is not a guaranteed public contract.
 */
@Schema(description = "A page of results")
public record PageResponse<T>(
        @Schema(description = "Items on this page") List<T> content,
        @Schema(description = "Zero-based page index", example = "0") int page,
        @Schema(description = "Requested page size (max 100)", example = "20") int size,
        @Schema(description = "Total number of matching items", example = "42") long totalElements,
        @Schema(description = "Total number of pages", example = "3") int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return of(page, Function.identity());
    }

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
