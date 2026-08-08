package com.niet.facultyachievement.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    private String accessToken;
    @Builder.Default
    private String tokenType = "Bearer";
    private Long userId;
    private String email;
    private String fullName;
    private String role;
}
