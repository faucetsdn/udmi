/**
 * Layer 3 — Workbench API client.
 *
 * The single place in the frontend permitted to perform network I/O. Views and
 * components receive typed data and never call fetch() or EventSource directly.
 * Every failure raises an Error carrying the server's actionable message.
 */

import { logger } from './logger.js';

async function request(path, { method = 'GET', body = null, module = 'ApiClient' } = {}) {
  const correlationId = logger.newCorrelationId('ui');
  const startedAt = performance.now();

  const options = {
    method,
    headers: { 'X-Correlation-ID': correlationId },
  };
  if (body !== null) {
    options.headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(body);
  }

  let response;
  try {
    response = await fetch(path, options);
  } catch (cause) {
    logger.error(module, 'request.network_error', {
      correlationId,
      details: { path, method },
      error: { code: 'NETWORK', message: cause.message },
    });
    throw new Error(`Cannot reach the Workbench server (${path}): ${cause.message}`);
  }

  const durationMs = performance.now() - startedAt;
  const payload = await response.json().catch(() => null);

  if (!response.ok) {
    const message = payload?.error || `${response.status} ${response.statusText}`;
    logger.error(module, 'request.error', {
      correlationId,
      durationMs,
      details: { path, method, status: response.status },
      error: { code: `HTTP_${response.status}`, message },
    });
    const error = new Error(message);
    error.status = response.status;
    throw error;
  }

  logger.info(module, 'request.complete', {
    correlationId,
    durationMs,
    details: { path, method },
  });
  return payload;
}

/** Discovery and artifact reads — all backed by real repository content. */
export const api = {
  health: () => request('/api/health'),

  listSiteModels: () => request('/api/site-models'),

  listSiteRoots: () => request('/api/site-roots'),

  addSiteRoot: (path) =>
    request('/api/site-roots', { method: 'POST', body: { path }, module: 'SiteRootsClient' }),

  removeSiteRoot: (path) =>
    request(`/api/site-roots?path=${encodeURIComponent(path)}`, {
      method: 'DELETE',
      module: 'SiteRootsClient',
    }),

  listDevices: (siteModel) =>
    request(`/api/devices?site_model=${encodeURIComponent(siteModel)}`),

  /** Picker-sized device list: {device_id, is_gateway, gateway_id} only. */
  listDeviceSummaries: (siteModel) =>
    request(`/api/devices/summary?site_model=${encodeURIComponent(siteModel)}`),

  getDevice: (siteModel, deviceId) =>
    request(
      `/api/device?site_model=${encodeURIComponent(siteModel)}&device_id=${encodeURIComponent(deviceId)}`
    ),

  listSequences: () => request('/api/sequences'),

  getResults: (siteModel, deviceId) =>
    request(
      `/api/results?site_model=${encodeURIComponent(siteModel)}&device_id=${encodeURIComponent(deviceId)}`
    ),

  browse: (path = '') => request(`/api/browse?path=${encodeURIComponent(path)}`),

  readFile: (path) => request(`/api/file?path=${encodeURIComponent(path)}`),

  sequencerOptions: () => request('/api/sequencer/options'),

  listSessions: () => request('/api/sequencer/sessions'),

  runSequencer: (payload) =>
    request('/api/sequencer/run', { method: 'POST', body: payload, module: 'SequencerClient' }),

  stopSequencer: (sessionId) =>
    request('/api/sequencer/stop', {
      method: 'POST',
      body: { session_id: sessionId },
      module: 'SequencerClient',
    }),

  /** Per-device sequencer compliance rollup for the whole site model. */
  siteCompliance: (siteModel) =>
    request(`/api/compliance?site_model=${encodeURIComponent(siteModel)}`, {
      module: 'ComplianceClient',
    }),

  /**
   * Direct URL for a sequencer report artifact. Reports are served as
   * attachments rather than fetched into memory so the browser's own download
   * machinery handles naming and large files.
   */
  deviceReportUrl: (siteModel, deviceId, kind) =>
    `/api/device/report?site_model=${encodeURIComponent(siteModel)}` +
    `&device_id=${encodeURIComponent(deviceId)}&kind=${encodeURIComponent(kind)}`,

  /**
   * Describes what committing this site model's results would do, without
   * touching the repository. The dialog is built entirely from this response so
   * the branch list, remote list, the devices whose results are affected, and
   * any blocking reason all come from real git state rather than from
   * assumptions made in the browser.
   *
   * There is no device parameter: a sequencer run writes results for a device
   * into three separate places under the site model, and committing only one
   * device's results directory left the rest of the run uncommitted.
   */
  commitPreview: (siteModel) =>
    request(
      `/api/results/commit/preview?site_model=${encodeURIComponent(siteModel)}`,
      { module: 'CommitClient' }
    ),

  commitResults: ({ siteModel, message, branch, createBranch = false,
                    push = false, remote = null }) =>
    request('/api/results/commit', {
      method: 'POST',
      module: 'CommitClient',
      body: {
        site_model: siteModel,
        message,
        branch,
        create_branch: createBranch,
        push,
        remote,
      },
    }),

  diagnosticsLogs: (limit = 200) => request(`/api/diagnostics/logs?limit=${limit}`),

  /**
   * Packages the site model, out/ and cached tool configs (private keys
   * excluded). The server fails explicitly rather than return a partial bundle.
   */
  createSupportBundle: (siteModel) =>
    request('/api/support-bundle', {
      method: 'POST',
      body: { site_model: siteModel },
      module: 'SupportBundleClient',
    }),

  /** Attachment URL for a created bundle; the browser handles the download. */
  supportBundleUrl: (bundleId) =>
    `/api/support-bundle/download?bundle_id=${encodeURIComponent(bundleId)}`,

  /** Email notification consent, readiness, and recent deliveries. */
  getNotifications: () => request('/api/notifications', { module: 'NotificationsClient' }),

  setNotificationConsent: (consent) =>
    request('/api/notifications/consent', {
      method: 'POST',
      body: { consent },
      module: 'NotificationsClient',
    }),

  sendTestNotification: () =>
    request('/api/notifications/test', { method: 'POST', body: {}, module: 'NotificationsClient' }),

  // Every testbed call names the drawer's project spec; the backend derives all
  // ports from it and rejects a missing or non-local spec with 400.
  getTestbedStatus: (projectSpec) =>
    request(`/api/testbed/status?project_spec=${encodeURIComponent(projectSpec ?? '')}`, {
      module: 'TestbedClient',
    }),

  getTestbedConnection: ({ projectSpec, siteModel, deviceId }) =>
    request(
      `/api/testbed/connection?project_spec=${encodeURIComponent(projectSpec ?? '')}` +
        `&site_model=${encodeURIComponent(siteModel ?? '')}&device_id=${encodeURIComponent(deviceId ?? '')}`,
      { module: 'TestbedClient' }
    ),

  startTestbed: ({ siteModel, projectSpec, clean = false }) =>
    request('/api/testbed/start', {
      method: 'POST',
      body: { site_model: siteModel, project_spec: projectSpec, clean },
      module: 'TestbedClient',
    }),

  stopTestbed: (projectSpec) =>
    request('/api/testbed/stop', {
      method: 'POST',
      body: { project_spec: projectSpec },
      module: 'TestbedClient',
    }),

  restartTestbed: ({ siteModel, projectSpec }) =>
    request('/api/testbed/restart', {
      method: 'POST',
      body: { site_model: siteModel, project_spec: projectSpec },
      module: 'TestbedClient',
    }),

  startPubber: ({ siteModel, deviceId, projectSpec, serialNo = '1234' }) =>
    request('/api/testbed/pubber/start', {
      method: 'POST',
      body: { site_model: siteModel, device_id: deviceId, project_spec: projectSpec, serial_no: serialNo },
      module: 'TestbedClient',
    }),

  stopPubber: ({ deviceId, projectSpec }) =>
    request('/api/testbed/pubber/stop', {
      method: 'POST',
      body: { device_id: deviceId, project_spec: projectSpec },
      module: 'TestbedClient',
    }),

  getTestbedLogs: (component = 'setup', tail = 100) =>
    request(`/api/testbed/logs?component=${encodeURIComponent(component)}&tail=${tail}`, {
      module: 'TestbedClient',
    }),

  callTool: async (name, args = {}) => {
    const envelope = await request('/rpc', {
      method: 'POST',
      module: 'McpClient',
      body: { jsonrpc: '2.0', id: Date.now(), method: 'tools/call', params: { name, arguments: args } },
    });
    const text = envelope?.result?.content?.[0]?.text ?? '{}';
    let parsed = text;
    try {
      parsed = JSON.parse(text);
    } catch {
      /* tool returned plain text */
    }
    if (envelope?.result?.isError) {
      throw new Error(typeof parsed === 'string' ? parsed : JSON.stringify(parsed));
    }
    return parsed;
  },
};

