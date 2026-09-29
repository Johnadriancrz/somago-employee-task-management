package com.workos.workos_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.entity.Notification;
import com.workos.workos_backend.entity.NotificationEventType;
import com.workos.workos_backend.service.NotificationService;

/**
 * HTTP-level tests for the Notifications endpoints, verifying status codes
 * and the JSON contract. Runs as the fixed local-dev actor (sarah-chen),
 * same convention as ChatControllerTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local-dev")
@Transactional
class NotificationControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private NotificationService notificationService;

    @Test
    void listReturnsEmptyArrayWhenTheActorHasNoNotifications() {
        MvcTestResult result = mvc.get().uri("/api/notifications").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$").asArray().isEmpty();
    }

    @Test
    void listIncludesANotificationWhereTheActorIsTheRecipient() {
        notificationService.notify("sarah-chen", NotificationEventType.CLOCK_IN, "Sarah Chen clocked in.");

        MvcTestResult result = mvc.get().uri("/api/notifications").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$").asArray().hasSize(1);
        assertThat(result).bodyJson().extractingPath("$[0].eventType").isEqualTo("CLOCK_IN");
        assertThat(result).bodyJson().extractingPath("$[0].read").isEqualTo(false);
    }

    @Test
    void markReadFlipsTheActorsOwnNotificationToRead() {
        Notification notification = notificationService
                .notify("sarah-chen", NotificationEventType.CLOCK_IN, "Sarah Chen clocked in.")
                .get(0);

        MvcTestResult result =
                mvc.patch().uri("/api/notifications/" + notification.getId() + "/read").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.read").isEqualTo(true);
    }

    @Test
    void markReadRejectsANotificationBelongingToAnotherRecipientWith403() {
        Notification notification = notificationService
                .notify("alex-morgan", NotificationEventType.CLOCK_IN, "Alex Morgan clocked in.")
                .stream()
                .filter(n -> n.getRecipient().getId().equals("alex-morgan"))
                .findFirst()
                .orElseThrow();

        MvcTestResult result =
                mvc.patch().uri("/api/notifications/" + notification.getId() + "/read").exchange();

        assertThat(result).hasStatus(403);
    }

    @Test
    void markAllReadReturnsOkAndClearsTheActorsUnreadNotifications() {
        notificationService.notify("sarah-chen", NotificationEventType.CLOCK_IN, "Sarah Chen clocked in.");
        notificationService.notify("sarah-chen", NotificationEventType.CLOCK_OUT, "Sarah Chen clocked out.");

        MvcTestResult result = mvc.patch().uri("/api/notifications/read-all").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.ok").isEqualTo(true);
        assertThat(notificationService.listForRecipient("sarah-chen")).allMatch(Notification::isRead);
    }

    @Test
    void unreadCountIsZeroWhenTheActorHasNoNotifications() {
        MvcTestResult result = mvc.get().uri("/api/notifications/unread-count").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.count").isEqualTo(0);
    }

    @Test
    void unreadCountReflectsOnlyTheActorsOwnUnreadNotifications() {
        Notification first = notificationService
                .notify("sarah-chen", NotificationEventType.CLOCK_IN, "Sarah Chen clocked in.")
                .stream().filter(n -> n.getRecipient().getId().equals("sarah-chen")).findFirst().orElseThrow();
        notificationService.notify("sarah-chen", NotificationEventType.CLOCK_OUT, "Sarah Chen clocked out.");
        notificationService.markRead("sarah-chen", first.getId());

        MvcTestResult result = mvc.get().uri("/api/notifications/unread-count").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.count").isEqualTo(1);
    }

    @Test
    void unreadCountDoesNotIncludeAnotherRecipientsNotifications() {
        notificationService.notify("alex-morgan", NotificationEventType.CLOCK_IN, "Alex Morgan clocked in.");

        MvcTestResult result = mvc.get().uri("/api/notifications/unread-count").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.count").isEqualTo(0);
    }
}
