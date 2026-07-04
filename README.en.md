# GitHub Copilot Quest Lv.3 — COBOL Modernization to Azure

[日本語](./README.md) | **English**

> **Mission:** Read, understand, and **modernize to Azure** the "undocumented,
> comment-free" legacy COBOL application contained here — driving it all with
> **GitHub Copilot** and AI agents.

---

## 🎯 About this workshop

**GitHub Copilot Quest Lv.3** is a hands-on event where you go all-in with
GitHub Copilot to take on the **modernization of a legacy COBOL application**.

The goal is simple — **reshape this COBOL application into something that runs on
Azure.**

That said, you **don't have to be bound** by the conventions of traditional
COBOL migration (literal **Straight Conversion**, mechanical
**Shift / Lift & Shift**, line-by-line replacement). Instead:

- Have the AI agent **analyze the code and recover the specification**
- **Rediscover the domain** through dialogue with AI, and **redesign what it
  should be**
- Leap to a new architecture with the **free thinking that only generative AI
  makes possible**

— challenging you to take on **AI-native modernization** is the aim of this
Quest. There is no prepared "correct answer." **The answer you and Copilot
arrive at is the correct one.**

---

## 🗺️ Your mission

This repository is a "real-world legacy system whose knowledge has been lost,"
from which **documentation and comments have been intentionally removed**. There
is no specification. There are no explanatory comments in the code. There is
**only working, executable code**.

When you inherit COBOL assets in the field, this is exactly the situation you
face. That is precisely why —

1. **Analyze** — Have Copilot read the code and ask it what it does.
2. **Understand** — Reconstruct the business domain, data flows, and batch
   structure together with AI.
3. **Modernize** — Based on that understanding, reshape it into a modern
   implementation on Azure.

Where to start, which Azure services to run it on, and how far to rebuild it are
**up to you**.

---

## 💡 Tips for getting started (use Copilot to the fullest)

There is no fixed way to do this, but when you get stuck, try these:

- **Have the agent explore**: Start with "What does this repository do?" and
  "Where is the entry point?"
- **Have it recover the specification**: Point at a specific program and ask
  "Explain this program's inputs, outputs, and business rules."
- **Have it visualize**: "Draw the data flow / batch dependencies as a Mermaid
  diagram."
- **Have it rediscover the domain**: "What business domain can be inferred from
  this schema and code?"
- **Have it envision the target**: "If we put this on Azure, what architectures
  are possible? Compare the options."
- **Have it rebuild**: Language migration, turning it into APIs,
  containerization, going serverless, data migration … let your imagination run
  free.

> 🧭 Starting by **running the code and observing it** sharpens the questions you
> ask the AI (see below).

---

## 🛠️ Try running it first (Build & Run)

This app is configured to run in a **Dev Container**. There is no design
documentation — start your analysis by **building it, running it, and observing
the output**.

### Prerequisites

- **Visual Studio Code + the Dev Containers extension** (or **GitHub
  Codespaces**)
  - `.devcontainer/` defines the full toolchain (COBOL compiler, PostgreSQL,
    message broker, etc.).
- Main runtimes if you are not using the Dev Container: **GnuCOBOL**,
  **OCESQL**, **PostgreSQL**, **RabbitMQ**.

### Steps

```bash
# 1) Open the repository in a Dev Container / Codespaces (the toolchain is set up automatically)

# 2) Prepare the database schema
make migrate

# 3) Build all programs
make build-all

# 4) Run all unit tests (a great entry point for observing behavior)
make test-all

# 5) Run the end-to-end pipeline
make -C tests/e2e smoke
```

> Each `subsystems/*/` and the `tests/` directories also have their own
> `Makefile`.
> The `make` targets, the contents of `tests/`, and the schema in
> `db/migration/` are **primary sources for reading the specification**.
> Ask Copilot "What does this Makefile do?" and "What does this test guarantee?"

---

## 📦 About this repository

- Implementation language: **COBOL** (built with GnuCOBOL; embedded SQL via
  OCESQL)
- Data store: **PostgreSQL** / Messaging: **RabbitMQ**
- Form: a **batch-oriented business application** composed of multiple programs
- **Documentation and comments have been intentionally removed** (to serve as
  the Quest's analysis target). What remains is only the code needed to run and
  the build/test assets.

Everything else — "what the app is, how it works, and how it should be
reshaped" — is for you to **uncover together with Copilot.**

---

## 👤 Maintainer

- **Shinya Yanagihara** (Microsoft) — Maintainer
- GitHub: [@shinyay](https://github.com/shinyay)
- Contact: shinya.yanagihara@microsoft.com

For Code of Conduct and security matters, see
[CODE_OF_CONDUCT.md](./CODE_OF_CONDUCT.md) / [SECURITY.md](./SECURITY.md).

---

**Happy modernizing. 🚀**
