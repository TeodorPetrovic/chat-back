package com.chat.back.repository;

import com.chat.back.entity.Conversation;
import com.chat.back.entity.ConversationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    List<Conversation> findByType(ConversationType type);
}
