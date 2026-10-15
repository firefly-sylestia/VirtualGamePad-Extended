#!/usr/bin/env python3
"""VirtualGamePad Extended signing-key wizard.

Run it with no arguments and it opens a guided, step-by-step wizard:

    python3 tools/signing/vgp_signing.py

The wizard starts on the **Android** release key, then walks you through the
**Windows** code-signing certificate. Every field is asked one at a time, with
an explanation of what it is and what to type, and after each key is created the
wizard prints exactly what to paste where.

The password question is always asked: type your own, or press Enter and the
wizard generates a strong one and shows it. When the terminal cannot hide what
you type, the wizard says so and still asks, instead of silently generating a
password and moving on.

Right after a key exists the wizard prints every GitHub Actions secret with a
description of what it holds and a ready-to-paste ``gh secret set`` command, and
offers to upload them for you.

It can also, from the same menu:

* show the current state of your signing setup,
* rebuild signing.properties for a keystore you already have,
* print the secrets and paste instructions again at any time,
* decrypt a secret blob back into a key file,
* inspect an existing keystore or PFX,
* encrypt a key for a GitHub Actions secret,
* upload a secret with the GitHub CLI.

Existing keys are replaced automatically (after a confirmation), and the file
being replaced is kept next to the new one with a timestamped ``.bak`` suffix,
so regenerating never fails and never silently loses the previous file.

No third-party Python packages are needed. The wizard shells out to tools that
must already be on ``PATH``:

* ``keytool``  - Android keystore (ships with any JDK)
* ``openssl``  - Windows certificate and PFX
* ``gpg``      - optional, passphrase-encrypted secrets
* ``gh``       - optional, push a secret straight to GitHub

The scripted subcommands (``android``, ``windows``, ``properties``, ``status``,
``inspect``, ``encrypt``, ``decrypt``, ``gh-secret``) are still available for
automation; run with ``--help`` to see them.
"""

from __future__ import annotations

import argparse
import base64
import getpass
import os
import secrets as pysecrets
import shutil
import string
import subprocess
import sys
import textwrap
import time
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_ANDROID_KEYSTORE = REPO_ROOT / "mobile" / "release.keystore"
DEFAULT_SIGNING_PROPERTIES = REPO_ROOT / "mobile" / "signing.properties"
DEFAULT_WINDOWS_OUT = REPO_ROOT / "tools" / "signing" / "out" / "windows"
DEFAULT_SECRETS_OUT = REPO_ROOT / "tools" / "signing" / "out" / "secrets"

# Secret names suggested for GitHub Actions. Nothing in this repository reads
# them yet; they are a convention so the names stay consistent.
ANDROID_SECRET_NAMES = (
    "ANDROID_KEYSTORE_BASE64",
    "ANDROID_KEYSTORE_PASSWORD",
    "ANDROID_KEY_ALIAS",
    "ANDROID_KEY_PASSWORD",
)
WINDOWS_SECRET_NAMES = ("WINDOWS_PFX_BASE64", "WINDOWS_PFX_PASSWORD")

# One line per secret saying what it holds, so the copy-paste summary is
# self-explanatory in the GitHub UI.
SECRET_NOTES = {
    "ANDROID_KEYSTORE_BASE64": "base64 of mobile/release.keystore - the APK signing key",
    "ANDROID_KEYSTORE_PASSWORD": "password protecting that keystore",
    "ANDROID_KEY_ALIAS": "alias of the signing key inside the keystore",
    "ANDROID_KEY_PASSWORD": "password of the signing key itself",
    "WINDOWS_PFX_BASE64": "base64 of the code-signing PFX bundle",
    "WINDOWS_PFX_PASSWORD": "password protecting that PFX",
}


# --------------------------------------------------------------------------- #
# Tiny terminal UI                                                            #
# --------------------------------------------------------------------------- #
class Style:
    """ANSI styling, disabled automatically when the output is not a terminal."""

    RESET = "\033[0m"
    BOLD = "\033[1m"
    DIM = "\033[2m"
    RED = "\033[31m"
    GREEN = "\033[32m"
    YELLOW = "\033[33m"
    BLUE = "\033[34m"
    MAGENTA = "\033[35m"
    CYAN = "\033[36m"
    GREY = "\033[90m"

    enabled = sys.stdout.isatty() and os.environ.get("NO_COLOR") is None

    @classmethod
    def paint(cls, text: str, *codes: str) -> str:
        if not cls.enabled or not codes:
            return text
        return "".join(codes) + text + cls.RESET


def term_width() -> int:
    return max(64, min(shutil.get_terminal_size((88, 24)).columns, 92))


def title_block(text: str, subtitle: str = "") -> None:
    """Draw the wizard banner."""
    width = term_width()
    print()
    print(Style.paint("╔" + "═" * (width - 2) + "╗", Style.CYAN, Style.BOLD))
    print(
        Style.paint("║ ", Style.CYAN, Style.BOLD)
        + Style.paint(text.ljust(width - 4), Style.BOLD)
        + Style.paint(" ║", Style.CYAN, Style.BOLD)
    )
    if subtitle:
        print(
            Style.paint("║ ", Style.CYAN, Style.BOLD)
            + Style.paint(subtitle.ljust(width - 4), Style.GREY)
            + Style.paint(" ║", Style.CYAN, Style.BOLD)
        )
    print(Style.paint("╚" + "═" * (width - 2) + "╝", Style.CYAN, Style.BOLD))


def panel(title: str, lines: list[str], colour: str = Style.BLUE) -> None:
    """Draw a titled box with wrapped body text."""
    width = term_width()
    inner = width - 4
    print()
    print(Style.paint("┌─ " + title + " " + "─" * max(0, width - len(title) - 5) + "┐", colour))
    for raw in lines:
        if raw == "":
            print(Style.paint("│", colour) + " " * (width - 2) + Style.paint("│", colour))
            continue
        for chunk in textwrap.wrap(raw, inner, subsequent_indent="  ") or [""]:
            print(
                Style.paint("│ ", colour)
                + chunk.ljust(inner)
                + Style.paint(" │", colour)
            )
    print(Style.paint("└" + "─" * (width - 2) + "┘", colour))


def block(title: str, body: str) -> None:
    """Draw a copy-paste block that is easy to select."""
    width = term_width()
    print()
    print(Style.paint(f"  ── {title} " + "─" * max(0, width - len(title) - 7), Style.GREEN))
    for line in body.splitlines() or [""]:
        print("  " + line)
    print(Style.paint("  " + "─" * (width - 4), Style.GREEN))


def step(index: int, total: int, heading: str) -> None:
    """Announce the field about to be asked for."""
    width = term_width()
    label = f" Step {index} of {total}: {heading} "
    print()
    print(Style.paint(label + "─" * max(0, width - len(label) - 1), Style.MAGENTA, Style.BOLD))


def hint(text: str) -> None:
    for line in textwrap.wrap(text, term_width() - 4):
        print(Style.paint("  " + line, Style.GREY))


