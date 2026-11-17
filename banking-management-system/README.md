# Banking Management System

A simple Spring Boot 3 / Java 17 REST API for basic banking: customers, savings/current accounts, deposits, withdrawals,
atomic fund transfers, transaction history, and JWT authentication with ADMIN / CUSTOMER roles.

> **Runs with zero database setup** (in-memory H2 by default). Switch to MySQL with one flag when you want persistent data.

## Tech stack
Java 17 · Spring Boot 3.2.5 · Spring Web · Spring Data JPA/Hibernate · Spring Security + JWT (jjwt 0.12.5) · BCrypt ·
H2 (default) / MySQL 8 · springdoc-openapi (Swagger UI) · JUnit 5 · Mockito · MockMvc · JaCoCo · SLF4J/Logback · Maven

## Project structure
```
banking-management-system/
├── pom.xml
├── README.md
├── .gitignore
├── .github/workflows/maven-build.yml     # CI: compile, test, coverage, package, upload JAR
├── database/schema.sql                    # optional MySQL schema
├── database/sample-data.sql               # optional MySQL sample data
├── postman/banking-api-collection.json    # import into Postman
├── docs/architecture.md
└── src/
    ├── main/java/com/banking/
    │   ├── controller/   REST endpoints
    │   ├── service/      business logic (@Transactional)
    │   ├── repository/   Spring Data JPA
    │   ├── entity/       JPA entities (customers, users, accounts, transactions)
    │   ├── dto/          request/response records + validation
    │   ├── exception/    custom exceptions + GlobalExceptionHandler
    │   ├── security/     JWT service + filter + UserDetailsService
    │   ├── config/       SecurityConfig, OpenApiConfig, DataInitializer
    │   └── util/         account-number / reference generators
    ├── main/resources/   application.properties, application-mysql.properties, logback-spring.xml
    └── test/             unit tests (Mockito) + end-to-end API tests (MockMvc, H2)
```

## 1. Prerequisites
| Tool | Version | Check |
|------|---------|-------|
| JDK | 17 or newer | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Git | any | `git --version` |
| MySQL | 8 (optional) | `mysql --version` |

**Install (examples)**
- Windows: `winget install EclipseAdoptium.Temurin.17.JDK`, then download Maven from https://maven.apache.org/download.cgi, unzip, and add its `bin` folder to `PATH`.
- macOS: `brew install openjdk@17 maven git`
- Ubuntu/Debian: `sudo apt update && sudo apt install -y openjdk-17-jdk maven git`

Open a **new** terminal afterwards and re-run the check commands.

## 2. Build and run

```bash
unzip banking-management-system.zip
cd banking-management-system

mvn clean package          # compiles, runs all tests, builds target/banking-management-system.jar
java -jar target/banking-management-system.jar
```
or run without building the JAR: `mvn spring-boot:run`

The app starts on **http://localhost:8080**. A default admin is created on first start:

| Username | Password |
|----------|----------|
| `admin`  | `Admin@12345` |

Change them (and the JWT secret) with environment variables before any real use — see *Configuration*.

## 3. Swagger UI
Open **http://localhost:8080/swagger-ui/index.html** → `POST /api/auth/login` → copy the `token` → click **Authorize** → paste the token.

## 4. Try the API (curl)
```bash
# 1. Login as admin
curl -s -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"Admin@12345"}'
# copy the "token" value, then:
TOKEN=<paste-token-here>

# 2. Create a customer
curl -s -X POST localhost:8080/api/customers -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
     -d '{"fullName":"Amit Patil","email":"amit@example.com","phone":"9876543210","address":"Pune","dateOfBirth":"1990-04-12"}'

# 3. Open a savings account (use the customer "id" returned above)
curl -s -X POST localhost:8080/api/accounts -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
     -d '{"customerId":1,"type":"SAVINGS","initialDeposit":10000}'

# 4. Deposit / withdraw / transfer (use real 12-digit account numbers)
curl -s -X POST localhost:8080/api/transactions/deposit  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"accountNumber":"<acc>","amount":500}'
curl -s -X POST localhost:8080/api/transactions/withdraw -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"accountNumber":"<acc>","amount":200}'
curl -s -X POST localhost:8080/api/transactions/transfer -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"fromAccount":"<acc1>","toAccount":"<acc2>","amount":100}'

# 5. History
curl -s "localhost:8080/api/transactions/<acc>?type=DEPOSIT&from=2026-01-01&to=2026-12-31" -H "Authorization: Bearer $TOKEN"
```
**Postman:** import `postman/banking-api-collection.json` and run the requests in order (tokens/ids are saved automatically).

