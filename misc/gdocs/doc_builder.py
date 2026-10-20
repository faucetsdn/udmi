#!/usr/bin/env python3
"""Google Docs API helper library and CLI for Docs v1."""

import argparse
import os
import re
import sys
import google.auth
from google.auth.transport.requests import AuthorizedSession

SCOPES = [
    "https://www.googleapis.com/auth/documents",
    "https://www.googleapis.com/auth/drive",
]

DOCS_API_BASE = "https://docs.googleapis.com/v1/documents"

INLINE_TOKEN_RE = re.compile(
    r"(\[[^\]]+\]\([^)]+\)"
    r"|`[^`]+`"
    r"|\*\*\*[^*]+\*\*\*"
    r"|\*\*[^*]+\*\*"
    r"|\*[^*\n]+\*)"
)

TABLE_SEPARATOR_RE = re.compile(
    r"^\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)+\|?$"
)


def utf16_len(text: str) -> int:
  """Returns the length of text in UTF-16 code units as used by Google Docs."""
  return len(text.encode("utf-16-le")) // 2


def b(text: str) -> tuple[str, dict]:
  """Returns a bold text segment tuple."""
  return (text, {"bold": True})


def i(text: str) -> tuple[str, dict]:
  """Returns an italic text segment tuple."""
  return (text, {"italic": True})


def c(text: str) -> tuple[str, dict]:
  """Returns an inline monospace code segment tuple."""
  return (text, {"code": True})


def link(text: str, url: str) -> tuple[str, dict]:
  """Returns a hyperlink text segment tuple."""
  return (text, {"link": url})


def get_credentials(scopes: list[str] | None = None):
  """Loads Google Application Default Credentials with quota project."""
  creds, project = google.auth.default(scopes=scopes or SCOPES)
  if not creds:
    raise RuntimeError("Failed to load Google Application Default Credentials.")
  quota_project = getattr(creds, "quota_project_id", None) or project
  if hasattr(creds, "with_quota_project") and quota_project:
    creds = creds.with_quota_project(quota_project)
  return creds


def get_authorized_session(creds=None) -> tuple[AuthorizedSession, dict]:
  """Returns an AuthorizedSession and default headers with quota project."""
  if creds is None:
    creds = get_credentials()
  session = AuthorizedSession(creds)
  headers = {}
  if getattr(creds, "quota_project_id", None):
    headers["X-Goog-User-Project"] = creds.quota_project_id
  return session, headers


class DocsClient:
  """REST client for the Google Docs v1 API backed by AuthorizedSession."""

  def __init__(self, creds=None, timeout: int = 30):
    self.session, self.headers = get_authorized_session(creds)
    self.timeout = timeout

  def create_document(self, title: str) -> dict:
    """Creates a new Google Doc with the given title."""
    resp = self.session.post(
        DOCS_API_BASE,
        headers=self.headers,
        json={"title": title},
        timeout=self.timeout,
    )
    if resp.status_code != 200:
      raise RuntimeError(
          f"Docs API create failed ({resp.status_code}): {resp.text}"
      )
    return resp.json()

  def get_document(
      self,
      doc_id: str,
      include_tabs: bool = False,
      resource_key: str | None = None,
  ) -> dict:
    """Fetches a Google Doc JSON resource by documentId."""
    headers = dict(self.headers)
    if resource_key:
      headers["X-Goog-Drive-Resource-Keys"] = f"{doc_id}/{resource_key}"
    url = f"{DOCS_API_BASE}/{doc_id}"
    if include_tabs:
      url += "?includeTabsContent=true"
    resp = self.session.get(url, headers=headers, timeout=self.timeout)
    if resp.status_code != 200:
      raise RuntimeError(
          f"Docs API get failed ({resp.status_code}): {resp.text}"
      )
    return resp.json()

  def batch_update(self, doc_id: str, requests_list: list[dict]) -> dict:
    """Executes a batchUpdate request list against doc_id."""
    url = f"{DOCS_API_BASE}/{doc_id}:batchUpdate"
    resp = self.session.post(
        url,
        headers=self.headers,
        json={"requests": requests_list},
        timeout=self.timeout,
    )
    if resp.status_code != 200:
      raise RuntimeError(
          f"Docs API batchUpdate failed ({resp.status_code}): {resp.text}"
      )
    return resp.json()


