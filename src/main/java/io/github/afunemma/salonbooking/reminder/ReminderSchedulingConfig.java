package io.github.afunemma.salonbooking.reminder;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on Spring's {@code @Scheduled} jobs. Tests switch the reminder job off with
 * {@code app.reminders.cron=-} and run it by hand instead.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class ReminderSchedulingConfig {

}
