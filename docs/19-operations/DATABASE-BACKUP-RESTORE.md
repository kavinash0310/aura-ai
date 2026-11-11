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

Keep the archive and matching manifest together. The hash detects accidental changes; it does not prove who created a backup. Store backups on encrypted storage and keep at least one copy separate from the computer running AURA. AURA does not encrypt backups or copy them to remote storage automatically.

## Review retention and optionally schedule daily backups

Retention defaults to a 30-day window and keeps at least the two newest verified backups. First preview which matching archive/manifest pairs would be removed:

```powershell
pwsh -NoProfile -File .\scripts\Remove-ExpiredAuraBackups.ps1 `
  -OutputDirectory '.\backups' `
  -RetentionDays 30 `
  -MinimumBackups 2
```

Preview is the default. Add `-Apply` only when you intend to delete the listed, expired backups. The script examines only direct files in the chosen folder with AURA's generated filename pattern and valid matching SHA-256 manifests. It keeps malformed, changed, or manifest-less files, and always protects the configured minimum number of newest valid backups. It does not recurse into subfolders.

To register an optional Windows scheduled task, run this once while signed in as the Windows user who runs AURA:

```powershell
pwsh -NoProfile -File .\scripts\Register-AuraBackupSchedule.ps1 `
  -DailyAt '02:30' `
  -OutputDirectory '.\backups' `
  -RetentionDays 30 `
  -MinimumBackups 2
```

The task runs daily at the chosen local time, creates and verifies a backup first, then applies retention. It uses the current Windows user's interactive session without elevation; it runs only while that user is logged in. Docker Desktop and the Compose database must be running. If the computer is asleep or the user is signed out, Windows may run the missed task after it becomes available, provided the user signs back in. The absolute repository and backup paths are stored in the task; recreate the task if either path changes. The installer refuses to overwrite a task with the same name. To use another name, pass `-TaskName` both when registering and removing it.

Remove the scheduled task without deleting its archives:

```powershell
pwsh -NoProfile -File .\scripts\Unregister-AuraBackupSchedule.ps1 -ConfirmRemoval
```

The scheduler runs the same verified backup script. If the database is down or backup verification fails, retention does not run, so the previous backup set is preserved. The scheduled task does not encrypt backups, send them off-device, or guarantee recovery if the machine or disk fails.

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
- Scheduling is optional and only works while the interactive Windows user session and Docker Desktop are available.
- Practice restoring to a disposable environment before relying on a backup for an important recovery.