def get_docs_service(creds=None) -> DocsClient:
  """Builds and returns a DocsClient instance."""
  return DocsClient(creds=creds)


def get_doc_end_index(doc: dict) -> int:
  """Returns the final insertion index before the trailing newline of doc."""
  content = doc.get("body", {}).get("content", [])
  if not content:
    return 1
  return content[-1]["endIndex"] - 1


def parse_inline_markdown(text: str) -> list[tuple[str, dict]]:
  """Parses inline Markdown formatting into (text, style_dict) segments."""
  segments = []
  last_end = 0
  for match in INLINE_TOKEN_RE.finditer(text):
    start, end = match.span()
    if start > last_end:
      segments.append((text[last_end:start], {}))
    token = match.group(0)
    if token.startswith("[") and "](" in token and token.endswith(")"):
      bracket_end = token.index("](")
      label = token[1:bracket_end]
      url = token[bracket_end + 2 : -1]
      if label.startswith("`") and label.endswith("`") and len(label) > 2:
        segments.append((label[1:-1], {"code": True, "link": url}))
      else:
        segments.append((label, {"link": url}))
    elif token.startswith("`") and token.endswith("`"):
      segments.append((token[1:-1], {"code": True}))
    elif token.startswith("***") and token.endswith("***"):
      segments.append((token[3:-3], {"bold": True, "italic": True}))
    elif token.startswith("**") and token.endswith("**"):
      inner = token[2:-2]
      segments.extend(_apply_style_to_inner(inner, {"bold": True}))
    elif token.startswith("*") and token.endswith("*"):
      inner = token[1:-1]
      segments.extend(_apply_style_to_inner(inner, {"italic": True}))
    last_end = end
  if last_end < len(text):
    segments.append((text[last_end:], {}))
  return segments


def _apply_style_to_inner(
    inner_text: str, base_style: dict
) -> list[tuple[str, dict]]:
  """Parses nested inline code inside bold or italic spans."""
  sub_segments = []
  code_re = re.compile(r"(`[^`]+`)")
  last_idx = 0
  for match in code_re.finditer(inner_text):
    start, end = match.span()
    if start > last_idx:
      sub_segments.append((inner_text[last_idx:start], dict(base_style)))
    merged = dict(base_style)
    merged["code"] = True
    sub_segments.append((match.group(0)[1:-1], merged))
    last_idx = end
  if last_idx < len(inner_text):
    sub_segments.append((inner_text[last_idx:], dict(base_style)))
  return sub_segments


def _build_text_style_request(
    start_index: int, end_index: int, style: dict
) -> dict | None:
  """Constructs an updateTextStyle request for a styled inline segment."""
  text_style = {}
  fields = []
  if style.get("bold"):
    text_style["bold"] = True
    fields.append("bold")
  if style.get("italic"):
    text_style["italic"] = True
    fields.append("italic")
  if style.get("code"):
    text_style["weightedFontFamily"] = {"fontFamily": "Roboto Mono"}
    text_style["fontSize"] = {"magnitude": 9.5, "unit": "PT"}
    text_style["backgroundColor"] = {
        "color": {"rgbColor": {"red": 0.94, "green": 0.94, "blue": 0.94}}
    }
    fields.extend(["weightedFontFamily", "fontSize", "backgroundColor"])
  if style.get("link"):
    text_style["link"] = {"url": style["link"]}
    fields.append("link")
  if not fields:
    return None
  return {
      "updateTextStyle": {
          "range": {"startIndex": start_index, "endIndex": end_index},
          "textStyle": text_style,
          "fields": ",".join(fields),
      }
  }


