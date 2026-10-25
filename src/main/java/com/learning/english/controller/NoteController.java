package com.learning.english.controller;

import com.learning.english.dto.NoteDto;
import com.learning.english.dto.NoteRequest;
import com.learning.english.service.NoteService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notes")
public class NoteController {

    @Autowired
    private NoteService noteService;

    @GetMapping
    public ResponseEntity<List<NoteDto>> getNotes(@RequestParam(required = false) UUID topicId) {
        if (topicId != null) {
            return ResponseEntity.ok(noteService.getNotesByTopic(topicId));
        }
        return ResponseEntity.ok(noteService.getAllNotes());
    }

    @GetMapping("/{id}")
    public ResponseEntity<NoteDto> getNoteById(@PathVariable UUID id) {
        return ResponseEntity.ok(noteService.getNoteDetail(id));
    }

    @PostMapping
    public ResponseEntity<NoteDto> saveNote(@Valid @RequestBody NoteRequest request) {
        return ResponseEntity.ok(noteService.createOrUpsertNote(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateNote(
            @PathVariable UUID id,
            @Valid @RequestBody NoteRequest request
    ) {
        try {
            NoteDto updated = noteService.updateNote(id, request);
            return ResponseEntity.ok(updated);
        } catch (NoteService.NoteConflictException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", "CONFLICT",
                    "message", ex.getMessage(),
                    "conflict", ex.getConflict()
            ));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNote(@PathVariable UUID id) {
        noteService.deleteNote(id);
        return ResponseEntity.noContent().build();
    }
}
