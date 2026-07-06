package com.practicebank.masters.branch;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 支店マスタテーブル (branches) への MyBatis マッパー。
 *
 * <p>COBOL の 4 プログラムに対応する操作を提供する:
 * <ul>
 *   <li>BR-LOOKUP  → {@link #findByCode(String)}</li>
 *   <li>BR-LOAD    → {@link #insert(Branch)} (一括は {@link BranchLoadService})</li>
 *   <li>BR-LIST-ALL → {@link #findAll()}</li>
 *   <li>BR-LIST-BY-REGION → {@link #findByBranchType(String)}</li>
 * </ul>
 */
@Mapper
public interface BranchMapper {

    /**
     * BR-LOOKUP: 支店コード (主キー) による単一検索。
     */
    Optional<Branch> findByCode(@Param("branchCode") String branchCode);

    /**
     * BR-LOAD: 1 件の支店レコードを登録する。
     */
    int insert(@Param("b") Branch branch);

    /**
     * BR-LIST-ALL: 全支店を支店コード昇順で取得する。
     */
    List<Branch> findAll();

    /**
     * BR-LIST-BY-REGION: 支店種別が一致する支店を全件取得する。
     *
     * <p>COBOL 版は ISAM 代替キー (BR-REC-REGION) で検索していたが、
     * RDB 版では {@code branches.branch_type} 列でフィルタする。
     */
    List<Branch> findByBranchType(@Param("branchType") String branchType);
}