class DocBuilder:
  """Accumulates text, formatting, and table requests for Google Docs API."""

  def __init__(self, start_index: int = 1):
    self.idx = start_index
    self.requests = []
    self.bullet_ranges = []
    self._ops = []

  def add_heading(self, text: str, level: int = 1):
    """Appends a heading paragraph (HEADING_1 through HEADING_6)."""
    if not 1 <= level <= 6:
      raise ValueError(f"Heading level must be between 1 and 6, got {level}.")
    segments = parse_inline_markdown(text) if isinstance(text, str) else text
    self._ops.append(("heading", segments, level))
    self.emit_heading(segments, level)

  def emit_heading(self, segments, level: int):
    """Emits Docs API requests for a heading at self.idx."""
    normalized = self._normalize_segments(segments)
    plain_text = "".join(seg_text for seg_text, _ in normalized)
    full_text = plain_text + "\n"
    start = self.idx
    end = start + utf16_len(full_text)
    self.requests.append(
        {"insertText": {"location": {"index": start}, "text": full_text}}
    )
    self.requests.append({
        "updateParagraphStyle": {
            "range": {"startIndex": start, "endIndex": end},
            "paragraphStyle": {"namedStyleType": f"HEADING_{level}"},
            "fields": "namedStyleType",
        }
    })
    offset = start
    for seg_text, style in normalized:
      seg_len = utf16_len(seg_text)
      if seg_len > 0 and style:
        req = _build_text_style_request(offset, offset + seg_len, style)
        if req:
          self.requests.append(req)
      offset += seg_len
    self.idx = end

  def add_paragraph(
      self, segments, bullet: bool = False, indent: bool = False
  ):
    """Appends a rich-text paragraph with optional bullet or indent."""
    norm_segments = self._normalize_segments(segments)
    self._ops.append(("paragraph", norm_segments, bullet, indent))
    self.emit_paragraph(norm_segments, bullet=bullet, indent=indent)

  def emit_paragraph(
      self, norm_segments: list[tuple[str, dict]], bullet: bool, indent: bool
  ):
    """Emits Docs API requests for a paragraph at self.idx."""
    plain_text = "".join(seg_text for seg_text, _ in norm_segments)
    full_text = plain_text + "\n"
    p_start = self.idx
    p_end = p_start + utf16_len(full_text)
    text_end = p_end - 1

    self.requests.append(
        {"insertText": {"location": {"index": p_start}, "text": full_text}}
    )

    para_style = {"namedStyleType": "NORMAL_TEXT"}
    style_fields = ["namedStyleType"]
    if indent:
      para_style["indentStart"] = {"magnitude": 18, "unit": "PT"}
      para_style["indentFirstLine"] = {"magnitude": 18, "unit": "PT"}
      style_fields.extend(["indentStart", "indentFirstLine"])

    self.requests.append({
        "updateParagraphStyle": {
            "range": {"startIndex": p_start, "endIndex": p_end},
            "paragraphStyle": para_style,
            "fields": ",".join(style_fields),
        }
    })

    if text_end > p_start:
      self.requests.append({
          "updateTextStyle": {
              "range": {"startIndex": p_start, "endIndex": text_end},
              "textStyle": {
                  "bold": False,
                  "italic": False,
                  "underline": False,
              },
              "fields": "bold,italic,underline",
          }
      })

    offset = p_start
    for seg_text, style in norm_segments:
      seg_len = utf16_len(seg_text)
      if seg_len > 0 and style:
        req = _build_text_style_request(offset, offset + seg_len, style)
        if req:
          self.requests.append(req)
      offset += seg_len

    if bullet:
      self.bullet_ranges.append((p_start, p_end))
    self.idx = p_end

  def add_code_block(self, code_text: str):
    """Appends an indented, gray-shaded monospace code block."""
    self._ops.append(("code_block", code_text))
    self.emit_code_block(code_text)

  def emit_code_block(self, code_text: str):
    """Emits Docs API requests for a code block at self.idx."""
    start = self.idx
    full_text = code_text.strip("\n") + "\n"
    end = start + utf16_len(full_text)
    self.requests.append(
        {"insertText": {"location": {"index": start}, "text": full_text}}
    )
    self.requests.append({
        "updateParagraphStyle": {
            "range": {"startIndex": start, "endIndex": end},
            "paragraphStyle": {
                "namedStyleType": "NORMAL_TEXT",
                "indentStart": {"magnitude": 18, "unit": "PT"},
                "indentFirstLine": {"magnitude": 18, "unit": "PT"},
                "shading": {
                    "backgroundColor": {
                        "color": {
                            "rgbColor": {
                                "red": 0.95,
                                "green": 0.95,
                                "blue": 0.95,
                            }
                        }
                    }
                },
            },
            "fields": "namedStyleType,indentStart,indentFirstLine,shading",
        }
    })
    if end - 1 > start:
      self.requests.append({
          "updateTextStyle": {
              "range": {"startIndex": start, "endIndex": end - 1},
              "textStyle": {
                  "bold": False,
                  "italic": False,
                  "weightedFontFamily": {"fontFamily": "Roboto Mono"},
                  "fontSize": {"magnitude": 9, "unit": "PT"},
              },
              "fields": "bold,italic,weightedFontFamily,fontSize",
          }
      })
    self.idx = end

  def add_table(
      self,
      rows_data: list[list],
      header_bold: bool = True,
      first_col_bold: bool = False,
  ):
    """Queues a table to be inserted and populated when apply() is executed."""
    if not rows_data or not rows_data[0]:
      raise ValueError("Table rows_data must be a non-empty 2D list.")
    self._ops.append(("table", rows_data, header_bold, first_col_bold))

  def add_markdown(self, md_text: str, skip_top_h1: bool = False):
    """Parses a Markdown document and appends its structure to this builder."""
    lines = md_text.splitlines()
    idx = 0
    num_lines = len(lines)
    skipped_h1 = False

    while idx < num_lines:
      line = lines[idx]
      stripped = line.strip()

      if not stripped:
        idx += 1
        continue

      # Fenced code block
      if stripped.startswith("```"):
        idx += 1
        code_lines = []
        while idx < num_lines and not lines[idx].strip().startswith("```"):
          code_lines.append(lines[idx])
          idx += 1
        if idx < num_lines:
          idx += 1
        self.add_code_block("\n".join(code_lines))
        continue

      # ATX Heading (# through ######)
      heading_match = re.match(r"^(#{1,6})\s+(.*)$", stripped)
      if heading_match:
        level = len(heading_match.group(1))
        heading_text = heading_match.group(2).strip()
        if level == 1 and skip_top_h1 and not skipped_h1:
          skipped_h1 = True
          idx += 1
          continue
        self.add_heading(parse_inline_markdown(heading_text), level=level)
        idx += 1
        continue

      # Markdown Table
      if (
          "|" in stripped
          and idx + 1 < num_lines
          and TABLE_SEPARATOR_RE.match(lines[idx + 1].strip())
      ):
        table_rows = [self._parse_table_row(stripped)]
        idx += 2  # Skip header and separator rows
        while idx < num_lines:
          row_line = lines[idx].strip()
          if not row_line or "|" not in row_line:
            break
          table_rows.append(self._parse_table_row(row_line))
          idx += 1
        col_count = len(table_rows[0])
        normalized_rows = []
        for row in table_rows:
          if len(row) < col_count:
            row = row + [[]] * (col_count - len(row))
          elif len(row) > col_count:
            row = row[:col_count]
          normalized_rows.append(row)
        self.add_table(normalized_rows, header_bold=True, first_col_bold=False)
        continue

      # Blockquote (> ...)
      if stripped.startswith(">"):
        quote_lines = []
        while idx < num_lines and lines[idx].strip().startswith(">"):
          q_line = re.sub(r"^>\s?", "", lines[idx].strip())
          if q_line:
            quote_lines.append(q_line)
          idx += 1
        quote_text = " ".join(quote_lines)
        self.add_paragraph(parse_inline_markdown(quote_text), indent=True)
        continue

      # Bullet list item (- or *)
      bullet_match = re.match(r"^[-*]\s+(.*)$", stripped)
      if bullet_match:
        item_lines = [bullet_match.group(1).strip()]
        idx += 1
        while idx < num_lines:
          next_line = lines[idx]
          next_stripped = next_line.strip()
          if (
              not next_stripped
              or next_stripped.startswith(("#", "```", ">", "- ", "* "))
              or re.match(r"^\d+\.\s+", next_stripped)
              or (
                  "|" in next_stripped
                  and idx + 1 < num_lines
                  and TABLE_SEPARATOR_RE.match(lines[idx + 1].strip())
              )
          ):
            break
          if next_line.startswith("  "):
            item_lines.append(next_stripped)
            idx += 1
          else:
            break
        self.add_paragraph(
            parse_inline_markdown(" ".join(item_lines)), bullet=True
        )
        continue

      # Regular paragraph (accumulate wrapped lines)
      para_lines = [stripped]
      idx += 1
      while idx < num_lines:
        next_stripped = lines[idx].strip()
        if (
            not next_stripped
            or next_stripped.startswith(("#", "```", ">", "- ", "* "))
            or (
                "|" in next_stripped
                and idx + 1 < num_lines
                and TABLE_SEPARATOR_RE.match(lines[idx + 1].strip())
            )
        ):
          break
        para_lines.append(next_stripped)
        idx += 1
      self.add_paragraph(parse_inline_markdown(" ".join(para_lines)))

  def _parse_table_row(self, row_line: str) -> list[list[tuple[str, dict]]]:
    """Splits a Markdown table row into parsed inline segment lists per cell."""
    trimmed = row_line.strip()
    if trimmed.startswith("|"):
      trimmed = trimmed[1:]
    if trimmed.endswith("|"):
      trimmed = trimmed[:-1]
    cells = [cell.strip() for cell in trimmed.split("|")]
    return [parse_inline_markdown(cell) for cell in cells]

  def _normalize_segments(self, segments) -> list[tuple[str, dict]]:
    """Normalizes a str or list of str/(str, dict) into (str, dict) tuples."""
    if isinstance(segments, str):
      return [(segments, {})]
    normalized = []
    for seg in segments:
      if isinstance(seg, str):
        if seg:
          normalized.append((seg, {}))
      else:
        text, style = seg
        if text:
          normalized.append((text, dict(style) if style else {}))
    return normalized

  def finalize_requests(self) -> list[dict]:
    """Finalizes and returns accumulated batchUpdate requests."""
    reqs = list(self.requests)
    for start, end in self.bullet_ranges:
      reqs.append({
          "createParagraphBullets": {
              "range": {"startIndex": start, "endIndex": end},
              "bulletPreset": "BULLET_DISC_CIRCLE_SQUARE",
          }
      })
    return reqs

  def apply(self, client: DocsClient, doc_id: str, clear_existing: bool = True):
    """Applies all queued operations (including tables) to doc_id."""
    if clear_existing:
      existing = client.get_document(doc_id)
      existing_end = get_doc_end_index(existing)
      if existing_end > 1:
        client.batch_update(
            doc_id,
            [{
                "deleteContentRange": {
                    "range": {"startIndex": 1, "endIndex": existing_end}
                }
            }],
        )

    stage_ops = []
    for op in self._ops:
      if op[0] == "table":
        _, rows_data, header_bold, first_col_bold = op
        self._flush_stage_and_table(
            client,
            doc_id,
            stage_ops,
            rows_data=rows_data,
            header_bold=header_bold,
            first_col_bold=first_col_bold,
        )
        stage_ops = []
      else:
        stage_ops.append(op)

    if stage_ops:
      self._flush_stage_and_table(client, doc_id, stage_ops)

  def _flush_stage_and_table(
      self,
      client: DocsClient,
      doc_id: str,
      stage_ops: list,
      *,
      rows_data: list[list] | None = None,
      header_bold: bool = True,
      first_col_bold: bool = False,
  ):
    """Executes a stage of text operations followed by an optional table."""
    doc_state = client.get_document(doc_id)
    start_idx = get_doc_end_index(doc_state)
    stage_builder = DocBuilder(start_index=start_idx)

    for op in stage_ops:
      kind = op[0]
      if kind == "heading":
        stage_builder.emit_heading(op[1], op[2])
      elif kind == "paragraph":
        stage_builder.emit_paragraph(op[1], bullet=op[2], indent=op[3])
      elif kind == "code_block":
        stage_builder.emit_code_block(op[1])

    reqs = stage_builder.finalize_requests()
    table_insert_idx = stage_builder.idx
    if rows_data is not None:
      reqs.append({
          "insertTable": {
              "location": {"index": table_insert_idx},
              "rows": len(rows_data),
              "columns": len(rows_data[0]),
          }
      })

    if reqs:
      client.batch_update(doc_id, reqs)

    if rows_data is not None:
      self._populate_inserted_table(
          client,
          doc_id,
          min_start_index=table_insert_idx,
          rows_data=rows_data,
          header_bold=header_bold,
          first_col_bold=first_col_bold,
      )

  def _populate_inserted_table(
      self,
      client: DocsClient,
      doc_id: str,
      *,
      min_start_index: int,
      rows_data: list[list],
      header_bold: bool,
      first_col_bold: bool,
  ):
    """Populates the newly inserted table cells in reverse order."""
    doc_state = client.get_document(doc_id)
    table_elem = None
    for el in doc_state.get("body", {}).get("content", []):
      if "table" in el and el.get("startIndex", 0) >= min_start_index:
        table_elem = el["table"]
        break
    if table_elem is None:
      for el in reversed(doc_state.get("body", {}).get("content", [])):
        if "table" in el:
          table_elem = el["table"]
          break
    if table_elem is None:
      raise RuntimeError("Inserted table was not found in document body.")

    table_reqs = []
    for r_idx in range(len(rows_data) - 1, -1, -1):
      for c_idx in range(len(rows_data[r_idx]) - 1, -1, -1):
        cell = table_elem["tableRows"][r_idx]["tableCells"][c_idx]
        cell_start = cell["content"][0]["startIndex"]
        cell_segments = self._normalize_segments(rows_data[r_idx][c_idx])
        cell_plain = "".join(seg_text for seg_text, _ in cell_segments)
        if not cell_plain:
          continue

        table_reqs.append({
            "insertText": {
                "location": {"index": cell_start},
                "text": cell_plain,
            }
        })

        force_bold = (r_idx == 0 and header_bold) or (
            c_idx == 0 and first_col_bold
        )
        offset = cell_start
        for seg_text, style in cell_segments:
          seg_len = utf16_len(seg_text)
          merged_style = dict(style)
          if force_bold:
            merged_style["bold"] = True
          if seg_len > 0 and merged_style:
            req = _build_text_style_request(
                offset, offset + seg_len, merged_style
            )
            if req:
              table_reqs.append(req)
          offset += seg_len

    if table_reqs:
      client.batch_update(doc_id, table_reqs)


