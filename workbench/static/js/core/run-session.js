/**
 * Layer 2 — Sequencer run lifecycle.
 *
 * Owns starting, streaming, stopping, and recovering a `bin/sequencer` run so
 * the sequencer view can stay focused on discovery, selection, and rendering.
 * Raw SSE framing stays in the Layer 3 client; this module only interprets the
 * decoded event names.
 */

import { api, streamEvents } from './api.js';
import { logger } from './logger.js';

const MODULE = 'RunSession';

export class RunSession {
  constructor({ onLog, onTestEvent, onComplete, onError, onNotice }) {
    this.onLog = onLog;
    this.onTestEvent = onTestEvent;
    this.onComplete = onComplete;
    this.onError = onError;
    this.onNotice = onNotice;
    this.stream = null;
  }

  /** Launches a run from explicit user state and begins streaming it. */
  async start(state, { notify = false } = {}) {
    const started = await api.runSequencer({
      site_model: state.siteModel,
      device_id: state.deviceId,
      project_spec: state.projectSpec.trim(),
      tests: state.selectedTests,
      log_level: state.logLevel,
      min_stage: state.minStage,
      serial_no: state.serialNo || null,
      notify,
    });
    this.attach(started.session_id, 0);
    return started;
  }

  /** Subscribes to a session's output, resuming from a byte offset. */
  attach(sessionId, offset = 0) {
    this.abort();
    const path =
      `/api/sequencer/stream?session_id=${encodeURIComponent(sessionId)}&offset=${offset}`;

    this.stream = streamEvents(path, {
      module: MODULE,
      onEvent: (name, data) => this._dispatch(name, data),
    });

    this.stream.done.catch((cause) => {
      if (cause.name === 'AbortError') return;
      logger.error(MODULE, 'stream.failed', {
        details: { sessionId },
        error: { code: 'STREAM', message: cause.message },
      });
      this.onError(`Stream interrupted: ${cause.message}`);
      this.onComplete({ exitCode: null, aborted: true });
    });
  }

  _dispatch(name, data) {
    if (name === 'log') {
      this.onLog(data.text);
    } else if (name === 'test_event') {
      this.onTestEvent(data);
    } else if (name === 'complete') {
      this.onComplete({ exitCode: data.exit_code, aborted: data.stopped });
    } else if (name === 'error') {
      this.onError(data.message);
    }
  }

  /** Terminates the remote process group for a session. */
  async stop(sessionId) {
    const result = await api.stopSequencer(sessionId);
    this.onNotice(`Session ${sessionId}: ${result.status}`, 'warn');
    return result;
  }

  /**
   * Finds a run still in flight, so reloading the page does not orphan it.
   * v1 had no recovery path at all.
   */
  async recover() {
    const { sessions } = await api.listSessions();
    const active = sessions.find((session) => session.running);
    if (!active) return null;

    logger.warn(MODULE, 'run.recovered', { details: { sessionId: active.session_id } });
    this.attach(active.session_id, 0);
    return active;
  }

  abort() {
    this.stream?.abort();
    this.stream = null;
  }
}
