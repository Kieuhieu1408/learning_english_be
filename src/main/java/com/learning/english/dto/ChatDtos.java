package com.learning.english.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ChatDtos {

    private ChatDtos() {}

    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConversationDto(
            UUID id,
            String title,
            UUID noteId,
            Instant createdAt,
            Instant updatedAt
    ) {}

    @Builder
    public record CreateConversationRequest(
            UUID id,
            @NotBlank(message = "Conversation title cannot be blank")
            String title,
            UUID noteId
    ) {}

    @Builder
    public record UpdateConversationRequest(
            @NotBlank(message = "Conversation title cannot be blank")
            String title
    ) {}

    @Builder
    public record ChatMessageDto(
            UUID id,
            String role,
            String content,
            Instant createdAt
    ) {}

    @Builder
    public record ChatSendRequest(
            UUID conversationId,
            UUID noteId,
            List<UUID> wordIds,
            @NotBlank(message = "Message cannot be blank")
            String message
    ) {}
}