def extract_structural_elements(elements: list[dict]) -> list[str]:
  """Converts Google Docs structural elements into Markdown lines."""
  text_parts = []
  for elem in elements:
    if "paragraph" in elem:
      para = elem["paragraph"]
      style = para.get("paragraphStyle", {}).get(
          "namedStyleType", "NORMAL_TEXT"
      )
      prefix = ""
      if style == "TITLE":
        prefix = "# "
      elif style == "SUBTITLE":
        prefix = "## "
      elif style.startswith("HEADING_"):
        level = int(style.split("_")[1])
        prefix = "#" * level + " "
      elif "bullet" in para:
        nesting = para["bullet"].get("nestingLevel", 0)
        prefix = ("  " * nesting) + "- "
      line = []
      for pe in para.get("elements", []):
        if "textRun" in pe:
          line.append(pe["textRun"].get("content", ""))
        elif "richLink" in pe:
          rl = pe["richLink"].get("richLinkProperties", {})
          rl_title = rl.get("title", "")
          rl_uri = rl.get("uri", "")
          line.append(f"[{rl_title}]({rl_uri})")
      text_parts.append(prefix + "".join(line))
    elif "table" in elem:
      table = elem["table"]
      rows = table.get("tableRows", [])
      for r_idx, row in enumerate(rows):
        cells = []
        for cell in row.get("tableCells", []):
          cell_text = (
              "".join(extract_structural_elements(cell.get("content", [])))
              .strip()
              .replace("\n", " ")
          )
          cells.append(cell_text)
        text_parts.append("| " + " | ".join(cells) + " |\n")
        if r_idx == 0 and cells:
          text_parts.append("| " + " | ".join(["---"] * len(cells)) + " |\n")
      text_parts.append("\n")
    elif "tableOfContents" in elem:
      toc = elem["tableOfContents"]
      text_parts.extend(extract_structural_elements(toc.get("content", [])))
  return text_parts


