/**
 * Export helpers for the Mantis panel: clipboard writes, copy buttons,
 * reasoning/transcript serialisation, and Mermaid diagram rasterisation.
 *
 * Every helper fails loudly. A clipboard that is unavailable (non-secure
 * context, unsupported browser) or a write that the browser rejects surfaces
 * as a thrown Error with an actionable message; callers route it to the
 * panel's error box. Nothing here swallows a failure.
 */

const FEEDBACK_MS = 1500;
const PNG_SCALE = 2;

// ------------------------------------------------------------ clipboard ---

function requireSecureContext() {
  if (!window.isSecureContext) {
    throw new Error(
      'Clipboard API unavailable: this page is not a secure context ' +
      '(open the Workbench via https:// or http://localhost).'
    );
  }
}

/** Writes plain text to the clipboard or throws an explicit Error. */
export async function copyText(text) {
  requireSecureContext();
  if (!navigator.clipboard?.writeText) {
    throw new Error('Clipboard API unavailable: this browser does not expose navigator.clipboard.writeText.');
  }
  try {
    await navigator.clipboard.writeText(text);
  } catch (cause) {
    throw new Error(`Clipboard write rejected by the browser: ${cause.message || cause}`);
  }
}

/**
 * Builds an icon-only copy button. `getText` is called at click time so a
 * streaming bubble copies what it holds at that moment. Failures are handed to
 * `onError` verbatim.
 */
export function makeCopyButton({ label, getText, onError, className = '' }) {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = `btn-icon mantis-copy-btn ${className}`.trim();
  button.setAttribute('aria-label', label);
  button.title = label;
  button.appendChild(icon('content_copy'));
  button.addEventListener('click', async (event) => {
    // Inside a <summary> the click would otherwise toggle the disclosure.
    event.preventDefault();
    event.stopPropagation();
    try {
      await copyText(getText());
      flashSuccess(button);
    } catch (cause) {
      onError(cause.message);
    }
  });
  return button;
}

/** Swaps the button's first icon to a check mark for FEEDBACK_MS. */
export function flashSuccess(button) {
  const glyph = button.querySelector('.material-symbols-outlined');
  if (!glyph) {
    throw new Error('flashSuccess: button has no material-symbols icon to swap.');
  }
  if (!button.dataset.icon) button.dataset.icon = glyph.textContent;
  glyph.textContent = 'check';
  button.classList.add('is-copied');
  window.clearTimeout(button._copyTimer);
  button._copyTimer = window.setTimeout(() => {
    glyph.textContent = button.dataset.icon;
    button.classList.remove('is-copied');
  }, FEEDBACK_MS);
}

export function icon(name) {
  const span = document.createElement('span');
  span.className = 'material-symbols-outlined';
  span.setAttribute('aria-hidden', 'true');
  span.textContent = name;
  return span;
}

// ------------------------------------------------------- transcript text ---

function fencedJson(value) {
  return '```json\n' + JSON.stringify(value ?? {}, null, 2) + '\n```';
}

/** Serialises one turn's recorded reasoning items in arrival order. */
export function reasoningText(items) {
  const out = [];
  for (const item of items) {
    if (item.type === 'phase') {
      out.push(`### Phase: ${item.text}`);
    } else if (item.type === 'tool_call') {
      out.push(`**Tool call:** \`${item.tool}()\`\n${fencedJson(item.args)}`);
    } else if (item.type === 'tool_result') {
      out.push(`**Tool result:** \`${item.tool}\` → ${item.summary}\n${fencedJson(item.output)}`);
    } else if (item.type === 'thought') {
      out.push(item.text.trim());
    } else {
      throw new Error(`reasoningText: unknown reasoning item type '${item.type}'.`);
    }
  }
  return out.join('\n\n');
}

function tableCell(value) {
  return String(value ?? '').replace(/\|/g, '\\|').replace(/\r?\n/g, ' ');
}

