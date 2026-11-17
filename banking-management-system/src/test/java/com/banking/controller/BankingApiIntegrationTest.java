package com.banking.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests: real Spring context, real security filter chain, real JPA (H2), real transactions.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BankingApiIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper om;

    // ---------- helpers ----------

    private String json(Object o) throws Exception {
        return om.writeValueAsString(o);
    }

    private JsonNode read(MvcResult r) throws Exception {
        return om.readTree(r.getResponse().getContentAsString());
    }

    private String login(String username, String password) throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn();
        return read(r).get("token").asText();
    }

    private String adminToken() throws Exception {
        return login("admin", "Admin@12345");
    }

    private String customerToken() throws Exception {
        String username = "user" + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", "Password@123"))))
                .andExpect(status().isCreated());
        return login(username, "Password@123");
    }

    private Map<String, Object> customerBody(String email) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", "Test Customer");
        body.put("email", email);
        body.put("phone", "9876543210");
        body.put("address", "Pune, India");
        body.put("dateOfBirth", "1990-05-15");
        return body;
    }

    private long createCustomer(String adminToken) throws Exception {
        MvcResult r = mvc.perform(post("/api/customers").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(customerBody("c" + UUID.randomUUID() + "@example.com"))))
                .andExpect(status().isCreated()).andReturn();
        return read(r).get("id").asLong();
    }

    private String openAccount(String adminToken, long customerId, String type, String deposit) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", customerId);
        body.put("type", type);
        body.put("initialDeposit", new BigDecimal(deposit));
        MvcResult r = mvc.perform(post("/api/accounts").header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated()).andReturn();
        return read(r).get("accountNumber").asText();
    }

    private BigDecimal balance(String token, String accountNumber) throws Exception {
        MvcResult r = mvc.perform(get("/api/accounts/" + accountNumber + "/balance")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return read(r).get("balance").decimalValue();
    }

    private void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    private MvcResult postJson(String token, String url, Object body, int expectedStatus) throws Exception {
        return mvc.perform(post(url).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().is(expectedStatus)).andReturn();
    }

    // ---------- authentication & authorization ----------

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/accounts/123456789012")).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withGarbageToken_returns401() throws Exception {
        mvc.perform(get("/api/accounts/123456789012").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_thenLogin_returnsJwt_andDuplicateRegistrationReturns409() throws Exception {
        String username = "user" + UUID.randomUUID().toString().substring(0, 8);
        String body = json(Map.of("username", username, "password", "Password@123"));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        MvcResult login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        JsonNode node = read(login);
        assertFalse(node.get("token").asText().isBlank());
        assertEquals("CUSTOMER", node.get("role").asText());
        assertEquals("Bearer", node.get("tokenType").asText());
    }

    @Test
    void register_withShortPassword_returns400() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "validname", "password", "short"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "admin", "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }

    @Test
    void customerRole_cannotUseAdminEndpoints_returns403() throws Exception {
        String token = customerToken();

        mvc.perform(get("/api/customers").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/accounts").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("customerId", 1, "type", "CURRENT"))))
                .andExpect(status().isForbidden());
    }

    // ---------- customers ----------

    @Test
    void createCustomer_withInvalidData_returns400WithConsistentErrorBody() throws Exception {
        Map<String, Object> body = customerBody("not-an-email");
        mvc.perform(post("/api/customers").header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/api/customers"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("email")));
    }

    @Test
    void createCustomer_withDuplicateEmail_returns409() throws Exception {
        String admin = adminToken();
        String email = "dup" + UUID.randomUUID() + "@example.com";
        postJson(admin, "/api/customers", customerBody(email), 201);
        postJson(admin, "/api/customers", customerBody(email), 409);
    }

    @Test
    void customer_canBeUpdatedSearchedAndDeactivated() throws Exception {
        String admin = adminToken();
        long id = createCustomer(admin);

        Map<String, Object> update = customerBody("updated" + UUID.randomUUID() + "@example.com");
        update.put("fullName", "Zed Unique Name");
        mvc.perform(put("/api/customers/" + id).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Zed Unique Name"));

        mvc.perform(get("/api/customers").param("search", "zed unique").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));

        mvc.perform(delete("/api/customers/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/customers/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void getUnknownCustomer_returns404() throws Exception {
        mvc.perform(get("/api/customers/999999").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ---------- accounts, deposit, withdraw, transfer, history ----------

    @Test
    void fullBankingFlow_depositWithdrawTransferAndHistory() throws Exception {
        String admin = adminToken();
        String user = customerToken();
        long customerId = createCustomer(admin);
        String savings = openAccount(admin, customerId, "SAVINGS", "1000.00");
        String current = openAccount(admin, customerId, "CURRENT", "500.00");

        assertTrue(savings.matches("\\d{12}"));
        assertNotEquals(savings, current);
        mvc.perform(get("/api/customers/" + customerId + "/accounts").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // deposit
        MvcResult deposit = postJson(user, "/api/transactions/deposit",
                Map.of("accountNumber", savings, "amount", new BigDecimal("500.00"), "description", "salary"), 201);
        assertEquals("DEPOSIT", read(deposit).get("type").asText());
        assertAmount("1500.00", balance(user, savings));

        // withdraw
        postJson(user, "/api/transactions/withdraw",
                Map.of("accountNumber", savings, "amount", new BigDecimal("200.00")), 201);
        assertAmount("1300.00", balance(user, savings));

        // transfer
        MvcResult transfer = postJson(user, "/api/transactions/transfer",
                Map.of("fromAccount", savings, "toAccount", current, "amount", new BigDecimal("300.00")), 201);
        String reference = read(transfer).get("referenceNumber").asText();
        assertAmount("1000.00", balance(user, savings));
        assertAmount("800.00", balance(user, current));

        // history: initial deposit + deposit + withdrawal + transfer debit
        MvcResult history = mvc.perform(get("/api/transactions/" + savings).header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()).andReturn();
        assertEquals(4, read(history).size());

        MvcResult withdrawals = mvc.perform(get("/api/transactions/" + savings).param("type", "WITHDRAWAL")
                        .header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()).andReturn();
        assertEquals(1, read(withdrawals).size());
        assertEquals("DEBIT", read(withdrawals).get(0).get("direction").asText());

        MvcResult today = mvc.perform(get("/api/transactions/" + savings).param("from", LocalDate.now().toString())
                        .header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()).andReturn();
        assertEquals(4, read(today).size());

        MvcResult tomorrow = mvc.perform(get("/api/transactions/" + savings)
                        .param("from", LocalDate.now().plusDays(1).toString())
                        .header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()).andReturn();
        assertEquals(0, read(tomorrow).size());

        // both legs of the transfer are found by the shared reference
        MvcResult byRef = mvc.perform(get("/api/transactions/reference/" + reference)
                        .header("Authorization", "Bearer " + user))
                .andExpect(status().isOk()).andReturn();
        assertEquals(2, read(byRef).size());
    }

    @Test
    void withdraw_moreThanBalance_returns400_andBalanceIsUnchanged() throws Exception {
        String admin = adminToken();
        long customerId = createCustomer(admin);
        String account = openAccount(admin, customerId, "CURRENT", "100.00");

        MvcResult r = postJson(admin, "/api/transactions/withdraw",
                Map.of("accountNumber", account, "amount", new BigDecimal("500.00")), 400);

        assertEquals("Insufficient balance", read(r).get("message").asText());
        assertAmount("100.00", balance(admin, account));
    }

    @Test
    void transfer_withInsufficientFunds_returns400_andNeitherBalanceChanges() throws Exception {
        String admin = adminToken();
        long customerId = createCustomer(admin);
        String a = openAccount(admin, customerId, "CURRENT", "100.00");
        String b = openAccount(admin, customerId, "CURRENT", "200.00");

        postJson(admin, "/api/transactions/transfer",
                Map.of("fromAccount", a, "toAccount", b, "amount", new BigDecimal("300.00")), 400);

        assertAmount("100.00", balance(admin, a));
        assertAmount("200.00", balance(admin, b));
    }

    @Test
    void transfer_toUnknownAccount_returns404_andSourceIsNotDebited() throws Exception {
        String admin = adminToken();
        long customerId = createCustomer(admin);
        String a = openAccount(admin, customerId, "CURRENT", "1000.00");

        postJson(admin, "/api/transactions/transfer",
                Map.of("fromAccount", a, "toAccount", "999999999999", "amount", new BigDecimal("50.00")), 404);

        assertAmount("1000.00", balance(admin, a));
    }

    @Test
    void transfer_toSameAccount_returns400() throws Exception {
        String admin = adminToken();
        long customerId = createCustomer(admin);
        String a = openAccount(admin, customerId, "CURRENT", "1000.00");

        postJson(admin, "/api/transactions/transfer",
                Map.of("fromAccount", a, "toAccount", a, "amount", new BigDecimal("50.00")), 400);
        assertAmount("1000.00", balance(admin, a));
    }

    @Test
    void deposit_toUnknownAccount_returns404() throws Exception {
        postJson(adminToken(), "/api/transactions/deposit",
                Map.of("accountNumber", "999999999999", "amount", new BigDecimal("10.00")), 404);
    }

    @Test
    void deposit_withNegativeAmount_returns400() throws Exception {
        postJson(adminToken(), "/api/transactions/deposit",
                Map.of("accountNumber", "123456789012", "amount", new BigDecimal("-10.00")), 400);
    }

    @Test
    void closeAccount_requiresZeroBalance() throws Exception {
        String admin = adminToken();
        long customerId = createCustomer(admin);
        String account = openAccount(admin, customerId, "CURRENT", "100.00");

        mvc.perform(delete("/api/accounts/" + account).header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());

        postJson(admin, "/api/transactions/withdraw", Map.of("accountNumber", account, "amount", new BigDecimal("100.00")), 201);
        mvc.perform(delete("/api/accounts/" + account).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/accounts/" + account).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void history_withInvalidTypeParameter_returns400() throws Exception {
        String admin = adminToken();
        long customerId = createCustomer(admin);
        String account = openAccount(admin, customerId, "CURRENT", "100.00");

        mvc.perform(get("/api/transactions/" + account).param("type", "BOGUS")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
    }
}
