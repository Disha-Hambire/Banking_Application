package com.banking.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record CustomerRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "must contain 10 to 15 digits") String phone,
        @Size(max = 255) String address,
        @NotNull @Past LocalDate dateOfBirth) {
}