function matrixText(data) {
  const caption = data.final === true
    ? 'Hypothesis Resolution Audit — final verdicts'
    : 'Hypotheses under investigation — no verdicts reached yet';
  const rows = [
    `## ${caption}`,
    '',
    '| Hypothesis | Verdict | Rationale | Evidence |',
    '| --- | --- | --- | --- |',
  ];
  for (const h of data.hypotheses || []) {
    rows.push(`| ${tableCell(h.hypothesis || 'Hypothesis text missing from the backend payload.')} ` +
      `| ${tableCell(h.verdict || 'NO VERDICT')} | ${tableCell(h.rationale || '—')} ` +
      `| ${tableCell(h.evidence_tier || 'not reported')} |`);
  }
  // The audit section was split off the answer by the adapter; the transcript
  // keeps it so a copied report still carries its record of what was ruled out.
  if (data.final === true && data.audit_markdown) {
    rows.push('', data.audit_markdown);
  }
  return rows.join('\n');
}

/**
 * Renders the recorded transcript entries (in DOM order) as markdown.
 * Entry kinds: user, reasoning, matrix, answer, note, error.
 */
export function transcriptText(entries, contextLabel, now = new Date()) {
  const parts = [
    `# Mantis transcript\n\n- Context: ${contextLabel}\n- Exported: ${now.toISOString()}`,
  ];
  for (const entry of entries) {
    if (entry.kind === 'user') {
      parts.push(`## User\n\n${entry.text}`);
    } else if (entry.kind === 'reasoning') {
      parts.push(`## Agent Reasoning\n\n${reasoningText(entry.items)}`);
    } else if (entry.kind === 'matrix') {
      parts.push(matrixText(entry.data));
    } else if (entry.kind === 'answer') {
      parts.push(`## Mantis\n\n${entry.raw}`);
    } else if (entry.kind === 'note') {
      parts.push(`> Note: ${entry.text}`);
    } else if (entry.kind === 'error') {
      parts.push(`## Error\n\n\`\`\`\n${entry.text}\n\`\`\``);
    } else {
      throw new Error(`transcriptText: unknown transcript entry kind '${entry.kind}'.`);
    }
  }
  return parts.join('\n\n') + '\n';
}

// -------------------------------------------------------------- diagrams ---

/** mantis-diagram-YYYYMMDD-HHMMSS.<ext> in local time. */
export function diagramFilename(ext, now = new Date()) {
  const p = (n) => String(n).padStart(2, '0');
  const stamp = `${now.getFullYear()}${p(now.getMonth() + 1)}${p(now.getDate())}-` +
    `${p(now.getHours())}${p(now.getMinutes())}${p(now.getSeconds())}`;
  return `mantis-diagram-${stamp}.${ext}`;
}

/**
 * Serialises a rendered Mermaid SVG as a standalone document with xmlns and
 * explicit pixel dimensions taken from its viewBox (Mermaid emits
 * width="100%" plus a max-width style, which an <img> cannot size).
 */
export function serializeSvg(svg) {
  if (!svg) throw new Error('No rendered diagram SVG found to export.');
  const box = svg.viewBox?.baseVal;
  const rect = svg.getBoundingClientRect();
  const width = Math.ceil(box?.width || rect.width);
  const height = Math.ceil(box?.height || rect.height);
  if (!width || !height) {
    throw new Error('Diagram SVG has no measurable size (missing viewBox); cannot export it.');
  }
  const clone = svg.cloneNode(true);
  clone.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
  clone.setAttribute('xmlns:xlink', 'http://www.w3.org/1999/xlink');
  clone.setAttribute('width', String(width));
  clone.setAttribute('height', String(height));
  clone.style.removeProperty('max-width');
  const xml = new XMLSerializer().serializeToString(clone);
  return { xml: `<?xml version="1.0" encoding="UTF-8"?>\n${xml}`, width, height };
}

