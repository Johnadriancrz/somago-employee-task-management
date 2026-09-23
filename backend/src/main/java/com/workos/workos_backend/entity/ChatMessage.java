package com.workos.workos_backend.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Mirrors the frontend's {@code ChatMessage} type (workos-app/src/lib/types.ts).
 * {@code conversationId} is a derived string ("general", or
 * "dm:&lt;personIdA&gt;:&lt;personIdB&gt;" with the two ids sorted — see
 * src/lib/conversation-id.ts), not a foreign key to any "conversations"
 * table — there isn't one, matching the frontend's own chat-repository.ts.
 * Whether the acting person may read/post to a given conversationId
 * ("general" is open to everyone; a DM is only visible to its two
 * participants) is enforced in {@link com.workos.workos_backend.service.ChatService},
 * not the DB.
 */
@Entity
@Table(name = "chat_messages")
public class ChatMessage extends AssignedIdEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "conversation_id", nullable = false, length = 160)
    private String conversationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private Person author;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChatMessage() {
    }

    public ChatMessage(String id, String conversationId, Person author, String text, Instant createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.author = author;
        this.text = text;
        this.createdAt = createdAt;
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public Person getAuthor() {
        return author;
    }

    public void setAuthor(Person author) {
        this.author = author;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
