# PostgreSQL backup and restore

These PowerShell scripts create a portable PostgreSQL custom-format archive from the Compose `db` service. They use the database container's `pg_dump` and `pg_restore`, so no PostgreSQL client needs to be installed on Windows.

## Create a backup

From the repository root, with Docker Desktop running and the AURA database healthy:

```powershell
pwsh -NoProfile -File .\scripts\Backup-AuraDatabase.ps1
```

By default, the archive and its `.manifest.json` file go in the Git-ignored `backups/` directory. An alternate output directory can be provided:

```powershell
pwsh -NoProfile -File .\scripts\Backup-AuraDatabase.ps1 -OutputDirectory 'E:\Encrypted\AURA'
```

The script creates a custom-format archive, checks that PostgreSQL can list its contents, copies it back into the database container and checks the copied archive, then writes a manifest with its byte count and SHA-256 digest. Each backup uses a unique name; an existing archive is not overwritten. The database remains online during backup. PostgreSQL's dump is transactionally consistent, but writes made after that snapshot are not included.

Keep the archive and matching manifest together. The hash detects accidental changes; it does not prove who created a backup. Store backups on encrypted storage and keep at least one copy separate from the computer running AURA. The project does not automate backup scheduling, encryption, remote storage, or retention cleanup.

## Restore a backup

Restore replaces the current AURA database contents. Before running it, verify that the archive is one you created or otherwise trust, and that its adjacent manifest is present. Restoring an untrusted PostgreSQL archive can execute database definitions from that archive.

```powershell
pwsh -NoProfile -File .\scripts\Restore-AuraDatabase.ps1 `
  -BackupPath '.\backups\aura-db-20261108T120000Z-1234abcd.dump' `
  -ConfirmRestore
```

The script checks the manifest filename, size, and SHA-256 digest, then asks PostgreSQL to inspect the archive before changing the database. If the API is running, it stops it and creates a fresh, verified pre-restore backup in the same directory. It restores in one database transaction, so a failed PostgreSQL restore rolls back its changes. On success, it starts the API only if it was running beforehand. If restore execution fails, the API stays stopped; inspect the output and use the printed pre-restore backup when recovery is needed.

To restore the pre-restore safety copy, pass its archive path and `-ConfirmRestore` to the same script. Do not run `docker compose down -v` during recovery; that removes the database volume and is not part of logical restore.

## Scope and limits

- A backup contains all data in the PostgreSQL database, including imported document text and research history. Treat it as sensitive even though it excludes `.env` secrets and original PDF files.
- The script targets the local Compose project and its configured database. It does not support hosted PostgreSQL or point-in-time recovery.
- Keep the archive and manifest paired. Restore refuses a missing, mismatched, or modified manifest.
- Backup output is not automatically encrypted. Git ignores the default `backups/` directory, but that does not protect its contents from other local users, malware, or disk loss.
- Practice restoring to a disposable environment before relying on a backup for an important recovery.
