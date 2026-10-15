# Signing keys for VirtualGamePad Extended

This directory contains a small, dependency-free Python helper for managing the
signing keys used by the two buildable artifacts:

| Artifact | Key material | Consumed by |
| --- | --- | --- |
| Android / APK | JKS/PKCS12 keystore + passwords | `mobile/signing.properties` -> `mobile/app/build.gradle.kts` |
| Windows / PC | Code-signing certificate + PFX | `signtool` on Windows builds |

Everything runs **locally**. Nothing here touches CI by itself; the last
section explains how to hand the encrypted keys to GitHub so a workflow *could*
use them later.

> Why you need this: when `mobile/signing.properties` is missing, the Gradle
> build sets `release.signingConfig = null` and produces an **unsigned** release
> APK. CI currently builds with no secrets, so the release APK is unsigned.

> **The generator is interactive.** Run `android` or `windows` with no flags and
> it asks for each field (file path, alias, name, validity, passwords), echoes
> every choice, and prints a summary of the key it created. Pass `--no-input`
> for scripts, or set an individual flag to skip just that prompt. Use `inspect`
> (section 6) to view the details of a key again at any time.
>
> **The password question is always asked.** Type your own password, or press
> Enter with nothing typed and the wizard generates a strong one and shows it on
> screen. If your terminal cannot hide typing (a piped script, an IDE run window,
> some git-bash consoles) the wizard says so and still asks instead of quietly
> generating a password you never see.
>
> **It also offers to set up GitHub for you.** As soon as a key exists the wizard
> lists every GitHub Actions secret with a description of what it holds, prints a
> ready-to-paste `gh secret set` command for each, and asks whether to upload them
> now. Replacements keep a timestamped `.bak` copy of any file they overwrite, so
> a regenerate never destroys the previous key or `signing.properties`.

## What you need

- Python 3.10 or newer (standard library only, no `pip install`).
- `keytool` on `PATH` - ships with any JDK (Temurin 21 is what CI uses).
- `openssl` on `PATH` - for the Windows certificate.
- Optional: `gpg` for passphrase encryption, `gh` (GitHub CLI) to upload secrets.

Check everything resolves:

```bash
python3 tools/signing/vgp_signing.py --help
```

## Start here: the guided wizard

Run the tool with no arguments and it opens a menu-driven wizard that walks you
through **Android first, then Windows**, asking for one field at a time and
printing exactly what to paste at the end of each part:

```bash
python3 tools/signing/vgp_signing.py
```

The menu also lets you check your setup, reprint any paste instructions, decrypt
a secret blob inside the wizard, inspect a key, or upload a secret with `gh`.
Old key files are replaced automatically (after a confirmation), so regenerating
a key never fails because a file is already there.

Prefer the scripted commands below for automation; every one of them still works.

## 1. Generate the Android keystore (APK)

```bash
python3 tools/signing/vgp_signing.py android \
  --keystore mobile/release.keystore \
  --alias vgp-release
```

This will:

- create a PKCS12 keystore at `mobile/release.keystore` (RSA-2048, 10000 days),
- generate a strong random password and print it **once**,
- write `mobile/signing.properties` (gitignored) so the next local release build
  is signed:

```properties
STORE_FILE=/abs/path/mobile/release.keystore
STORE_PASSWORD=...
KEY_ALIAS=vgp-release
KEY_PASSWORD=...
```

Useful overrides:

- `--password <pw>` - one password for both the store and the key.
- `--store-password` / `--key-password` - set them individually.
- `--validity-days N`, `--dname "CN=..."`, `--force` to overwrite.

Verify it was created:

```bash
keytool -list -keystore mobile/release.keystore -alias vgp-release
```

**Keep `mobile/release.keystore` and the passwords safe and out of Git.** Losing
the keystore means you can never ship an update to an app installed from it.

## 2. Generate the Windows code-signing certificate (PC)

```bash
python3 tools/signing/vgp_signing.py windows \
  --out-dir tools/signing/out/windows \
  --password '<pfx-password>'
```

This produces three files in `tools/signing/out/windows/` (gitignored):

- `codesign.crt.pem` - the public certificate,
- `codesign.key.pem` - the private key,
- `codesign.pfx` - the bundle you feed to `signtool` / upload as a secret.

