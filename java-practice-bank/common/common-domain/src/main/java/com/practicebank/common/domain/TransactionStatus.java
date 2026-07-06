package com.practicebank.common.domain;

/** 取引ステータス。COBOL の TXN-STATUS および DB CHECK 制約 (PT/SE/RV) に対応。 */
public enum TransactionStatus {
    PT("Posted", "記帳済"),
    SE("Settled", "決済済"),
    RV("Reversed", "取消済");

    private final String englishName;
    private final String japaneseName;

    TransactionStatus(String englishName, String japaneseName) {
        this.englishName = englishName;
        this.japaneseName = japaneseName;
    }

    public String code() {
        return name();
    }

    public static TransactionStatus fromCode(String code) {
        return valueOf(code);
    }
}
