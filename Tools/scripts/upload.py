"""Post CI-built APKs to a Telegram channel (continuous deployment).

Uploads go through MTProto (Pyrogram, logged in as a bot), not the HTTP Bot API,
so the 50 MB Bot API upload limit does not apply.

Usage:
    python Tools/scripts/upload.py <apk-dir> [<apk-dir> ...]

Environment:
    TELEGRAM_APP_ID, TELEGRAM_APP_HASH   api_id / api_hash from my.telegram.org
    TELEGRAM_BOT_TOKEN                   token of a bot that is an admin of the channel
    TELEGRAM_CHAT_ID                     channel: @username or numeric id (-100...)
    COMMIT_ID, COMMIT_URL, COMMIT_MESSAGE, BRANCH, BUILD_LABEL   caption data
    TELEGRAM_ABIS                        optional, comma separated ABI filter (default: all APKs)
"""

import asyncio
import contextlib
import html
import os
import re
import sys
from pathlib import Path

from pyrogram import Client
from pyrogram.enums import ParseMode
from pyrogram.types import InputMediaDocument

CAPTION_LIMIT = 1024  # Telegram media caption limit, counted on the rendered text
ATTEMPTS = 3


def env(name: str, default: str = "") -> str:
    return (os.environ.get(name) or default).strip()


def find_apks(dirs: list[str]) -> list[Path]:
    abis = [a.strip() for a in env("TELEGRAM_ABIS").split(",") if a.strip()]
    apks = sorted({p for d in dirs for p in Path(d).rglob("*.apk")})
    if abis:
        apks = [p for p in apks if any(abi in p.name for abi in abis)]
    return apks


TRAILER = re.compile(r"^[A-Za-z][A-Za-z0-9-]*: \S")


def strip_trailers(message: str) -> str:
    """Drop the git trailer block (Co-Authored-By:, Signed-off-by:, ...) at the end of a message."""
    paragraphs = message.strip().split("\n\n")
    if len(paragraphs) > 1 and all(TRAILER.match(line) for line in paragraphs[-1].splitlines()):
        paragraphs.pop()
    return "\n\n".join(paragraphs).strip()


def build_caption() -> str:
    commit_id = env("COMMIT_ID", "unknown")
    short_id = commit_id[:7]
    commit_url = env("COMMIT_URL")
    branch = env("BRANCH")
    label = env("BUILD_LABEL", "CI build")

    header = f"<b>{html.escape(label)}</b>"
    commit_ref = (f'<a href="{html.escape(commit_url, quote=True)}">{short_id}</a>'
                  if commit_url else f"<code>{short_id}</code>")
    footer = commit_ref + (f" on <code>{html.escape(branch)}</code>" if branch else "")

    # Budget the commit message so the rendered caption stays under the limit.
    # Markup does not count towards the limit, only visible text does.
    visible_overhead = len(label) + len(short_id) + len(branch) + 16
    budget = CAPTION_LIMIT - visible_overhead
    message = strip_trailers(env("COMMIT_MESSAGE")) or "(no commit message)"
    if len(message) > budget:
        message = message[: budget - 1].rstrip() + "…"

    return f"{header}\n\n<blockquote expandable>{html.escape(message)}</blockquote>\n\n{footer}"


async def with_retry(what: str, func):
    for attempt in range(1, ATTEMPTS + 1):
        try:
            return await func()
        except Exception as e:  # noqa: BLE001 - network / flood errors, retry all
            print(f"{what}: attempt {attempt}/{ATTEMPTS} failed: {e!r}", file=sys.stderr)
            if attempt == ATTEMPTS:
                raise
            await asyncio.sleep(10 * attempt)


async def main() -> int:
    dirs = sys.argv[1:] or ["."]
    apks = find_apks(dirs)
    if not apks:
        print(f"No APKs found in {dirs}", file=sys.stderr)
        return 1

    missing = [n for n in ("TELEGRAM_APP_ID", "TELEGRAM_APP_HASH", "TELEGRAM_BOT_TOKEN", "TELEGRAM_CHAT_ID")
               if not env(n)]
    if missing:
        print(f"Missing environment: {', '.join(missing)}", file=sys.stderr)
        return 1

    chat_id: int | str = env("TELEGRAM_CHAT_ID")
    with contextlib.suppress(ValueError):
        chat_id = int(chat_id)

    caption = build_caption()
    print("APKs:\n  " + "\n  ".join(str(p) for p in apks))
    print("Caption:\n" + caption)

    client = Client(
        "retrogram_ci",
        api_id=int(env("TELEGRAM_APP_ID")),
        api_hash=env("TELEGRAM_APP_HASH"),
        bot_token=env("TELEGRAM_BOT_TOKEN"),
        in_memory=True,
        no_updates=True,
    )
    async with client:
        if len(apks) == 1:
            await with_retry("send_document", lambda: client.send_document(
                chat_id, str(apks[0]), caption=caption, parse_mode=ParseMode.HTML))
        else:
            # Albums take up to 10 files; the caption goes on the last one so it shows under the group.
            media = [InputMediaDocument(str(p)) for p in apks[:10]]
            media[-1] = InputMediaDocument(str(apks[min(len(apks), 10) - 1]),
                                           caption=caption, parse_mode=ParseMode.HTML)
            await with_retry("send_media_group", lambda: client.send_media_group(chat_id, media))
    print("Posted to Telegram.")
    return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
