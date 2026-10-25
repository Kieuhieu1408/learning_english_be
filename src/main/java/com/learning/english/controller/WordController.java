package com.learning.english.controller;

import com.learning.english.dto.*;
import com.learning.english.service.WordService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/words")
public class WordController {

    @Autowired
    private WordService wordService;

    @GetMapping
    public ResponseEntity<WordListResponse> getWords(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "recent") String sortBy,
            @RequestParam(required = false, defaultValue = "50") int limit,
            @RequestParam(required = false, defaultValue = "0") int offset
    ) {
        return ResponseEntity.ok(wordService.getWords(q, status, sortBy, limit, offset));
    }

    @PostMapping("/context")
    public ResponseEntity<SaveWordContextResponse> saveWordContext(@Valid @RequestBody SaveWordContextRequest request) {
        return ResponseEntity.ok(wordService.saveWordContext(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WordDto> updateWord(
            @PathVariable UUID id,
            @RequestBody UpdateWordRequest request
    ) {
        return ResponseEntity.ok(wordService.updateWord(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWord(@PathVariable UUID id) {
        wordService.deleteWord(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{wordId}/contexts/{ctxId}")
    public ResponseEntity<Void> deleteWordContext(
            @PathVariable UUID wordId,
            @PathVariable UUID ctxId
    ) {
        wordService.deleteWordContext(wordId, ctxId);
        return ResponseEntity.noContent().build();
    }
}