/**
 * Consumes a Server-Sent Events stream, dispatching each frame to `onEvent`.
 * Returns an object exposing `abort()` so callers can cancel cleanly.
 */
export function streamEvents(path, { method = 'GET', body = null, onEvent, module = 'SseClient' }) {
  const controller = new AbortController();
  const correlationId = logger.newCorrelationId('sse');

  const options = {
    method,
    headers: { Accept: 'text/event-stream', 'X-Correlation-ID': correlationId },
    signal: controller.signal,
  };
  if (body !== null) {
    options.headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(body);
  }

  const done = (async () => {
    logger.info(module, 'sse.connect', { correlationId, details: { path } });
    const response = await fetch(path, options);
    if (!response.ok || !response.body) {
      const payload = await response.json().catch(() => null);
      throw new Error(payload?.error || `Stream failed: ${response.status} ${response.statusText}`);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';

    while (true) {
      const { value, done: finished } = await reader.read();
      if (finished) break;
      buffer += decoder.decode(value, { stream: true });

      const frames = buffer.split('\n\n');
      buffer = frames.pop() ?? '';

      for (const frame of frames) {
        if (!frame.trim()) continue;
        let eventName = 'message';
        let dataLine = '{}';
        for (const line of frame.split('\n')) {
          if (line.startsWith('event:')) eventName = line.slice(6).trim();
          else if (line.startsWith('data:')) dataLine = line.slice(5).trim();
        }
        try {
          onEvent(eventName, JSON.parse(dataLine));
        } catch (cause) {
          logger.warn(module, 'sse.parse_error', {
            correlationId,
            details: { raw: dataLine.slice(0, 200), reason: cause.message },
          });
        }
      }
    }
    logger.info(module, 'sse.closed', { correlationId, details: { path } });
  })();

  return { abort: () => controller.abort(), done };
}
