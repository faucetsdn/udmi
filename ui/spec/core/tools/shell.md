# 🐚 Tool Specification: Host Shell

This document defines the functional capabilities, domain responsibilities, and state controls for the UDMI Workbench **Host Shell**.

---

## 1. Domain Purpose & Responsibilities

The Host Shell is the top-level orchestrator for the Workbench UI. Its functional responsibilities are:
- Displaying global workspace identity (app name, workspace subtitle, active site model path).
- Managing interactive site model path selection and filesystem navigation.
- Enforcing backend security policies and dynamic feature routing.
- Maintaining global application state and broadcasting updates downstream to active tool views.

---

## 2. Functional Capabilities & Controls

### 2.1 Workspace Selection & Context Controls
- **Site Model Path Input**: Exposes active `Site Model Path` with editing and path selection capabilities.
- **Workspace Connection Status**: Displays workspace connection state (`LOADED` when connected vs `NO SITE MODEL` when unconfigured).
- **First-Launch Setup Guidance**: Provides immediate setup callout when no valid site model path is configured.

### 2.2 Tool Feature Navigation
- **Tool Feature Switching**: Exposes controls to navigate between permitted tool views (e.g. `Sequencer`, `Mantis`).
- **Security Policy Enforcement**: Disables and hides tool controls for features not permitted by backend security policy or URL feature flags.

### 2.3 Interactive Directory Explorer
- **Filesystem Navigation**: Allows browsing local directory paths, parent directories (`..`), and confirming target folders.
- **Cross-Platform & WSL Navigation**: Supports navigating home directories (`~`), system root (`/`), and WSL drive mounts (`/mnt/c`, `/mnt/d`).
