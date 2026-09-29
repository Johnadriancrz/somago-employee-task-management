package com.workos.workos_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.workos.workos_backend.entity.Notification;
import com.workos.workos_backend.entity.NotificationEventType;
import com.workos.workos_backend.entity.Person;
import com.workos.workos_backend.exception.ForbiddenException;

/**
 * Exercises the self-notification rule (spec section 18) directly, mirroring
 * TimeEntryServiceTest's approach: actor/recipient ids are passed explicitly,
 * and CEO/HR/Operation Manager accounts are minted via AccountService since
 * the seeded local-dev people all have a null accessRole (spec section
 * 13.3). Covers every scenario listed in section 18.4.
 */
@SpringBootTest
@ActiveProfiles("local-dev")
@Transactional
class NotificationServiceTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AccountService accountService;

    private String newAccount(String accessRole) {
        Person person = accountService.createAccount(
                "Test " + accessRole, accessRole.toLowerCase().replace(" ", ".") + "-"
                        + UUID.randomUUID() + "@workos.dev",
                "Password123!", accessRole);
        return person.getId();
    }

    @Test
    void actorReceivesTheirOwnNotification() {
        notificationService.notify("sarah-chen", NotificationEventType.CLOCK_IN, "Sarah Chen clocked in.");

        List<Notification> received = notificationService.listForRecipient("sarah-chen");

        assertThat(received).hasSize(1);
        assertThat(received.get(0).getActor().getId()).isEqualTo("sarah-chen");
    }

    @Test
    void ceoReceivesTheNotification() {
        String ceoId = newAccount("CEO");

        notificationService.notify("sarah-chen", NotificationEventType.CLOCK_IN, "Sarah Chen clocked in.");

        assertThat(notificationService.listForRecipient(ceoId)).hasSize(1);
    }

    @Test
    void hrReceivesTheNotification() {
        String hrId = newAccount("HR");

        notificationService.notify("sarah-chen", NotificationEventType.CLOCK_IN, "Sarah Chen clocked in.");

        assertThat(notificationService.listForRecipient(hrId)).hasSize(1);
    }

    @Test
    void operationManagerReceivesTheNotification() {
        String omId = newAccount("Operation Manager");

        notificationService.notify("sarah-chen", NotificationEventType.CLOCK_IN, "Sarah Chen clocked in.");

        assertThat(notificationService.listForRecipient(omId)).hasSize(1);
    }

    @Test
    void actorWhoIsCeoReceivesExactlyOneNotification() {
        String ceoId = newAccount("CEO");
        newAccount("HR");
        newAccount("Operation Manager");

        notificationService.notify(ceoId, NotificationEventType.CLOCK_IN, "CEO clocked in.");

        assertThat(notificationService.listForRecipient(ceoId)).hasSize(1);
    }

    @Test
    void actorWhoIsHrReceivesExactlyOneNotification() {
        newAccount("CEO");
        String hrId = newAccount("HR");
        newAccount("Operation Manager");

        notificationService.notify(hrId, NotificationEventType.CLOCK_IN, "HR clocked in.");

        assertThat(notificationService.listForRecipient(hrId)).hasSize(1);
    }

    @Test
    void actorWhoIsOperationManagerReceivesExactlyOneNotification() {
        newAccount("CEO");
        newAccount("HR");
        String omId = newAccount("Operation Manager");

        notificationService.notify(omId, NotificationEventType.CLOCK_IN, "Operation Manager clocked in.");

        assertThat(notificationService.listForRecipient(omId)).hasSize(1);
    }

    @Test
    void everyManagementRoleAndTheActorAreAllDistinctRecipientsForOneEvent() {
        String ceoId = newAccount("CEO");
        String hrId = newAccount("HR");
        String omId = newAccount("Operation Manager");

        List<Notification> created =
                notificationService.notify("sarah-chen", NotificationEventType.BOARD_CREATED, "Sarah Chen created a board.");

        assertThat(created).hasSize(4);
        assertThat(notificationService.listForRecipient("sarah-chen")).hasSize(1);
        assertThat(notificationService.listForRecipient(ceoId)).hasSize(1);
        assertThat(notificationService.listForRecipient(hrId)).hasSize(1);
        assertThat(notificationService.listForRecipient(omId)).hasSize(1);
    }

    @Test
    void actorsReadStateIsIndependentFromOtherRecipients() {
        String ceoId = newAccount("CEO");
        notificationService.notify("sarah-chen", NotificationEventType.CLOCK_IN, "Sarah Chen clocked in.");
        Notification actorCopy = notificationService.listForRecipient("sarah-chen").get(0);

        notificationService.markRead("sarah-chen", actorCopy.getId());

        assertThat(notificationService.listForRecipient("sarah-chen").get(0).isRead()).isTrue();
        assertThat(notificationService.listForRecipient(ceoId).get(0).isRead()).isFalse();
    }

    @Test
    void markReadRejectsANonRecipientWithForbidden() {
        String ceoId = newAccount("CEO");
        notificationService.notify("sarah-chen", NotificationEventType.CLOCK_IN, "Sarah Chen clocked in.");
        Notification ceoCopy = notificationService.listForRecipient(ceoId).get(0);

        assertThatThrownBy(() -> notificationService.markRead("sarah-chen", ceoCopy.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void noDuplicateNotificationExistsForTheSameEventAndRecipient() {
        String ceoId = newAccount("CEO");

        notificationService.notify("sarah-chen", NotificationEventType.CLOCK_IN, "Sarah Chen clocked in.");

        // One row per recipient per event, never more, even though the CEO
        // both matches the management-role lookup and is a distinct person
        // from the actor here.
        assertThat(notificationService.listForRecipient(ceoId)).hasSize(1);
        assertThat(notificationService.listForRecipient("sarah-chen")).hasSize(1);
    }

    @Test
    void markAllReadOnlyAffectsThatRecipientsNotifications() {
        String ceoId = newAccount("CEO");
        notificationService.notify("sarah-chen", NotificationEventType.CLOCK_IN, "Sarah Chen clocked in.");
        notificationService.notify("alex-morgan", NotificationEventType.CLOCK_IN, "Alex Morgan clocked in.");

        notificationService.markAllRead(ceoId);

        assertThat(notificationService.listForRecipient(ceoId)).allMatch(Notification::isRead);
        assertThat(notificationService.listForRecipient("sarah-chen").get(0).isRead()).isFalse();
    }
}
