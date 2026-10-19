/**
 * Layer 1 — desktop notifications for finished work.
 *
 * Every finished sequencer run and Mantis answer raises an OS notification
 * through the browser Notification API, independent of the per-run "Email me
 * when done" toggles. It fires only while the Workbench tab is not in front
 * (hidden, minimised, or another window focused), so it never pops up over a
 * result the operator is already looking at. With the tab closed there is no
 * page to raise it; email covers that case.
 *
 * Permission is requested once, from the click that starts the work (browsers
 * only honour the request from a user gesture). If it is denied or the
 * browser has no Notification API, subscribers are told so the app bar can
 * say so persistently; the operator is never re-prompted.
 */

const listeners = new Set();

function currentStatus() {
  if (typeof window === 'undefined' || !('Notification' in window)) return 'unsupported';
  return window.Notification.permission; // 'default' | 'granted' | 'denied'
}

function publish() {
  const status = currentStatus();
  for (const listener of listeners) listener(status);
}

// Permission can change outside the page (site settings); follow it live.
if (typeof navigator !== 'undefined' && navigator.permissions?.query) {
  navigator.permissions
    .query({ name: 'notifications' })
    .then((permission) => {
      permission.onchange = publish;
    })
    .catch(() => {
      // The Permissions API does not know 'notifications' in this browser.
      // Status is still read from Notification.permission on every publish.
    });
}

function tabInFront() {
  return !document.hidden && document.hasFocus();
}

export const desktopNotify = {
  status: currentStatus,

  /** Calls `listener(status)` now and on every change. Returns an unsubscribe. */
  subscribe(listener) {
    listeners.add(listener);
    listener(currentStatus());
    return () => listeners.delete(listener);
  },

  /**
   * Asks for permission if it has never been asked. Call from a click
   * handler: outside a user gesture browsers ignore or auto-deny the prompt.
   */
  async requestFromGesture() {
    if (currentStatus() !== 'default') return currentStatus();
    await window.Notification.requestPermission();
    publish();
    return currentStatus();
  },

  /**
   * Raises a notification when the tab is not in front and permission is
   * granted. Clicking it brings the Workbench tab forward. Returns whether a
   * notification was shown.
   */
  notify({ title, body, tag }) {
    if (tabInFront() || currentStatus() !== 'granted') return false;
    const notification = new window.Notification(title, { body, tag });
    notification.onclick = () => {
      window.focus();
      notification.close();
    };
    return true;
  },
};
