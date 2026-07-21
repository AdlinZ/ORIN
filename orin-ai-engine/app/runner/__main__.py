"""
Runner CLI — ``python -m app.runner`` entry point.

Usage::

    python -m app.runner enroll \\
        --name my-runner \\
        --token sk-enroll-... \\
        --url https://orin.example.com

After successful enrollment the Runner starts a heartbeat loop that
runs until SIGTERM / SIGINT or the machine credential is rejected. Drain only
stops new work; it does not stop heartbeats.
"""

from __future__ import annotations

import argparse
import logging
import os
import sys
from typing import Optional

from app.runner.client import RunnerClient
from app.runner.credential_store import clear, load, save
from app.runner.enrollment import enroll
from app.runner.heartbeat import HeartbeatLoop

logger = logging.getLogger(__name__)


def _build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        prog="orin-runner",
        description="ORIN Runner — connect your server to the ORIN Control Plane",
    )
    sub = p.add_subparsers(dest="command", required=True)

    # ---- enroll -------------------------------------------------------
    enroll_p = sub.add_parser("enroll", help="Enroll this machine with an Enrollment Token")
    enroll_p.add_argument("--name", required=True, help="Runner name (must match the token)")
    enroll_p.add_argument(
        "--token",
        default=os.getenv("ORIN_ENROLLMENT_TOKEN"),
        help="One-time Enrollment Token (or set ORIN_ENROLLMENT_TOKEN)",
    )
    enroll_p.add_argument("--url", required=True, help="Control Plane base URL, e.g. https://orin.example.com")
    enroll_p.add_argument("--concurrency", type=int, default=1, help="Max concurrent Agent Runs (default: 1)")

    # ---- resume -------------------------------------------------------
    resume_p = sub.add_parser("resume", help="Resume heartbeat with previously-enrolled credentials")
    # no required args — reads from ~/.orin/credentials.json

    # ---- status -------------------------------------------------------
    sub.add_parser("status", help="Print local enrollment status")

    # ---- unenroll -----------------------------------------------------
    sub.add_parser("unenroll", help="Remove local credentials (does NOT revoke on the server)")

    return p


def main(argv: Optional[list[str]] = None) -> int:
    # Default to "resume" when invoked with no arguments (Docker CMD-friendly)
    if argv is not None and len(argv) == 0:
        argv = ["resume"]
    parser = _build_parser()
    args = parser.parse_args(argv)
    if args.command == "enroll" and not args.token:
        parser.error("enroll requires --token or ORIN_ENROLLMENT_TOKEN")

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        datefmt="%Y-%m-%dT%H:%M:%S",
    )

    if args.command == "enroll":
        return _cmd_enroll(args)
    elif args.command == "resume":
        return _cmd_resume()
    elif args.command == "status":
        return _cmd_status()
    elif args.command == "unenroll":
        return _cmd_unenroll()
    else:
        print(f"Unknown command: {args.command}", file=sys.stderr)
        return 1


# ------------------------------------------------------------------
# command implementations
# ------------------------------------------------------------------


def _cmd_enroll(args: argparse.Namespace) -> int:
    client = RunnerClient(args.url)

    # 1) Enroll
    try:
        response = enroll(client, token=args.token, name=args.name,
                          max_concurrency=args.concurrency)
    except Exception as exc:
        logger.error("Enrollment failed: %s", exc)
        return 1

    runner_id = response["runnerId"]
    logger.info("Enrollment successful — runnerId=%s", runner_id)

    # 2) Persist credentials
    save(args.url, response)
    logger.info("Credentials saved to ~/.orin/credentials.json")

    # 3) Start heartbeat loop
    interval = response.get("heartbeatIntervalSec", 15)
    loop = HeartbeatLoop(
        client=client,
        runner_id=runner_id,
        credential=response["credential"],
        interval_sec=interval,
    )
    try:
        loop.run()
    except KeyboardInterrupt:
        logger.info("Runner stopped by user")
    return 0


def _cmd_resume() -> int:
    creds = load()
    if creds is None:
        logger.error("Not enrolled — run 'python -m app.runner enroll' first")
        return 1

    client = RunnerClient(creds["control_plane_url"])
    loop = HeartbeatLoop(
        client=client,
        runner_id=creds["runner_id"],
        credential=creds["credential"],
        interval_sec=creds.get("heartbeat_interval_sec", 15),
    )
    try:
        loop.run()
    except KeyboardInterrupt:
        logger.info("Runner stopped by user")
    return 0


def _cmd_status() -> int:
    creds = load()
    if creds is None:
        print("Status: NOT_ENROLLED")
        return 0
    print(f"Status: ENROLLED")
    print(f"  Runner ID:     {creds['runner_id']}")
    print(f"  Credential ID: {creds['credential_id']}")
    print(f"  Key prefix:    {creds.get('key_prefix', 'N/A')}")
    print(f"  Last 4:        {creds.get('last4', 'N/A')}")
    print(f"  Control Plane: {creds['control_plane_url']}")
    print(f"  Heartbeat:     every {creds.get('heartbeat_interval_sec', 15)}s")
    return 0


def _cmd_unenroll() -> int:
    clear()
    print("Local credentials removed.  (Use the ORIN UI to revoke the Runner if needed.)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
