package com.prolean.common.api;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Standard page envelope used by every list endpoint.
 *
 * <p>The field names are part of the API contract: {@code content}, {@code page}, {@code size},
 * {@code totalElements}, {@code totalPages}, {@code first}, {@code last}.
 *
 * @param content page items
 * @param page zero-based page index
 * @param size requested page size
 * @param totalElements total number of matching rows
 * @param totalPages total number of pages
 * @param first true when this is the first page
 * @param last true when this is the last page
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public PageResponse {
        content = content == null ? List.of() : List.copyOf(content);
    }

    /** Maps a Spring Data page onto the envelope. */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    /** Maps a page of already-mapped items onto the envelope. */
    public static <T> PageResponse<T> of(List<T> content, Page<?> page) {
        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    /** Builds the envelope from raw paging values. */
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        int totalPages = (int) ((totalElements + size - 1) / size);
        boolean last = totalPages == 0 || page >= totalPages - 1;
        return new PageResponse<>(content, page, size, totalElements, totalPages, page == 0, last);
    }
}
