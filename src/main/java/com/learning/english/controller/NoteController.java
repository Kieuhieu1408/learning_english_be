package com.learning.english.controller;

import com.learning.english.dto.NoteRequest;
import com.learning.english.model.Note;
import com.learning.english.service.NoteService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notes")
public class NoteController {

    @Autowired
    private NoteService noteService;

    @GetMapping
    public ResponseEntity<List<Note>> getNotes(@RequestParam(required = false) UUID topicId) {
        if (topicId != null) {
            return ResponseEntity.ok(noteService.getNotesByTopic(topicId));
        }
        return ResponseEntity.ok(noteService.getAllNotes());
    }

    @PostMapping
    public ResponseEntity<Note> saveNote(@Valid @RequestBody NoteRequest request) {
        return ResponseEntity.ok(noteService.saveNote(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNote(@PathVariable UUID id) {
        noteService.deleteNote(id);
        return ResponseEntity.noContent().build();
    }
}
