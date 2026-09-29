package com.workos.workos_backend.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The single source of truth for "what business calendar date does this
 * instant fall on" — introduced for the Time Clock + Overtime feature, which
 * needs a business date (for the 5:30 PM reminder trigger, and for bucketing
 * clock-in/out instants into regular/overtime workdays) and found no existing
 * timezone/locale concept anywhere in this codebase to reuse.
 *
 * <p>Deliberately never {@code ZoneId.systemDefault()} — relying on whatever
 * timezone the server OS/JVM happens to be configured with would silently
 * shift both the reminder's fire time and every hours calculation if the
 * backend were ever redeployed to a host in a different zone. {@code
 * app.business.zone-id} (default {@code Asia/Manila}) is the one
 * configurable knob every business-date computation in this feature goes
 * through.
 */
@Component
public class BusinessClock {

    private final ZoneId zoneId;

    public BusinessClock(@Value("${app.business.zone-id}") String zoneId) {
        this.zoneId = ZoneId.of(zoneId);
    }

    public ZoneId zoneId() {
        return zoneId;
    }

    /** The business calendar date {@code instant} falls on, in the configured business timezone. */
    public LocalDate toBusinessDate(Instant instant) {
        return LocalDate.ofInstant(instant, zoneId);
    }
}
