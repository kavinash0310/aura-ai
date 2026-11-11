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

Keep the archive and matching manifest together. The hash detects accidental changes; it does not prove who created a backup. The default `.dump` output is plaintext. To encrypt the output, install the official [age command-line tool](https://github.com/FiloSottile/age), create an age identity, and pass its public recipient:

```powershell
$keyDirectory = Join-Path $env:LOCALAPPDATA 'Aura'
New-Item -ItemType Directory -Path $keyDirectory -Force | Out-Null
$identityPath = Join-Path $keyDirectory 'backup-identity.txt'
age-keygen -o $identityPath
$ageRecipient = age-keygen -y $identityPath
pwsh -NoProfile -File .\scripts\Backup-AuraDatabase.ps1 `
  -OutputDirectory 'E:\AURA-backups' `
  -AgeRecipient $ageRecipient
```

Encrypted output ends in `.dump.age`; the matching manifest records its SHA-256 and `age` format. The script accepts one native `age1...` recipient. It creates a temporary plaintext dump under the current Windows user's temp directory so PostgreSQL can verify it, encrypts it, and removes the temporary file. File deletion is not secure erasure; use BitLocker or equivalent full-disk encryption to protect temporary files and do not use an unencrypted shared temp volume. The restore script also decrypts to a temporary plaintext file before loading it, then removes that file. Keep the private identity separate from the backup set and synced folders, restrict access to it, and preserve an offline recovery copy; losing the identity makes encrypted backups unusable. The scheduler stores only the public recipient, never the private identity.

Copy both the `.dump.age` archive and its matching manifest to encrypted removable storage or another controlled off-device destination; AURA does not upload backups. Encrypting the file does not prove who created it: restore only trusted archives, and test recovery separately.

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

To encrypt each scheduled archive, install `age` so it is on the task user's `PATH`, then pass the public recipient as `-AgeRecipient 'age1...'` when registering. The scheduled task stores only that public recipient; it does not store or need the private identity.

Remove the scheduled task without deleting its archives:

```powershell
pwsh -NoProfile -File .\scripts\Unregister-AuraBackupSchedule.ps1 -ConfirmRemoval
```

The scheduler runs the same verified backup script. If the database is down or backup verification fails, retention does not run, so the previous backup set is preserved. The scheduled task does not send backups off-device or guarantee recovery if the machine or disk fails.

## Restore a backup

Restore replaces the current AURA database contents. Before running it, verify that the archive is one you created or otherwise trust, and that its adjacent manifest is present. Restoring an untrusted PostgreSQL archive can execute database definitions from that archive.

```powershell
pwsh -NoProfile -File .\scripts\Restore-AuraDatabase.ps1 `
  -BackupPath '.\backups\aura-db-20261108T120000Z-1234abcd.dump' `
  -ConfirmRestore
```

For an encrypted archive, pass its `.dump.age` path and the separate private identity file:

```powershell
pwsh -NoProfile -File .\scripts\Restore-AuraDatabase.ps1 `
  -BackupPath '.\backups\aura-db-20261108T120000Z-1234abcd.dump.age' `
  -AgeIdentityFile "$env:LOCALAPPDATA\Aura\backup-identity.txt" `
  -ConfirmRestore
```

Encrypted restores require one native age identity. Before stopping the API, the script verifies the ciphertext digest and decrypts the archive; its automatic pre-restore safety backup is encrypted to the same identity's public recipient.

The script checks the manifest filename, size, and SHA-256 digest, then asks PostgreSQL to inspect the archive before changing the database. If the API is running, it stops it and creates a fresh, verified pre-restore backup in the same directory. It restores in one database transaction, so a failed PostgreSQL restore rolls back its changes. On success, it starts the API only if it was running beforehand. If restore execution fails, the API stays stopped; inspect the output and use the printed pre-restore backup when recovery is needed.

To restore the pre-restore safety copy, pass its archive path and `-ConfirmRestore` to the same script. Do not run `docker compose down -v` during recovery; that removes the database volume and is not part of logical restore.

## Scope and limits

- A backup contains all data in the PostgreSQL database, including imported document text and research history. Treat it as sensitive even though it excludes `.env` secrets and original PDF files.
- The script targets the local Compose project and its configured database. It does not support hosted PostgreSQL or point-in-time recovery.
- Keep the archive and manifest paired. Restore refuses a missing, mismatched, or modified manifest.
- Backup output is plaintext unless `-AgeRecipient` is explicitly supplied. Git ignores the default `backups/` directory, but that does not protect plaintext archives or temporary files from other local users, malware, or disk loss.
- Age-encrypted backups can be copied off-device, but AURA does not perform remote uploads. Keep the private identity separate and verify a restore before depending on the backup.
- Scheduling is optional and only works while the interactive Windows user session and Docker Desktop are available.
- Practice restoring to a disposable environment before relying on a backup for an important recovery.
