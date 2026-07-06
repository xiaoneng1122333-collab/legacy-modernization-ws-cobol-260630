package com.practicebank.masters.branch;

/**
 * 支店マスタの 1 行。COBOL の BR-REC に対応する。
 *
 * <p>DB テーブル {@code branches} の行と 1:1 で対応し、COBOL プログラム群
 * (BR-LOOKUP / BR-LOAD / BR-LIST-ALL / BR-LIST-BY-REGION) が扱う支店情報
 * (支店コード・支店名（漢字/カナ）・支店種別・住所・電話番号) を保持する。
 *
 * @param branchCode     支店コード (CHAR(3) 主キー)
 * @param branchName     支店名（漢字）
 * @param branchNameKana 支店名（カナ）
 * @param branchType     支店種別 (CHAR(1))
 * @param address        住所
 * @param phone          電話番号
 */
public record Branch(
        String branchCode,
        String branchName,
        String branchNameKana,
        String branchType,
        String address,
        String phone
) {
}
