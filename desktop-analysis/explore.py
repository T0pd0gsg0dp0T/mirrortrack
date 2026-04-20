#!/usr/bin/env python3
"""
Starter analysis script for a decrypted MirrorTrack DB.

Shows three common queries:
  1. Latest snapshot per collector (most recent key/value pairs)
  2. Time-series for a single collector (e.g. battery level over time)
  3. Pivot a collector's long-form rows into a wide-form DataFrame

Run:
  python explore.py /tmp/mirrortrack_20260417.db
"""

import argparse
import sys
from pathlib import Path

import duckdb
import pandas as pd


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("db", type=Path, help="Decrypted plaintext SQLite DB")
    args = ap.parse_args()

    if not args.db.exists():
        print(f"DB not found: {args.db}", file=sys.stderr)
        return 1

    # DuckDB reads SQLite directly via the sqlite scanner extension.
    con = duckdb.connect(":memory:")
    con.execute("INSTALL sqlite; LOAD sqlite;")
    con.execute(f"ATTACH '{args.db}' AS mt (TYPE SQLITE);")

    print("\n=== Row counts by collector ===")
    df = con.execute("""
        SELECT collectorId, COUNT(*) AS rows, MAX(timestamp) AS latest_ms
        FROM mt.data_points
        GROUP BY collectorId
        ORDER BY latest_ms DESC
    """).df()
    print(df.to_string(index=False))

    print("\n=== Latest snapshot: build_info ===")
    snapshot = con.execute("""
        WITH latest AS (
          SELECT MAX(timestamp) AS t
          FROM mt.data_points WHERE collectorId = 'build_info'
        )
        SELECT key, value, valueType
        FROM mt.data_points, latest
        WHERE collectorId = 'build_info' AND timestamp = latest.t
        ORDER BY key
    """).df()
    print(snapshot.to_string(index=False))

    print("\n=== Pivoted wide-form (every build_info snapshot over time) ===")
    wide = con.execute("""
        PIVOT (
          SELECT timestamp, key, value
          FROM mt.data_points
          WHERE collectorId = 'build_info'
        )
        ON key
        USING FIRST(value)
        GROUP BY timestamp
        ORDER BY timestamp
    """).df()
    # Convert epoch ms to human-readable for display
    if "timestamp" in wide.columns:
        wide["timestamp"] = pd.to_datetime(wide["timestamp"], unit="ms")
    print(wide.to_string(index=False))

    print("\n=== Screen state events today ===")
    today = con.execute("""
        SELECT timestamp, value AS event
        FROM mt.data_points
        WHERE collectorId = 'screen_state'
          AND timestamp > (
            CAST(EXTRACT(EPOCH FROM current_date) AS BIGINT) * 1000
          )
        ORDER BY timestamp
    """).df()
    if not today.empty:
        today["timestamp"] = pd.to_datetime(today["timestamp"], unit="ms")
    print(today.to_string(index=False) if not today.empty else "(no events today)")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
