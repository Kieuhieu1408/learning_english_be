package com.learning.english.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    private String token;
    @Builder.Default
    private String tokenType = "Bearer";
    private UUID userId;
    private String username;
    private String email;
    private String role;
    private UserInfoDto user;

    @Builder
    public record UserInfoDto(
            String username,
            String name,
            String email,
            String role
    ) {}
}
