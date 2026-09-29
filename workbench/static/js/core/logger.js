/**
 * Structured Client Logger & Ring Buffer (Section 7 of workbench/GEMINI.md).
 *
 * Maintains a bounded 1,000-entry in-memory ring buffer of canonical LogEntry
 * records with end-to-end correlationId tracing.
 */

const MAX_BUFFER_SIZE = 1000;

export class WorkbenchLogger {
  constructor() {
    this._buffer = [];
    this._listeners = new Set();
  }

  newCorrelationId(prefix = 'ui') {
    const rand = Math.random().toString(16).slice(2, 10);
    return `${prefix}-${rand}`;
  }

  log({
    level = 'INFO',
    layer = 'UI',
    correlationId,
    module = 'App',
    event = 'ui.action',
    durationMs,
    context,
    details,
    error,
  }) {
    const entry = {
      timestamp: new Date().toISOString(),
      level,
      layer,
      correlationId: correlationId || this.newCorrelationId(),
      module,
      event,
    };
    if (typeof durationMs === 'number') entry.durationMs = Math.round(durationMs * 100) / 100;
    if (context) entry.context = context;
    if (details) entry.details = details;
    if (error) entry.error = error;

    this._buffer.push(entry);
    if (this._buffer.length > MAX_BUFFER_SIZE) {
      this._buffer.shift();
    }
    for (const fn of this._listeners) {
      try {
        fn(entry);
      } catch (_) {
        // Ignore observer errors
      }
    }
    return entry;
  }

  info(module, event, opts = {}) {
    return this.log({ ...opts, level: 'INFO', module, event });
  }

  warn(module, event, opts = {}) {
    return this.log({ ...opts, level: 'WARN', module, event });
  }

  error(module, event, opts = {}) {
    return this.log({ ...opts, level: 'ERROR', module, event });
  }

  getEntries(limit = 200) {
    return this._buffer.slice(-limit);
  }

  subscribe(listener) {
    this._listeners.add(listener);
    return () => this._listeners.delete(listener);
  }

  clear() {
    this._buffer = [];
  }
}

export const logger = new WorkbenchLogger();
