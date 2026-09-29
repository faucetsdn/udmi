/**
 * Inline markdown for Mantis answers, with repository doc citations as links.
 *
 * Pure string functions with no DOM access, so they can be exercised directly
 * under Node. Every character of model output is HTML-escaped before it reaches
 * innerHTML; the only markup emitted is <code>, <strong>, <em>, and <a> elements
 * whose href is built here from a validated path, never copied from the input.
 *
 * Link target: GET /api/repo-doc (workbench/server/repo_docs.py), which serves
 * docs/*.md and schema/*.json from the checkout under test as text/plain. The
 * local checkout is what Mantis read, so the link shows the exact text it cited.
 */

const SEGMENT = '[A-Za-z0-9_.-]+';
const DOC_PATH = `(?:docs/(?:${SEGMENT}/)*${SEGMENT}\\.md|schema/(?:${SEGMENT}/)*${SEGMENT}\\.json)`;
const DOC_EXACT_RE = new RegExp(`^(${DOC_PATH})(#[A-Za-z0-9_-]*)?$`);
// A bare path must not be the tail of a longer path or word (for example an
// absolute path, or `mydocs/x.md`), hence the guards on both sides.
const DOC_BARE_RE = new RegExp(`(^|[^A-Za-z0-9_/.-])(${DOC_PATH})(#[A-Za-z0-9_-]*)?(?=$|[^A-Za-z0-9_/-])`, 'g');

export const REPO_DOC_ENDPOINT = '/api/repo-doc';

export function escapeHtml(str) {
  if (str === null || str === undefined) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

/**
 * The endpoint URL for a repository-relative doc path, or null when the text
 * is not exactly one allowed doc path. A `#fragment` is accepted and dropped:
 * the endpoint serves plain text, which has no anchors to scroll to.
 */
export function docHref(text) {
  const match = DOC_EXACT_RE.exec(String(text ?? '').trim());
  if (!match) return null;
  const segments = match[1].split('/');
  if (segments.some((segment) => segment === '.' || segment === '..')) return null;
  return `${REPO_DOC_ENDPOINT}?path=${encodeURIComponent(match[1])}`;
}

function docAnchor(href, innerHtml) {
  return `<a class="mantis-doc-link" href="${escapeHtml(href)}" target="_blank" ` +
    `rel="noopener noreferrer">${innerHtml}</a>`;
}

/**
 * Renders one line of inline markdown to safe HTML: `code`, **bold**, *em*,
 * [text](docs/...) links, and bare docs/... or schema/... paths as links.
 * Markdown links to anything other than an allowed doc path stay literal text.
 */
export function formatInlineMarkdown(str) {
  if (!str) return '';
  const held = [];
  const hold = (html) => `\u0000${held.push(html) - 1}\u0000`;
  // NUL delimits held fragments, so it cannot be allowed through from input.
  let text = String(str).replace(/\u0000/g, '');

  text = text.replace(/\[([^\]\n]+)\]\(([^)\s]+)\)/g, (whole, label, target) => {
    const href = docHref(target);
    if (!href) return whole;
    const code = /^`([^`]+)`$/.exec(label);
    const inner = code ? `<code>${escapeHtml(code[1])}</code>` : escapeHtml(label);
    return hold(docAnchor(href, inner));
  });

  text = text.replace(/`([^`]+)`/g, (_, code) => {
    const html = `<code>${escapeHtml(code)}</code>`;
    const href = docHref(code);
    return hold(href ? docAnchor(href, html) : html);
  });

  text = escapeHtml(text);
  text = text.replace(DOC_BARE_RE, (whole, lead, path, fragment) => {
    const href = docHref(path + (fragment || ''));
    if (!href) return whole;
    return lead + hold(docAnchor(href, escapeHtml(path + (fragment || ''))));
  });
  text = text.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  text = text.replace(/\*([^*]+)\*/g, '<em>$1</em>');
  return text.replace(/\u0000(\d+)\u0000/g, (_, idx) => held[Number(idx)] ?? '');
}
