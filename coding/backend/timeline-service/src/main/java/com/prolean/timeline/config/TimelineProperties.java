package com.prolean.timeline.config;

import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "prolean.timeline")
public class TimelineProperties {

    private String zone = "Asia/Jakarta";
    private LocalDate coverageStart = LocalDate.of(2025, 1, 1);
    private LocalDate coverageEnd = LocalDate.of(2027, 12, 31);

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public LocalDate getCoverageStart() {
        return coverageStart;
    }

    public void setCoverageStart(LocalDate coverageStart) {
        this.coverageStart = coverageStart;
    }

    public LocalDate getCoverageEnd() {
        return coverageEnd;
    }

    public void setCoverageEnd(LocalDate coverageEnd) {
        this.coverageEnd = coverageEnd;
    }

    public LocalDate today() {
        return LocalDate.now(ZoneId.of(zone));
    }
}
