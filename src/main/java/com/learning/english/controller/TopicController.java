package com.learning.english.controller;

import com.learning.english.dto.TopicDto;
import com.learning.english.dto.TopicRequest;
import com.learning.english.service.TopicService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/topics")
public class TopicController {

    @Autowired
    private TopicService topicService;

    @GetMapping
    public ResponseEntity<List<TopicDto>> getAllTopics() {
        return ResponseEntity.ok(topicService.getAllTopics());
    }

    @PostMapping
    public ResponseEntity<TopicDto> createTopic(@Valid @RequestBody TopicRequest request) {
        return ResponseEntity.ok(topicService.createOrUpsertTopic(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TopicDto> updateTopic(
            @PathVariable UUID id,
            @Valid @RequestBody TopicRequest request
    ) {
        return ResponseEntity.ok(topicService.updateTopic(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTopic(@PathVariable UUID id) {
        topicService.deleteTopic(id);
        return ResponseEntity.noContent().build();
    }
}
