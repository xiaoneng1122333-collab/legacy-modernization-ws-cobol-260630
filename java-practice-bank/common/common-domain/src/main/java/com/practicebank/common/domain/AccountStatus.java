package com.practicebank.common.domain;

/** 口座状態。COBOL の ACCT-REC-STATUS に対応。 */
public enum AccountStatus {
    P("Pending", "申請中"),
    A("Active", "活性"),
    S("Suspended", "停止"),
    L("Lost/Collection", "債権回収"),
    C("Closed", "解約"),
    F("Force-closed", "強制解約"),
    D("Dormant", "休眠");

    private final String englishName;
    private final String japaneseName;

    AccountStatus(String englishName, String japaneseName) {
        this.englishName = englishName;
        this.japaneseName = japaneseName;
    }

    /** COBOL の 1 文字コード ('P','A','S','L','C','F','D') に変換 */
    public char toCode() {
        return name().charAt(0);
    }

    /** COBOL の 1 文字コードから逆変換 */
    public static AccountStatus fromCode(char code) {
        for (AccountStatus s : values()) {
            if (s.toCode() == code) return s;
        }
        throw new IllegalArgumentException("Unknown account status code: " + code);
    }
}
