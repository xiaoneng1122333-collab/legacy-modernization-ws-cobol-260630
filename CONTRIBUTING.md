# Contributing / コントリビューションガイド

> **GitHub Copilot Quest Lv.3 — COBOL Modernization to Azure** ワークショップへの貢献ガイドです。
> Contribution guide for the **GitHub Copilot Quest Lv.3 — COBOL Modernization to Azure** workshop.

**言語 / Language:** [日本語](#日本語) | [English](#english)

---

## 日本語

このリポジトリは **GitHub Copilot Quest Lv.3 — COBOL Modernization to Azure** の
ワークショップ用教材です。ご協力ありがとうございます 🙌

### 🎯 このリポジトリの性質を理解する

このリポジトリは **意図的にドキュメントとコメントを取り除いた** レガシー COBOL
アプリケーションです。「ナレッジが失われた現実のレガシーシステム」を、参加者が
GitHub Copilot を使って解析・理解・モダナイズすることが目的です。

> ⚠️ **解析の答えを"埋め戻す"PR はご遠慮ください。**
> 仕様書・コメント・データフロー図などを本体に追記する PR は、Quest の解析対象を
> ネタバレさせてしまうためマージできません。解析結果はご自身のフォークや
> 別リポジトリでお楽しみください。

### ✅ 歓迎するコントリビューション

- ビルド・セットアップ・Dev Container の不具合修正
- `Makefile` / スクリプト / テスト資材の不整合や環境依存の修正
- タイポ・リンク切れ・軽微な誤りの修正
- ワークショップの進行を助ける **足場（scaffolding）** の改善提案

大きめの変更や方針に関わる提案は、まず **Issue を立てて相談** してください。

### 🛠️ 開発環境

環境構築とビルド/テストの手順は [README.md](./README.md) を参照してください。
このアプリは **Dev Container / GitHub Codespaces** で動くように構成されています。

変更を送る前に、最低限これらが通ることを確認してください:

```bash
make migrate      # スキーマ準備
make build-all    # 全プログラムのビルド
make test-all     # ユニットテスト一括実行
```

### 🔀 プルリクエストの流れ

1. リポジトリをフォークし、作業用ブランチを作成する。
2. 変更は **1 つの目的にフォーカス** して小さく保つ。
3. コミットメッセージは [Conventional Commits](https://www.conventionalcommits.org/)
   に従う（例: `fix: correct migrate target path`, `docs: ...`, `chore: ...`）。
4. `make build-all` と `make test-all` が通ることを確認する。
5. PR の説明に **何を・なぜ** 変更したかを書く。関連 Issue があればリンクする。

### 📜 行動規範

参加にあたっては [行動規範 (CODE_OF_CONDUCT.md)](./CODE_OF_CONDUCT.md) を遵守してください。

### ❓ 困ったときは

質問やサポートについては [SUPPORT.md](./SUPPORT.md) を参照してください。

---

## English

This repository is workshop material for **GitHub Copilot Quest Lv.3 — COBOL
Modernization to Azure**. Thank you for your help 🙌

### 🎯 Understand the nature of this repository

This repository is a legacy COBOL application from which **documentation and
comments have been intentionally removed**. The goal is for participants to use
GitHub Copilot to analyze, understand, and modernize a "real-world legacy system
whose knowledge has been lost."

> ⚠️ **Please do not submit PRs that "fill back in" the answers.**
> PRs that add specifications, comments, or data-flow diagrams to the codebase
> spoil the material meant to be analyzed in the Quest and cannot be merged.
> Please enjoy your analysis results in your own fork or a separate repository.

### ✅ Contributions we welcome

- Fixes to build, setup, or Dev Container problems
- Fixes to inconsistencies or environment-specific issues in the `Makefile`,
  scripts, or test assets
- Typos, broken links, and minor corrections
- Proposals to improve the **scaffolding** that helps the workshop run

For larger changes or anything affecting direction, please **open an Issue to
discuss first**.

### 🛠️ Development environment

See [README.md](./README.md) for setup and build/test instructions. This app is
configured to run in a **Dev Container / GitHub Codespaces**.

Before submitting a change, please make sure at least the following pass:

```bash
make migrate      # Prepare the schema
make build-all    # Build all programs
make test-all     # Run all unit tests
```

### 🔀 Pull request flow

1. Fork the repository and create a working branch.
2. Keep changes **focused on a single purpose** and small.
3. Follow [Conventional Commits](https://www.conventionalcommits.org/) for
   commit messages (e.g., `fix: correct migrate target path`, `docs: ...`,
   `chore: ...`).
4. Make sure `make build-all` and `make test-all` pass.
5. Describe **what** and **why** in the PR description, and link any related
   Issue.

### 📜 Code of Conduct

By participating, you agree to abide by our
[Code of Conduct (CODE_OF_CONDUCT.md)](./CODE_OF_CONDUCT.md).

### ❓ Need help?

For questions and support, see [SUPPORT.md](./SUPPORT.md).
