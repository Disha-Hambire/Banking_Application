-- Optional sample data for MySQL (run AFTER the application has started once, or after schema.sql).
-- Run:  mysql -u root -p banking_db < database/sample-data.sql
-- Note: users (including the admin) are created by the application, not by this script,
--       so that no password hash is ever stored in source control.

USE banking_db;

INSERT INTO customers (full_name, email, phone, address, date_of_birth, created_at, active) VALUES
 ('Amit Patil',  'amit.patil@example.com',  '9876543210', 'Pune, Maharashtra',   '1990-04-12', NOW(6), 1),
 ('Sneha Joshi', 'sneha.joshi@example.com', '9123456780', 'Mumbai, Maharashtra', '1994-09-23', NOW(6), 1);

INSERT INTO accounts (account_number, customer_id, type, balance, active, created_at)
SELECT '100000000001', id, 'SAVINGS', 25000.00, 1, NOW(6) FROM customers WHERE email = 'amit.patil@example.com';
INSERT INTO accounts (account_number, customer_id, type, balance, active, created_at)
SELECT '100000000002', id, 'CURRENT', 8000.00, 1, NOW(6) FROM customers WHERE email = 'sneha.joshi@example.com';

INSERT INTO transactions (reference_number, account_id, type, amount, balance_after, description, created_at)
SELECT 'TXNSAMPLE000000001', id, 'DEPOSIT', 25000.00, 25000.00, 'Initial deposit', NOW(6) FROM accounts WHERE account_number = '100000000001';
INSERT INTO transactions (reference_number, account_id, type, amount, balance_after, description, created_at)
SELECT 'TXNSAMPLE000000002', id, 'DEPOSIT', 8000.00, 8000.00, 'Initial deposit', NOW(6) FROM accounts WHERE account_number = '100000000002';
