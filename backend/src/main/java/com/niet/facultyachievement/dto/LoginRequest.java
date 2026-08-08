package com.niet.facultyachievement.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequest {

    @NotBlank(message = "Email or Employee ID is required")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}
