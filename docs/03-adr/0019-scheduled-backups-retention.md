# ADR 0019: Optional scheduled backups with verified retention

- Status: Accepted
- Date: 2026-11-11

## Context

The local PostgreSQL dump and restore scripts create integrity-checked backup pairs, but the owner had to run them manually and old files accumulated. A scheduled cleanup must not remove arbitrary files or leave the owner with no recoverable copy.

## Decision

Provide an opt-in Windows Task Scheduler installer that registers a daily task in the current user's interactive, non-elevated session. The task runs a verified backup first, then retention. The retention command scans only direct files matching AURA's generated dump-name pattern, validates the paired manifest and SHA-256 digest, previews deletion by default, and keeps at least the configured number of newest valid backups (default two). Applying deletion requires an explicit `-Apply` switch; scheduled runs pass that switch only after a new backup succeeds. Invalid and unmanifested files are kept.

## Consequences

- AURA gains simple local scheduling without a daemon, database credential copy, or additional service.
- Docker Desktop, the Compose database, the signed-in Windows user, and the configured backup path must be available. The task does not run as SYSTEM or with administrator rights.
- Retention limits disk growth while preserving a minimum recovery set, but deleting expired files remains a destructive operation. The default manual command only previews.
- The local backup files remain unencrypted and are not copied off-device; losing or compromising the disk can still expose or destroy all copies.
- Moving the checkout requires removing and registering the task again because it stores absolute paths.

## Reconsider when

Replace this local scheduler if AURA moves to a managed host or needs unattended backups while the desktop user is signed out. Add encryption and independent off-device copies before calling backups production-resilient.

## Learning prompts

- Why does scheduled retention verify manifests and preserve a minimum number of copies?
- Why does a Windows desktop task run only in an interactive user session?
- Why does a valid checksum not replace encryption or an off-device copy?
