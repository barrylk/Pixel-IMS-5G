# Liquid Glass Home Implementation Plan

> **For agentic workers:** Execute sequentially in the current local branch. No delegation or GitHub publication is authorized.

**Goal:** Match the opening page to the approved live-monitor design with visible, evidence-based status and actions.

**Architecture:** Share the existing quiet LiquidPane surface. Keep SetupPage as the VM/navigation adapter; render a pure HomeDashboard for previewing. Retain current setup and patch action handlers. Home reads setup checks only; network snapshots stay in Network.

**Tech Stack:** Kotlin, Jetpack Compose, existing Material3 and libsu/Shizuku integrations; no new runtime dependencies.

**Spec:** docs/superpowers/specs/2026-10-23-liquid-home.md

## Constraints
No GitHub push, no production signing changes, no hardware writes. No invented NR attachment or modem-rejection claim. Existing patch confirmations remain in control of patch writes.

- [x] Add component-list transition and unknown-width regression cases; verify the new cases fail before implementation.
- [x] Extract LiquidPane to components and reuse it from the existing monitor.
- [x] Keep actual radio readings in Network; remove the redundant Home radio scan and unsupported diagnostic claims.
- [x] Replace SetupPage composition with the dashboard, open check list and direct navigation; keep VM actions and patch confirmations.
- [x] Let the patch card use the shared surface and show its details on Home.
- [x] Add dark/light/loading/no-SIM Compose previews using clearly marked fixture states.
- [x] Run unit tests, debug/release builds, release lint and diff checks. Copy and hash the local preview APK. Install on the connected Pixel 7 Pro and inspect Home plus the separated CA sources. See build/home-redesign/verification.md for results and limits.
