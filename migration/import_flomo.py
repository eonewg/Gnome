#!/usr/bin/env python3
"""Import a flomo HTML export into Memos v0.30 through the official API."""

from __future__ import annotations

import argparse
import getpass
import hashlib
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Iterable
import unicodedata

try:
    from bs4 import BeautifulSoup, NavigableString, Tag
except ImportError as exc:  # pragma: no cover - depends on the local runtime
    raise SystemExit(
        "缺少 beautifulsoup4，请先运行：python -m pip install beautifulsoup4"
    ) from exc


INVISIBLE_CHARACTERS = str.maketrans("", "", "\u200b\u2061\u2063\ufeff")
REPEATED_BLANK_LINES = re.compile(r"\n{3,}")
MAX_MEMO_BYTES = 8192
SPLIT_TARGET_BYTES = 7600


@dataclass(frozen=True)
class FlomoMemo:
    source_index: int
    create_time: str
    content: str
    part_index: int = 1
    part_count: int = 1

    @property
    def content_sha256(self) -> str:
        return hashlib.sha256(self.content.encode("utf-8")).hexdigest()

    @property
    def duplicate_key(self) -> tuple[int, str]:
        return (timestamp_seconds(self.create_time), self.content_sha256)


    @property
    def state_key(self) -> tuple[int, int]:
        return (self.source_index, self.part_index)

def normalize_text(value: str) -> str:
    value = value.translate(INVISIBLE_CHARACTERS).replace("\xa0", " ")
    lines = [line.rstrip() for line in value.splitlines()]
    return REPEATED_BLANK_LINES.sub("\n\n", "\n".join(lines)).strip()


def render_inline(node: Any) -> str:
    if isinstance(node, NavigableString):
        return str(node)
    if not isinstance(node, Tag):
        return ""

    name = node.name.lower()
    if name == "br":
        return "\n"

    content = "".join(render_inline(child) for child in node.children)
    # flomo exports occasionally contain invalid nested <p> elements. Treat every
    # paragraph as a block even when BeautifulSoup has to keep that nesting.
    if name == "p":
        return f"\n\n{content}\n\n"

    if name in {"strong", "b"} and content.strip():
        return f"**{content.strip()}**"
    if name in {"em", "i"} and content.strip():
        return f"*{content.strip()}*"
    if name == "a":
        href = node.get("href", "").strip()
        label = content.strip() or href
        return f"[{label}]({href})" if href else label
    return content


def render_list(list_node: Tag, depth: int = 0) -> list[str]:
    ordered = list_node.name.lower() == "ol"
    lines: list[str] = []
    items = list_node.find_all("li", recursive=False)
    for index, item in enumerate(items, start=1):
        nested_lists = item.find_all(["ul", "ol"], recursive=False)
        direct_parts: list[str] = []
        for child in item.children:
            if isinstance(child, Tag) and child.name.lower() in {"ul", "ol"}:
                continue
            direct_parts.append(render_inline(child))
        text = normalize_text("".join(direct_parts))
        prefix = f"{index}. " if ordered else "- "
        indent = "  " * depth
        text_lines = text.splitlines() or [""]
        lines.append(f"{indent}{prefix}{text_lines[0]}")
        continuation_indent = " " * (len(indent) + len(prefix))
        lines.extend(f"{continuation_indent}{line}" for line in text_lines[1:])
        for nested in nested_lists:
            lines.extend(render_list(nested, depth + 1))
    return lines


def content_to_markdown(content_node: Tag) -> str:
    blocks: list[str] = []
    for child in content_node.children:
        if isinstance(child, NavigableString):
            text = normalize_text(str(child))
            if text:
                blocks.append(text)
            continue
        if not isinstance(child, Tag):
            continue
        if child.name.lower() in {"ul", "ol"}:
            rendered = "\n".join(render_list(child))
        else:
            rendered = normalize_text(render_inline(child))
        if rendered:
            blocks.append(rendered)
    return normalize_text("\n\n".join(blocks))


def rfc3339_from_flomo(value: str, utc_offset_hours: int) -> str:
    parsed = datetime.strptime(value, "%Y-%m-%d %H:%M:%S")
    offset = timezone.utc if utc_offset_hours == 0 else timezone(
        # flomo's export contains local wall-clock time but no timezone.
        timedelta(hours=utc_offset_hours)
    )
    return parsed.replace(tzinfo=offset).isoformat(timespec="seconds")


def timestamp_seconds(value: str) -> int:
    return int(datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp())