def _process_tab(tab: dict, out_lines: list[str]):
  """Recursively extracts Markdown content from a document tab."""
  props = tab.get("tabProperties", {})
  title = props.get("title", "Untitled Tab")
  tab_id = props.get("tabId", "")
  out_lines.append(f"\n<!-- TAB: {title} (id={tab_id}) -->\n\n")
  doc_tab = tab.get("documentTab", {})
  body = doc_tab.get("body", {}).get("content", [])
  out_lines.extend(extract_structural_elements(body))
  for child in tab.get("childTabs", []):
    _process_tab(child, out_lines)


def fetch_document_markdown(
    doc_id: str, resource_key: str | None = None
) -> str:
  """Fetches a Google Doc via the Docs v1 API and converts it to Markdown."""
  if not doc_id:
    raise ValueError("doc_id is required.")
  client = DocsClient(
      creds=get_credentials([
          "https://www.googleapis.com/auth/documents.readonly",
          "https://www.googleapis.com/auth/drive.readonly",
      ])
  )
  data = client.get_document(
      doc_id, include_tabs=True, resource_key=resource_key
  )
  doc_title = data.get("title", "")
  out_lines = [f"# {doc_title}\n"]
  if data.get("tabs"):
    for tab in data["tabs"]:
      _process_tab(tab, out_lines)
  elif "body" in data:
    out_lines.extend(
        extract_structural_elements(data["body"].get("content", []))
    )
  return "".join(out_lines)