/** Rasterises the SVG at PNG_SCALE on a white background. */
export function svgToPngBlob(svg) {
  const { xml, width, height } = serializeSvg(svg);
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onerror = () => reject(new Error('The browser could not decode the diagram SVG as an image.'));
    img.onload = () => {
      const canvas = document.createElement('canvas');
      canvas.width = width * PNG_SCALE;
      canvas.height = height * PNG_SCALE;
      const ctx = canvas.getContext('2d');
      ctx.fillStyle = '#ffffff';
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
      try {
        canvas.toBlob((blob) => {
          if (blob) resolve(blob);
          else reject(new Error('Canvas produced no PNG data for the diagram.'));
        }, 'image/png');
      } catch (cause) {
        if (cause.name === 'SecurityError') {
          reject(new Error(
            'PNG export blocked: the diagram contains HTML labels (foreignObject) that taint ' +
            'the canvas. Use "Download SVG" for this diagram instead.'
          ));
        } else {
          reject(cause);
        }
      }
    };
    img.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(xml);
  });
}

/** Writes the diagram to the clipboard as image/png or throws. */
export async function copyDiagramImage(svg) {
  requireSecureContext();
  if (!navigator.clipboard?.write || typeof ClipboardItem === 'undefined') {
    throw new Error(
      'Copying images is not supported by this browser (no navigator.clipboard.write / ' +
      'ClipboardItem). Use "Download PNG" instead.'
    );
  }
  // The item is built synchronously with a pending blob so Safari still sees
  // the write as part of the click gesture.
  const blobPromise = svgToPngBlob(svg);
  let write;
  try {
    write = navigator.clipboard.write([new ClipboardItem({ 'image/png': blobPromise })]);
  } catch (cause) {
    throw new Error(`Clipboard image write rejected by the browser: ${cause.message || cause}`);
  }
  await blobPromise;
  try {
    await write;
  } catch (cause) {
    throw new Error(`Clipboard image write rejected by the browser: ${cause.message || cause}`);
  }
}

function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export async function downloadDiagramPng(svg) {
  saveBlob(await svgToPngBlob(svg), diagramFilename('png'));
}

export function downloadDiagramSvg(svg) {
  const { xml } = serializeSvg(svg);
  saveBlob(new Blob([xml], { type: 'image/svg+xml' }), diagramFilename('svg'));
}

/**
 * Markup for the Copy image / Download PNG / Download SVG buttons shared by
 * the inline card and the fullscreen modal.
 */
export const DIAGRAM_ACTIONS_HTML = `
  <button type="button" class="btn btn-ghost btn-sm btn-diagram-action" data-diagram-act="copy"
          title="Copy diagram to clipboard as PNG" aria-label="Copy diagram image">
    <span class="material-symbols-outlined" aria-hidden="true">content_copy</span> Copy image
  </button>
  <button type="button" class="btn btn-ghost btn-sm btn-diagram-action" data-diagram-act="png"
          title="Download diagram as PNG">
    <span class="material-symbols-outlined" aria-hidden="true">download</span> Download PNG
  </button>
  <button type="button" class="btn btn-ghost btn-sm btn-diagram-action" data-diagram-act="svg"
          title="Download diagram as SVG">
    <span class="material-symbols-outlined" aria-hidden="true">download</span> Download SVG
  </button>
`;

/**
 * Wires the three diagram action buttons under `root`. `getSvg` resolves the
 * SVG at click time; `onError` receives any failure message.
 */
export function wireDiagramActions(root, getSvg, onError) {
  const handlers = {
    copy: (svg) => copyDiagramImage(svg),
    png: (svg) => downloadDiagramPng(svg),
    svg: (svg) => downloadDiagramSvg(svg),
  };
  for (const button of root.querySelectorAll('[data-diagram-act]')) {
    const run = handlers[button.dataset.diagramAct];
    if (!run) {
      throw new Error(`wireDiagramActions: unknown action '${button.dataset.diagramAct}'.`);
    }
    button.addEventListener('click', async () => {
      try {
        await run(getSvg());
        flashSuccess(button);
      } catch (cause) {
        onError(cause.message);
      }
    });
  }
}
