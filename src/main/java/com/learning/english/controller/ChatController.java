package com.learning.english.controller;

import com.learning.english.dto.ChatDtos.*;
import com.learning.english.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationDto>> getConversations() {
        return ResponseEntity.ok(chatService.getConversations());
    }

    @PostMapping("/conversations")
    public ResponseEntity<ConversationDto> createConversation(@Valid @RequestBody CreateConversationRequest request) {
        return ResponseEntity.ok(chatService.createOrUpsertConversation(request));
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<List<ChatMessageDto>> getMessages(@PathVariable UUID id) {
        return ResponseEntity.ok(chatService.getMessages(id));
    }

    @PutMapping("/conversations/{id}")
    public ResponseEntity<ConversationDto> renameConversation(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateConversationRequest request
    ) {
        return ResponseEntity.ok(chatService.renameConversation(id, request));
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable UUID id) {
        chatService.deleteConversation(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/send", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(@Valid @RequestBody ChatSendRequest request) {
        return chatService.sendMessageAndStream(request);
    }
}