### Endpoints
| Method | Path | Who |
|--------|------|-----|
| POST | `/api/auth/register`, `/api/auth/login` | public |
| POST / GET / GET{id} / PUT / DELETE | `/api/customers` (`?search=` on list; DELETE = deactivate) | ADMIN |
| POST | `/api/accounts` | ADMIN |
| GET | `/api/accounts/{accountNumber}`, `/api/accounts/{accountNumber}/balance` | any logged-in user |
| GET | `/api/customers/{customerId}/accounts` | ADMIN |
| DELETE | `/api/accounts/{accountNumber}` (close; balance must be 0) | ADMIN |
| POST | `/api/transactions/deposit`, `/withdraw`, `/transfer` | any logged-in user |
| GET | `/api/transactions/{accountNumber}` (`type`, `from`, `to` filters) | any logged-in user |
| GET | `/api/transactions/reference/{referenceNumber}` | any logged-in user |

### Business rules
- Money is always `BigDecimal` (scale 2, `DECIMAL(19,2)` in the DB). Balances can never go negative.
- SAVINGS accounts keep a **minimum balance of 500.00**; CURRENT accounts 0.00.
- Maximum amount per withdrawal/transfer: `banking.limits.withdrawal` (default 50 000.00).
- Transfers run in one `@Transactional` unit; accounts are row-locked (`SELECT … FOR UPDATE`) in a fixed order to avoid lost updates and deadlocks.
- A transfer returns one reference (`TXN…`); its two history rows are `TXN…-D` (debit) and `TXN…-C` (credit), and both are returned by `/reference/{ref}`.
- Passwords are stored as BCrypt hashes. Public registration always creates the `CUSTOMER` role.

## 5. Use MySQL instead of H2 (optional)
```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS banking_db;"   # tables are created automatically
```
Linux/macOS:
```bash
export DB_USERNAME=root
export DB_PASSWORD=your_mysql_password
java -jar target/banking-management-system.jar --spring.profiles.active=mysql
```
Windows PowerShell:
```powershell
$env:DB_USERNAME="root"; $env:DB_PASSWORD="your_mysql_password"
java -jar target/banking-management-system.jar --spring.profiles.active=mysql
```
Optionally load `database/schema.sql` first and `database/sample-data.sql` afterwards.

## 6. Configuration (environment variables)
| Variable | Default | Purpose |
|----------|---------|---------|
| `JWT_SECRET` | dev-only placeholder | **Set your own, ≥ 32 characters, in any shared/real environment** |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | `admin` / `Admin@12345` | initial admin account |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / `root` | MySQL profile only |
| `SERVER_PORT` | 8080 | change port |

## 7. Tests and coverage
```bash
mvn test                   # run all tests
mvn clean verify           # tests + JaCoCo report
```
Open `target/site/jacoco/index.html` in a browser to see coverage.
Tests use an isolated in-memory H2 database (`src/test/resources/application.properties`) – they never touch MySQL.

- `service/*Test` – Mockito unit tests: customer creation/duplicates, account creation + number generation, deposit, withdrawal, insufficient balance, minimum balance, limits, transfers, history filters.
- `dto/CustomerRequestValidationTest` – bean-validation rules.
- `controller/BankingApiIntegrationTest` – full API through MockMvc with real security and DB: 401/403/400/404/409 responses, JWT login, complete deposit → withdraw → transfer → history flow, and "failed transfer leaves both balances unchanged" (rollback behaviour).

## 8. Upload to GitHub
1. Create a free account at https://github.com and click **New repository** (name: `banking-management-system`). Leave "Add README / .gitignore" **unchecked**. Click **Create repository** and copy the repo URL.
2. In the project folder:
```bash
git init
git add .
git commit -m "Initial commit: banking management system"
git branch -M main
git remote add origin https://github.com/<your-username>/banking-management-system.git
git push -u origin main
```
3. When asked to sign in, use your GitHub username and a **Personal Access Token** as the password (GitHub → Settings → Developer settings → Personal access tokens → *Tokens (classic)* → scope `repo`), or install GitHub CLI and run `gh auth login`.
4. Open the **Actions** tab of your repo: the *Maven Build* workflow runs automatically and uploads the JAR and the JaCoCo report as downloadable artifacts.

Never commit real passwords or JWT secrets (`.env` is already in `.gitignore`).

## 9. Troubleshooting
| Problem | Fix |
|---------|-----|
| `mvn: command not found` | Install Maven and open a new terminal |
| `release version 17 not supported` | JDK is older than 17 – install JDK 17+ and check `JAVA_HOME` |
| `Port 8080 was already in use` | `java -jar target/banking-management-system.jar --server.port=9090` |
| `Access denied for user 'root'` (MySQL) | Set `DB_USERNAME` / `DB_PASSWORD` correctly |
| `Communications link failure` | MySQL isn't running – start the MySQL service |
| 401 on every call | Token missing/expired – log in again and send `Authorization: Bearer <token>` |
| 403 on `/api/customers` | You're logged in as a CUSTOMER – log in as `admin` |
| Dependencies won't download | Check internet/proxy settings for Maven Central |
| Optional Maven wrapper | `mvn -N wrapper:wrapper` creates `mvnw` / `mvnw.cmd` |

## Known simplifications
This is a learning/starter project, not production banking software. Not included: idempotency keys for duplicate-request protection, per-customer ownership checks (any logged-in user can operate on any account number), refresh tokens, rate limiting, and database migrations (Flyway/Liquibase).
