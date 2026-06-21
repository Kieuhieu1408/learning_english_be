package com.learning.english.controller;

import com.learning.english.dto.CreateUserRequest;
import com.learning.english.model.User;
import com.learning.english.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private UserService userService;

    @PostMapping("/create-user")
    public ResponseEntity<User> createUser(@Valid @RequestBody CreateUserRequest request) {
        User createdUser = userService.createUserByAdmin(request);
        return ResponseEntity.ok(createdUser);
    }
}
