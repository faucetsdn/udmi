// ==========================================================================
// UDMI WORKBENCH BACKGROUND SERVICE WORKER (WEB NOTIFICATIONS & RECOVERY)
// ==========================================================================

const monitoredSessions = new Map(); // sessionId -> { interval, deviceId, siteModel }

self.addEventListener('install', (event) => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener('message', (event) => {
  const data = event.data;
  if (!data) return;

  if (data.type === 'START_MONITORING' && data.sessionId) {
    startMonitoringSession(data.sessionId, data.deviceId, data.siteModel);
  } else if (data.type === 'STOP_MONITORING' && data.sessionId) {
    stopMonitoringSession(data.sessionId);
  }
});

function startMonitoringSession(sessionId, deviceId, siteModel) {
  if (monitoredSessions.has(sessionId)) return;

  const interval = setInterval(async () => {
    try {
      const res = await fetch(`/api/sequencer_status?session_id=${sessionId}&offset=0`);
      if (!res.ok) return;
      const statusData = await res.json();

      if (!statusData.running) {
        stopMonitoringSession(sessionId);
        await handleSessionCompletion(sessionId, deviceId, siteModel, statusData.exit_code);
      }
    } catch (e) {
      console.error("[SW] Error polling sequencer status:", e);
    }
  }, 2500);

  monitoredSessions.set(sessionId, { interval, deviceId, siteModel });
}

function stopMonitoringSession(sessionId) {
  const meta = monitoredSessions.get(sessionId);
  if (meta && meta.interval) {
    clearInterval(meta.interval);
  }
  monitoredSessions.delete(sessionId);
}

async function handleSessionCompletion(sessionId, deviceId, siteModel, exitCode) {
  let passCount = 0;
  let failCount = 0;
  let bodyText = `Execution finished for target device [${deviceId || 'AHU-1'}].`;
  let title = "UDMI Test Suite Completed";

  try {
    if (siteModel && deviceId) {
      const res = await fetch(`/api/device_results?site_model=${encodeURIComponent(siteModel)}&device=${encodeURIComponent(deviceId)}`);
      if (res.ok) {
        const data = await res.json();
        if (data.results) {
          const results = Object.values(data.results);
          passCount = results.filter(r => r.status === 'PASS' || r.status === 'PASSED').length;
          failCount = results.filter(r => r.status === 'FAIL' || r.status === 'FAILED').length;

          if (failCount > 0) {
            title = "⚠️ UDMI Test Failures Detected";
            bodyText = `Device [${deviceId}]: ${failCount} failed, ${passCount} passed. Mantis AI is triaging root causes.`;
          } else if (passCount > 0) {
            title = "✅ UDMI Test Suite Passed";
            bodyText = `Device [${deviceId}]: All ${passCount} compliance tests passed successfully!`;
          }
        }
      }
    }
  } catch (e) {
    console.error("[SW] Error fetching completion results:", e);
  }

  if (self.registration && self.registration.showNotification) {
    self.registration.showNotification(title, {
      body: bodyText,
      icon: '/assets/workbench_logo.png',
      tag: `udmi-session-${sessionId}`,
      requireInteraction: failCount > 0,
      data: { url: '/ui/v2/index.html', deviceId, sessionId, passCount, failCount }
    });
  }

  // Also notify open client windows
  const allClients = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
  allClients.forEach(client => {
    client.postMessage({
      type: 'BACKGROUND_TEST_COMPLETED',
      sessionId,
      deviceId,
      passCount,
      failCount,
      exitCode
    });
  });
}

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const data = event.notification.data || {};
  const targetUrl = data.url || '/ui/v2/index.html';

  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(clientList => {
      for (const client of clientList) {
        if ('focus' in client) {
          client.focus();
          client.postMessage({ type: 'NOTIFICATION_CLICKED', data });
          return;
        }
      }
      if (self.clients.openWindow) {
        return self.clients.openWindow(targetUrl);
      }
    })
  );
});
