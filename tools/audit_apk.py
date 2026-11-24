"""Read-only checks for QA copy/auth implementation accidentally packaged in an APK.

Usage: python tools/audit_apk.py APK [--expect-qa]
Checks every uncompressed APK entry in UTF-8/UTF-16LE, including DEX markers.
This supplements build tests; it is not a general secrets scanner.
"""
import argparse
import hashlib
import json
import re
from pathlib import Path
from zipfile import ZipFile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("apk", type=Path)
mode = parser.add_mutually_exclusive_group()
mode.add_argument("--expect-qa", action="store_true")
mode.add_argument("--release", action="store_true", help="Also require diagnostic log tags to be absent")
args = parser.parse_args()
markers = {
    "qa_package": "com.clutchalarm.app.behaviorqa",
    "qa_app_label": "ClutchAlarm QA",
    "fixture_email": "qa@example.com",
    "fixture_password": "clutch-qa",
    "fixture_session": "qa-session:",
    "qa_auth_class": "Lcom/clutchalarm/app/auth/QaAuthenticationRepository;",
    "qa_login_label": "QA 검증용 로그인",
    "qa_login_hint": "QA 전용 인증 체험",
    "qa_registration_hint": "QA 전용 가입 체험",
    "qa_deletion_hint": "QA 검증용 계정의 탈퇴",
    "qa_home_action": "QA 로그인 화면으로",
    "notification_diagnostics": "알림 판정 상세",
    "publication_diagnostics": "시스템 발송 요청이 성공한 알림입니다.",
}
with ZipFile(args.apk) as archive:
    dex_files = [n for n in archive.namelist() if re.fullmatch(r"classes\d*\.dex", n)]
    if not dex_files:
        raise SystemExit("No DEX files found; refusing to report success")
    entries = {n: archive.read(n) for n in archive.namelist() if not n.endswith("/")}
locations = {name: [n for n, data in entries.items()
                    if any(text.encode(encoding) in data for encoding in ("utf-8", "utf-16le"))]
             for name, text in markers.items()}
found = {name: bool(paths) for name, paths in locations.items()}
tags = ("ClutchSession", "ClutchNotificationOpen", "ClutchNotificationPost", "ClutchNotifications", "ClutchBehavior")
diagnostic_tags = {tag: any(tag.encode() in data for data in entries.values()) for tag in tags}
passed = all(found.values()) if args.expect_qa else not any(found.values())
if args.release:
    passed = passed and not any(diagnostic_tags.values())
print(json.dumps({"apk": str(args.apk), "sha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(),
                  "expected": "qa" if args.expect_qa else "release" if args.release else "production-debug",
                  "dex_files": len(dex_files), "entries_checked": len(entries),
                  "markers_present": found, "diagnostic_tags_present": diagnostic_tags,
                  "passed": passed}, ensure_ascii=False, indent=2))
raise SystemExit(0 if passed else 1)
