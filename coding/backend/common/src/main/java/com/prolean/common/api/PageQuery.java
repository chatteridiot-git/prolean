package com.prolean.common.api;

import com.prolean.common.error.ApiException;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Validated pagination query parameters shared by every list endpoint.
 *
 * <p>Page numbers are zero-based. Sizes are capped at 100 to keep responses bounded. Sorting is
 * allow-listed per endpoint so a client cannot sort by an unindexed or sensitive column.
 *
 * @param page zero-based page index
 * @param size page size, 1-100
 * @param sort optional sort expression, {@code field,asc} or {@code field,desc}
 */
public record PageQuery(int page, int size, String sort) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    /** Validates raw request parameters and applies defaults. */
    public static PageQuery of(Integer page, Integer size, String sort) {
        int resolvedPage = page == null ? 0 : page;
        int resolvedSize = size == null ? DEFAULT_SIZE : size;
        if (resolvedPage < 0) {
            throw ApiException.validation("page", "must be zero or greater");
        }
        if (resolvedSize < 1 || resolvedSize > MAX_SIZE) {
            throw ApiException.validation("size", "must be between 1 and " + MAX_SIZE);
        }
        return new PageQuery(resolvedPage, resolvedSize, sort);
    }

    /**
     * Converts this query into a Spring Data pageable.
     *
     * @param allowedSort fields the endpoint permits
     * @param defaultSort fallback expression when the client did not supply one
     */
    public Pageable toPageable(Set<String> allowedSort, String defaultSort) {
        String expression = sort == null || sort.isBlank() ? defaultSort : sort;
        return PageRequest.of(page, size, parseSort(expression, allowedSort));
    }

    /**
     * Converts this query and always appends a stable tie-breaker.
     *
     * <p>Without a unique final sort key, pagination can skip or duplicate rows when two records
     * share the primary sort value.
     */
    public Pageable toPageable(Set<String> allowedSort, String defaultSort, String tieBreaker) {
        Sort parsed = parseSort(sort == null || sort.isBlank() ? defaultSort : sort, allowedSort);
        if (parsed.getOrderFor(tieBreaker) == null) {
            parsed = parsed.and(Sort.by(Sort.Direction.DESC, tieBreaker));
        }
        return PageRequest.of(page, size, parsed);
    }

    private static Sort parseSort(String expression, Set<String> allowedSort) {
        String[] parts = expression.split(",", 2);
        String field = parts[0].trim();
        String direction = parts.length == 2 ? parts[1].trim().toLowerCase(Locale.ROOT) : "asc";
        if (!allowedSort.contains(field)) {
            throw ApiException.validation("sort", "unsupported sort field: " + field);
        }
        if (!direction.equals("asc") && !direction.equals("desc")) {
            throw ApiException.validation("sort", "direction must be asc or desc");
        }
        return Sort.by(Sort.Direction.fromString(direction), field);
    }
}