def info(text: str) -> None:
    print(Style.paint("  " + text, Style.CYAN))


def ok(text: str) -> None:
    print(Style.paint("  ✓ " + text, Style.GREEN, Style.BOLD))


def warn(text: str) -> None:
    print(Style.paint("  ! " + text, Style.YELLOW))


def error(text: str) -> None:
    print(Style.paint("  ✗ " + text, Style.RED, Style.BOLD), file=sys.stderr)


def bullet(text: str) -> None:
    for index, line in enumerate(textwrap.wrap(text, term_width() - 6)):
        print("    " + ("• " if index == 0 else "  ") + line)


def pause(text: str = "Press Enter to continue") -> None:
    try:
        input(Style.paint(f"\n  {text} ", Style.DIM))
    except EOFError:
        pass


def ask(
    label: str,
    default: str | None = None,
    help_text: str | None = None,
    validator=None,
) -> str:
    """Ask for one value, showing the default and echoing the choice."""
    if help_text:
        hint(help_text)
    prompt = f"  {label}"
    prompt += f" [{default}]" if default else ""
    while True:
        try:
            raw = input(Style.paint(prompt + ": ", Style.BOLD)).strip()
        except EOFError:
            raw = ""
        value = raw or (default or "")
        if validator is not None:
            problem = validator(value)
            if problem:
                warn(problem)
                continue
        print(Style.paint(f"    → {value}", Style.GREY))
        return value


def read_hidden(prompt: str) -> tuple[str, bool]:
    """Read a line without echoing it, and still ask when echo cannot be disabled.

    Returns the typed text and whether it was actually hidden.

    ``getpass`` refuses to hide input when stdin is not a terminal (a piped
    script, an IDE run window, some git-bash setups) and used to make the wizard
    skip the password question entirely. Here that case falls back to a visible
    prompt, so the question is always asked.
    """
    if sys.stdin.isatty():
        try:
            return getpass.getpass(prompt), True
        except (EOFError, KeyboardInterrupt):
            raise
        except Exception as exc:  # noqa: BLE001 - any getpass failure falls back
            warn(f"Cannot hide your typing here ({exc}).")
    else:
        warn("Input is not a terminal, so what you type will be visible.")
    try:
        return input(prompt), False
    except EOFError:
        return "", False


def ask_secret(
    label: str,
    help_text: str | None = None,
    generator=None,
    allow_blank: bool = False,
) -> str:
    """Ask for a password; pressing Enter with no input generates a strong one."""
    if help_text:
        hint(help_text)
    hint("Type your own password, or just press Enter and the wizard generates one.")

    value, hidden = read_hidden(Style.paint(f"  {label}: ", Style.BOLD))
    if value:
        print(Style.paint("    → (hidden)" if hidden else "    → (as typed)", Style.GREY))
        return value
    if allow_blank:
        return ""

    value = generator() if generator else random_password()
    print()
    warn(f"{label} was generated. It is shown here once - copy it now:")
    print(Style.paint(f"    → {value}", Style.YELLOW, Style.BOLD))
    print(
        Style.paint(
            "    It is also repeated in the summary at the end of this step.", Style.GREY
        )
    )
    return value


def confirm(question: str, default: bool = True) -> bool:
    suffix = "[Y/n]" if default else "[y/N]"
    try:
        raw = input(Style.paint(f"  {question} {suffix} ", Style.BOLD)).strip().lower()
    except EOFError:
        return default
    if not raw:
        return default
    return raw in ("y", "yes")


def random_password(length: int = 32) -> str:
    alphabet = string.ascii_letters + string.digits
    return "".join(pysecrets.choice(alphabet) for _ in range(length))


def display_path(path: Path) -> str:
    """Show a path relative to the repository when it is inside it."""
    try:
        return str(path.relative_to(REPO_ROOT))
    except ValueError:
        return str(path)


# --------------------------------------------------------------------------- #
# External tools                                                              #
# --------------------------------------------------------------------------- #
def require(tool: str, hint_text: str) -> None:
    if shutil.which(tool) is None:
        error(f"'{tool}' was not found on PATH.")
        hint(hint_text)
        raise SystemExit(2)


def run(cmd: list, env: dict | None = None, stdin: bytes | None = None, capture: bool = False):
    printable = " ".join(str(part) for part in cmd)
    print(Style.paint(f"    $ {printable}", Style.GREY))
    return subprocess.run(
        [str(part) for part in cmd],
        check=True,
        env=env,
        input=stdin,
        capture_output=capture,
    )


def backup_path(path: Path) -> Path:
    """A timestamped neighbour so a replaced file is never simply lost."""
    stamp = time.strftime("%Y%m%d-%H%M%S")
    return path.with_name(f"{path.name}.{stamp}.bak")


def remove_existing(paths: list[Path], assume_yes: bool = False) -> None:
    """Move previous key files aside so regenerating never fails or loses data."""
    existing = [path for path in paths if path.exists()]
    if not existing:
        return

    print()
    warn("These existing files will be replaced (a .bak copy is kept):")
    for path in existing:
        bullet(str(path))
    if not assume_yes and not confirm("Replace them now?", default=True):
        error("Aborted: nothing was changed.")
        raise SystemExit(1)

    for path in existing:
        try:
            path.rename(backup_path(path))
            ok(f"replaced {path.name} (kept a backup next to it)")
        except OSError as exc:
            error(f"could not replace {path}: {exc}")
            raise SystemExit(1)


def write_properties(
    keystore: Path,
    alias: str,
    store_password: str,
    key_password: str,
    properties: Path | None = None,
) -> Path:
    """Write the signing.properties file Gradle reads, backing up any old one."""
    target = properties or DEFAULT_SIGNING_PROPERTIES
    if target.exists():
        backup = backup_path(target)
        target.rename(backup)
        info(f"previous {target.name} kept as {backup.name}")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(
        "# Generated by tools/signing/vgp_signing.py - DO NOT COMMIT\n"
        f"STORE_FILE={keystore}\n"
        f"STORE_PASSWORD={store_password}\n"
        f"KEY_ALIAS={alias}\n"
        f"KEY_PASSWORD={key_password}\n"
    )
    return target


# --------------------------------------------------------------------------- #
# GitHub Actions secrets                                                      #
# --------------------------------------------------------------------------- #
def secret_entry(name: str, value: str | None = None, blob: Path | None = None) -> dict:
    """One secret to show, and optionally upload: a value or a file to pipe in."""
    return {
        "name": name,
        "description": SECRET_NOTES.get(name, ""),
        "value": value,
        "blob": blob,
    }


