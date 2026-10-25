package com.learning.english.controller;

import com.learning.english.dto.ProgressDashboardResponse;
import com.learning.english.service.ProgressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    @Autowired
    private ProgressService progressService;

    @GetMapping("/dashboard")
    public ResponseEntity<ProgressDashboardResponse> getDashboard() {
        return ResponseEntity.ok(progressService.getDashboard());
    }
}
