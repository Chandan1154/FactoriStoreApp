#!/usr/bin/env python3
import argparse
import hashlib
import importlib.util
import ipaddress
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit


ROOT = Path(__file__).resolve().parents[1]
PACKAGE_PART = re.compile(r"^[a-z][a-z0-9_]*$")


def normalize_store_url(raw_url: str) -> tuple[str, str]:
    value = raw_url.strip()
    if not value:
        raise ValueError("Store URL is required.")
    if "://" not in value:
        value = "https://" + value
    try:
        parsed = urlsplit(value)
        host = parsed.hostname
        port = parsed.port
    except ValueError as error:
        raise ValueError(f"Invalid store URL: {error}") from error
    if parsed.scheme.lower() != "https" or not host:
        raise ValueError("Store URL must be an HTTPS URL with a valid hostname.")
    if parsed.username is not None or parsed.password is not None:
        raise ValueError("Store URL must not contain credentials.")
    if port not in (None, 443):
        raise ValueError("Store URL must use the default HTTPS port.")
    host = host.rstrip(".").encode("idna").decode("ascii").lower()
    if "." not in host or host == "localhost" or host.endswith(
        (".local", ".localhost", ".internal", ".test", ".example", ".invalid")
    ):
        raise ValueError("Store URL must use a public DNS hostname.")
    try:
        ipaddress.ip_address(host)
    except ValueError:
        pass
    else:
        raise ValueError("IP-address storefront URLs are not supported.")
    labels = host.split(".")
    if any(
        not re.fullmatch(r"[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?", label)
        for label in labels
    ) or re.fullmatch(r"[0-9.]+", host):
        raise ValueError("Store URL contains an invalid hostname.")
    normalized = urlunsplit(
        ("https", host, parsed.path or "", parsed.query, "")
    )
    return normalized, host


def package_id_for(host: str, app_name: str) -> str:
    labels = [re.sub(r"[^a-z0-9]", "", label) for label in reversed(host.split("."))]
    labels = [label for label in labels if label]
    parts = [
        label[:63] if label[0].isalpha() else f"h{label[:62]}"
        for label in labels[-3:]
    ]
    digest = hashlib.sha256(f"{host}|{app_name.casefold()}".encode()).hexdigest()[:10]
    return ".".join(["com", "factori", "store", *parts, f"a{digest}"])


def validate_package_id(value: str) -> str:
    if len(value) > 200 or not all(PACKAGE_PART.fullmatch(part) for part in value.split(".")):
        raise ValueError(
            "Application ID must be dot-separated lowercase Android package components "
            "starting with a letter."
        )
    return value


def validate_release_environment() -> None:
    names = (
        "STORE_SIGNING_STORE_FILE",
        "STORE_SIGNING_STORE_PASSWORD",
        "STORE_SIGNING_KEY_ALIAS",
        "STORE_SIGNING_KEY_PASSWORD",
    )
    missing = [name for name in names if not os.environ.get(name)]
    if missing:
        raise ValueError(
            "Release builds require all signing values in environment variables: "
            + ", ".join(missing)
        )
    if not Path(os.environ["STORE_SIGNING_STORE_FILE"]).is_file():
        raise ValueError("STORE_SIGNING_STORE_FILE does not refer to a readable keystore file.")


def main() -> int:
    parser = argparse.ArgumentParser(description="Build a retailer-configured Factori Android app.")
    parser.add_argument("--store-name", required=True)
    parser.add_argument("--store-url", required=True)
    parser.add_argument("--icon", required=True, type=Path)
    parser.add_argument("--application-id")
    parser.add_argument("--version-code", type=int, default=1)
    parser.add_argument("--version-name", default="1.0")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--release", action="store_true")
    parser.add_argument("--offline", action="store_true")
    args = parser.parse_args()

    if importlib.util.find_spec("PIL") is None:
        raise ValueError(
            "Pillow is required to generate Android launcher icons. "
            f"Install it for {sys.executable} with: "
            f'"{sys.executable}" -m pip install -r "{ROOT / "requirements-apk.txt"}"'
        )

    name = args.store_name.strip()
    if not name or len(name) > 64 or any(ord(char) < 32 for char in name):
        raise ValueError("Store name must contain 1–64 printable characters.")
    if not 1 <= args.version_code <= 2_100_000_000:
        raise ValueError("Version code must be between 1 and 2100000000.")
    if (
        not args.version_name
        or len(args.version_name) > 40
        or any(ord(char) < 32 for char in args.version_name)
    ):
        raise ValueError("Version name must contain 1–40 printable characters.")
    storefront_url, host = normalize_store_url(args.store_url)
    app_id = validate_package_id(args.application_id or package_id_for(host, name))
    icon_path = args.icon.expanduser().resolve()
    if not icon_path.is_file():
        raise ValueError(f"Store icon does not exist: {icon_path}")
    if args.release:
        validate_release_environment()

    output = args.output or ROOT / "artifacts" / f"{app_id}.apk"
    output = output.expanduser().resolve()
    output.parent.mkdir(parents=True, exist_ok=True)

    task = ":app:assembleRelease" if args.release else ":app:assembleDebug"
    variant = "release" if args.release else "debug"
    with tempfile.TemporaryDirectory(prefix="factori-store-icon-") as temp_dir:
        icon_resources = Path(temp_dir) / "res"
        subprocess.run(
            [
                sys.executable,
                str(ROOT / "scripts" / "generate-store-icon.py"),
                "--icon",
                str(icon_path),
                "--output-dir",
                str(icon_resources),
            ],
            cwd=ROOT,
            check=True,
        )
        gradle_command = os.environ.get("GRADLE_BIN") or str(ROOT / "gradlew")
        command = [
            gradle_command,
            "--no-daemon",
            task,
            f"-PSTORE_APP_NAME={name}",
            f"-PSTORE_APP_ID={app_id}",
            f"-PSTORE_URL={storefront_url}",
            f"-PSTORE_VERSION_CODE={args.version_code}",
            f"-PSTORE_VERSION_NAME={args.version_name}",
            "-PSTORE_LAUNCHER_ICON=@mipmap/store_icon",
            f"-PSTORE_ICON_RES={icon_resources}",
        ]
        if args.offline:
            command.insert(2, "--offline")
        subprocess.run(command, cwd=ROOT, check=True, timeout=1200)

    built_apk = ROOT / "app" / "build" / "outputs" / "apk" / variant / f"app-{variant}.apk"
    if not built_apk.is_file():
        raise FileNotFoundError(f"Gradle completed but expected APK was not created: {built_apk}")
    with zipfile.ZipFile(built_apk) as apk:
        bad_member = apk.testzip()
        if bad_member:
            raise ValueError(f"Generated APK is corrupt at {bad_member}.")
    shutil.copy2(built_apk, output)

    subprocess.run(
        [
            "bash",
            str(ROOT / "scripts" / "validate-apk.sh"),
            str(output),
            app_id,
            name,
        ],
        cwd=ROOT,
        check=True,
    )
    print(f"Generated {variant} APK: {output}")
    print(f"Application ID: {app_id}")
    print(f"Store URL: {storefront_url}")
    if not args.release:
        print("This debug APK is signed with the local Android debug key; do not distribute it.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
        print(f"APK generation failed: {error}", file=sys.stderr)
        sys.exit(1)
