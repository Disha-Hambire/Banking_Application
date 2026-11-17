package com.banking.service;

import com.banking.dto.TransactionRequest;
import com.banking.dto.TransactionResponse;
import com.banking.dto.TransferRequest;
import com.banking.dto.TransferResponse;
import com.banking.entity.Account;
import com.banking.entity.AccountType;
import com.banking.entity.BankTransaction;
import com.banking.entity.TransactionType;
import com.banking.exception.InsufficientBalanceException;
import com.banking.exception.InvalidTransactionException;
import com.banking.exception.ResourceNotFoundException;
import com.banking.exception.ValidationException;
import com.banking.repository.AccountRepository;
import com.banking.repository.BankTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final String ACC_A = "111111111111";
    private static final String ACC_B = "222222222222";

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private BankTransactionRepository transactionRepository;

    private TransactionService service;

    @BeforeEach
    void setUp() {
        service = new TransactionService(accountRepository, transactionRepository, new BigDecimal("50000.00"));
    }

    private Account account(String number, String balance, AccountType type) {
        Account a = new Account();
        a.setAccountNumber(number);
        a.setType(type);
        a.setBalance(new BigDecimal(balance));
        return a;
    }

    private void assertBalance(String expected, Account account) {
        assertEquals(0, new BigDecimal(expected).compareTo(account.getBalance()),
                "Expected balance " + expected + " but was " + account.getBalance());
    }

    private TransactionRequest req(String accountNumber, String amount) {
        return new TransactionRequest(accountNumber, new BigDecimal(amount), "test");
    }

    // ---------- deposit ----------

    @Test
    void deposit_increasesBalanceAndRecordsTransaction() {
        Account a = account(ACC_A, "1000.00", AccountType.CURRENT);
        when(accountRepository.findByAccountNumberForUpdate(ACC_A)).thenReturn(Optional.of(a));

        TransactionResponse response = service.deposit(req(ACC_A, "500.25"));

        assertBalance("1500.25", a);
        assertEquals(TransactionType.DEPOSIT, response.type());
        assertEquals("CREDIT", response.direction());

        ArgumentCaptor<BankTransaction> captor = ArgumentCaptor.forClass(BankTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(0, new BigDecimal("500.25").compareTo(captor.getValue().getAmount()));
        assertEquals(0, new BigDecimal("1500.25").compareTo(captor.getValue().getBalanceAfter()));
        assertTrue(captor.getValue().getReferenceNumber().startsWith("TXN"));
    }

    @Test
    void deposit_toUnknownAccount_throwsNotFound() {
        when(accountRepository.findByAccountNumberForUpdate(ACC_A)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.deposit(req(ACC_A, "10")));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void deposit_toInactiveAccount_isRejected() {
        Account a = account(ACC_A, "100.00", AccountType.CURRENT);
        a.setActive(false);
        when(accountRepository.findByAccountNumberForUpdate(ACC_A)).thenReturn(Optional.of(a));

        assertThrows(InvalidTransactionException.class, () -> service.deposit(req(ACC_A, "10")));
        assertBalance("100.00", a);
    }

    @Test
    void deposit_zeroOrNegativeAmount_isRejected() {
        assertThrows(InvalidTransactionException.class, () -> service.deposit(req(ACC_A, "0")));
        assertThrows(InvalidTransactionException.class, () -> service.deposit(req(ACC_A, "-5")));
        verifyNoInteractions(accountRepository);
    }

    // ---------- withdraw ----------

    @Test
    void withdraw_decreasesBalanceAndRecordsDebit() {
        Account a = account(ACC_A, "1000.00", AccountType.CURRENT);
        when(accountRepository.findByAccountNumberForUpdate(ACC_A)).thenReturn(Optional.of(a));

        TransactionResponse response = service.withdraw(req(ACC_A, "400"));

        assertBalance("600.00", a);
        assertEquals(TransactionType.WITHDRAWAL, response.type());
        assertEquals("DEBIT", response.direction());
        verify(transactionRepository).save(any(BankTransaction.class));
    }

    @Test
    void withdraw_moreThanBalance_throwsInsufficientAndKeepsBalance() {
        Account a = account(ACC_A, "100.00", AccountType.CURRENT);
        when(accountRepository.findByAccountNumberForUpdate(ACC_A)).thenReturn(Optional.of(a));

        assertThrows(InsufficientBalanceException.class, () -> service.withdraw(req(ACC_A, "500")));

        assertBalance("100.00", a);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void withdraw_thatBreachesSavingsMinimumBalance_isRejected() {
        Account a = account(ACC_A, "1000.00", AccountType.SAVINGS); // minimum balance 500
        when(accountRepository.findByAccountNumberForUpdate(ACC_A)).thenReturn(Optional.of(a));

        assertThrows(InsufficientBalanceException.class, () -> service.withdraw(req(ACC_A, "600")));
        assertBalance("1000.00", a);
    }

    @Test
    void withdraw_savingsDownToExactMinimumBalance_isAllowed() {
        Account a = account(ACC_A, "1000.00", AccountType.SAVINGS);
        when(accountRepository.findByAccountNumberForUpdate(ACC_A)).thenReturn(Optional.of(a));

        service.withdraw(req(ACC_A, "500"));

        assertBalance("500.00", a);
    }

    @Test
    void withdraw_aboveTransactionLimit_isRejectedBeforeTouchingAccount() {
        assertThrows(InvalidTransactionException.class, () -> service.withdraw(req(ACC_A, "50000.01")));
        verifyNoInteractions(accountRepository);
    }

    // ---------- transfer ----------

    @Test
    void transfer_movesMoneyAndWritesBothLegsWithSharedReference() {
        Account a = account(ACC_A, "1000.00", AccountType.CURRENT);
        Account b = account(ACC_B, "200.00", AccountType.CURRENT);
        when(accountRepository.findByAccountNumberForUpdate(ACC_A)).thenReturn(Optional.of(a));
        when(accountRepository.findByAccountNumberForUpdate(ACC_B)).thenReturn(Optional.of(b));

        TransferResponse response = service.transfer(new TransferRequest(ACC_A, ACC_B, new BigDecimal("300"), "rent"));

        assertBalance("700.00", a);
        assertBalance("500.00", b);
        assertEquals(0, new BigDecimal("700.00").compareTo(response.fromBalanceAfter()));

        ArgumentCaptor<BankTransaction> captor = ArgumentCaptor.forClass(BankTransaction.class);
        verify(transactionRepository, times(2)).save(captor.capture());
        List<BankTransaction> saved = captor.getAllValues();
        BankTransaction debit = saved.stream().filter(t -> t.getType() == TransactionType.TRANSFER_DEBIT).findFirst().orElseThrow();
        BankTransaction credit = saved.stream().filter(t -> t.getType() == TransactionType.TRANSFER_CREDIT).findFirst().orElseThrow();
        assertEquals(ACC_A, debit.getAccount().getAccountNumber());
        assertEquals(ACC_B, credit.getAccount().getAccountNumber());
        assertEquals(response.referenceNumber() + "-D", debit.getReferenceNumber());
        assertEquals(response.referenceNumber() + "-C", credit.getReferenceNumber());
    }

    @Test
    void transfer_whenSourceIsHigherNumberedAccount_stillMovesMoneyInRightDirection() {
        Account a = account(ACC_A, "100.00", AccountType.CURRENT);
        Account b = account(ACC_B, "900.00", AccountType.CURRENT);
        when(accountRepository.findByAccountNumberForUpdate(ACC_A)).thenReturn(Optional.of(a));
        when(accountRepository.findByAccountNumberForUpdate(ACC_B)).thenReturn(Optional.of(b));

        service.transfer(new TransferRequest(ACC_B, ACC_A, new BigDecimal("400"), null));

        assertBalance("500.00", a);
        assertBalance("500.00", b);
    }

    @Test
    void transfer_toSameAccount_isRejected() {
        assertThrows(InvalidTransactionException.class,
                () -> service.transfer(new TransferRequest(ACC_A, ACC_A, new BigDecimal("10"), null)));
        verifyNoInteractions(accountRepository);
    }

    @Test
    void transfer_withInsufficientFunds_changesNothing() {
        Account a = account(ACC_A, "100.00", AccountType.CURRENT);
        Account b = account(ACC_B, "200.00", AccountType.CURRENT);
        when(accountRepository.findByAccountNumberForUpdate(ACC_A)).thenReturn(Optional.of(a));
        when(accountRepository.findByAccountNumberForUpdate(ACC_B)).thenReturn(Optional.of(b));

        assertThrows(InsufficientBalanceException.class,
                () -> service.transfer(new TransferRequest(ACC_A, ACC_B, new BigDecimal("300"), null)));

        assertBalance("100.00", a);
        assertBalance("200.00", b);
        verify(transactionRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void transfer_toUnknownDestination_throwsNotFoundAndChangesNothing() {
        Account a = account(ACC_A, "1000.00", AccountType.CURRENT);
        when(accountRepository.findByAccountNumberForUpdate(ACC_A)).thenReturn(Optional.of(a));
        when(accountRepository.findByAccountNumberForUpdate(ACC_B)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.transfer(new TransferRequest(ACC_A, ACC_B, new BigDecimal("100"), null)));

        assertBalance("1000.00", a);
        verify(transactionRepository, never()).save(any());
    }

    // ---------- history ----------

    private BankTransaction tx(Account a, TransactionType type, LocalDateTime when) {
        BankTransaction t = BankTransaction.of(a, type, BigDecimal.TEN, BigDecimal.TEN, null, null, "TXN" + type + when);
        t.setCreatedAt(when);
        return t;
    }

    @Test
    void history_filtersByTypeAndDate() {
        Account a = account(ACC_A, "0", AccountType.CURRENT);
        LocalDate today = LocalDate.of(2026, 5, 10);
        when(accountRepository.findByAccountNumber(ACC_A)).thenReturn(Optional.of(a));
        when(transactionRepository.findByAccount_AccountNumberOrderByCreatedAtDesc(ACC_A)).thenReturn(List.of(
                tx(a, TransactionType.WITHDRAWAL, today.atTime(15, 0)),
                tx(a, TransactionType.DEPOSIT, today.atTime(9, 0)),
                tx(a, TransactionType.DEPOSIT, today.minusDays(5).atTime(9, 0))));

        assertEquals(3, service.history(ACC_A, null, null, null).size());
        assertEquals(2, service.history(ACC_A, TransactionType.DEPOSIT, null, null).size());
        assertEquals(2, service.history(ACC_A, null, today, today).size());
        assertEquals(1, service.history(ACC_A, TransactionType.DEPOSIT, today, today).size());
        assertEquals(0, service.history(ACC_A, null, today.plusDays(1), null).size());
    }

    @Test
    void history_withFromAfterTo_isRejected() {
        assertThrows(ValidationException.class, () ->
                service.history(ACC_A, null, LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 1)));
    }

    @Test
    void history_forUnknownAccount_throwsNotFound() {
        when(accountRepository.findByAccountNumber(ACC_A)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.history(ACC_A, null, null, null));
    }

    @Test
    void byReference_unknown_throwsNotFound() {
        when(transactionRepository.findByReferenceNumberStartingWith("TXNNOPE")).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> service.byReference("TXNNOPE"));
    }
}