def android_secret_entries(result: dict) -> list[dict]:
    """The Android secrets, with a fresh base64 blob on disk."""
    keystore = Path(result["keystore"])
    blob = DEFAULT_SECRETS_OUT / "android-keystore.b64"
    blob.parent.mkdir(parents=True, exist_ok=True)
    blob.write_bytes(base64.b64encode(keystore.read_bytes()))
    return [
        secret_entry(ANDROID_SECRET_NAMES[0], blob=blob),
        secret_entry(ANDROID_SECRET_NAMES[1], value=result.get("store_password")),
        secret_entry(ANDROID_SECRET_NAMES[2], value=result.get("alias")),
        secret_entry(ANDROID_SECRET_NAMES[3], value=result.get("key_password")),
    ]


def windows_secret_entries(result: dict) -> list[dict]:
    """The Windows secrets, with a fresh base64 blob on disk."""
    pfx = Path(result["pfx_path"])
    blob = DEFAULT_SECRETS_OUT / "windows-codesign.pfx.b64"
    blob.parent.mkdir(parents=True, exist_ok=True)
    blob.write_bytes(base64.b64encode(pfx.read_bytes()))
    return [
        secret_entry(WINDOWS_SECRET_NAMES[0], blob=blob),
        secret_entry(WINDOWS_SECRET_NAMES[1], value=result.get("password")),
    ]


def github_secrets_flow(entries: list[dict], assume_yes: bool = False) -> None:
    """Describe every secret, print the commands, then offer to upload them."""
    entries = [entry for entry in entries if entry.get("value") or entry.get("blob")]
    if not entries:
        return

    title_block(
        "GitHub Actions secrets",
        "Everything to paste, and a one-key way to upload it",
    )

    rows = [f"{entry['name']:<28}{entry['description']}" for entry in entries]
    panel("What goes where", rows)

    commands: list[str] = []
    values: list[str] = []
    for entry in entries:
        if entry["blob"] is not None:
            commands.append(f"gh secret set {entry['name']} < {entry['blob']}")
            values.append(f"{entry['name']:<28} (the whole file {entry['blob'].name})")
        else:
            commands.append(f"gh secret set {entry['name']} --body '{entry['value']}'")
            values.append(f"{entry['name']:<28} {entry['value']}")

    block("1) Paste these values into the GitHub web UI", "\n".join(values))
    block("2) Or run these commands with the GitHub CLI", "\n".join(commands))
    hint(
        "In the web UI: Settings > Secrets and variables > Actions > New repository "
        "secret, then paste one name and one value per secret."
    )

    if assume_yes:
        return
    if not confirm("Upload these as GitHub Actions secrets now?", default=False):
        info("Not uploaded. Reproduce them any time with option 4 (Show what to paste).")
        return

    require("gh", "Install the GitHub CLI (https://cli.github.com) and run 'gh auth login'.")
    repo = ask("Repository (blank for the current one)", "")
    for entry in entries:
        cmd = ["gh", "secret", "set", entry["name"]]
        if repo:
            cmd += ["--repo", repo]
        try:
            if entry["blob"] is not None:
                run(cmd, stdin=Path(entry["blob"]).read_bytes())
            else:
                run(cmd + ["--body", entry["value"]])
        except subprocess.CalledProcessError as exc:
            error(f"Could not upload {entry['name']} (exit code {exc.returncode}).")
            hint("Run `gh auth login` and check that you have admin access to the repository.")
            return
        ok(f"uploaded {entry['name']}")


# --------------------------------------------------------------------------- #
# Android / APK key                                                           #
# --------------------------------------------------------------------------- #
def android_flow(assume_yes: bool = False, scripted: dict | None = None) -> dict:
    """Walk through creating the Android release keystore. Returns the result."""
    scripted = scripted or {}
    require("keytool", "Install a JDK (for example Temurin 21) so `keytool` exists.")

    title_block(
        "STEP 1 — Android release signing",
        "This key signs the APK. Android refuses an update signed with a different key.",
    )
    panel(
        "What you are about to create",
        [
            "A PKCS#12 keystore holding one RSA 2048 key, plus a signing.properties file",
            "that Gradle reads when building a release APK.",
            "Keep the keystore somewhere safe and never commit it. The wizard writes it to",
            "mobile/release.keystore by default, which this repository already ignores.",
        ],
    )
    if not assume_yes:
        pause("Press Enter to start the Android steps")

    total = 9
    step(1, total, "Keystore file path")
    keystore = Path(
        scripted.get("keystore")
        or ask(
            "Keystore file path",
            str(DEFAULT_ANDROID_KEYSTORE),
            "Where the keystore will be written. Press Enter to accept the default.",
        )
    ).expanduser().resolve()

    step(2, total, "Key alias")
    alias = scripted.get("alias") or ask(
        "Key alias",
        "vgp-release",
        "The alias names the key inside the keystore. It is not secret; "
        "'vgp-release' is a clear choice.",
    )

    step(3, total, "Organisation name (O)")
    organisation = scripted.get("organisation") or ask(
        "Organisation",
        "VirtualGamePad Extended",
        "Shown on the certificate. Use your own or project name.",
    )

    step(4, total, "Organisational unit (OU)")
    unit = scripted.get("unit") or ask(
        "Organisational unit", "Mobile", "The team or department. Any short word is fine."
    )

    step(5, total, "City (L)")
    locality = scripted.get("locality") or ask("City", "Unknown", "Your city.")

    step(6, total, "State (ST)")
    state = scripted.get("state") or ask("State or province", "Unknown", "Your state or region.")

    step(7, total, "Country code (C)")

    def check_country(value: str) -> str | None:
        if len(value) != 2 or not value.isalpha():
            return "Use a two letter country code, for example US or IN."
        return None

    country = (scripted.get("country") or ask("Country code", "US", None, check_country)).upper()

    step(8, total, "Validity in days")
    validity = int(
        scripted.get("validity")
        or ask(
            "Validity in days",
            "10000",
            "10000 days is about 27 years, which is the usual Android recommendation.",
        )
    )

    step(9, total, "Keystore password")
    store_password = scripted.get("store_password") or ask_secret(
        "Keystore password",
        "Leave blank and the wizard generates a strong one for you.",
    )
    key_password = scripted.get("key_password") or store_password

    dname = scripted.get("dname") or (
        f"CN={organisation}, OU={unit}, O={organisation}, L={locality}, ST={state}, C={country}"
    )

    # Always clear the previous keystore so a regenerate never fails on a
    # duplicate alias.
    remove_existing([keystore], assume_yes=assume_yes)
    keystore.parent.mkdir(parents=True, exist_ok=True)

    env = dict(os.environ)
    env["VGP_STORE_PASSWORD"] = store_password
    env["VGP_KEY_PASSWORD"] = key_password

    print()
    info("Creating the keystore...")
    run(
        [
            "keytool", "-genkeypair", "-v",
            "-keystore", keystore,
            "-storetype", "PKCS12",
            "-alias", alias,
            "-keyalg", "RSA", "-keysize", "2048",
            "-validity", str(validity),
            "-dname", dname,
            "-storepass:env", "VGP_STORE_PASSWORD",
            "-keypass:env", "VGP_KEY_PASSWORD",
        ],
        env=env,
    )
    ok(f"keystore created at {keystore}")

    properties = Path(
        scripted.get("properties") or str(DEFAULT_SIGNING_PROPERTIES)
    ).expanduser().resolve()
    write_properties(keystore, alias, store_password, key_password, properties)
    ok(f"signing.properties written to {properties}")

    result = {
        "keystore": keystore,
        "alias": alias,
        "dname": dname,
        "validity": validity,
        "store_password": store_password,
        "key_password": key_password,
        "properties": properties,
    }
    show_android_secrets(result, assume_yes=assume_yes)
    github_secrets_flow(android_secret_entries(result), assume_yes=assume_yes)
    return result


