/**
 * Layer 2 — Feature stage gate.
 *
 * `bin/sequencer` silently skips any sequence whose feature stage orders below
 * the run's minimum stage: no result, no error, nothing. This
 * mirrors that rule on the client so a selection that would vanish is flagged
 * before the run starts rather than discovered afterwards from an empty report.
 *
 * The admitted-stage list is supplied by the backend rather than hardcoded, so
 * it cannot drift from the Java `FeatureStage` ordering.
 */

import { store } from './store.js';

export class StageGate {
  constructor() {
    this.options = [];
    this.sequences = [];
  }

  /** Backend-supplied stage options, each carrying the stages it admits. */
  setOptions(minStages) {
    this.options = minStages || [];
  }

  setSequences(sequences) {
    this.sequences = sequences || [];
  }

  /** Records which feature stages the selected minimum stage actually admits. */
  apply(minStage) {
    const option = this.options.find((candidate) => candidate.value === minStage);
    store.update('stage.admitted', { stagesAdmitted: option?.admits ?? [] });
  }

  /** True when a sequence at `stage` would be skipped without reporting. */
  excludes(stage, admitted) {
    return Boolean(admitted.length > 0 && stage && !admitted.includes(stage.toUpperCase()));
  }

  /**
   * Store state plus the currently selected sequences the gate would drop.
   * Derived on read so it cannot drift from whatever is selected right now.
   */
  state() {
    const state = store.getState();
    const admitted = state.stagesAdmitted || [];
    if (admitted.length === 0) return { ...state, stageExcluded: [] };

    const stageByName = new Map(
      this.sequences.map((sequence) => [sequence.name, sequence.stage])
    );
    const stageExcluded = state.selectedTests.filter((name) =>
      this.excludes(stageByName.get(name), admitted)
    );
    return { ...state, stageExcluded };
  }
}
