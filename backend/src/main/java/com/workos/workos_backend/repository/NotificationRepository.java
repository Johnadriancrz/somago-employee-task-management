package com.workos.workos_backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.workos.workos_backend.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, String> {

    /** GET /api/notifications — newest first. */
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(String recipientId);

    List<Notification> findByRecipientIdAndReadFalse(String recipientId);

    long countByRecipientIdAndReadFalse(String recipientId);
}
