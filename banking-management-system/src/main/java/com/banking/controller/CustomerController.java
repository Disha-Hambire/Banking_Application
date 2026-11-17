package com.banking.controller;

import com.banking.dto.CustomerRequest;
import com.banking.dto.CustomerResponse;
import com.banking.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@Tag(name = "Customers (ADMIN)")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    @Operation(summary = "Create a customer (201, 400, 409)")
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.create(request));
    }

    @GetMapping
    @Operation(summary = "List customers; optional ?search= matches id, name or email (200)")
    public List<CustomerResponse> list(@RequestParam(name = "search", required = false) String search) {
        return customerService.list(search);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a customer (200, 404)")
    public CustomerResponse get(@PathVariable("id") Long id) {
        return customerService.get(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a customer (200, 400, 404, 409)")
    public CustomerResponse update(@PathVariable("id") Long id, @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate a customer - soft delete (204, 404)")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        customerService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