def show_android_secrets(result: dict, assume_yes: bool = False) -> None:
    """Print exactly what to paste after the Android key exists."""
    keystore: Path = result["keystore"]
    properties: Path = result["properties"]

    title_block("Android — what to paste", "Easiest first: nothing to do, the file is already written.")
    panel(
        "Checklist",
        [
            "1. Gradle already reads mobile/signing.properties, which this wizard wrote for you.",
            "2. Keep a backup of the keystore. Losing it means you can never update on Play again.",
            "3. Optionally store both as GitHub Actions secrets (step 3 below).",
        ],
    )

    block(
        f"1) Contents of {display_path(properties)}  (already written, shown for reference)",
        properties.read_text().rstrip(),
    )

    secrets_dir = DEFAULT_SECRETS_OUT
    secrets_dir.mkdir(parents=True, exist_ok=True)
    blob_path = secrets_dir / "android-keystore.b64"
    blob_path.write_bytes(base64.b64encode(keystore.read_bytes()))
    ok(f"base64 copy of the keystore written to {blob_path}")

    block(
        "2) Password and alias, copy these into your password manager",
        "\n".join(
            [
                f"Keystore file    : {keystore}",
                f"Key alias        : {result['alias']}",
                f"Store password   : {result['store_password']}",
                f"Key password     : {result['key_password']}",
                f"Certificate name : {result['dname']}",
                f"Valid on         : {result['validity']} days",
            ]
        ),
    )

    block(
        "3) Next: store them as GitHub Actions secrets",
        "The wizard asks about that now, and option 4 reprints it later.",
    )

    warn("Decrypt the base64 blob later from this wizard's menu: option 5 (Decrypt a secret).")


# --------------------------------------------------------------------------- #
# Windows / PC code-signing certificate                                       #
# --------------------------------------------------------------------------- #
def windows_flow(assume_yes: bool = False, scripted: dict | None = None) -> dict:
    """Walk through creating the Windows code-signing certificate and PFX."""
    scripted = scripted or {}
    require("openssl", "Install OpenSSL so the certificate can be created.")

    title_block(
        "STEP 2 - Windows code signing",
        "A self-signed certificate plus a PFX bundle that signtool can use.",
    )
    panel(
        "What you are about to create",
        [
            "codesign.key.pem  - the private key",
            "codesign.crt.pem  - the certificate",
            "codesign.pfx      - both, protected by one password. This is the file signtool",
            "                    and GitHub Actions need.",
            "",
            "A self-signed certificate is fine for local signing; the Windows SmartScreen",
            "warning still appears until the certificate is trusted by the machine.",
        ],
    )
    if not assume_yes:
        pause("Press Enter to start the Windows steps")

    total = 6
    step(1, total, "Output directory")
    out_dir = Path(
        scripted.get("out_dir")
        or ask(
            "Output directory",
            str(DEFAULT_WINDOWS_OUT),
            "Where the three files are written.",
        )
    ).expanduser().resolve()

    step(2, total, "Certificate common name (CN)")
    common_name = scripted.get("common_name") or ask(
        "Common name",
        "VirtualGamePad Extended Code Signing",
        "The name shown on the certificate. Any clear name works for self-signing.",
    )

    step(3, total, "Organisation (O)")
    organisation = scripted.get("organisation") or ask(
        "Organisation", "VirtualGamePad Extended", "Shown on the certificate."
    )

    step(4, total, "PFX friendly name")
    alias = scripted.get("alias") or ask(
        "Friendly name",
        "vgp-codesign",
        "A label stored inside the PFX so you can recognise it later.",
    )

    step(5, total, "Validity in days")
    validity = int(
        scripted.get("validity")
        or ask("Validity in days", "3650", "3650 days is ten years.")
    )

    step(6, total, "PFX password")
    password = scripted.get("password") or ask_secret(
        "PFX password", "Leave blank and the wizard generates a strong one for you."
    )

    key_path = out_dir / "codesign.key.pem"
    cert_path = out_dir / "codesign.crt.pem"
    pfx_path = out_dir / "codesign.pfx"

    # Regenerate cleanly even when previous files are present.
    remove_existing([key_path, cert_path, pfx_path], assume_yes=assume_yes)
    out_dir.mkdir(parents=True, exist_ok=True)

    subject = scripted.get("subject") or f"/CN={common_name}/O={organisation}"

    print()
    info("Creating the private key and certificate...")
    run(
        [
            "openssl", "req", "-x509", "-newkey", "rsa:4096", "-nodes",
            "-keyout", key_path, "-out", cert_path,
            "-days", str(validity),
            "-subj", subject,
            "-addext", "extendedKeyUsage=codeSigning",
            "-addext", "keyUsage=digitalSignature",
        ]
    )

    info("Bundling both into a PFX...")
    run(
        [
            "openssl", "pkcs12", "-export", "-out", pfx_path,
            "-inkey", key_path, "-in", cert_path,
            "-passout", f"pass:{password}", "-name", alias,
        ]
    )
    ok(f"PFX created at {pfx_path}")

    result = {
        "out_dir": out_dir,
        "key_path": key_path,
        "cert_path": cert_path,
        "pfx_path": pfx_path,
        "alias": alias,
        "subject": subject,
        "validity": validity,
        "password": password,
    }
    show_windows_secrets(result)
    github_secrets_flow(windows_secret_entries(result), assume_yes=assume_yes)
    return result


def show_windows_secrets(result: dict) -> None:
    """Print exactly what to paste after the Windows certificate exists."""
    pfx_path: Path = result["pfx_path"]

    title_block("Windows - what to paste", "One file and one password is all signtool needs.")

    secrets_dir = DEFAULT_SECRETS_OUT
    secrets_dir.mkdir(parents=True, exist_ok=True)
    blob_path = secrets_dir / "windows-codesign.pfx.b64"
    blob_path.write_bytes(base64.b64encode(pfx_path.read_bytes()))
    ok(f"base64 copy of the PFX written to {blob_path}")

    block(
        "1) Sign a build locally",
        "\n".join(
            [
                f"$env:VGP_PFX = \"{pfx_path}\"",
                "signtool sign /f $env:VGP_PFX /p <password> /fd SHA256 "
                "path\\to\\VGamepadPC.exe",
                "",
                "Signing is optional: an unsigned build still runs, it just shows a",
                "SmartScreen warning the first time.",
            ]
        ),
    )

    block(
        "2) Details to keep in your password manager",
        "\n".join(
            [
                f"PFX bundle    : {pfx_path}",
                f"Private key   : {result['key_path']}",
                f"Certificate   : {result['cert_path']}",
                f"Friendly name : {result['alias']}",
                f"Subject       : {result['subject']}",
                f"Valid on      : {result['validity']} days",
                f"PFX password  : {result['password']}",
            ]
        ),
    )

    block(
        "3) Next: store them as GitHub Actions secrets",
        "The wizard asks about that now, and option 4 reprints it later.",
    )

    warn("Keep the PFX password written down: the wizard cannot recover it later.")