It is a **self-signed** certificate (`extendedKeyUsage = codeSigning`). It makes
a signature verifiable but only *trusted* after the certificate is imported into
the machine's trust store:

```powershell
# On the Windows machine that should trust the build:
Import-PfxCertificate -FilePath codesign.pfx `
  -CertStoreLocation Cert:\LocalMachine\Root
```

Sign an executable (placeholder path shown for reference only - running builds
is a CI job, see `AGENTS.md`):

```powershell
signtool sign /f codesign.pfx /p "<pfx-password>" `
  /fd SHA256 /tr http://timestamp.digicert.com /td SHA256 VGamepadPC.exe
```

For real distribution you would replace this with an EV/OV certificate issued
by a public CA; the tooling and secret layout stay identical.

## 3. Encrypt a key for use as a GitHub secret

GitHub secrets are text, and many secrets (keystore, PFX) are binary, so encode
them first. Two modes are supported.

### Base64 (simplest - recommended default)

```bash
python3 tools/signing/vgp_signing.py encrypt mobile/release.keystore --mode base64
# -> mobile/release.keystore.b64
```

### GPG passphrase encryption (defence in depth)

```bash
python3 tools/signing/vgp_signing.py encrypt tools/signing/out/windows/codesign.pfx \
  --mode gpg --prompt
# -> tools/signing/out/windows/codesign.pfx.gpg ; you are asked for a passphrase
```

`encrypt` always writes a file; add `--out <path>` to choose its location. The
passphrase can come from `--passphrase`, the `VGP_SECRET_PASSPHRASE` environment
variable, or an interactive prompt (`--prompt`).

## 4. Add the values to GitHub Secrets

Recommended secret names:

| Secret | Contents |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | base64 of `release.keystore` (or the `.gpg` text) |
| `ANDROID_KEYSTORE_PASSWORD` | the store password |
| `ANDROID_KEY_ALIAS` | `vgp-release` |
| `ANDROID_KEY_PASSWORD` | the key password |
| `WINDOWS_PFX_BASE64` | base64 of `codesign.pfx` (or the `.gpg` text) |
| `WINDOWS_PFX_PASSWORD` | the PFX password |

### Option A - GitHub CLI (fastest)

```bash
gh secret set ANDROID_KEYSTORE_BASE64 --repo <owner>/<repo> < mobile/release.keystore.b64
gh secret set ANDROID_KEYSTORE_PASSWORD --repo <owner>/<repo> --body '<store-password>'
gh secret set ANDROID_KEY_ALIAS          --repo <owner>/<repo> --body 'vgp-release'
gh secret set ANDROID_KEY_PASSWORD       --repo <owner>/<repo> --body '<key-password>'
gh secret set WINDOWS_PFX_BASE64         --repo <owner>/<repo> < tools/signing/out/windows/codesign.pfx.b64
gh secret set WINDOWS_PFX_PASSWORD       --repo <owner>/<repo> --body '<pfx-password>'
```

The helper has a shortcut that does the same thing:

```bash
python3 tools/signing/vgp_signing.py gh-secret ANDROID_KEYSTORE_BASE64 \
  mobile/release.keystore.b64 --repo <owner>/<repo>
```

### Option B - GitHub web UI

1. Open **Settings -> Secrets and variables -> Actions**.
2. Click **New repository secret**.
3. For `*_BASE64`: paste the **entire contents** of the `.b64` file (one long
   line of base64). Name it, save.
4. Repeat for the password/alias secrets, pasting the plain values.

Environment-scoped secrets work the same way under **Environments -> <env> ->
Environment secrets**; pass `--env <name>` to the CLI helper.

## 4b. Rebuild a lost signing.properties (do not regenerate the key!)

`mobile/signing.properties` is gitignored, so a fresh clone or a lost file leaves
Gradle building unsigned release APKs. Do **not** regenerate the keystore for
this: a new key cannot update an already installed app. Point the wizard at the
keystore you still have instead:

```bash
python3 tools/signing/vgp_signing.py properties \
  --keystore mobile/release.keystore --alias vgp-release
```

