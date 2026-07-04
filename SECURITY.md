# Security Policy / セキュリティポリシー

> **GitHub Copilot Quest Lv.3** ワークショップのセキュリティポリシーです。
> Security policy for the **GitHub Copilot Quest Lv.3** workshop.

**言語 / Language:** [日本語](#日本語) | [English](#english)

---

## 日本語

### 📚 このリポジトリの位置づけ

このリポジトリは **学習・ワークショップ用の教材**（GitHub Copilot Quest Lv.3）です。
本番システムではありません。以下の点にご注意ください。

- 含まれるデータは **架空の練習用データ（"practice bank"）** であり、実在する
  個人情報・金融情報は含まれていません。
- レガシー COBOL 資産を **そのまま本番環境にデプロイしないでください。** 教材として、
  古いプラクティスや脆弱になり得る構成が意図的に残っている場合があります。

### 🔒 対象バージョン

正式なバージョニングは行っていません。セキュリティ上の報告は、常に既定ブランチ
（`main`）の最新状態を対象とします。

| バージョン | サポート |
| --- | --- |
| `main` (最新) | ✅ |
| それ以前のコミット | ❌ |

### 🚨 脆弱性の報告方法

**公開 Issue には、機微なセキュリティ情報を書かないでください。**

- 可能であれば、GitHub の **Private Vulnerability Reporting**
  （リポジトリの **Security** タブ →「Report a vulnerability」）を利用して
  非公開で報告してください。
- 上記が利用できない場合は、メンテナ **[@shinyay](https://github.com/shinyay)** に
  非公開の手段で連絡してください。

報告の際は、以下を含めていただけると調査がスムーズです。

- 影響を受けるファイル・コンポーネント
- 再現手順、または PoC（概念実証）
- 想定される影響

ワークショップ教材のためベストエフォートでの対応となりますが、ご報告には感謝します 🙏

---

## English

### 📚 About this repository

This repository is **educational / workshop material** (GitHub Copilot Quest
Lv.3). It is not a production system. Please note the following:

- The data it contains is **fictional practice data ("practice bank")** and
  contains no real personal or financial information.
- **Do not deploy the legacy COBOL assets to production as-is.** As teaching
  material, outdated practices or potentially vulnerable configurations may have
  been intentionally left in place.

### 🔒 Supported versions

There is no formal versioning. Security reports always target the latest state
of the default branch (`main`).

| Version | Supported |
| --- | --- |
| `main` (latest) | ✅ |
| Earlier commits | ❌ |

### 🚨 Reporting a vulnerability

**Please do not put sensitive security information in a public Issue.**

- If possible, report privately using GitHub's **Private Vulnerability
  Reporting** (the **Security** tab of the repository → "Report a
  vulnerability").
- If that is not available, contact the maintainer
  **[@shinyay](https://github.com/shinyay)** through a private channel.

Including the following helps the investigation go smoothly:

- The affected file(s) or component(s)
- Reproduction steps, or a PoC (proof of concept)
- The expected impact

As this is workshop material, responses are best-effort, but your reports are
appreciated 🙏