def verify_keystore(keystore: Path, password: str) -> bool:
    """Ask keytool whether this password actually opens the keystore."""
    require("keytool", "Install a JDK (for example Temurin 21) so `keytool` exists.")
    env = dict(os.environ)
    env["VGP_STORE_PASSWORD"] = password
    result = subprocess.run(
        ["keytool", "-list", "-keystore", str(keystore), "-storepass:env", "VGP_STORE_PASSWORD"],
        env=env,
        capture_output=True,
    )
    return result.returncode == 0


def restore_properties_flow() -> None:
    """Rebuild signing.properties for a keystore that already exists.

    Nothing is regenerated here: the password is checked with keytool first, so
    an existing APK signing key keeps signing every future build.
    """
    title_block(
        "Rebuild signing.properties",
        "Reuse a keystore you already have, without making a new key",
    )
    panel(
        "When to use this",
        [
            "You have mobile/release.keystore but lost mobile/signing.properties (it is",
            "gitignored, so it is not in Git). Nothing is regenerated: this only writes the",
            "four lines Gradle reads.",
            "",
            "Never regenerate the keystore to fix a missing properties file - a new key can",
            "not update an already installed app.",
        ],
    )

    keystore = Path(
        ask("Keystore file", str(DEFAULT_ANDROID_KEYSTORE))
    ).expanduser().resolve()
    if not keystore.is_file():
        error(f"{keystore} is not a file")
        pause()
        return

    alias = ask("Key alias", "vgp-release")
    store_password = ""
    while True:
        store_password = ask_secret(
            "Keystore password", "The password that opens the keystore file."
        )
        info("Checking the password with keytool...")
        if verify_keystore(keystore, store_password):
            ok("keytool accepted that password.")
            break
        error("keytool could not open the keystore with that password.")
        hint("Check your password manager or the scrollback of the run that made the key.")
        if not confirm("Try another password?", default=True):
            return

    key_password = ask_secret(
        "Key password",
        "Usually the same as the keystore password; press Enter to reuse it.",
        allow_blank=True,
    )
    properties = write_properties(
        keystore, alias, store_password, key_password or store_password
    )
    ok(f"signing.properties written to {properties}")
    info("Release builds pick it up automatically; no other step is needed.")
    pause()


# --------------------------------------------------------------------------- #
# Status dashboard                                                            #
# --------------------------------------------------------------------------- #
def status_flow() -> None:
    """Show what exists already and what to do next."""
    keystore = DEFAULT_ANDROID_KEYSTORE
    properties = DEFAULT_SIGNING_PROPERTIES
    pfx = DEFAULT_WINDOWS_OUT / "codesign.pfx"
    secrets_dir = DEFAULT_SECRETS_OUT

    def mark(exists: bool) -> str:
        return Style.paint("ready", Style.GREEN, Style.BOLD) if exists else Style.paint(
            "missing", Style.YELLOW
        )

    title_block("Signing setup", "Where you are and what is left to do")
    print()
    print(f"  Android keystore      {mark(keystore.exists())}   {keystore}")
    print(f"  signing.properties    {mark(properties.exists())}   {properties}")
    print(f"  Windows PFX           {mark(pfx.exists())}   {pfx}")
    print(f"  Secret blobs          {mark(secrets_dir.exists())}   {secrets_dir}")

    print()
    if not keystore.exists():
        info("Next: choose option 1 to create the Android release key.")
    elif not pfx.exists():
        info("Next: choose option 2 to create the Windows code-signing certificate.")
    else:
        ok("Both keys exist. Option 4 reprints the paste instructions whenever you need them.")

    if properties.exists():
        warn("signing.properties holds a real password. Keep it out of git.")
    pause()


# --------------------------------------------------------------------------- #
# Help                                                                        #
# --------------------------------------------------------------------------- #
def help_flow() -> None:
    title_block("Help", "What each key is for and which one to make first")
    panel(
        "Android first, then Windows",
        [
            "Android: the APK must be signed with the same key forever, or Android refuses to",
            "install an update. Create this key first and back it up immediately.",
            "",
            "Windows: signing the .exe removes the SmartScreen warning once the certificate is",
            "trusted. This key can be replaced at any time, so it is the lower risk of the two.",
        ],
    )
    panel(
        "The files involved",
        [
            "mobile/release.keystore        the Android private key",
            "mobile/signing.properties      the passwords Gradle reads (never commit)",
            "tools/signing/out/windows/     the Windows key, certificate and PFX",
            "tools/signing/out/secrets/     base64 copies for GitHub Actions secrets",
        ],
    )
    panel(
        "Every command",
        [
            "vgp_signing.py                 this guided wizard",
            "vgp_signing.py status          print the setup dashboard",
            "vgp_signing.py android         create the Android key without the wizard",
            "vgp_signing.py windows         create the Windows key without the wizard",
            "vgp_signing.py inspect FILE    view an existing keystore or PFX",
            "vgp_signing.py decrypt FILE    turn a secret blob back into a key file",
        ],
    )
    pause()


# --------------------------------------------------------------------------- #
# Encrypt / decrypt secret blobs                                              #
# --------------------------------------------------------------------------- #
def _strip_suffix(name: str, suffix: str) -> str:
    if suffix and name.endswith(suffix):
        return name[: -len(suffix)]
    return f"{name}.decoded"


def _decrypt_gpg_bytes(source: Path, passphrase: str) -> bytes:
    result = subprocess.run(
        ["gpg", "--batch", "--yes", "--decrypt", "--passphrase", passphrase, str(source)],
        check=True,
        capture_output=True,
    )
    return result.stdout


def decode_blob(mode: str, source: Path, passphrase: str | None) -> bytes:
    """Turn a saved secret blob back into the original bytes."""
    if mode == "gpg":
        require("gpg", "Install GnuPG to read a gpg-encrypted secret.")
        if not passphrase:
            passphrase = getpass.getpass("  Secret passphrase: ")
        return _decrypt_gpg_bytes(source, passphrase)
    return base64.b64decode(source.read_bytes())