def create_document_from_markdown(title: str, markdown_path: str) -> dict:
  """Creates a new Google Doc and populates it from a Markdown file."""
  if not title:
    raise ValueError("Document title must be non-empty.")
  if not os.path.isfile(markdown_path):
    raise FileNotFoundError(f"Markdown file not found: {markdown_path}")

  with open(markdown_path, "r", encoding="utf-8") as f:
    md_text = f.read()

  client = get_docs_service()
  created = client.create_document(title)
  doc_id = created["documentId"]

  builder = DocBuilder()
  builder.add_markdown(md_text)
  builder.apply(client, doc_id, clear_existing=False)

  doc_url = f"https://docs.google.com/document/d/{doc_id}/edit"
  return {"documentId": doc_id, "title": title, "url": doc_url}


def update_document_from_markdown(doc_id: str, markdown_path: str) -> dict:
  """Replaces the body of an existing Google Doc from a Markdown file."""
  if not doc_id:
    raise ValueError("doc_id must be non-empty.")
  if not os.path.isfile(markdown_path):
    raise FileNotFoundError(f"Markdown file not found: {markdown_path}")

  with open(markdown_path, "r", encoding="utf-8") as f:
    md_text = f.read()

  client = get_docs_service()
  builder = DocBuilder()
  builder.add_markdown(md_text)
  builder.apply(client, doc_id, clear_existing=True)

  doc_url = f"https://docs.google.com/document/d/{doc_id}/edit"
  return {"documentId": doc_id, "url": doc_url}


