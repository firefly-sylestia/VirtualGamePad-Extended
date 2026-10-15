#!/usr/bin/env python3
"""VirtualGamePad Extended signing-key helper.

Generate, inspect, encrypt/decrypt, and publish the signing keys used by the
two buildable artifacts of this repository:

* Android / APK  -> a JKS/PKCS12 keystore consumed by ``mobile/signing.properties``
* Windows / PC   -> a code-signing certificate + PFX consumed by ``signtool``

Every generation command is **interactive by default**: it asks for each field
(path, alias, distinguished name, validity, passwords) and shows the value you
just entered, so nothing is hidden. Pass ``--no-input`` for scripted use, or
supply individual flags to skip just those prompts.

The tool has no third-party Python dependencies. It shells out to tools that
must already be on ``PATH``:

* ``keytool``  (ships with any JDK)  - Android keystore create/list
* ``openssl``                         - Windows certificate + PFX inspection
* ``gpg``      (optional)             - passphrase-encrypted secrets
* ``gh``       (optional)             - push a secret straight to GitHub

Run ``python3 tools/signing/vgp_signing.py --help`` and see
``tools/signing/README.md`` for the full workflow, including how to decrypt and
view the details of every generated key.
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
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_ANDROID_KEYSTORE = REPO_ROOT / "mobile" / "release.keystore"
DEFAULT_SIGNING_PROPERTIES = REPO_ROOT / "mobile" / "signing.properties"
DEFAULT_WINDOWS_OUT = REPO_ROOT / "tools" / "signing" / "out" / "windows"
DEFAULT_DNAME = (
    "CN=VirtualGamePad Extended, OU=Mobile, O=VirtualGamePad Extended, "
    "L=Unknown, ST=Unknown, C=US"
)
DEFAULT_WIN_SUBJECT = "/CN=VirtualGamePad Extended Code Signing/O=VirtualGamePad Extended"


def log(message: str) -> None:
    print(f"[vgp-signing] {message}", file=sys.stderr)


def fail(message: str) -> None:
    sys.exit(f"[vgp-signing] error: {message}")


def require(tool: str, hint: str) -> None:
    if shutil.which(tool) is None:
        fail(f"required tool '{tool}' was not found on PATH. {hint}")


def run(cmd: list, env: dict | None = None, stdin: bytes | None = None) -> None:
    log("run: " + " ".join(str(part) for part in cmd))
    subprocess.run([str(part) for part in cmd], check=True, env=env, input=stdin)


def is_interactive(no_input: bool) -> bool:
    return (not no_input) and sys.stdin.isatty()


def prompt(text: str, default: str | None = None, interactive: bool = True) -> str:
    """Ask the user for a value, showing the default, and echo what was chosen."""
    if not interactive:
        return default or ""
    suffix = f" [{default}]" if default else ""
    try:
        value = input(f"{text}{suffix}: ").strip()
    except EOFError:
        value = ""
    resolved = value or (default or "")
    log(f"{text} = {resolved}")
    return resolved


def prompt_password(text: str, interactive: bool, generate_hint: str = "") -> str:
    """Ask for a password (hidden). Blank input generates a strong random one."""
    if not interactive:
        return random_password()
    hint = " (leave blank to generate)" if generate_hint else ""
    try:
        value = getpass.getpass(f"{text}{hint}: ")
    except EOFError:
        value = ""
    if not value:
        value = random_password()
        log(f"{text} = (generated, shown once below)")
        print(f"  {text}: {value}", file=sys.stderr)
    else:
        log(f"{text} = ********")
    return value


def random_password(length: int = 32) -> str:
    alphabet = string.ascii_letters + string.digits
    return "".join(pysecrets.choice(alphabet) for _ in range(length))


# --------------------------------------------------------------------------- #
# Android / APK                                                               #
# --------------------------------------------------------------------------- #
def cmd_android(args: argparse.Namespace) -> None:
    require("keytool", "Install a JDK (e.g. Temurin 21) so `keytool` is available.")
    interactive = is_interactive(args.no_input)

    keystore = Path(
        args.keystore
        or prompt("Keystore file path", str(DEFAULT_ANDROID_KEYSTORE), interactive)
    ).expanduser().resolve()
    alias = args.alias or prompt("Key alias", "vgp-release", interactive)
    dname = args.dname or prompt("Certificate name (dname)", DEFAULT_DNAME, interactive)
    validity = args.validity_days or int(
        prompt("Validity in days", "10000", interactive) or "10000"
    )

    if keystore.exists() and not args.force:
        fail(f"{keystore} already exists (pass --force to overwrite)")
    keystore.parent.mkdir(parents=True, exist_ok=True)

    store_password = args.store_password or args.password or prompt_password(
        "Keystore password", interactive, generate_hint=True
    )
    key_password = args.key_password or args.password or store_password

    env = dict(os.environ)
    env["VGP_STORE_PASSWORD"] = store_password
    env["VGP_KEY_PASSWORD"] = key_password

    run(
        [
            "keytool", "-genkeypair", "-v",
            "-keystore", keystore,
            "-storetype", "PKCS12",
            "-alias", alias,
            "-keyalg", "RSA", "-keysize", "2048",
            "-validity", str(int(validity)),
            "-dname", dname,
            "-storepass:env", "VGP_STORE_PASSWORD",
            "-keypass:env", "VGP_KEY_PASSWORD",
        ],
        env=env,
    )

    props_path = Path(
        args.properties or str(DEFAULT_SIGNING_PROPERTIES)
    ).expanduser().resolve()
    props_path.parent.mkdir(parents=True, exist_ok=True)
    props_path.write_text(
        "# Generated by tools/signing/vgp_signing.py - DO NOT COMMIT\n"
        f"STORE_FILE={keystore}\n"
        f"STORE_PASSWORD={store_password}\n"
        f"KEY_ALIAS={alias}\n"
        f"KEY_PASSWORD={key_password}\n"
    )

    print("", file=sys.stderr)
    log("--- Android keystore details (keep these safe) ---")
    print(f"  Keystore file : {keystore}")
    print(f"  Alias         : {alias}")
    print(f"  DName         : {dname}")
    print(f"  Validity      : {validity} days")
    print(f"  Store password: {store_password}")
    print(f"  Key password  : {key_password}")
    print(f"  signing.properties written to {props_path} (gitignored)")
    log("View these again any time with: vgp_signing.py inspect " + str(keystore))


# --------------------------------------------------------------------------- #
# Windows / PC                                                                #
# --------------------------------------------------------------------------- #
def cmd_windows(args: argparse.Namespace) -> None:
    require("openssl", "Install OpenSSL (Linux/macOS/mingw) to create the certificate.")
    interactive = is_interactive(args.no_input)

    out = Path(
        args.out_dir or prompt("Output directory", str(DEFAULT_WINDOWS_OUT), interactive)
    ).expanduser().resolve()
    subject = args.subject or prompt(
        "Certificate subject", DEFAULT_WIN_SUBJECT, interactive
    )
    alias = args.alias or prompt("PFX friendly name", "vgp-codesign", interactive)
    validity = args.validity_days or int(
        prompt("Validity in days", "3650", interactive) or "3650"
    )
    password = args.password or prompt_password(
        "PFX password", interactive, generate_hint=True
    )

    out.mkdir(parents=True, exist_ok=True)
    key_path = out / "codesign.key.pem"
    cert_path = out / "codesign.crt.pem"
    pfx_path = out / "codesign.pfx"
    for existing in (key_path, cert_path, pfx_path):
        if existing.exists() and not args.force:
            fail(f"{existing} already exists (pass --force to overwrite)")

    run([
        "openssl", "req", "-x509", "-newkey", "rsa:4096", "-nodes",
        "-keyout", key_path, "-out", cert_path,
        "-days", str(int(validity)),
        "-subj", subject,
        "-addext", "extendedKeyUsage=codeSigning",
        "-addext", "keyUsage=digitalSignature",
    ])
    run([
        "openssl", "pkcs12", "-export", "-out", pfx_path,
        "-inkey", key_path, "-in", cert_path,
        "-passout", f"pass:{password}", "-name", alias,
    ])

    print("", file=sys.stderr)
    log("--- Windows code-signing details (keep these safe) ---")
    print(f"  Subject    : {subject}")
    print(f"  Friendly   : {alias}")
    print(f"  Validity   : {validity} days")
    print(f"  Certificate: {cert_path}")
    print(f"  Private key: {key_path}")
    print(f"  PFX bundle : {pfx_path}")
    print(f"  PFX password: {password}")
    log("View these again any time with: vgp_signing.py inspect " + str(pfx_path))


# --------------------------------------------------------------------------- #
# Inspect / view an existing key                                              #
# --------------------------------------------------------------------------- #
def _detect_kind(path: Path, requested: str) -> str:
    if requested != "auto":
        return requested
    if path.suffix.lower() in (".pfx", ".p12"):
        return "pfx"
    return "keystore"


def cmd_inspect(args: argparse.Namespace) -> None:
    path = Path(args.file).expanduser().resolve()
    if not path.is_file():
        fail(f"{path} is not a file")
    kind = _detect_kind(path, args.kind)
    interactive = is_interactive(args.no_input)

    if kind == "pfx":
        require("openssl", "Install OpenSSL to inspect a PFX/P12 file.")
        password = args.password or getpass.getpass("PFX password: ")
        # -nokeys prints the certificate metadata; -info shows the encryption.
        run(["openssl", "pkcs12", "-in", path, "-info", "-nokeys",
             "-passin", f"pass:{password}"])
    else:
        require("keytool", "Install a JDK so `keytool` is available.")
        password = args.password
        if password is None:
            if interactive:
                password = getpass.getpass("Keystore password: ")
            else:
                fail("a keystore password is required (use --password)")
        env = dict(os.environ)
        env["VGP_STORE_PASSWORD"] = password
        run(["keytool", "-list", "-v", "-keystore", path,
             "-storepass:env", "VGP_STORE_PASSWORD"], env=env)


# --------------------------------------------------------------------------- #
# Encrypt / decrypt for GitHub secrets                                        #
# --------------------------------------------------------------------------- #
def _read_secret_passphrase(args: argparse.Namespace) -> str:
    if args.passphrase:
        return args.passphrase
    if os.environ.get("VGP_SECRET_PASSPHRASE"):
        return os.environ["VGP_SECRET_PASSPHRASE"]
    if args.prompt or sys.stdin.isatty():
        return getpass.getpass("Secret passphrase: ")
    fail("no passphrase available (use --passphrase, VGP_SECRET_PASSPHRASE, or --prompt)")


def cmd_encrypt(args: argparse.Namespace) -> None:
    source = Path(args.file).expanduser().resolve()
    if not source.is_file():
        fail(f"{source} is not a file")

    if args.mode == "gpg":
        require("gpg", "Install GnuPG to use passphrase encryption.")
        output = Path(args.out or f"{source.name}.gpg").expanduser().resolve()
        run([
            "gpg", "--batch", "--yes", "--symmetric", "--cipher-algo", "AES256",
            "--passphrase", _read_secret_passphrase(args), "-o", output, source,
        ])
    else:
        output = Path(args.out or f"{source.name}.b64").expanduser().resolve()
        output.write_bytes(base64.b64encode(source.read_bytes()))

    log(f"encrypted secret written: {output}")
    log("Copy its contents into a GitHub Actions secret (see tools/signing/README.md).")


def cmd_decrypt(args: argparse.Namespace) -> None:
    source = Path(args.file).expanduser().resolve()
    if not source.is_file():
        fail(f"{source} is not a file")

    if args.mode == "gpg":
        require("gpg", "Install GnuPG to decrypt passphrase-encrypted secrets.")
        plaintext = _decrypt_gpg_bytes(source, _read_secret_passphrase(args))
    else:
        plaintext = base64.b64decode(source.read_bytes())

    if args.stdout:
        sys.stdout.buffer.write(plaintext)
        return

    output = Path(args.out or _strip_suffix(source.name, ".b64" if args.mode == "base64" else ".gpg"))
    output = output.expanduser().resolve()
    output.write_bytes(plaintext)
    log(f"decrypted secret written: {output}")


def _decrypt_gpg_bytes(source: Path, passphrase: str) -> bytes:
    result = subprocess.run(
        ["gpg", "--batch", "--yes", "--decrypt", "--passphrase", passphrase, str(source)],
        check=True, capture_output=True,
    )
    return result.stdout


def _strip_suffix(name: str, suffix: str) -> str:
    if suffix and name.endswith(suffix):
        return name[: -len(suffix)]
    return f"{name}.decoded"


# --------------------------------------------------------------------------- #
# Publish straight to GitHub Secrets                                          #
# --------------------------------------------------------------------------- #
def cmd_gh_secret(args: argparse.Namespace) -> None:
    require("gh", "Install the GitHub CLI (https://cli.github.com) and run `gh auth login`.")
    source = Path(args.file).expanduser().resolve()
    if not source.is_file():
        fail(f"{source} is not a file")

    cmd = ["gh", "secret", "set", args.name]
    if args.repo:
        cmd += ["--repo", args.repo]
    if args.env:
        cmd += ["--env", args.env]
    log(f"uploading {source.name} to secret '{args.name}'"
        + (f" in {args.repo}" if args.repo else ""))
    run(cmd, stdin=source.read_bytes())
    log("secret uploaded")


# --------------------------------------------------------------------------- #
# CLI                                                                         #
# --------------------------------------------------------------------------- #
def _add_common(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--no-input", action="store_true",
                        help="Never prompt; use defaults and generated passwords")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="vgp_signing.py",
        description="Generate, inspect and manage VirtualGamePad Extended signing keys.",
    )
    sub = parser.add_subparsers(dest="command", required=True)

    android = sub.add_parser("android", help="Generate an Android release keystore + signing.properties")
    android.add_argument("--keystore")
    android.add_argument("--properties")
    android.add_argument("--alias")
    android.add_argument("--validity-days", type=int)
    android.add_argument("--dname")
    android.add_argument("--password", help="Use one password for the store and the key")
    android.add_argument("--store-password")
    android.add_argument("--key-password")
    android.add_argument("--force", action="store_true", help="Overwrite an existing keystore")
    _add_common(android)
    android.set_defaults(func=cmd_android)

    windows = sub.add_parser("windows", help="Generate a Windows code-signing certificate + PFX")
    windows.add_argument("--out-dir")
    windows.add_argument("--subject")
    windows.add_argument("--alias")
    windows.add_argument("--validity-days", type=int)
    windows.add_argument("--password", help="PFX export password")
    windows.add_argument("--force", action="store_true", help="Overwrite existing files")
    _add_common(windows)
    windows.set_defaults(func=cmd_windows)

    inspect = sub.add_parser("inspect", help="View the details of an existing keystore or PFX")
    inspect.add_argument("file")
    inspect.add_argument("--kind", choices=["auto", "keystore", "pfx"], default="auto")
    inspect.add_argument("--password")
    _add_common(inspect)
    inspect.set_defaults(func=cmd_inspect)

    encrypt = sub.add_parser("encrypt", help="Encrypt a key file for a GitHub secret")
    encrypt.add_argument("file")
    encrypt.add_argument("--mode", choices=["base64", "gpg"], default="base64")
    encrypt.add_argument("--out")
    encrypt.add_argument("--passphrase")
    encrypt.add_argument("--prompt", action="store_true")
    encrypt.set_defaults(func=cmd_encrypt)

    decrypt = sub.add_parser("decrypt", help="Decrypt a secret file (mirror of `encrypt`)")
    decrypt.add_argument("file")
    decrypt.add_argument("--mode", choices=["base64", "gpg"], default="base64")
    decrypt.add_argument("--out")
    decrypt.add_argument("--stdout", action="store_true", help="Print instead of writing a file")
    decrypt.add_argument("--passphrase")
    decrypt.add_argument("--prompt", action="store_true")
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
    args.func(args)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
