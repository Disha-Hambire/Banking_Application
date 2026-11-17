package com.banking.service;

import com.banking.dto.AccountResponse;
import com.banking.dto.OpenAccountRequest;
import com.banking.entity.Account;
import com.banking.entity.AccountType;
import com.banking.entity.BankTransaction;
import com.banking.entity.Customer;
import com.banking.exception.InvalidTransactionException;
import com.banking.exception.ResourceNotFoundException;
import com.banking.repository.AccountRepository;
import com.banking.repository.BankTransactionRepository;
import com.banking.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private BankTransactionRepository transactionRepository;

    @InjectMocks
    private AccountService accountService;

    private Customer customer(boolean active) {
        Customer c = new Customer();
        c.setId(1L);
        c.setActive(active);
        return c;
    }

    private Account account(String balance) {
        Account a = new Account();
        a.setAccountNumber("123456789012");
        a.setCustomer(customer(true));
        a.setType(AccountType.CURRENT);
        a.setBalance(new BigDecimal(balance));
        return a;
    }

    @Test
    void openSavingsAccount_generatesNumberSetsBalanceAndRecordsInitialDeposit() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(true)));
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = accountService.open(
                new OpenAccountRequest(1L, AccountType.SAVINGS, new BigDecimal("1000")));

        assertTrue(response.accountNumber().matches("[1-9]\\d{11}"));
        assertEquals(AccountType.SAVINGS, response.type());
        assertEquals(0, new BigDecimal("1000.00").compareTo(response.balance()));
        assertTrue(response.active());
        verify(transactionRepository, times(1)).save(any(BankTransaction.class));
    }

    @Test
    void openAccount_retriesWhenGeneratedNumberAlreadyExists() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(true)));
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(true, false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        accountService.open(new OpenAccountRequest(1L, AccountType.CURRENT, null));

        verify(accountRepository, times(2)).existsByAccountNumber(anyString());
    }

    @Test
    void openSavingsAccount_belowMinimumDeposit_isRejected() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(true)));

        assertThrows(InvalidTransactionException.class, () ->
                accountService.open(new OpenAccountRequest(1L, AccountType.SAVINGS, new BigDecimal("100"))));

        verify(accountRepository, never()).save(any());
    }

    @Test
    void openCurrentAccount_withoutInitialDeposit_hasZeroBalanceAndNoTransaction() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(true)));
        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = accountService.open(new OpenAccountRequest(1L, AccountType.CURRENT, null));

        assertEquals(0, BigDecimal.ZERO.compareTo(response.balance()));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void openAccount_forInactiveCustomer_isRejected() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer(false)));

        assertThrows(InvalidTransactionException.class, () ->
                accountService.open(new OpenAccountRequest(1L, AccountType.CURRENT, null)));
    }

    @Test
    void openAccount_forUnknownCustomer_throwsNotFound() {
        when(customerRepository.findById(5L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                accountService.open(new OpenAccountRequest(5L, AccountType.CURRENT, null)));
    }

    @Test
    void balance_returnsCurrentBalance() {
        when(accountRepository.findByAccountNumber("123456789012")).thenReturn(Optional.of(account("250.75")));

        assertEquals(0, new BigDecimal("250.75").compareTo(accountService.balance("123456789012").balance()));
    }

    @Test
    void balance_forUnknownAccount_throwsNotFound() {
        when(accountRepository.findByAccountNumber("999999999999")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> accountService.balance("999999999999"));
    }

    @Test
    void close_withRemainingBalance_isRejected() {
        Account a = account("10.00");
        when(accountRepository.findByAccountNumber("123456789012")).thenReturn(Optional.of(a));

        assertThrows(InvalidTransactionException.class, () -> accountService.close("123456789012"));
        assertTrue(a.isActive());
    }

    @Test
    void close_withZeroBalance_deactivatesAccount() {
        Account a = account("0.00");
        when(accountRepository.findByAccountNumber("123456789012")).thenReturn(Optional.of(a));

        accountService.close("123456789012");

        assertFalse(a.isActive());
    }
}
