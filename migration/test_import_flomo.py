import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

from bs4 import BeautifulSoup

from import_flomo import (
    FlomoMemo,
    MemosApi,
    content_to_markdown,
    extract_memos_tags,
    parse_flomo_export,
    split_content,
)


class FlomoImporterTest(unittest.TestCase):
    def test_converts_paragraphs_emphasis_breaks_and_nested_lists(self):
        node = BeautifulSoup(
            """
            <div class="content">
              <p>#想法 第一行<br>第二行 <strong>重点</strong></p>
              <ol><li><p>一级</p><ul><li><p>二级</p></li></ul></li></ol>
            </div>
            """,
            "html.parser",
        ).select_one(".content")

        self.assertEqual(
            content_to_markdown(node),
            "#想法 第一行\n第二行 **重点**\n\n1. 一级\n  - 二级",
        )

    def test_parses_export_time_and_removes_invisible_characters(self):
        html = """
        <html><body><div class="memos">
          <div class="memo">
            <div class="time">2026-08-20 18:35:52</div>
            <div class="content"><p>#想法 测\u2061试</p></div>
            <div class="files"></div>
          </div>
        </div></body></html>
        """
        with tempfile.TemporaryDirectory() as directory:
            archive_path = Path(directory) / "flomo.zip"
            with zipfile.ZipFile(archive_path, "w") as archive:
                archive.writestr("export/index.html", html)
            memos, media = parse_flomo_export(archive_path)

        self.assertEqual(len(memos), 1)
        self.assertEqual(memos[0].create_time, "2026-08-20T18:35:52+08:00")
        self.assertEqual(memos[0].content, "#想法 测试")
        self.assertEqual(media, [])

    def test_separates_invalid_nested_paragraph_after_tag(self):
        node = BeautifulSoup(
            '<div class="content"><p>#提示词<p>请你扮演助手</p></p></div>',
            "html.parser",
        ).select_one(".content")

        content = content_to_markdown(node)

        self.assertEqual(content, "#提示词\n\n请你扮演助手")
        self.assertEqual(extract_memos_tags(content), {"提示词"})

    def test_extracts_tag_inside_markdown_emphasis(self):
        self.assertEqual(extract_memos_tags("**#书摘**\n\n正文"), {"书摘"})

    def test_splits_oversized_chinese_content_without_losing_text(self):
        content = "\n\n".join(["#提示词", "中文段落。" * 1700, "结尾"])

        parts = split_content(content)

        self.assertGreater(len(parts), 1)
        self.assertEqual("".join(parts), content)
        self.assertTrue(all(len(part.encode("utf-8")) <= 7600 for part in parts))

    def test_parse_marks_split_parts_and_keeps_each_below_server_limit(self):
        html = f"""
        <div class="memo">
          <div class="time">2026-08-20 18:35:52</div>
          <div class="content"><p>#提示词</p><p>{'中文段落。' * 1700}</p></div>
        </div>
        """
        with tempfile.TemporaryDirectory() as directory:
            archive_path = Path(directory) / "flomo.zip"
            with zipfile.ZipFile(archive_path, "w") as archive:
                archive.writestr("index.html", html)
            memos, _ = parse_flomo_export(archive_path)

        self.assertGreater(len(memos), 1)
        self.assertEqual([memo.part_index for memo in memos], list(range(1, len(memos) + 1)))
        self.assertTrue(all(memo.part_count == len(memos) for memo in memos))
        self.assertTrue(all(memo.content.startswith("> flomo 原记录分段 ") for memo in memos))
        self.assertTrue(all(len(memo.content.encode("utf-8")) <= 8192 for memo in memos))
        self.assertEqual(memos[0].create_time, "2026-08-20T18:35:52+08:00")
        self.assertEqual(memos[1].create_time, "2026-08-20T18:35:51+08:00")


    def test_create_memo_uses_v030_direct_memo_body(self):
        memo = FlomoMemo(
            source_index=1,
            create_time="2026-08-20T18:35:52+08:00",
            content="#想法 测试",
        )
        api = MemosApi("https://memos.example", "secret")

        with patch.object(api, "request", return_value={"name": "memos/test"}) as request:
            result = api.create_memo(memo, "PRIVATE")

        self.assertEqual(result["name"], "memos/test")
        request.assert_called_once_with(
            "POST",
            "/api/v1/memos",
            body={
                "content": "#想法 测试",
                "visibility": "PRIVATE",
                "createTime": "2026-08-20T18:35:52+08:00",
            },
        )



    def test_reports_media_instead_of_silently_dropping_it(self):
        html = """
        <div class="memo">
          <div class="time">2026-08-20 18:35:52</div>
          <div class="content"><p>带图</p></div>
          <div class="files"><img src="https://example.com/a.png"></div>
        </div>
        """
        with tempfile.TemporaryDirectory() as directory:
            archive_path = Path(directory) / "flomo.zip"
            with zipfile.ZipFile(archive_path, "w") as archive:
                archive.writestr("index.html", html)
            _, media = parse_flomo_export(archive_path)

        self.assertEqual(media, ["https://example.com/a.png"])


if __name__ == "__main__":
    unittest.main()

