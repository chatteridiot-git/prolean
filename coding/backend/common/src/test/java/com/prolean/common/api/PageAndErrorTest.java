package com.prolean.common.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prolean.common.error.ApiException;
import com.prolean.common.error.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class PageAndErrorTest {

    @Test
    void pageQueryDefaultsAndValidatesBounds() {
        PageQuery defaults = PageQuery.of(null, null, null);
        assertThat(defaults.page()).isZero();
        assertThat(defaults.size()).isEqualTo(PageQuery.DEFAULT_SIZE);
        assertThat(defaults.sort()).isNull();

        assertThatThrownBy(() -> PageQuery.of(-1, 20, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> PageQuery.of(0, 0, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> PageQuery.of(0, 101, null))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void pageQueryBuildsAllowListedSort() {
        PageQuery query = PageQuery.of(1, 10, "name,desc");
        var pageable = query.toPageable(java.util.Set.of("name"), "createdAt,asc");

        assertThat(pageable.getPageNumber()).isOne();
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort().getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.DESC);

        assertThat(PageQuery.of(0, 20, null).toPageable(java.util.Set.of("name"), "name,asc")
                .getSort().getOrderFor("name")).isNotNull();
        assertThat(PageQuery.of(0, 20, " ").toPageable(java.util.Set.of("name"), "name,asc")
                .getSort().getOrderFor("name")).isNotNull();
        assertThat(PageQuery.of(0, 20, "name").toPageable(java.util.Set.of("name"), "name,asc")
                .getSort().getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void pageQueryRejectsUnsafeSort() {
        assertThatThrownBy(() -> PageQuery.of(0, 20, "password,asc")
                .toPageable(java.util.Set.of("name"), "name,asc"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> PageQuery.of(0, 20, "name,sideways")
                .toPageable(java.util.Set.of("name"), "name,asc"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void pageQueryAppendsStableTieBreaker() {
        var withDefault = PageQuery.of(0, 20, null)
                .toPageable(java.util.Set.of("name", "id"), "name,asc", "id");
        assertThat(withDefault.getSort().getOrderFor("id").getDirection())
                .isEqualTo(Sort.Direction.DESC);

        var alreadyPresent = PageQuery.of(0, 20, "id,asc")
                .toPageable(java.util.Set.of("name", "id"), "name,asc", "id");
        assertThat(alreadyPresent.getSort().getOrderFor("id").getDirection())
                .isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void pageResponseMapsPagesAndRawValues() {
        PageImpl<String> page = new PageImpl<>(List.of("a"), PageRequest.of(1, 1), 2);
        PageResponse<String> from = PageResponse.from(page);
        assertThat(from.content()).containsExactly("a");
        assertThat(from.page()).isOne();
        assertThat(from.totalPages()).isEqualTo(2);
        assertThat(from.first()).isFalse();
        assertThat(from.last()).isTrue();

        PageResponse<String> mapped = PageResponse.of(List.of("b"), page);
        assertThat(mapped.content()).containsExactly("b");

        PageResponse<String> raw = PageResponse.of(null, 0, 20, 0);
        assertThat(raw.content()).isEmpty();
        assertThat(raw.totalPages()).isZero();
        assertThat(raw.last()).isTrue();
        assertThatThrownBy(() -> PageResponse.of(List.of(), 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void apiExceptionUsesDefaultsAndValidationErrors() {
        ApiException plain = new ApiException(ErrorCode.PROJECT_NOT_FOUND);
        assertThat(plain.detail()).isEqualTo(ErrorCode.PROJECT_NOT_FOUND.defaultDetail());
        assertThat(plain.status().value()).isEqualTo(404);
        assertThat(plain.errors()).isEmpty();

        ApiException blank = new ApiException(ErrorCode.FORBIDDEN, " ");
        assertThat(blank.detail()).isEqualTo(ErrorCode.FORBIDDEN.defaultDetail());

        ApiException validation = ApiException.validation("name", "must not be blank");
        assertThat(validation.code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(validation.errors()).hasSize(1);
        assertThat(ApiException.of(ErrorCode.DATA_CONFLICT, "custom").detail()).isEqualTo("custom");
        assertThat(new ApiException(ErrorCode.FORBIDDEN, null, null).errors()).isEmpty();
    }

    @Test
    void errorCodeExposesProblemMetadata() {
        assertThat(ErrorCode.AUTH_ACCOUNT_LOCKED.status().value()).isEqualTo(423);
        assertThat(ErrorCode.AUTH_ACCOUNT_LOCKED.title()).isEqualTo("Locked");
        assertThat(ErrorCode.AUTH_ACCOUNT_LOCKED.type())
                .isEqualTo("https://prolean.local/errors/auth-account-locked");
        assertThat(ErrorCode.VALIDATION_ERROR.defaultDetail()).isNotBlank();
    }
}