def hard_split_utf8(value: str, max_bytes: int) -> list[str]:
    current: list[str] = []
    current_bytes = 0
    chunks: list[str] = []
    for character in value:
        encoded_size = len(character.encode("utf-8"))
        if current and current_bytes + encoded_size > max_bytes:
            chunks.append("".join(current))
            current = [character]
            current_bytes = encoded_size
        else:
            current.append(character)
            current_bytes += encoded_size
    if current:
        chunks.append("".join(current))
    return chunks


def split_content(content: str, target_bytes: int = SPLIT_TARGET_BYTES) -> list[str]:
    if len(content.encode("utf-8")) <= MAX_MEMO_BYTES:
        return [content]

    chunks: list[str] = []
    current = ""
    blocks = content.split("\n\n")
    pieces = [
        block + ("\n\n" if index < len(blocks) - 1 else "")
        for index, block in enumerate(blocks)
    ]
    for piece in pieces:
        if len(piece.encode("utf-8")) > target_bytes:
            if current:
                chunks.append(current)
                current = ""
            hard_chunks = hard_split_utf8(piece, target_bytes)
            chunks.extend(hard_chunks[:-1])
            current = hard_chunks[-1]
            continue
        candidate = current + piece
        if len(candidate.encode("utf-8")) <= target_bytes:
            current = candidate
        else:
            chunks.append(current)
            current = piece
    if current:
        chunks.append(current)
    return chunks


def shift_rfc3339_seconds(value: str, seconds: int) -> str:
    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    return (parsed + timedelta(seconds=seconds)).isoformat(timespec="seconds")


def parse_flomo_export(zip_path: Path, utc_offset_hours: int = 8) -> tuple[list[FlomoMemo], list[str]]:
    with zipfile.ZipFile(zip_path) as archive:
        html_entries = [name for name in archive.namelist() if name.lower().endswith(".html")]
        if len(html_entries) != 1:
            raise ValueError(f"期望 ZIP 中恰好有一个 HTML，实际为 {len(html_entries)} 个")
        soup = BeautifulSoup(archive.read(html_entries[0]), "html.parser")

    memos: list[FlomoMemo] = []
    media_references: list[str] = []
    for index, memo_node in enumerate(soup.select(".memo"), start=1):
        time_node = memo_node.select_one(".time")
        content_node = memo_node.select_one(".content")
        if time_node is None or content_node is None:
            raise ValueError(f"第 {index} 条记录缺少时间或正文")
        content = content_to_markdown(content_node)
        if not content:
            raise ValueError(f"第 {index} 条记录正文为空")
        create_time = rfc3339_from_flomo(time_node.get_text(strip=True), utc_offset_hours)
        parts = split_content(content)
        part_count = len(parts)
        for part_index, part in enumerate(parts, start=1):
            part_content = part
            part_time = create_time
            if part_count > 1:
                marker = f"> flomo 原记录分段 {part_index}/{part_count}"
                part_content = f"{marker}\n\n{part}"
                part_time = shift_rfc3339_seconds(create_time, -(part_index - 1))
            if len(part_content.encode("utf-8")) > MAX_MEMO_BYTES:
                raise ValueError(f"第 {index} 条记录的第 {part_index} 段仍超过服务端限制")
            memos.append(
                FlomoMemo(
                    source_index=index,
                    create_time=part_time,
                    content=part_content,
                    part_index=part_index,
                    part_count=part_count,
                )
            )
        for media in memo_node.select(".files img, .files audio, .files source, .files a"):
            reference = (media.get("src") or media.get("href") or "").strip()
            if reference:
                media_references.append(reference)

    if not memos:
        raise ValueError("没有在导出文件中找到任何 .memo 记录")
    if len({memo.duplicate_key for memo in memos}) != len(memos):
        raise ValueError("导出包中存在创建时间与正文完全相同的重复记录，请先人工确认")
    return memos, media_references


