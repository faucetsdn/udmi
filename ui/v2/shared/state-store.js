// ==========================================================================
// UDMI WORKBENCH - CENTRAL STATE STORE & EVENT BUS (SPA ARCHITECTURE)
// ==========================================================================

export class StateStore {
  constructor() {
    this.state = {
      siteModel: '',
      // Explicit port 18833 triggers automatic isolated mode in shell_common.sh without sudo
      projectSpec: '//mqtt/localhost:18833',
      devices: [],
      activeDevice: null,
      activeTab: 'testbed',
      selectedTests: new Set(['system.base.telemetry', 'system.base.state', 'pointset.telemetry.events']),
      runningSessions: new Map(),
      testResults: {} // map of deviceId -> { testId: resultObj }
    };
    this.listeners = new Map();
  }

  get(key) {
    return this.state[key];
  }

  set(key, value) {
    const prev = this.state[key];
    if (prev !== value) {
      this.state[key] = value;
      this.emit(`change:${key}`, value, prev);
      this.emit('change', { key, value, prev, state: this.state });
    }
  }

  on(event, callback) {
    if (!this.listeners.has(event)) {
      this.listeners.set(event, new Set());
    }
    this.listeners.get(event).add(callback);
    return () => this.off(event, callback);
  }

  off(event, callback) {
    if (this.listeners.has(event)) {
      this.listeners.get(event).delete(callback);
    }
  }

  emit(event, data, extra = null) {
    if (this.listeners.has(event)) {
      this.listeners.get(event).forEach(cb => {
        try {
          cb(data, extra);
        } catch (e) {
          console.error(`Error in EventBus listener for ${event}:`, e);
        }
      });
    }
  }
}

// Global singleton instance for unified SPA communication
export const stateStore = new StateStore();
