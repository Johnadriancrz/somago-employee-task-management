package com.workos.workos_backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.dto.UpdateNotificationPreferencesRequest;
import com.workos.workos_backend.entity.NotificationPreference;
import com.workos.workos_backend.service.NotificationPreferenceService;

/**
 * HTTP-level tests for the Settings Phase S1 notification-preferences
 * endpoints. Runs as the fixed local-dev actor (sarah-chen), same convention
 * as TimeEntryControllerTest/NotificationControllerTest — a second person's
 * preferences (alex-morgan, seeded) are set up directly through the service
 * for the cross-person isolation test, since the local-dev actor is fixed for
 * the whole Spring context. Unauthenticated (no session, outside local-dev)
 * rejection is covered separately in SessionAuthenticationIntegrationTest,
 * which deliberately runs without the local-dev profile.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local-dev")
@Transactional
class NotificationPreferenceControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private NotificationPreferenceService notificationPreferenceService;

    @Test
    void getReturnsTheDocumentedDefaultsWhenNoPreferencesRowExistsYet() {
        MvcTestResult result = mvc.get().uri("/api/notification-preferences").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.mentionsEnabled").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.taskAssignedEnabled").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.dueSoonEnabled").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.weeklyDigestEnabled").isEqualTo(false);
    }

    @Test
    void patchUpdatesOnlyTheProvidedFieldAndLeavesTheRestAtTheirDefaults() {
        MvcTestResult result = mvc.patch().uri("/api/notification-preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"weeklyDigestEnabled\": true}")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.weeklyDigestEnabled").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.mentionsEnabled").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.taskAssignedEnabled").isEqualTo(true);
        assertThat(result).bodyJson().extractingPath("$.dueSoonEnabled").isEqualTo(true);
    }

    @Test
    void repeatedPatchesRemainPersistedAcrossSubsequentGets() {
        mvc.patch().uri("/api/notification-preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mentionsEnabled\": false}")
                .exchange();
        mvc.patch().uri("/api/notification-preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dueSoonEnabled\": false}")
                .exchange();

        MvcTestResult result = mvc.get().uri("/api/notification-preferences").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.mentionsEnabled").isEqualTo(false);
        assertThat(result).bodyJson().extractingPath("$.dueSoonEnabled").isEqualTo(false);
        // taskAssignedEnabled was never patched, so it must still reflect its default.
        assertThat(result).bodyJson().extractingPath("$.taskAssignedEnabled").isEqualTo(true);
    }

    @Test
    void patchIgnoresOmittedFieldsRatherThanResettingThemToDefaults() {
        mvc.patch().uri("/api/notification-preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"weeklyDigestEnabled\": true}")
                .exchange();

        MvcTestResult result = mvc.patch().uri("/api/notification-preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mentionsEnabled\": false}")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.mentionsEnabled").isEqualTo(false);
        // weeklyDigestEnabled was set true by the first PATCH and omitted from the
        // second body, so it must remain true, not silently revert to its default.
        assertThat(result).bodyJson().extractingPath("$.weeklyDigestEnabled").isEqualTo(true);
    }

    @Test
    void aPersonsOwnPatchNeverModifiesAnotherPersonsPreferences() {
        NotificationPreference alexMorgansPreferences = notificationPreferenceService.updatePreferences(
                "alex-morgan",
                new UpdateNotificationPreferencesRequest(false, false, false, true));

        mvc.patch().uri("/api/notification-preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mentionsEnabled\": true, \"taskAssignedEnabled\": true, "
                        + "\"dueSoonEnabled\": true, \"weeklyDigestEnabled\": false}")
                .exchange();

        NotificationPreference reloaded = notificationPreferenceService.getPreferences("alex-morgan");
        assertThat(reloaded.getId()).isEqualTo(alexMorgansPreferences.getId());
        assertThat(reloaded.isMentionsEnabled()).isFalse();
        assertThat(reloaded.isTaskAssignedEnabled()).isFalse();
        assertThat(reloaded.isDueSoonEnabled()).isFalse();
        assertThat(reloaded.isWeeklyDigestEnabled()).isTrue();
    }

    @Test
    void getAlwaysResolvesToTheSessionAuthenticatedActorsOwnRow() {
        notificationPreferenceService.updatePreferences(
                "alex-morgan",
                new UpdateNotificationPreferencesRequest(false, null, null, null));

        MvcTestResult result = mvc.get().uri("/api/notification-preferences").exchange();

        // The fixed local-dev actor is sarah-chen, never alex-morgan, regardless
        // of what other people's rows exist — so sarah-chen still sees defaults.
        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.mentionsEnabled").isEqualTo(true);
    }
}
