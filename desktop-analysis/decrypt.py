#!/usr/bin/env python3
"""
Decrypt an exported MirrorTrack SQLCipher DB for desktop analysis.

Workflow on the phone:
  Settings > Export → produces mirrortrack_export.zip containing:
    mirrortrack.db (SQLCipher encrypted)
    salt.bin (16 bytes)
    manifest.json (KDF params)

Workflow on desktop:
  $ python decrypt.py mirrortrack_export.zip
  (prompts for passphrase)
  → writes mirrortrack.db (plain SQLite, readable by DuckDB/pandas)

Legacy mode (pre-manifest):
  $ python decrypt.py mirrortrack.db.enc salt.bin
  (uses hardcoded KDF params)

Dependencies:
  pip install argon2-cffi pysqlcipher3

Security note: this script leaves a *plaintext* SQLite file on disk. Keep it
on an encrypted partition or an encrypted USB drive. Delete with `shred -u`
when done.
"""

import argparse
import getpass
import json
import sys
import zipfile
from pathlib import Path
from tempfile import TemporaryDirectory

from argon2.low_level import hash_secret_raw, Type


# Fallback defaults — must match CryptoManager.kt.
DEFAULT_ARGON2_T_COST = 3
DEFAULT_ARGON2_M_COST_KIB = 65_536
DEFAULT_ARGON2_PARALLELISM = 2
DEFAULT_KEY_LENGTH_BYTES = 32


def derive_key(
    passphrase: str,
    salt: bytes,
    t_cost: int = DEFAULT_ARGON2_T_COST,
    m_cost: int = DEFAULT_ARGON2_M_COST_KIB,
    parallelism: int = DEFAULT_ARGON2_PARALLELISM,
    hash_len: int = DEFAULT_KEY_LENGTH_BYTES,
) -> bytes:
    return hash_secret_raw(
        secret=passphrase.encode("utf-8"),
        salt=salt,
        time_cost=t_cost,
        memory_cost=m_cost,
        parallelism=parallelism,
        hash_len=hash_len,
        type=Type.ID,
    )


def decrypt_with_pysqlcipher(enc_path: Path, key: bytes, out_path: Path) -> None:
    try:
        from pysqlcipher3 import dbapi2 as sqlcipher
    except ImportError:
        print("pysqlcipher3 unavailable — falling back to CLI path.", file=sys.stderr)
        decrypt_with_cli(enc_path, key, out_path)
        return

    hex_key = key.hex()
    con = sqlcipher.connect(str(enc_path))
    cur = con.cursor()
    cur.execute(f"PRAGMA key = \"x'{hex_key}'\"")
    cur.execute("PRAGMA cipher_compatibility = 4")
    cur.execute(f"ATTACH DATABASE '{out_path}' AS plain KEY ''")
    cur.execute("SELECT sqlcipher_export('plain')")
    cur.execute("DETACH DATABASE plain")
    con.close()


def decrypt_with_cli(enc_path: Path, key: bytes, out_path: Path) -> None:
    import subprocess
    hex_key = key.hex()
    script = f"""
PRAGMA key = "x'{hex_key}'";
PRAGMA cipher_compatibility = 4;
ATTACH DATABASE '{out_path}' AS plain KEY '';
SELECT sqlcipher_export('plain');
DETACH DATABASE plain;
"""
    subprocess.run(
        ["sqlcipher", str(enc_path)],
        input=script,
        text=True,
        check=True,
    )


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("input", type=Path, help="Export .zip or legacy .db.enc")
    ap.add_argument("salt", type=Path, nargs="?", default=None,
                    help="salt.bin (only for legacy mode)")
    ap.add_argument("--out", type=Path, default=None,
                    help="Output path for plaintext DB")
    args = ap.parse_args()

    if not args.input.exists():
        print(f"Input not found: {args.input}", file=sys.stderr)
        return 1

    # Detect zip (manifest-based) vs legacy mode
    if zipfile.is_zipfile(args.input):
        return handle_zip_export(args.input, args.out)
    else:
        return handle_legacy_export(args.input, args.salt, args.out)


def handle_zip_export(zip_path: Path, out: Path | None) -> int:
    with TemporaryDirectory() as tmpdir:
        tmp = Path(tmpdir)
        with zipfile.ZipFile(zip_path) as zf:
            zf.extractall(tmp)

        db_file = tmp / "mirrortrack.db"
        salt_file = tmp / "salt.bin"
        manifest_file = tmp / "manifest.json"

        if not db_file.exists():
            print("mirrortrack.db not found in archive.", file=sys.stderr)
            return 1
        if not salt_file.exists():
            print("salt.bin not found in archive.", file=sys.stderr)
            return 1

        salt = salt_file.read_bytes()
        if len(salt) != 16:
            print(f"Unexpected salt length {len(salt)} (want 16).", file=sys.stderr)
            return 1

        # Read KDF params from manifest, or use defaults
        t_cost = DEFAULT_ARGON2_T_COST
        m_cost = DEFAULT_ARGON2_M_COST_KIB
        parallelism = DEFAULT_ARGON2_PARALLELISM

        if manifest_file.exists():
            manifest = json.loads(manifest_file.read_text())
            t_cost = manifest.get("t", t_cost)
            m_cost = manifest.get("m", m_cost)
            parallelism = manifest.get("p", parallelism)
            print(f"Manifest: kdf={manifest.get('kdf')}, t={t_cost}, "
                  f"m={m_cost}, p={parallelism}", file=sys.stderr)

        passphrase = getpass.getpass("DB passphrase: ")
        print("Deriving key (Argon2id)...", file=sys.stderr)
        key = derive_key(passphrase, salt, t_cost, m_cost, parallelism)

        out_path = out or zip_path.with_suffix(".sqlite")
        if out_path.exists():
            print(f"Output {out_path} exists; refusing to overwrite.", file=sys.stderr)
            return 1

        print(f"Decrypting → {out_path}", file=sys.stderr)
        decrypt_with_pysqlcipher(db_file, key, out_path)

        print(f"OK. Plaintext SQLite: {out_path}", file=sys.stderr)
        print(f"Quick peek: sqlite3 {out_path} 'SELECT COUNT(*) FROM data_points'",
              file=sys.stderr)
        return 0


def handle_legacy_export(enc_path: Path, salt_path: Path | None, out: Path | None) -> int:
    if salt_path is None or not salt_path.exists():
        print("Legacy mode requires salt.bin as second argument.", file=sys.stderr)
        return 1

    salt = salt_path.read_bytes()
    if len(salt) != 16:
        print(f"Unexpected salt length {len(salt)} (want 16).", file=sys.stderr)
        return 1

    out_path = out or enc_path.with_suffix("")
    if out_path.exists():
        print(f"Output {out_path} exists; refusing to overwrite.", file=sys.stderr)
        return 1

    passphrase = getpass.getpass("DB passphrase: ")
    print("Deriving key (Argon2id, ~500ms)...", file=sys.stderr)
    key = derive_key(passphrase, salt)

    print(f"Decrypting {enc_path} -> {out_path}", file=sys.stderr)
    decrypt_with_pysqlcipher(enc_path, key, out_path)

    print(f"OK. Plaintext SQLite: {out_path}", file=sys.stderr)
    print(f"Quick peek: sqlite3 {out_path} 'SELECT COUNT(*) FROM data_points'",
          file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
