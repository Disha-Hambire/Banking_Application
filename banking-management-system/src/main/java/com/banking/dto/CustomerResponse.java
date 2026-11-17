package com.banking.dto;

import com.banking.entity.Customer;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CustomerResponse(Long id, String fullName, String email, String phone, String address,
                               LocalDate dateOfBirth, LocalDateTime createdAt, boolean active) {

    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(c.getId(), c.getFullName(), c.getEmail(), c.getPhone(),
                c.getAddress(), c.getDateOfBirth(), c.getCreatedAt(), c.isActive());
    }
}