It asks for the password, **verifies it with `keytool` before writing anything**,
and then writes the four lines Gradle reads. Use `--store-password`/
`--password` for scripting. In the wizard the same thing is the menu entry
`R` (Rebuild signing.properties).

## 5. View the details of a generated key

The generator prints a summary when it finishes, and you can view it again at
any time with `inspect`:

```bash
# Android keystore: alias, owner, validity, signature algorithm, fingerprints
python3 tools/signing/vgp_signing.py inspect mobile/release.keystore

# Windows PFX: subject, friendly name, encryption and MAC details
python3 tools/signing/vgp_signing.py inspect tools/signing/out/windows/codesign.pfx
```

`inspect` auto-detects keystore vs PFX from the file extension; override with
`--kind keystore` or `--kind pfx`. It asks for the password if you do not pass
`--password`.

If a key has already been encrypted for GitHub, decrypt it first and view it in
one step:

```bash
# base64
python3 tools/signing/vgp_signing.py decrypt mobile/release.keystore.b64 \
  --stdout > release.keystore
python3 tools/signing/vgp_signing.py inspect release.keystore

# gpg (prompts for the passphrase), straight into inspect is not possible,
# so write it to a file first:
python3 tools/signing/vgp_signing.py decrypt tools/signing/out/windows/codesign.pfx.gpg \
  --mode gpg --out codesign.pfx
python3 tools/signing/vgp_signing.py inspect codesign.pfx
```

`decrypt --stdout` prints the raw bytes, which is handy for piping into another
tool or diffing against the original.

## 6. Decrypt (locally, and what CI would do)

Reverse any encrypted secret with the same mode:

```bash
# base64
python3 tools/signing/vgp_signing.py decrypt mobile/release.keystore.b64 \
  --mode base64 --out mobile/release.keystore

# gpg (prompts for the passphrase)
python3 tools/signing/vgp_signing.py decrypt tools/signing/out/windows/codesign.pfx.gpg \
  --mode gpg --prompt --out tools/signing/out/windows/codesign.pfx
```

Inside a GitHub Actions job, the equivalent steps are:

```yaml
- name: Decode Android keystore
  env:
    KEYSTORE_BASE64: ${{ secrets.ANDROID_KEYSTORE_BASE64 }}
  run: |
    echo "$KEYSTORE_BASE64" | base64 -d > mobile/release.keystore

- name: Write signing.properties
  env:
    STORE_PASSWORD: ${{ secrets.ANDROID_KEYSTORE_PASSWORD }}
    KEY_ALIAS: ${{ secrets.ANDROID_KEY_ALIAS }}
    KEY_PASSWORD: ${{ secrets.ANDROID_KEY_PASSWORD }}
  run: |
    cat > mobile/signing.properties <<EOF
    STORE_FILE=${GITHUB_WORKSPACE}/mobile/release.keystore
    STORE_PASSWORD=${STORE_PASSWORD}
    KEY_ALIAS=${KEY_ALIAS}
    KEY_PASSWORD=${KEY_PASSWORD}
    EOF

- name: Decode Windows PFX
  env:
    PFX_BASE64: ${{ secrets.WINDOWS_PFX_BASE64 }}
  run: |
    echo "$PFX_BASE64" | base64 -d > codesign.pfx
```

For a GPG-encrypted secret, decrypt with the passphrase held in a separate
secret:

```yaml
- run: |
    echo "${{ secrets.ANDROID_KEYSTORE_GPG }}" > keystore.gpg
    gpg --batch --yes --decrypt \
      --passphrase "${{ secrets.ANDROID_KEYSTORE_PASSPHRASE }}" \
      -o mobile/release.keystore keystore.gpg
```

> These snippets are illustrative only. This repository intentionally does not
> wire keys into any workflow yet; add them deliberately when you want signed
> CI artifacts.

## Security notes

- Never commit `*.keystore`, `*.jks`, `*.pfx`, `*.key.pem`, or
  `signing.properties`. The root `.gitignore` and `mobile/.gitignore` already
  exclude them.
- Passwords are passed to `keytool`/`gpg` via the environment, not the command
  line, so they do not leak into the process list.
- Store an offline backup of the Android keystore and its passwords; losing them
  prevents future updates to the same app.
- Rotate secrets immediately if a key file or password is ever exposed.
