package com.workos.workos_backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.workos.workos_backend.actor.ActingPersonResolver;
import com.workos.workos_backend.dto.NotificationResponse;
import com.workos.workos_backend.dto.OkResponse;
import com.workos.workos_backend.dto.UnreadCountResponse;
import com.workos.workos_backend.service.NotificationService;

/** Notifications endpoints (spec section 18). The recipient is always the server-resolved actor, never a client-supplied id. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final ActingPersonResolver actingPersonResolver;

    public NotificationController(NotificationService notificationService, ActingPersonResolver actingPersonResolver) {
        this.notificationService = notificationService;
        this.actingPersonResolver = actingPersonResolver;
    }

    @GetMapping
    public List<NotificationResponse> list() {
        String actorId = actingPersonResolver.currentPersonId();
        return notificationService.listForRecipient(actorId).stream().map(NotificationResponse::from).toList();
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount() {
        String actorId = actingPersonResolver.currentPersonId();
        return new UnreadCountResponse(notificationService.unreadCount(actorId));
    }

    @PatchMapping("/{notificationId}/read")
    public NotificationResponse markRead(@PathVariable String notificationId) {
        String actorId = actingPersonResolver.currentPersonId();
        return NotificationResponse.from(notificationService.markRead(actorId, notificationId));
    }

    @PatchMapping("/read-all")
    public OkResponse markAllRead() {
        String actorId = actingPersonResolver.currentPersonId();
        notificationService.markAllRead(actorId);
        return OkResponse.OK;
    }
}
