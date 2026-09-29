/**
 * Layer 2 — Discovery loader.
 *
 * Fetches repository discovery data and folds it into the workspace store.
 * Separating this from the sequencer view keeps "what the repository contains"
 * apart from "what the user is doing with it", and keeps both modules within
 * the single-responsibility and file-size guardrails.
 */

import { api } from './api.js';
import { store } from './store.js';

export class DiscoveryLoader {
  constructor({ onNotice }) {
    this.onNotice = onNotice;
    this.siteModels = [];
  }

  /** Loads the site model list, sequence catalog, and run option whitelists. */
  async loadCatalog() {
    // The sequence catalog is built by running the compiled validator, which
    // can fail on its own (missing or stale jar). That failure is returned
    // for the view to show in place of the list, without taking the site
    // model and option loading down with it.
    const [models, sequences, options] = await Promise.all([
      api.listSiteModels(),
      api.listSequences().then(
        (body) => ({ sequences: body.sequences, error: null }),
        (cause) => ({ sequences: [], error: cause.message }),
      ),
      api.sequencerOptions(),
    ]);
    this.siteModels = models.site_models;

    // A registered path that has gone away is reported, never quietly dropped.
    for (const root of models.unavailable_roots || []) {
      this.onNotice(`Registered site model path ${root.path} is unavailable: ${root.reason}`, 'warn');
    }
    // A site model whose cloud_iot_config.json cannot be parsed is skipped by
    // the server, so say which one and why instead of letting it vanish.
    for (const invalid of models.invalid_models || []) {
      this.onNotice(`Site model ${invalid.path} was skipped: ${invalid.error}`, 'warn');
    }
    return {
      siteModels: this.siteModels,
      sequences: sequences.sequences,
      sequencesError: sequences.error,
      options,
    };
  }

  /**
   * Confirms a persisted site model still exists. A stale selection is cleared
   * and reported rather than silently retried against a missing directory.
   */
  validateSiteModel(siteModel) {
    if (!siteModel) return false;
    if (this.siteModels.some((model) => model.path === siteModel)) return true;

    this.onNotice(`Saved site model '${siteModel}' no longer exists; select another.`, 'warn');
    store.update('site.invalid', { siteModel: '', deviceId: '' });
    return false;
  }

  /**
   * Loads the device picker list for a site model. Returns [] on failure.
   * Uses the summary listing (ids + gateway flags): the full inventory parses
   * every device's points, which is seconds of work on an 11k-device site.
   */
  async loadDevices(siteModel) {
    if (!siteModel) return [];
    try {
      const { devices } = await api.listDeviceSummaries(siteModel);
      return devices;
    } catch (cause) {
      this.onNotice(cause.message, 'error');
      return [];
    }
  }

  /**
   * Reloads recorded results for a device.
   *
   * `relabel` exists because this refresh serves two different callers. When
   * the operator picks a different device, the summary should announce that it
   * is showing history. When a run has just finished, the summary is already
   * displaying that run's verdict, and overwriting it with 'Historical runs'
   * silently discards the outcome the operator was waiting for.
   */
  async loadResults(siteModel, deviceId, { relabel = true } = {}) {
    if (!siteModel || !deviceId) return null;
    try {
      const payload = await api.getResults(siteModel, deviceId);
      const currentStatus = store.getState().testStatus || {};
      const update = {
        results: payload.results,
        resultsDirExists: payload.results_dir_exists,
        testStatus: currentStatus,
      };
      if (relabel) {
        update.statusLabel =
          Object.keys(payload.results).length ? 'Historical runs' : 'Idle';
      }
      store.update('results.load', update);

      if (!payload.results_dir_exists) {
        this.onNotice(
          `No previous results on disk for ${deviceId} (${payload.results_dir}). ` +
            'Run a sequence to create them.',
          'notice'
        );
      }
      return payload;
    } catch (cause) {
      this.onNotice(cause.message, 'error');
      return null;
    }
  }

  /**
   * Project spec suggestions, discovered rather than invented.
   *
   * bin/sequencer only accepts `//<iot_provider>/<project_id>[/<namespace>]`,
   * so the suggestion is the spec the server builds from the site's
   * cloud_iot_config.json. It is absent when the config names no
   * iot_provider; no provider is guessed. Recorded run artifacts are not a
   * source: their .attr envelopes carry only a bare projectId, and no
   * artifact records the spec a run was started with.
   */
  projectSpecSuggestions(siteModel) {
    const model = this.siteModels.find((candidate) => candidate.path === siteModel);
    return model?.project_spec ? [model.project_spec] : [];
  }
}
