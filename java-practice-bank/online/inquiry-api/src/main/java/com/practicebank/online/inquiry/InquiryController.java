package com.practicebank.online.inquiry;

import com.practicebank.masters.account.Account;
import com.practicebank.masters.account.AccountRepository;
import com.practicebank.masters.customer.Customer;
import com.practicebank.masters.customer.CustomerRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 照会オンライン API の REST コントローラ (COBOL INQ-MAIN の Phase 2 移植)。
 *
 * <p>5 照会モードのうち Phase 2 では以下の 3 本を提供する:
 * <ol>
 *   <li>顧客照会   — GET /api/inquiry/customer/{id}   (INQ-CUST-MODE)</li>
 *   <li>口座照会   — GET /api/inquiry/account/{no}    (INQ-ACCT-MODE)</li>
 *   <li>残高照会   — GET /api/inquiry/balance/{no}     (INQ-BAL-MODE)</li>
 * </ol>
 *
 * <p>住所部分一致検索 (INQ-CSRCH-MODE) と取引履歴照会 (INQ-TXN-HIST-MODE)
 * は同じコントローラで将来拡張する。レスポンスの {@code inqStatus} は
 * INQ-OUTPUT の INQ-STATUS ("00"=OK / "04"=NOT-FORD 等) と整合する。
 */
@RestController
@RequestMapping("/api/inquiry")
public class InquiryController {

    private static final String STATUS_OK = "00";

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;

    public InquiryController(CustomerRepository customerRepository,
                             AccountRepository accountRepository) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
    }

    /** INQ-CUST-MODE: 顧客 ID から顧客情報を 1 件返す。 */
    @GetMapping("/customer/{id}")
    public ResponseEntity<?> getCustomer(@PathVariable("id") String id) {
        Optional<Customer> customer = customerRepository.findById(id);
        if (customer.isEmpty()) {
            return ResponseEntity.status(404).body(errorResponse("04",
                    "Customer not found", Map.of("custId", id)));
        }
        return ResponseEntity.ok(okResponse(Map.of("customer", customer.get())));
    }

    /** INQ-ACCT-MODE: 口座番号から口座情報を 1 件返す。 */
    @GetMapping("/account/{no}")
    public ResponseEntity<?> getAccount(@PathVariable("no") String no) {
        Optional<Account> account = accountRepository.findByNumber(no);
        if (account.isEmpty()) {
            return ResponseEntity.status(404).body(errorResponse("04",
                    "Account not found", Map.of("accountNumber", no)));
        }
        return ResponseEntity.ok(okResponse(Map.of("account", account.get())));
    }

    /**
     * INQ-BAL-MODE: 口座番号の残高情報を返すスケルトン。
     *
     * <p>現実装の accounts テーブルは残高列を持たないため、ここでは口座の
     * 存在確認 + 口座基本情報を返す。残高テーブル (いくつかマスタが該当)
     * との結合はテーブル拡張後に組み込む。
     */
    @GetMapping("/balance/{no}")
    public ResponseEntity<?> getBalance(@PathVariable("no") String no) {
        Optional<Account> account = accountRepository.findByNumber(no);
        if (account.isEmpty()) {
            return ResponseEntity.status(404).body(errorResponse("04",
                    "Account not found", Map.of("accountNumber", no)));
        }
        Account a = account.get();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accountNumber", a.acctNumber());
        data.put("acctName", a.acctName());
        data.put("branchCode", a.branchCode());
        // balance_jpy は口座テーブルに未実装。将来拡張用。現状はマーカーを返す。
        data.put("balanceJpy", "N/A");
        return ResponseEntity.ok(okResponse(data));
    }

    private Map<String, Object> okResponse(Map<String, Object> data) {
        return buildResponse(STATUS_OK, 1, data);
    }

    private Map<String, Object> errorResponse(String status, String message, Map<String, Object> extra) {
        Map<String, Object> data = new LinkedHashMap<>(extra);
        data.put("message", message);
        return buildResponse(status, 0, data);
    }

    private Map<String, Object> buildResponse(String status, int queriesExecuted, Map<String, Object> data) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("inqStatus", status);
        envelope.put("inqQueriesExecuted", queriesExecuted);
        envelope.putAll(data);
        return envelope;
    }
}
