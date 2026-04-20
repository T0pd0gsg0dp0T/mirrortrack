# Pull encrypted DB off device and decrypt for inspection

Requires: `sqlcipher` binary installed on host
(`sudo apt install sqlcipher` on Ubuntu 22.04).

```bash
set -e

PKG="com.potpal.mirrortrack.debug"
OUT="/tmp/mirrortrack.db"

# Pull the encrypted DB. run-as requires debuggable build.
adb shell "run-as $PKG cat databases/mirrortrack.db" > "$OUT.enc"

# Decrypt. Replace PASSPHRASE with the key you set in the app.
# NOTE: this is the RAW 32-byte hex key, not the user passphrase. To inspect
# from outside the app, either add a dev-only "export hex key" button, or run
# the Argon2 KDF offline (see desktop-analysis/decrypt.py).

read -r -s -p "Raw hex key (64 chars): " HEXKEY
echo

sqlcipher "$OUT.enc" <<SQL
PRAGMA key = "x'$HEXKEY'";
PRAGMA cipher_compatibility = 4;
ATTACH DATABASE '$OUT' AS plain KEY '';
SELECT sqlcipher_export('plain');
DETACH DATABASE plain;
SQL

echo "Decrypted plaintext DB: $OUT"
echo "Inspect with: sqlite3 $OUT 'SELECT * FROM data_points ORDER BY timestamp DESC LIMIT 50;'"
```

For real analysis use `desktop-analysis/decrypt.py` which handles the Argon2
derivation automatically given the passphrase + salt.