class MemosApi:
    def __init__(self, server: str, token: str | None = None, timeout: int = 30):
        self.server = server.rstrip("/")
        self.token = token
        self.timeout = timeout

    def request(
        self,
        method: str,
        path: str,
        *,
        query: dict[str, Any] | None = None,
        body: dict[str, Any] | None = None,
        authenticated: bool = True,
    ) -> Any:
        url = f"{self.server}{path}"
        if query:
            url += "?" + urllib.parse.urlencode(query)
        headers = {"Accept": "application/json", "User-Agent": "moe-memos-flomo-import/1.0"}
        if authenticated:
            if not self.token:
                raise ValueError("此操作需要 Personal Access Token")
            headers["Authorization"] = f"Bearer {self.token}"
        data = None
        if body is not None:
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json; charset=utf-8"

        for attempt in range(4):
            request = urllib.request.Request(url, data=data, headers=headers, method=method)
            try:
                with urllib.request.urlopen(request, timeout=self.timeout) as response:
                    payload = response.read()
                    return json.loads(payload) if payload else None
            except urllib.error.HTTPError as exc:
                details = exc.read().decode("utf-8", errors="replace")
                if exc.code == 429 or 500 <= exc.code < 600:
                    if attempt < 3:
                        time.sleep(2**attempt)
                        continue
                raise RuntimeError(f"HTTP {exc.code} {method} {path}: {details}") from exc
            except urllib.error.URLError as exc:
                if attempt < 3:
                    time.sleep(2**attempt)
                    continue
                raise RuntimeError(f"网络请求失败 {method} {path}: {exc.reason}") from exc
        raise AssertionError("unreachable")

    def profile(self) -> dict[str, Any]:
        return self.request("GET", "/api/v1/instance/profile", authenticated=False)

    def current_user(self) -> dict[str, Any]:
        response = self.request("GET", "/api/v1/auth/me")
        user = response.get("user") if isinstance(response, dict) else None
        if not user or not user.get("name"):
            raise RuntimeError("PAT 有效，但服务器没有返回当前用户")
        return user

    def list_existing_keys(self, creator: str) -> set[tuple[int, str]]:
        result: set[tuple[int, str]] = set()
        page_token: str | None = None
        while True:
            query: dict[str, Any] = {
                "pageSize": 1000,
                "filter": f'creator == "{creator}"',
            }
            if page_token:
                query["pageToken"] = page_token
            response = self.request("GET", "/api/v1/memos", query=query)
            for memo in response.get("memos", []):
                content = memo.get("content", "")
                create_time = memo.get("createTime")
                if create_time:
                    digest = hashlib.sha256(content.encode("utf-8")).hexdigest()
                    result.add((timestamp_seconds(create_time), digest))
            page_token = response.get("nextPageToken")
            if not page_token:
                return result

    def create_memo(self, memo: FlomoMemo, visibility: str) -> dict[str, Any]:
        return self.request(
            "POST",
            "/api/v1/memos",
            body={
                "content": memo.content,
                "visibility": visibility,
                "createTime": memo.create_time,
            },
        )

    def get_memo(self, name: str) -> dict[str, Any]:
        return self.request("GET", f"/api/v1/{name}")

    def delete_memo(self, name: str) -> None:
        self.request("DELETE", f"/api/v1/{name}")


def source_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_state(path: Path, source_hash: str, server: str) -> dict[str, Any]:
    if not path.exists():
        return {"sourceSha256": source_hash, "server": server, "created": []}
    state = json.loads(path.read_text(encoding="utf-8"))
    if state.get("sourceSha256") != source_hash or state.get("server") != server:
        raise ValueError("状态文件与当前导出包或服务器不匹配")
    return state


def save_state(path: Path, state: dict[str, Any]) -> None:
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")
    temporary.replace(path)


def is_valid_tag_character(character: str) -> bool:
    category = unicodedata.category(character)
    return (
        category[0] in {"L", "N", "M"}
        or category == "So"
        or character in {"_", "-", "/", "&", "\u200d"}
    )


def extract_memos_tags(content: str) -> set[str]:
    tags: set[str] = set()
    for position, character in enumerate(content):
        if character != "#" or position + 1 >= len(content):
            continue
        first = content[position + 1]
        if first in {"#", " "} or not is_valid_tag_character(first):
            continue
        end = position + 1
        while end < len(content) and is_valid_tag_character(content[end]) and end - position <= 100:
            end += 1
        tags.add(content[position + 1 : end])
    return tags


def print_preview(memos: Iterable[FlomoMemo], media_references: list[str]) -> None:
    items = list(memos)
    source_count = len({memo.source_index for memo in items})
    split_sources = sorted({memo.source_index for memo in items if memo.part_count > 1})
    tags = sorted({tag for memo in items for tag in extract_memos_tags(memo.content)})
    times = [memo.create_time for memo in items]
    print(f"源记录数：{source_count}；预计创建 Memo：{len(items)}")
    if split_sources:
        print(f"超长记录拆分：{len(split_sources)} 条（源序号：{', '.join(map(str, split_sources))}）")
    print(f"时间范围：{min(times)} ～ {max(times)}")
    print(f"识别标签：{len(tags)} 个" + (f"（{', '.join(tags)}）" if tags else ""))
    print(f"媒体引用：{len(media_references)} 个")
    print("\n转换预览：")
    for memo in items[:3]:
        preview = memo.content[:300] + ("…" if len(memo.content) > 300 else "")
        print(f"\n[{memo.create_time}]\n{preview}")


