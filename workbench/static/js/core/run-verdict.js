/**
 * Layer 2 — Run verdict.
 *
 * Turns a finished run's exit code and per-test counters into the single
 * summary label shown in the run badge. Kept pure (no DOM, no store) so the
 * rule is testable on its own.
 *
 * 'Compliant' is a claim that every selected sequence ran and none failed, so
 * it requires all of: exit code 0, every selected test reported a result, and
 * zero failures. Anything short of that is not compliance:
 *   Aborted    — the operator stopped the run, or the stream was lost.
 *   Error      — bin/sequencer exited non-zero (or with no known exit code)
 *                and not a single test reported.
 *   Incomplete — non-zero/unknown exit code, or some selected test never
 *                reported a result.
 *   Failed     — the run completed cleanly and at least one test failed.
 *   Compliant  — exit 0, every selected test reported, no failures.
 */

export function runVerdict({ exitCode, aborted, metrics }) {
  if (aborted) return 'Aborted';
  const settled = metrics.pass + metrics.fail + metrics.skip;
  const cleanExit = exitCode === 0;
  if (!cleanExit && settled === 0) return 'Error';
  if (!cleanExit || metrics.pending > 0) return 'Incomplete';
  if (metrics.fail > 0) return 'Failed';
  return 'Compliant';
}
