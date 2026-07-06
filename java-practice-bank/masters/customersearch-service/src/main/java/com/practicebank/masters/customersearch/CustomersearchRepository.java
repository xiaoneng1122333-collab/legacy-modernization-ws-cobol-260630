package com.practicebank.masters.customersearch;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 3 つの COBOL プログラム (CSRCH-AND / CSRCH-BY-ADDRESS / CSRCH-LIST-PAGED)
 * の Java 実装を提供するリポジトリ。
 *
 * <p>Phase 2 では customer-service に依存せず、自スキーマの customers テーブルを
 * 直接検索するスタンドアロンサービスとして実装する。
 */
@Repository
public class CustomersearchRepository {

    private final CustomersearchMapper mapper;

    public CustomersearchRepository(CustomersearchMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * CSRCH-AND: カナ前方一致と電話前方一致の積集合検索。
     *
     * @param kana  カナ検索のプレフィックス
     * @param phone 電話検索のプレフィックス
     * @return 一致した顧客 (status=0) または EOF (status=10)
     */
    public CustomersearchOutput csrchAnd(String kana, String phone) {
        List<Customer> matches = mapper.searchByKanaAndPhone(kana, phone);
        if (matches.isEmpty()) {
            return CustomersearchOutput.eof();
        }
        return CustomersearchOutput.ok(matches.get(0));
    }

    /**
     * CSRCH-BY-ADDRESS: 住所部分一致検索。
     *
     * @param addrSubstr 住所部分文字列
     * @return 部分一致した最初の 1 件 (status=0) または EOF (status=10)
     */
    public CustomersearchOutput csrchByAddress(String addrSubstr) {
        Optional<Customer> match = mapper.searchByAddress(addrSubstr);
        return match.map(CustomersearchOutput::ok)
                .orElseGet(CustomersearchOutput::eof);
    }

    /**
     * CSRCH-LIST-PAGED: ページング 1 ページ取得。
     *
     * <p>COBOL の OP="P" に相当。start-after より大きい ID を ID 順で
     * page-size 件返す。返却数が page-size に満たない場合は最終ページ。
     *
     * @param startAfter カーソル開始位置 (0 は先頭から)
     * @param pageSize   1 ページの最大返却件数
     * @return ID 順の顧客リスト (最大 page-size 件)
     */
    public List<Customer> csrchListPaged(long startAfter, int pageSize) {
        return mapper.listPaged(startAfter, pageSize);
    }

    /**
     * CSRCH-LIST-PAGED: ページング 1 件取得 (OP=" " 次行取得)。
     *
     * <p>COBOL の OP=" " に相当。指定 ID の次の 1 件を返す。
     *
     * @param afterId 直前に取得した ID
     * @return 次の 1 件 (status=0) または EOF (status=10)
     */
    public CustomersearchOutput csrchListPagedNext(long afterId) {
        List<Customer> rows = mapper.listPaged(afterId, 1);
        if (rows.isEmpty()) {
            return CustomersearchOutput.eof();
        }
        return CustomersearchOutput.ok(rows.get(0));
    }
}
