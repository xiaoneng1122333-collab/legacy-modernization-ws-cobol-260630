package com.practicebank.masters.branch;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** BranchMapper を呼び出すリポジトリ。 */
@Repository
public class BranchRepository {

    private final BranchMapper mapper;

    public BranchRepository(BranchMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * BR-LOOKUP: 支店コード (主キー) で 1 件取得する。
     * 該当なしの場合は {@link Optional#empty()} を返す (COBOL の 04 NOT-FOUND に対応)。
     */
    public Optional<Branch> findByCode(String branchCode) {
        return mapper.findByCode(branchCode);
    }

    /**
     * BR-LIST-ALL: 全支店を支店コード昇順で取得する。
     */
    public List<Branch> findAll() {
        return mapper.findAll();
    }

    /**
     * BR-LIST-BY-REGION: 支店種別が一致する支店を全件取得する。
     */
    public List<Branch> findByBranchType(String branchType) {
        return mapper.findByBranchType(branchType);
    }

    /**
     * BR-LOAD: 1 件の支店レコードを登録する。
     */
    public int insert(Branch branch) {
        return mapper.insert(branch);
    }
}