def decrypt_flow() -> None:
    """Decrypt a secret blob inside the wizard, no external commands needed."""
    title_block("Decrypt a secret", "Turn a saved blob back into the key file it came from")
    panel(
        "How to use this",
        [
            "Paste the whole blob when asked, then press Enter and Ctrl-D (Ctrl-Z on Windows).",
            "Or type the path of a .b64 or .gpg file instead and press Enter.",
            "",
            "The saved blobs live in tools/signing/out/secrets/ by default.",
        ],
    )

    modes = {"1": "base64", "2": "gpg"}
    info("Format:  1) base64 (default)   2) gpg passphrase")
    try:
        choice = input(Style.paint("  Choose 1 or 2 [1]: ", Style.BOLD)).strip()
    except EOFError:
        choice = ""
    mode = modes.get(choice or "1", "base64")

    answer = ask(
        "Blob path (or press Enter to paste the blob)",
        str(DEFAULT_SECRETS_OUT / "android-keystore.b64"),
    )

    source: Path | None = Path(answer).expanduser()
    pasted_bytes: bytes | None = None
    if not source.is_file():
        hint("Paste the blob now, then Ctrl-D (Ctrl-Z then Enter on Windows).")
        pasted_bytes = sys.stdin.buffer.read()
        if not pasted_bytes.strip():
            error("No blob was given.")
            pause()
            return
        source = Path(REPO_ROOT / "tools" / "signing" / "out" / "pasted.secret")
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_bytes(pasted_bytes)

    try:
        plaintext = decode_blob(mode, source, None)
    except SystemExit:
        raise
    except Exception as exc:  # noqa: BLE001 - report any decode problem plainly
        error(f"Could not decrypt: {exc}")
        pause()
        return

    default_name = _strip_suffix(source.name, ".gpg" if mode == "gpg" else ".b64")
    target = Path(
        ask("Save the decrypted file as", str(DEFAULT_SECRETS_OUT / default_name))
    ).expanduser()
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(plaintext)
    ok(f"decrypted {len(plaintext)} bytes to {target}")
    info("Open it with:  vgp_signing.py inspect " + str(target))
    pause()


def encrypt_flow() -> None:
    title_block("Encrypt a key for a secret", "Wrap a key file so it can be pasted into GitHub")
    source = Path(ask("Key file to encrypt", str(DEFAULT_ANDROID_KEYSTORE))).expanduser()
    if not source.is_file():
        error(f"{source} is not a file")
        pause()
        return

    modes = {"1": "base64", "2": "gpg"}
    info("Format:  1) base64 (default)   2) gpg passphrase")
    try:
        choice = input(Style.paint("  Choose 1 or 2 [1]: ", Style.BOLD)).strip()
    except EOFError:
        choice = ""
    mode = modes.get(choice or "1", "base64")

    output = DEFAULT_SECRETS_OUT / (
        f"{source.name}.gpg" if mode == "gpg" else f"{source.name}.b64"
    )
    output.parent.mkdir(parents=True, exist_ok=True)

    if mode == "gpg":
        require("gpg", "Install GnuPG to use passphrase encryption.")
        passphrase = ask_secret("Passphrase", "Used again later to decrypt this secret.")
        run(
            ["gpg", "--batch", "--yes", "--symmetric", "--cipher-algo", "AES256",
             "--passphrase", passphrase, "-o", output, source]
        )
    else:
        output.write_bytes(base64.b64encode(source.read_bytes()))

    ok(f"secret written to {output}")
    block(
        "Copy into a GitHub secret",
        f"gh secret set ANDROID_KEYSTORE_BASE64 < {output}\n"
        "Or open the file, copy everything, and paste it into the secret's value box.",
    )
    pause()


# --------------------------------------------------------------------------- #
# Inspect an existing key                                                     #
# --------------------------------------------------------------------------- #
def detect_kind(path: Path, requested: str = "auto") -> str:
    if requested != "auto":
        return requested
    if path.suffix.lower() in (".pfx", ".p12"):
        return "pfx"
    return "keystore"


def inspect_file(path: Path, kind: str = "auto", password: str | None = None) -> None:
    if not path.is_file():
        error(f"{path} is not a file")
        return
    kind = detect_kind(path, kind)
    info(f"Reading {path} as a {kind}")

    if kind == "pfx":
        require("openssl", "Install OpenSSL to inspect a PFX/P12 file.")
        if password is None:
            password = getpass.getpass("  PFX password: ")
        run(["openssl", "pkcs12", "-in", path, "-info", "-nokeys",
             "-passin", f"pass:{password}"])
        return

    require("keytool", "Install a JDK so `keytool` is available.")
    if password is None:
        password = getpass.getpass("  Keystore password: ")
    env = dict(os.environ)
    env["VGP_STORE_PASSWORD"] = password
    run(["keytool", "-list", "-v", "-keystore", path,
         "-storepass:env", "VGP_STORE_PASSWORD"], env=env)


def inspect_flow() -> None:
    title_block("Inspect a key", "Look inside a keystore or PFX you already have")
    default = (
        str(DEFAULT_ANDROID_KEYSTORE)
        if DEFAULT_ANDROID_KEYSTORE.exists()
        else str(DEFAULT_WINDOWS_OUT / "codesign.pfx")
    )
    path = Path(ask("File to inspect", default)).expanduser()
    kinds = {"1": "auto", "2": "keystore", "3": "pfx"}
    info("Type:  1) detect automatically (default)   2) Android keystore   3) Windows PFX")
    try:
        choice = input(Style.paint("  Choose 1, 2 or 3 [1]: ", Style.BOLD)).strip()
    except EOFError:
        choice = ""
    inspect_file(path, kinds.get(choice or "1", "auto"))
    pause()


# --------------------------------------------------------------------------- #
# Publish a secret with the GitHub CLI                                        #
# --------------------------------------------------------------------------- #
def gh_flow() -> None:
    title_block("Upload a GitHub secret", "Store a blob with the GitHub CLI")
    require("gh", "Install the GitHub CLI (https://cli.github.com) and run 'gh auth login'.")

    source = Path(ask("Blob file to upload", str(DEFAULT_SECRETS_OUT / "android-keystore.b64")))
    if not source.is_file():
        error(f"{source} is not a file")
        pause()
        return

    names = ", ".join(ANDROID_SECRET_NAMES + WINDOWS_SECRET_NAMES)
    hint(f"Common names here: {names}")
    name = ask("Secret name", ANDROID_SECRET_NAMES[0])
    repo = ask("Repository (blank for the current one)", "")

    cmd = ["gh", "secret", "set", name]
    if repo:
        cmd += ["--repo", repo]
    run(cmd, stdin=source.read_bytes())
    ok(f"secret '{name}' uploaded")
    pause()


