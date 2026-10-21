package com.prolean.timeline.config;

import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "prolean.timeline")
public class TimelineProperties {

    private String zone = "Asia/Jakarta";

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public LocalDate today() {
        return LocalDate.now(ZoneId.of(zone));
    }
}
