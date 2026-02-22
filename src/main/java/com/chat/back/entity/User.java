package com.chat.back.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a registered user of the chat application.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Unique phone number used for registration (E.164 format, e.g. +12125551234). */
    @Column(name = "phone_number", unique = true, nullable = false, length = 20)
    private String phoneNumber;

    /** User-chosen display name. */
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    /** Optional status / "about" text. */
    @Column(name = "about", length = 500)
    private String about;

    /** URL to the user's profile picture (stored externally, e.g. S3). */
    @Column(name = "profile_picture_url")
    private String profilePictureUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen")
    private Instant lastSeen;

    /** Conversations this user participates in. */
    @OneToMany(mappedBy = "user", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = false)
    @Builder.Default
    private List<ConversationParticipant> conversations = new ArrayList<>();
}
