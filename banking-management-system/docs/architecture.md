# Architecture

```
Client ──HTTP──> JwtAuthFilter ──> Controller ──> Service (@Transactional) ──> Repository ──> H2 / MySQL
                     │                  │                │
                SecurityConfig     DTO validation   Business rules
                (roles/URLs)       (@Valid)         (limits, minimum balance, locking)
                                        │
                          GlobalExceptionHandler ──> consistent JSON errors
```

## Tables
- `customers` – profile data, unique email, `active` flag (soft delete)
- `users` – login (BCrypt password hash, role ADMIN/CUSTOMER)
- `accounts` – 12-digit unique number, type, `DECIMAL(19,2)` balance, FK to customers
- `transactions` – immutable history rows (reference, type, amount, balance after, counterpart), FK to accounts

## Transfer flow (single DB transaction)
1. Validate amount, different accounts, limit
2. Lock both account rows (`SELECT … FOR UPDATE`) in ascending account-number order
3. Check active + sufficient balance + minimum balance
4. Debit source, credit destination
5. Insert `…-D` and `…-C` history rows
Any exception rolls back everything.

## Error format
```json
{ "timestamp": "2026-11-17T10:22:00.100", "status": 400, "error": "Bad Request",
  "message": "Insufficient balance", "path": "/api/transactions/withdraw" }
```
