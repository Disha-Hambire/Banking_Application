package com.banking.repository;

import com.banking.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    List<Customer> findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String name, String email);
}
