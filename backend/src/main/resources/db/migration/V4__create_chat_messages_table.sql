-- Chat module. Does not modify V1, V2, or V3 in any way.
-- conversationId is a derived string, not a stored entity — "general" (the
-- one built-in channel) or "dm:<personIdA>:<personIdB>" with the two ids
-- sorted (see src/lib/conversation-id.ts). Both client and server compute
-- the same id independently, so there is no "conversations" table, matching
-- the frontend's own chat-repository.ts shape (ChatMessage only).
-- Access to a conversation is enforced in ChatService, not the DB.

CREATE TABLE chat_messages (
    id              VARCHAR(64)  NOT NULL,
    conversation_id VARCHAR(160) NOT NULL,
    author_id       VARCHAR(64)  NOT NULL,
    text            TEXT         NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_chat_messages_author
        FOREIGN KEY (author_id) REFERENCES people (id)
) ENGINE = InnoDB;

-- Speeds up "message history for this conversation, oldest first" (GET
-- /api/chat/messages), the hot path every time a channel/DM is opened.
CREATE INDEX idx_chat_messages_conversation ON chat_messages (conversation_id, created_at);
