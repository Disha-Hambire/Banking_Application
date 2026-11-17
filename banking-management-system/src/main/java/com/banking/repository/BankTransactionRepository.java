package com.banking.repository;

import com.banking.entity.BankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    List<BankTransaction> findByAccount_AccountNumberOrderByCreatedAtDesc(String accountNumber);

    List<BankTransaction> findByReferenceNumberStartingWith(String referenceNumber);
}