def show_paste_again() -> None:
    """Option 4: reprint what to paste from the files on disk."""
    title_block("What to paste", "Rebuilt from the files already on this machine")

    entries: list[dict] = []

    properties = DEFAULT_SIGNING_PROPERTIES
    if properties.exists():
        values: dict[str, str] = {}
        for line in properties.read_text().splitlines():
            if line.strip().startswith("#") or "=" not in line:
                continue
            key, _, value = line.partition("=")
            values[key.strip()] = value.strip()

        block(
            f"Android - {display_path(properties)}",
            properties.read_text().rstrip(),
        )
        keystore = Path(values.get("STORE_FILE", str(DEFAULT_ANDROID_KEYSTORE)))
        if keystore.is_file():
            entries += android_secret_entries(
                {
                    "keystore": keystore,
                    "alias": values.get("KEY_ALIAS", ""),
                    "store_password": values.get("STORE_PASSWORD", ""),
                    "key_password": values.get("KEY_PASSWORD", ""),
                }
            )
        else:
            warn(f"{keystore} is missing, so its keystore secret cannot be rebuilt.")
    else:
        warn("No mobile/signing.properties yet, so there is no Android key to paste.")
        info("Create it with option 1.")

    pfx = DEFAULT_WINDOWS_OUT / "codesign.pfx"
    if pfx.is_file():
        entries += windows_secret_entries({"pfx_path": pfx, "password": None})
        warn(
            "The PFX password is not stored on disk, so only the base64 secret is "
            "rebuilt here; take the password from your password manager."
        )
    else:
        info("Create the Windows certificate with option 2 to see its instructions here too.")

    github_secrets_flow(entries)
    pause()


# --------------------------------------------------------------------------- #
# The guided wizard                                                           #
# --------------------------------------------------------------------------- #
MENU: list[tuple[str, str, str]] = [
    ("1", "Android release signing", "start here, the APK key you must never lose"),
    ("2", "Windows code signing", "certificate and PFX for signtool"),
    ("3", "Check my setup", "what exists and what to do next"),
    ("4", "Show what to paste", "reprint every instruction and secret"),
    ("5", "Decrypt a secret", "turn a saved blob back into a key file"),
    ("6", "Inspect a key", "look inside a keystore or PFX"),
    ("7", "Encrypt a key for a secret", "wrap a file for GitHub Actions"),
    ("8", "Upload a GitHub secret", "needs the gh CLI"),
    ("9", "Help", "what each key is for, which to make first"),
    ("R", "Rebuild signing.properties", "reuse a keystore, never regenerate it"),
    ("0", "Exit", ""),
]


def draw_menu(first_run: bool) -> None:
    title_block("VirtualGamePad Extended", "signing key wizard")
    print()
    for key, label, note in MENU:
        prefix = Style.paint(f"  {key}", Style.CYAN, Style.BOLD)
        print(f"{prefix}  {label:<30}{Style.paint(note, Style.GREY)}")
    print()
    if first_run:
        info("Nothing is set up yet. Start with option 1, the Android release key.")
    else:
        info("Both keys are documented under option 4 whenever you need the instructions again.")


def guided_menu() -> int:
    """The interactive wizard loop. Returns a process exit code."""
    while True:
        first_run = not DEFAULT_ANDROID_KEYSTORE.exists()
        draw_menu(first_run)
        try:
            choice = input(Style.paint("\n  Choose an option: ", Style.BOLD)).strip()
        except (EOFError, KeyboardInterrupt):
            print()
            return 0

        try:
            if choice == "0":
                print()
                ok("Done. Keep the keystore and the passwords somewhere safe.")
                return 0
            if choice == "1":
                android_flow()
                if confirm(
                    "Android is done. Continue to the Windows code-signing key now?",
                    default=True,
                ):
                    windows_flow()
            elif choice == "2":
                windows_flow()
            elif choice == "3":
                status_flow()
            elif choice == "4":
                show_paste_again()
            elif choice == "5":
                decrypt_flow()
            elif choice == "6":
                inspect_flow()
            elif choice == "7":
                encrypt_flow()
            elif choice == "8":
                gh_flow()
            elif choice == "9":
                help_flow()
            elif choice.upper() == "R":
                restore_properties_flow()
            else:
                warn("Choose one of the numbers shown, then press Enter.")
        except KeyboardInterrupt:
            print()
            warn("Cancelled. Nothing further was changed.")
            return 0
        except subprocess.CalledProcessError as exc:
            print()
            error(f"A command failed with exit code {exc.returncode}.")
            hint("Read the output above; the wizard stopped before changing anything else.")
            pause()


# --------------------------------------------------------------------------- #
# Scripted CLI (kept for automation)                                          #
# --------------------------------------------------------------------------- #
def android_scripted(args: argparse.Namespace) -> dict:
    scripted: dict = {}
    for flag, key in (
        ("keystore", "keystore"),
        ("properties", "properties"),
        ("alias", "alias"),
        ("dname", "dname"),
        ("validity_days", "validity"),
        ("password", "store_password"),
        ("store_password", "store_password"),
        ("key_password", "key_password"),
    ):
        value = getattr(args, flag, None)
        if value not in (None, ""):
            scripted[key] = value
    if args.no_input:
        scripted.setdefault("keystore", str(DEFAULT_ANDROID_KEYSTORE))
        scripted.setdefault("properties", str(DEFAULT_SIGNING_PROPERTIES))
        scripted.setdefault("alias", "vgp-release")
        scripted.setdefault("organisation", "VirtualGamePad Extended")
        scripted.setdefault("unit", "Mobile")
        scripted.setdefault("locality", "Unknown")
        scripted.setdefault("state", "Unknown")
        scripted.setdefault("country", "US")
        scripted.setdefault("validity", 10000)
    return scripted


def windows_scripted(args: argparse.Namespace) -> dict:
    scripted: dict = {}
    for flag, key in (
        ("out_dir", "out_dir"),
        ("subject", "subject"),
        ("alias", "alias"),
        ("validity_days", "validity"),
        ("password", "password"),
    ):
        value = getattr(args, flag, None)
        if value not in (None, ""):
            scripted[key] = value
    if args.no_input:
        scripted.setdefault("out_dir", str(DEFAULT_WINDOWS_OUT))
        scripted.setdefault("common_name", "VirtualGamePad Extended Code Signing")
        scripted.setdefault("organisation", "VirtualGamePad Extended")
        scripted.setdefault("alias", "vgp-codesign")
        scripted.setdefault("validity", 3650)
    return scripted


def cmd_android(args: argparse.Namespace) -> int:
    android_flow(assume_yes=args.force or args.no_input, scripted=android_scripted(args))
    return 0


def cmd_windows(args: argparse.Namespace) -> int:
    windows_flow(assume_yes=args.force or args.no_input, scripted=windows_scripted(args))
    return 0


def cmd_inspect(args: argparse.Namespace) -> int:
    inspect_file(Path(args.file).expanduser().resolve(), args.kind, args.password)
    return 0