def main():
  parser = argparse.ArgumentParser(
      description="Create, update, or fetch Google Docs via the Docs v1 API."
  )
  subparsers = parser.add_subparsers(dest="command", required=True)

  create_parser = subparsers.add_parser(
      "create", help="Create a new Google Doc from a Markdown file."
  )
  create_parser.add_argument(
      "--title", required=True, help="Title of the new Google Doc."
  )
  create_parser.add_argument(
      "--markdown", required=True, help="Path to the source Markdown file."
  )

  update_parser = subparsers.add_parser(
      "update", help="Replace an existing Google Doc from a Markdown file."
  )
  update_parser.add_argument(
      "--doc-id", required=True, help="Target Google Doc documentId."
  )
  update_parser.add_argument(
      "--markdown", required=True, help="Path to the source Markdown file."
  )

  fetch_parser = subparsers.add_parser(
      "fetch", help="Fetch a Google Doc as Markdown."
  )
  fetch_parser.add_argument(
      "--doc-id", required=True, help="Target Google Doc documentId."
  )
  fetch_parser.add_argument(
      "--resource-key", default=None, help="Optional Drive resource key."
  )
  fetch_parser.add_argument(
      "--output", default=None, help="Optional output file path for Markdown."
  )

  args = parser.parse_args()

  if args.command == "create":
    info = create_document_from_markdown(args.title, args.markdown)
    doc_id = info["documentId"]
    doc_url = info["url"]
    print(f"documentId: {doc_id}")
    print(f"url: {doc_url}")
  elif args.command == "update":
    info = update_document_from_markdown(args.doc_id, args.markdown)
    doc_id = info["documentId"]
    doc_url = info["url"]
    print(f"documentId: {doc_id}")
    print(f"url: {doc_url}")
  elif args.command == "fetch":
    md_out = fetch_document_markdown(args.doc_id, args.resource_key)
    if args.output:
      with open(args.output, "w", encoding="utf-8") as f:
        f.write(md_out)
      print(f"Saved Markdown to {args.output}")
    else:
      sys.stdout.write(md_out)


if __name__ == "__main__":
  main()
