# skills/ — Context for AI agents

> 🌐 [Español](README.md) · **English**

This folder does NOT contain app code: it is **documentation aimed at AI agents**
(Claude Code, Copilot, etc.) that will work in this repository. Its goal is to give an
agent the context that is **not** inferred just by reading the code: conventions, design
decisions, known pitfalls, and commands to build/test.

## Contents

| File | What for |
|---|---|
| [`project-onboarding.md`](project-onboarding.en.md) | Entry point: what the project is, how it is organized, how to build/test, and the map of "where to touch for X". |
| [`conventions.md`](conventions.en.md) | Code conventions and **known pitfalls** (don't repeat mistakes already paid for). |
| [`debug-hooks.md`](debug-hooks.en.md) | Debug hooks via broadcast and logcat tags to drive/observe the service in e2e tests. |

## Quick rules for the agent

1. **Language:** the project and the user work in **Spanish**. Comments, commits, and docs
   in Spanish.
2. **Best practices first:** the owner explicitly insisted on respecting them (clean,
   testable architecture, no shortcuts). When in doubt, prioritize clarity and layer separation.
3. **Before changing behavior, read** [`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.en.md)
   and [`../TESTING.md`](../TESTING.en.md).
4. **Don't commit anything sensitive** — see the hardened `.gitignore` (keystores, `.env`, etc.).
5. **Windows:** don't run Gradle from the terminal and Android Studio syncing at the same time.
