package com.workos.workos_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's {@code @Scheduled} support, introduced for the Time Clock
 * + Overtime feature's 5:30 PM clock-out reminder ({@code
 * com.workos.workos_backend.scheduler.ClockOutReminderScheduler}) — no
 * scheduling infrastructure existed anywhere in this codebase before this.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
