package com.learning.english.controller;

import com.learning.english.dto.*;
import com.learning.english.service.DeltaSyncService;
import com.learning.english.service.SyncService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    @Autowired
    private SyncService syncService;

    @Autowired
    private DeltaSyncService deltaSyncService;

    @GetMapping("/counters")
    public ResponseEntity<SyncCountersResponse> getCounters() {
        return ResponseEntity.ok(syncService.getCounters());
    }

    @PostMapping("/delta")
    public ResponseEntity<DeltaSyncResponse> syncDelta(@RequestBody DeltaSyncRequest request) {
        return ResponseEntity.ok(deltaSyncService.syncDelta(request));
    }

    @PostMapping("/resolve-conflict")
    public ResponseEntity<NoteDto> resolveConflict(@Valid @RequestBody ResolveConflictRequest request) {
        return ResponseEntity.ok(deltaSyncService.resolveConflict(request));
    }

    @PostMapping("/batch")
    public ResponseEntity<SyncResponse> syncBatch(
            @RequestBody SyncRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        SyncResponse response = syncService.syncBatch(request, idempotencyKey);
        return ResponseEntity.ok(response);
    }
}