def import_memos(
    api: MemosApi,
    memos: list[FlomoMemo],
    state_path: Path,
    source_hash: str,
    visibility: str,
    limit: int | None,
    delay: float,
) -> None:
    profile = api.profile()
    if profile.get("version") != "0.30.0":
        raise RuntimeError(f"迁移工具已按 v0.30.0 验证，目标实例版本为 {profile.get('version')!r}")
    user = api.current_user()
    existing = api.list_existing_keys(user["name"])
    state = load_state(state_path, source_hash, api.server)
    already_created = {
        (item["sourceIndex"], item.get("partIndex", 1))
        for item in state["created"]
    }

    all_pending = [
        memo
        for memo in sorted(memos, key=lambda item: item.create_time, reverse=True)
        if memo.state_key not in already_created and memo.duplicate_key not in existing
    ]
    pending = all_pending[:limit] if limit is not None else all_pending
    print(
        f"当前用户：{user.get('displayName') or user.get('username')} ({user['name']})；"
        f"本轮待导入：{len(pending)}；剩余总数：{len(all_pending)}；"
        f"已存在或已记录：{len(memos) - len(all_pending)}"
    )

    for position, memo in enumerate(pending, start=1):
        created = api.create_memo(memo, visibility)
        name = created.get("name")
        if not name:
            raise RuntimeError(f"第 {memo.source_index} 条创建成功响应缺少资源名")
        state["created"].append(
            {
                "sourceIndex": memo.source_index,
                "partIndex": memo.part_index,
                "partCount": memo.part_count,
                "name": name,
                "createTime": memo.create_time,
                "contentSha256": memo.content_sha256,
            }
        )
        save_state(state_path, state)
        print(f"[{position}/{len(pending)}] 已创建 {name}")
        if delay > 0:
            time.sleep(delay)

    if pending:
        sample = api.get_memo(state["created"][-1]["name"])
        last_created = state["created"][-1]
        expected = next(
            memo
            for memo in memos
            if memo.source_index == last_created["sourceIndex"]
            and memo.part_index == last_created.get("partIndex", 1)
        )
        if sample.get("content") != expected.content:
            raise RuntimeError("导入后抽样校验失败：服务端正文与源数据不一致")
    refreshed = api.list_existing_keys(user["name"])
    missing = [memo for memo in memos if memo.duplicate_key not in refreshed]
    if missing:
        raise RuntimeError(f"全量校验失败：服务端缺少 {len(missing)} 条转换后记录")
    print(f"全量校验通过：服务端已找到全部 {len(memos)} 条转换后记录。")
    print(f"完成。本工具累计创建 {len(state['created'])} 条；状态文件：{state_path}")


def rollback(api: MemosApi, state_path: Path, source_hash: str) -> None:
    state = load_state(state_path, source_hash, api.server)
    created = list(reversed(state["created"]))
    for position, item in enumerate(created, start=1):
        api.delete_memo(item["name"])
        state["created"].remove(item)
        save_state(state_path, state)
        print(f"[{position}/{len(created)}] 已删除 {item['name']}")
    print("回滚完成。")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("zip_path", type=Path)
    parser.add_argument("--server", default="http://localhost:5230")
    parser.add_argument("--utc-offset", type=int, default=8)
    parser.add_argument("--visibility", choices=["PRIVATE", "PROTECTED", "PUBLIC"], default="PRIVATE")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--limit", type=int)
    parser.add_argument("--delay", type=float, default=0.15)
    parser.add_argument("--state-file", type=Path)
    parser.add_argument("--rollback", action="store_true")
    parser.add_argument("--yes", action="store_true", help="确认执行写入或回滚")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    zip_path = args.zip_path.resolve()
    if not zip_path.is_file():
        raise SystemExit(f"找不到导出包：{zip_path}")
    memos, media_references = parse_flomo_export(zip_path, args.utc_offset)
    print_preview(memos, media_references)
    if media_references:
        raise SystemExit("导出包包含媒体引用；当前版本不会静默丢弃附件，请先扩展附件迁移后再导入。")
    if args.dry_run:
        return 0
    if not args.yes:
        raise SystemExit("这是写操作。确认预览后请添加 --yes；建议先配合 --limit 3 试导入。")

    token = os.environ.get("MEMOS_PAT") or getpass.getpass("请输入 Memos PAT（不会保存）：")
    if not token.strip():
        raise SystemExit("PAT 不能为空")
    api = MemosApi(args.server, token.strip())
    state_path = args.state_file or zip_path.with_suffix(".state.json")
    digest = source_sha256(zip_path)
    if args.rollback:
        rollback(api, state_path, digest)
    else:
        import_memos(api, memos, state_path, digest, args.visibility, args.limit, args.delay)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print("\n已取消。", file=sys.stderr)
        raise SystemExit(130)
    except Exception as exc:
        print(f"迁移失败：{exc}", file=sys.stderr)
        raise SystemExit(1)