def cmd_encrypt(args: argparse.Namespace) -> int:
    source = Path(args.file).expanduser().resolve()
    if not source.is_file():
        error(f"{source} is not a file")
        return 1

    if args.mode == "gpg":
        require("gpg", "Install GnuPG to use passphrase encryption.")
        output = Path(args.out or f"{source.name}.gpg").expanduser().resolve()
        passphrase = args.passphrase or os.environ.get("VGP_SECRET_PASSPHRASE") or getpass.getpass(
            "  Secret passphrase: "
        )
        run(["gpg", "--batch", "--yes", "--symmetric", "--cipher-algo", "AES256",
             "--passphrase", passphrase, "-o", output, source])
    else:
        output = Path(args.out or f"{source.name}.b64").expanduser().resolve()
        output.write_bytes(base64.b64encode(source.read_bytes()))

    info(f"encrypted secret written: {output}")
    return 0


def cmd_decrypt(args: argparse.Namespace) -> int:
    source = Path(args.file).expanduser().resolve()
    if not source.is_file():
        error(f"{source} is not a file")
        return 1

    passphrase = args.passphrase or os.environ.get("VGP_SECRET_PASSPHRASE")
    plaintext = decode_blob(args.mode, source, passphrase)

    if args.stdout:
        sys.stdout.buffer.write(plaintext)
        return 0

    default = _strip_suffix(source.name, ".b64" if args.mode == "base64" else ".gpg")
    output = Path(args.out or default).expanduser().resolve()
    output.write_bytes(plaintext)
    info(f"decrypted secret written: {output}")
    return 0


def cmd_gh_secret(args: argparse.Namespace) -> int:
    require("gh", "Install the GitHub CLI (https://cli.github.com) and run 'gh auth login'.")
    source = Path(args.file).expanduser().resolve()
    if not source.is_file():
        error(f"{source} is not a file")
        return 1

    cmd = ["gh", "secret", "set", args.name]
    if args.repo:
        cmd += ["--repo", args.repo]
    if args.env:
        cmd += ["--env", args.env]
    run(cmd, stdin=source.read_bytes())
    info(f"secret '{args.name}' uploaded")
    return 0


def cmd_status(args: argparse.Namespace) -> int:  # noqa: ARG001 - uniform handler signature
    status_flow()
    return 0


def cmd_properties(args: argparse.Namespace) -> int:
    """Write signing.properties for an existing keystore (scripted)."""
    keystore = Path(args.keystore or DEFAULT_ANDROID_KEYSTORE).expanduser().resolve()
    if not keystore.is_file():
        error(f"{keystore} is not a file")
        return 1

    password = args.store_password or args.password or os.environ.get("VGP_STORE_PASSWORD")
    if not password:
        password = getpass.getpass("  Keystore password: ")
    if not args.no_verify and not verify_keystore(keystore, password):
        error("keytool could not open the keystore with that password.")
        return 1

    targets = write_properties(
        keystore,
        args.alias,
        password,
        args.key_password or password,
        Path(args.properties).expanduser().resolve() if args.properties else None,
    )
    info(f"signing.properties written: {targets}")
    return 0


# --------------------------------------------------------------------------- #
# Entry point                                                                 #
# --------------------------------------------------------------------------- #
def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="vgp_signing.py",
        description=(
            "VirtualGamePad Extended signing key wizard. Run with no arguments for the guided "
            "wizard, or pick a subcommand for scripted use."
        ),
    )
    parser.add_argument(
        "--wizard", action="store_true", help="Open the guided wizard (the default)"
    )
    sub = parser.add_subparsers(dest="command")

    android = sub.add_parser(
        "android", help="Create the Android release keystore and signing.properties"
    )
    android.add_argument("--keystore")
    android.add_argument("--properties")
    android.add_argument("--alias")
    android.add_argument("--validity-days", type=int)
    android.add_argument("--dname")
    android.add_argument("--password", help="Use one password for the store and the key")
    android.add_argument("--store-password")
    android.add_argument("--key-password")
    android.add_argument(
        "--force", action="store_true", help="Replace an existing keystore without asking"
    )
    android.add_argument(
        "--no-input", action="store_true", help="Never prompt; use defaults everywhere"
    )
    android.set_defaults(func=cmd_android)

    windows = sub.add_parser(
        "windows", help="Create the Windows code-signing certificate and PFX"
    )
    windows.add_argument("--out-dir")
    windows.add_argument("--subject")
    windows.add_argument("--alias")
    windows.add_argument("--validity-days", type=int)
    windows.add_argument("--password", help="PFX export password")
    windows.add_argument(
        "--force", action="store_true", help="Replace existing files without asking"
    )
    windows.add_argument(
        "--no-input", action="store_true", help="Never prompt; use defaults everywhere"
    )
    windows.set_defaults(func=cmd_windows)

    status = sub.add_parser("status", help="Print the signing setup dashboard")
    status.set_defaults(func=cmd_status)

    properties = sub.add_parser(
        "properties",
        help="Write signing.properties for a keystore that already exists",
    )
    properties.add_argument("--keystore")
    properties.add_argument("--properties")
    properties.add_argument("--alias", default="vgp-release")
    properties.add_argument("--password", help="Store password (and key password)")
    properties.add_argument("--store-password")
    properties.add_argument("--key-password")
    properties.add_argument(
        "--no-verify", action="store_true", help="Skip the keytool password check"
    )
    properties.set_defaults(func=cmd_properties)

    inspect = sub.add_parser("inspect", help="View an existing keystore or PFX")
    inspect.add_argument("file")
    inspect.add_argument("--kind", choices=["auto", "keystore", "pfx"], default="auto")
    inspect.add_argument("--password")
    inspect.set_defaults(func=cmd_inspect)

    encrypt = sub.add_parser("encrypt", help="Encrypt a key file for a GitHub secret")
    encrypt.add_argument("file")
    encrypt.add_argument("--mode", choices=["base64", "gpg"], default="base64")
    encrypt.add_argument("--out")
    encrypt.add_argument("--passphrase")
    encrypt.set_defaults(func=cmd_encrypt)

    decrypt = sub.add_parser("decrypt", help="Decrypt a secret file (mirror of encrypt)")
    decrypt.add_argument("file")
    decrypt.add_argument("--mode", choices=["base64", "gpg"], default="base64")
    decrypt.add_argument("--out")
    decrypt.add_argument("--stdout", action="store_true", help="Print instead of writing")
    decrypt.add_argument("--passphrase")
    decrypt.set_defaults(func=cmd_decrypt)

    gh_secret = sub.add_parser("gh-secret", help="Upload a file to a GitHub Actions secret")
    gh_secret.add_argument("name")
    gh_secret.add_argument("file")
    gh_secret.add_argument("--repo", help="OWNER/REPO (defaults to the current repository)")
    gh_secret.add_argument("--env", help="Environment name, if the secret is environment-scoped")
    gh_secret.set_defaults(func=cmd_gh_secret)

    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    if args.command is None:
        # No subcommand: the guided wizard when a human is watching, otherwise
        # the help text so a CI log stays readable.
        if sys.stdin.isatty() or args.wizard:
            try:
                return guided_menu()
            except KeyboardInterrupt:
                print()
                return 0
        parser.print_help()
        return 0

    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
