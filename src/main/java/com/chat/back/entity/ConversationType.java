package com.chat.back.entity;

/**
 * Discriminates between one-to-one (direct) conversations and group chats.
 */
public enum ConversationType {
    DIRECT,
    GROUP
}
