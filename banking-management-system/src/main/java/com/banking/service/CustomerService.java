package com.banking.service;

import com.banking.dto.CustomerRequest;
import com.banking.dto.CustomerResponse;
import com.banking.entity.Customer;
import com.banking.exception.DuplicateResourceException;
import com.banking.exception.ResourceNotFoundException;
import com.banking.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        String email = request.email().trim().toLowerCase();
        if (customerRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("A customer with this email already exists");
        }
        Customer customer = new Customer();
        apply(customer, request, email);
        Customer saved = customerRepository.save(customer);
        log.info("Customer created with id={}", saved.getId());
        return CustomerResponse.from(saved);
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer customer = find(id);
        String email = request.email().trim().toLowerCase();
        if (customerRepository.existsByEmailAndIdNot(email, id)) {
            throw new DuplicateResourceException("A customer with this email already exists");
        }
        apply(customer, request, email);
        Customer saved = customerRepository.save(customer);
        log.info("Customer updated with id={}", id);
        return CustomerResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        return CustomerResponse.from(find(id));
    }

    /** Lists all customers, or searches by id, name or email when a search term is given. */
    @Transactional(readOnly = true)
    public List<CustomerResponse> list(String search) {
        List<Customer> customers = new ArrayList<>();
        if (search == null || search.isBlank()) {
            customers.addAll(customerRepository.findAll());
        } else {
            String term = search.trim();
            customers.addAll(
                    customerRepository.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(term, term));
            if (term.matches("\\d{1,18}")) {
                customerRepository.findById(Long.valueOf(term)).ifPresent(c -> {
                    if (!customers.contains(c)) {
                        customers.add(c);
                    }
                });
            }
        }
        return customers.stream().map(CustomerResponse::from).toList();
    }

    /** "Delete" is a soft delete: the customer is deactivated, history is kept. */
    @Transactional
    public void deactivate(Long id) {
        Customer customer = find(id);
        customer.setActive(false);
        customerRepository.save(customer);
        log.info("Customer deactivated with id={}", id);
    }

    private Customer find(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id " + id));
    }

    private void apply(Customer c, CustomerRequest r, String email) {
        c.setFullName(r.fullName().trim());
        c.setEmail(email);
        c.setPhone(r.phone());
        c.setAddress(r.address());
        c.setDateOfBirth(r.dateOfBirth());
    }
}
