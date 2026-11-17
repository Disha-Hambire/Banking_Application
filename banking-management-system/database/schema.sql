-- Optional: the application creates/updates these tables automatically (spring.jpa.hibernate.ddl-auto=update).
-- Use this script if you prefer to create the MySQL schema manually.
-- Run:  mysql -u root -p < database/schema.sql

CREATE DATABASE IF NOT EXISTS banking_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE banking_db;

CREATE TABLE IF NOT EXISTS customers (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL,
    phone         VARCHAR(20)  NOT NULL,
    address       VARCHAR(255),
    date_of_birth DATE         NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6),
    active        BIT(1)       NOT NULL DEFAULT 1,
    CONSTRAINT uk_customers_email UNIQUE (email)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS users (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL,
    password   VARCHAR(100) NOT NULL,          -- BCrypt hash only
    role       VARCHAR(20)  NOT NULL,          -- ADMIN | CUSTOMER
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6),
    CONSTRAINT uk_users_username UNIQUE (username)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS accounts (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_number VARCHAR(12)    NOT NULL,
    customer_id    BIGINT         NOT NULL,
    type           VARCHAR(20)    NOT NULL,    -- SAVINGS | CURRENT
    balance        DECIMAL(19,2)  NOT NULL DEFAULT 0.00,
    active         BIT(1)         NOT NULL DEFAULT 1,
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6),
    CONSTRAINT uk_accounts_number UNIQUE (account_number),
    CONSTRAINT fk_accounts_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT chk_accounts_balance CHECK (balance >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS transactions (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    reference_number     VARCHAR(40)   NOT NULL,
    account_id           BIGINT        NOT NULL,
    type                 VARCHAR(20)   NOT NULL, -- DEPOSIT | WITHDRAWAL | TRANSFER_DEBIT | TRANSFER_CREDIT
    amount               DECIMAL(19,2) NOT NULL,
    balance_after        DECIMAL(19,2) NOT NULL,
    counterpart_account  VARCHAR(12),
    description          VARCHAR(200),
    created_at           DATETIME(6)   NOT NULL,
    CONSTRAINT uk_transactions_reference UNIQUE (reference_number),
    CONSTRAINT fk_transactions_account FOREIGN KEY (account_id) REFERENCES accounts (id)
) ENGINE=InnoDB;

CREATE INDEX idx_accounts_customer     ON accounts (customer_id);
CREATE INDEX idx_transactions_account  ON transactions (account_id, created_at);
CREATE INDEX idx_transactions_created  ON transactions (created_at);
