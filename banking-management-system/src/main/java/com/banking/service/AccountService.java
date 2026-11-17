package com.banking.service;

import com.banking.dto.AccountResponse;
import com.banking.dto.BalanceResponse;
import com.banking.dto.OpenAccountRequest;
import com.banking.entity.Account;
import com.banking.entity.BankTransaction;
import com.banking.entity.Customer;
import com.banking.entity.TransactionType;
import com.banking.exception.InvalidTransactionException;
import com.banking.exception.ResourceNotFoundException;
import com.banking.repository.AccountRepository;
import com.banking.repository.BankTransactionRepository;
import com.banking.repository.CustomerRepository;
import com.banking.util.NumberGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;
    private final BankTransactionRepository transactionRepository;

    public AccountService(AccountRepository accountRepository, CustomerRepository customerRepository,
                          BankTransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public AccountResponse open(OpenAccountRequest request) {
        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id " + request.customerId()));
        if (!customer.isActive()) {
            throw new InvalidTransactionException("Cannot open an account for an inactive customer");
        }

        BigDecimal initial = request.initialDeposit() == null
                ? BigDecimal.ZERO
                : request.initialDeposit().setScale(2, RoundingMode.HALF_UP);
        BigDecimal minimum = request.type().getMinBalance();
        if (initial.compareTo(minimum) < 0) {
            throw new InvalidTransactionException(
                    "Initial deposit must be at least " + minimum + " for a " + request.type() + " account");
        }

        Account account = new Account();
        account.setAccountNumber(generateUniqueAccountNumber());
        account.setCustomer(customer);
        account.setType(request.type());
        account.setBalance(initial);
        Account saved = accountRepository.save(account);

        if (initial.signum() > 0) {
            transactionRepository.save(BankTransaction.of(saved, TransactionType.DEPOSIT, initial, initial,
                    null, "Initial deposit", NumberGenerator.transactionReference()));
        }
        log.info("Account opened for customerId={}", customer.getId());
        return AccountResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public AccountResponse get(String accountNumber) {
        return AccountResponse.from(find(accountNumber));
    }

    @Transactional(readOnly = true)
    public BalanceResponse balance(String accountNumber) {
        Account account = find(accountNumber);
        return new BalanceResponse(account.getAccountNumber(), account.getBalance());
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listByCustomer(Long customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer not found with id " + customerId);
        }
        return accountRepository.findByCustomerId(customerId).stream().map(AccountResponse::from).toList();
    }

    /** Closing = deactivating. The balance must be zero first. */
    @Transactional
    public void close(String accountNumber) {
        Account account = find(accountNumber);
        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new InvalidTransactionException("Account balance must be zero before closing the account");
        }
        account.setActive(false);
        accountRepository.save(account);
        log.info("Account closed");
    }

    private Account find(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));
    }

    private String generateUniqueAccountNumber() {
        String number;
        do {
            number = NumberGenerator.accountNumber();
        } while (accountRepository.existsByAccountNumber(number));
        return number;
    }
}
