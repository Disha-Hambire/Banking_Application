package com.banking.service;

import com.banking.dto.TransactionRequest;
import com.banking.dto.TransactionResponse;
import com.banking.dto.TransferRequest;
import com.banking.dto.TransferResponse;
import com.banking.entity.Account;
import com.banking.entity.BankTransaction;
import com.banking.entity.TransactionType;
import com.banking.exception.InsufficientBalanceException;
import com.banking.exception.InvalidTransactionException;
import com.banking.exception.ResourceNotFoundException;
import com.banking.exception.ValidationException;
import com.banking.repository.AccountRepository;
import com.banking.repository.BankTransactionRepository;
import com.banking.util.NumberGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final AccountRepository accountRepository;
    private final BankTransactionRepository transactionRepository;
    private final BigDecimal transactionLimit;

    public TransactionService(AccountRepository accountRepository,
                              BankTransactionRepository transactionRepository,
                              @Value("${banking.limits.withdrawal:50000.00}") BigDecimal transactionLimit) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.transactionLimit = transactionLimit;
    }

    @Transactional
    public TransactionResponse deposit(TransactionRequest request) {
        BigDecimal amount = normalize(request.amount());
        Account account = lock(request.accountNumber());
        ensureActive(account);

        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
        BankTransaction tx = BankTransaction.of(account, TransactionType.DEPOSIT, amount, account.getBalance(),
                null, request.description(), NumberGenerator.transactionReference());
        transactionRepository.save(tx);
        log.info("Deposit completed ref={}", tx.getReferenceNumber());
        return TransactionResponse.from(tx);
    }

    @Transactional
    public TransactionResponse withdraw(TransactionRequest request) {
        BigDecimal amount = normalize(request.amount());
        ensureWithinLimit(amount);
        Account account = lock(request.accountNumber());
        ensureActive(account);
        ensureSufficientFunds(account, amount);

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        BankTransaction tx = BankTransaction.of(account, TransactionType.WITHDRAWAL, amount, account.getBalance(),
                null, request.description(), NumberGenerator.transactionReference());
        transactionRepository.save(tx);
        log.info("Withdrawal completed ref={}", tx.getReferenceNumber());
        return TransactionResponse.from(tx);
    }

    /**
     * Atomic transfer: both balance updates and both history rows are saved in a single
     * database transaction, so any failure rolls everything back.
     */
    @Transactional
    public TransferResponse transfer(TransferRequest request) {
        BigDecimal amount = normalize(request.amount());
        String from = request.fromAccount();
        String to = request.toAccount();
        if (from.equals(to)) {
            throw new InvalidTransactionException("Source and destination accounts must be different");
        }
        ensureWithinLimit(amount);

        // Lock in a fixed order (lowest account number first) to avoid deadlocks.
        boolean fromFirst = from.compareTo(to) < 0;
        Account first = lock(fromFirst ? from : to);
        Account second = lock(fromFirst ? to : from);
        Account source = fromFirst ? first : second;
        Account destination = fromFirst ? second : first;

        ensureActive(source);
        ensureActive(destination);
        ensureSufficientFunds(source, amount);

        source.setBalance(source.getBalance().subtract(amount));
        destination.setBalance(destination.getBalance().add(amount));
        accountRepository.save(source);
        accountRepository.save(destination);

        String reference = NumberGenerator.transactionReference();
        transactionRepository.save(BankTransaction.of(source, TransactionType.TRANSFER_DEBIT, amount,
                source.getBalance(), to, request.description(), reference + "-D"));
        transactionRepository.save(BankTransaction.of(destination, TransactionType.TRANSFER_CREDIT, amount,
                destination.getBalance(), from, request.description(), reference + "-C"));
        log.info("Transfer completed ref={}", reference);
        return new TransferResponse(reference, from, to, amount, source.getBalance(), LocalDateTime.now());
    }

    /** Transaction history of an account, newest first, optionally filtered by type and date range. */
    @Transactional(readOnly = true)
    public List<TransactionResponse> history(String accountNumber, TransactionType type,
                                             LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ValidationException("'from' date must not be after 'to' date");
        }
        accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));

        LocalDateTime start = from == null ? null : from.atStartOfDay();
        LocalDateTime endExclusive = to == null ? null : to.plusDays(1).atStartOfDay();

        return transactionRepository.findByAccount_AccountNumberOrderByCreatedAtDesc(accountNumber).stream()
                .filter(t -> type == null || t.getType() == type)
                .filter(t -> start == null || !t.getCreatedAt().isBefore(start))
                .filter(t -> endExclusive == null || t.getCreatedAt().isBefore(endExclusive))
                .map(TransactionResponse::from)
                .toList();
    }

    /** Finds by reference. A transfer reference returns both legs (debit and credit). */
    @Transactional(readOnly = true)
    public List<TransactionResponse> byReference(String referenceNumber) {
        List<TransactionResponse> result = transactionRepository.findByReferenceNumberStartingWith(referenceNumber)
                .stream().map(TransactionResponse::from).toList();
        if (result.isEmpty()) {
            throw new ResourceNotFoundException("Transaction not found: " + referenceNumber);
        }
        return result;
    }

    // ---------- helpers ----------

    private BigDecimal normalize(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidTransactionException("Amount must be greater than zero");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private void ensureWithinLimit(BigDecimal amount) {
        if (amount.compareTo(transactionLimit) > 0) {
            throw new InvalidTransactionException("Amount exceeds the transaction limit of " + transactionLimit);
        }
    }

    private Account lock(String accountNumber) {
        return accountRepository.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountNumber));
    }

    private void ensureActive(Account account) {
        if (!account.isActive()) {
            throw new InvalidTransactionException("Account " + account.getAccountNumber() + " is not active");
        }
    }

    private void ensureSufficientFunds(Account account, BigDecimal amount) {
        BigDecimal remaining = account.getBalance().subtract(amount);
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance");
        }
        BigDecimal minimum = account.getType().getMinBalance();
        if (remaining.compareTo(minimum) < 0) {
            throw new InsufficientBalanceException("Operation would breach the minimum balance of " + minimum);
        }
    }
}
