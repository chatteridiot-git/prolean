package com.prolean.timeline.api;

import com.prolean.common.api.PageQuery;
import com.prolean.common.api.PageResponse;
import com.prolean.common.security.CurrentUser;
import com.prolean.timeline.dto.HolidayDtos.HolidayRequest;
import com.prolean.timeline.dto.HolidayDtos.HolidayResponse;
import com.prolean.timeline.service.HolidayService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/holidays")
public class HolidayController {

    private final HolidayService holidayService;

    public HolidayController(HolidayService holidayService) {
        this.holidayService = holidayService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<HolidayResponse>> list(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        var horizon = holidayService.horizon();
        var list = holidayService.list(year, PageQuery.of(page, size == null ? 100 : size, sort));
        var headers = new HttpHeaders();
        if (horizon.startDate() != null) {
            headers.set("X-Holiday-Coverage-Start", horizon.startDate().toString());
        }
        if (horizon.endDate() != null) {
            headers.set("X-Holiday-Coverage-End", horizon.endDate().toString());
        }
        return ResponseEntity.ok().headers(headers).body(list);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HolidayResponse> create(@Valid @RequestBody HolidayRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        HolidayResponse created = holidayService.create(request, CurrentUser.from(jwt));
        return ResponseEntity.created(URI.create("/api/v1/holidays/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public HolidayResponse update(@PathVariable UUID id, @Valid @RequestBody HolidayRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return holidayService.update(id, request, CurrentUser.from(jwt));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        holidayService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
