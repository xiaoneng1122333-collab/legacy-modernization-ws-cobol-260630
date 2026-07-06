package com.practicebank.online.inquiry;

import com.practicebank.masters.account.Account;
import com.practicebank.masters.account.AccountRepository;
import com.practicebank.masters.customer.Customer;
import com.practicebank.masters.customer.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 照会コントローラ結合テスト (@SpringBootTest + MockMvc)。
 *
 * <p>データベース / 外部マッパーの境界は {@code @MockBean} で差し替え、REST
 * 層 (URL マッピング / ステータスコード / ボディ形状 / INQ-OUTPUT 相当の
 * フィールド名) を検証する。実マッパー動作は CustomerRepositoryTest /
 * AccountRepositoryTest が検証済みなので、ここでは境界置換で十分。
 */
@SpringBootTest(classes = InquiryApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class InquiryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CustomerRepository customerRepository;

    @MockBean
    private AccountRepository accountRepository;

    // INQ-CUST-MODE
    @Test
    void getCustomer_returns200_whenFound() throws Exception {
        Customer customer = new Customer(
                "0000000002", "田中 太郎", "タナカ タロウ",
                "A", "G", "03-1234-5678", "東京都中央区1-1");
        when(customerRepository.findById("0000000002")).thenReturn(Optional.of(customer));

        mockMvc.perform(get("/api/inquiry/customer/0000000002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inqStatus").value("00"))
                .andExpect(jsonPath("$.inqQueriesExecuted").value(1))
                .andExpect(jsonPath("$.customer.custId").value("0000000002"))
                .andExpect(jsonPath("$.customer.custName").value("田中 太郎"));
    }

    @Test
    void getCustomer_returns404_whenNotFound() throws Exception {
        when(customerRepository.findById("0000000099")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/inquiry/customer/0000000099"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.inqStatus").value("04"))
                .andExpect(jsonPath("$.message").value("Customer not found"))
                .andExpect(jsonPath("$.custId").value("0000000099"));
    }

    // INQ-ACCT-MODE
    @Test
    void getAccount_returns200_whenFound() throws Exception {
        Account account = new Account(
                "0010030000001", "山田太郎", "001", "003",
                "A", "0000000002", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1));
        when(accountRepository.findByNumber("0010030000001")).thenReturn(Optional.of(account));

        mockMvc.perform(get("/api/inquiry/account/0010030000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inqStatus").value("00"))
                .andExpect(jsonPath("$.account.acctNumber").value("0010030000001"))
                .andExpect(jsonPath("$.account.acctName").value("山田太郎"))
                .andExpect(jsonPath("$.account.branchCode").value("001"));
    }

    @Test
    void getAccount_returns404_whenNotFound() throws Exception {
        when(accountRepository.findByNumber("9999999999999")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/inquiry/account/9999999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.inqStatus").value("04"))
                .andExpect(jsonPath("$.message").value("Account not found"));
    }

    // INQ-BAL-MODE: 現実装スケルトン — 口座存在なら 200 (balanceJpy=N/A マーカー)
    @Test
    void getBalance_returns200_withBalanceMarker_whenFound() throws Exception {
        Account account = new Account(
                "0010010099502", "テスト花子", "001", "001",
                "A", "0000000003", LocalDate.of(2025, 4, 1), null);
        when(accountRepository.findByNumber("0010010099502")).thenReturn(Optional.of(account));

        mockMvc.perform(get("/api/inquiry/balance/0010010099502"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inqStatus").value("00"))
                .andExpect(jsonPath("$.accountNumber").value("0010010099502"))
                .andExpect(jsonPath("$.acctName").value("テスト花子"))
                .andExpect(jsonPath("$.balanceJpy").value("N/A"));
    }

    @Test
    void getBalance_returns404_whenNotFound() throws Exception {
        when(accountRepository.findByNumber(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/inquiry/balance/0000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.inqStatus").value("04"))
                .andExpect(jsonPath("$.message").value("Account not found"));
    }
}
