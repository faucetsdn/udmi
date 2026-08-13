// ==========================================================================
// UDMI WORKBENCH - NOTIFICATION & TOAST MANAGER
// ==========================================================================

export class NotificationManager {
  static requestPermission() {
    if (typeof window !== 'undefined' && 'Notification' in window) {
      if (Notification.permission === 'default') {
        Notification.requestPermission();
      }
    }
  }

  static notify({ title, body, type = 'info', icon = '/assets/workbench_logo.png', duration = 6000, actionText = null, onAction = null }) {
    // 1. Fire OS Web Notification if granted
    if (typeof window !== 'undefined' && 'Notification' in window && Notification.permission === 'granted') {
      try {
        if (navigator.serviceWorker && navigator.serviceWorker.controller) {
          navigator.serviceWorker.ready.then(reg => {
            reg.showNotification(title, { body, icon, tag: 'udmi-notification', requireInteraction: type === 'error' });
          });
        } else {
          new Notification(title, { body, icon });
        }
      } catch (e) {
        console.warn("Failed to trigger OS notification:", e);
      }
    }

    // 2. Show in-app floating visual Toast banner
    this.showToast({ title, message: body, type, duration, actionText, onAction });
  }

  static showToast({ title, message, type = 'info', duration = 6000, actionText = null, onAction = null }) {
    if (typeof document === 'undefined') return;

    let container = document.getElementById('toast-container');
    if (!container) {
      container = document.createElement('div');
      container.id = 'toast-container';
      container.style.cssText = 'position: fixed; bottom: 24px; right: 24px; z-index: 10000; display: flex; flex-direction: column; gap: 12px; max-width: 400px;';
      document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    const colors = {
      info: { bg: '#e8f0fe', border: '#1a73e8', text: '#174ea6', icon: 'info' },
      success: { bg: '#e6f4ea', border: '#137333', text: '#0d652d', icon: 'check_circle' },
      warning: { bg: '#fef7e0', border: '#f29900', text: '#b06000', icon: 'warning' },
      error: { bg: '#fce8e6', border: '#c5221f', text: '#a50e0e', icon: 'error' }
    };
    const c = colors[type] || colors.info;

    toast.style.cssText = `
      background: ${c.bg};
      border-left: 4px solid ${c.border};
      border-radius: 8px;
      padding: 14px 18px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.15);
      display: flex;
      align-items: flex-start;
      gap: 12px;
      font-family: 'Roboto', sans-serif;
      animation: toastSlideIn 0.35s cubic-bezier(0.16, 1, 0.3, 1) forwards;
      transition: opacity 0.35s ease, transform 0.35s ease;
    `;

    if (!document.getElementById('toast-keyframes')) {
      const styleEl = document.createElement('style');
      styleEl.id = 'toast-keyframes';
      styleEl.textContent = `
        @keyframes toastSlideIn {
          from { opacity: 0; transform: translateY(20px); }
          to { opacity: 1; transform: translateY(0); }
        }
      `;
      document.head.appendChild(styleEl);
    }

    toast.innerHTML = `
      <span class="material-symbols-outlined" style="color: ${c.border}; font-size: 24px; flex-shrink: 0;">${c.icon}</span>
      <div style="flex: 1;">
        <div style="font-weight: 700; font-size: 14px; color: #202124; margin-bottom: 4px;">${title}</div>
        <div style="font-size: 13px; color: #3c4043; line-height: 1.4;">${message}</div>
        ${actionText ? `<button class="toast-action-btn" style="margin-top: 8px; background: transparent; border: 1px solid ${c.border}; color: ${c.text}; padding: 4px 10px; border-radius: 4px; font-size: 12px; font-weight: 600; cursor: pointer;">${actionText}</button>` : ''}
      </div>
      <button class="toast-close-btn" style="background: none; border: none; font-size: 18px; cursor: pointer; color: #5f6368; padding: 0;">&times;</button>
    `;

    const closeToast = () => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(10px)';
      setTimeout(() => toast.remove(), 350);
    };

    toast.querySelector('.toast-close-btn').addEventListener('click', closeToast);

    if (actionText && onAction) {
      toast.querySelector('.toast-action-btn').addEventListener('click', () => {
        onAction();
        closeToast();
      });
    }

    container.appendChild(toast);

    if (duration > 0) {
      setTimeout(closeToast, duration);
    }
  }
}
