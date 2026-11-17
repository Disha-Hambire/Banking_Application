package com.banking.service;

import com.banking.dto.CustomerRequest;
import com.banking.dto.CustomerResponse;
import com.banking.entity.Customer;
import com.banking.exception.DuplicateResourceException;
import com.banking.exception.ResourceNotFoundException;
import com.banking.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerService customerService;

    private CustomerRequest request(String email) {
        return new CustomerRequest("John Doe", email, "9876543210", "Pune", LocalDate.of(1990, 1, 1));
    }

    private Customer existing(Long id, String email) {
        Customer c = new Customer();
        c.setId(id);
        c.setFullName("John Doe");
        c.setEmail(email);
        c.setPhone("9876543210");
        c.setDateOfBirth(LocalDate.of(1990, 1, 1));
        return c;
    }

    @Test
    void create_savesCustomerWithNormalizedEmailAndActiveStatus() {
        when(customerRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        CustomerResponse response = customerService.create(request("  John@Example.COM "));

        assertEquals(1L, response.id());
        assertEquals("john@example.com", response.email());
        assertEquals("John Doe", response.fullName());
        assertTrue(response.active());
    }

    @Test
    void create_withDuplicateEmail_throwsAndDoesNotSave() {
        when(customerRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> customerService.create(request("john@example.com")));

        verify(customerRepository, never()).save(any());
    }

    @Test
    void get_unknownId_throwsResourceNotFound() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> customerService.get(99L));
    }

    @Test
    void update_changesFields() {
        Customer c = existing(1L, "john@example.com");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(c));
        when(customerRepository.existsByEmailAndIdNot("new@example.com", 1L)).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerResponse response = customerService.update(1L, request("new@example.com"));

        assertEquals("new@example.com", response.email());
        assertEquals("new@example.com", c.getEmail());
    }

    @Test
    void update_withEmailOfAnotherCustomer_throwsDuplicate() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(existing(1L, "john@example.com")));
        when(customerRepository.existsByEmailAndIdNot("taken@example.com", 1L)).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> customerService.update(1L, request("taken@example.com")));

        verify(customerRepository, never()).save(any());
    }

    @Test
    void deactivate_setsActiveFalse() {
        Customer c = existing(1L, "john@example.com");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(c));

        customerService.deactivate(1L);

        assertFalse(c.isActive());
        verify(customerRepository).save(c);
    }

    @Test
    void list_withNumericSearch_alsoMatchesById() {
        Customer byId = existing(7L, "seven@example.com");
        when(customerRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase("7", "7"))
                .thenReturn(List.of());
        when(customerRepository.findById(7L)).thenReturn(Optional.of(byId));

        List<CustomerResponse> result = customerService.list("7");

        assertEquals(1, result.size());
        assertEquals(7L, result.get(0).id());
    }

    @Test
    void list_withoutSearch_returnsAll() {
        when(customerRepository.findAll()).thenReturn(List.of(existing(1L, "a@example.com"), existing(2L, "b@example.com")));

        assertEquals(2, customerService.list(null).size());
    }
}
