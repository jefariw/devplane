# DevPlane

DevPlane is an internal developer platform for standardized service creation,
deployment, observability, and AI-assisted operations.

## Overview

Modern engineering teams often repeat the same setup work when creating and
operating services: repositories, infrastructure, CI/CD, observability,
deployment configuration, and operational tooling.

DevPlane aims to provide a standardized self-service platform for these workflows.

## Vision

Developers should be able to define what they want to build while DevPlane
handles the underlying platform requirements.

Example:

> Create a Java REST API with PostgreSQL and deploy it to staging.

DevPlane will eventually automate:

- Service scaffolding
- Repository provisioning
- CI/CD configuration
- Infrastructure provisioning
- Containerized deployment
- Observability
- Service ownership
- Operational diagnostics
- AI-assisted remediation

## Project Roadmap

### Phase 1 — Platform Core
- [ ] Service catalog
- [ ] Service creation workflow
- [ ] Golden-path service templates
- [ ] Repository provisioning
- [ ] Platform API

### Phase 2 — Deployment
- [ ] Containerization
- [ ] CI/CD
- [ ] Infrastructure as Code
- [ ] Kubernetes deployment
- [ ] Cloud infrastructure

### Phase 3 — Observability
- [ ] Metrics
- [ ] Logs
- [ ] Distributed tracing
- [ ] Service health monitoring

### Phase 4 — AI Operations
- [ ] Deployment diagnostics
- [ ] Tool-based agent workflows
- [ ] MCP integrations
- [ ] Human approval flows
- [ ] Automated remediation

## Architecture

Architecture documentation will live in:

`docs/architecture.md`

## Status

DevPlane is currently under active development.
