/**
 * Layer 1 — Shared drag-to-resize handle.
 *
 * The single resize mechanism for every adjustable panel in the Workbench.
 * Panels differ only in which axis they grow along and which custom property
 * they write, so all of that is supplied by the caller and none of it is
 * duplicated here.
 *
 * Contract:
 *   attachResizer(handleEl, { axis, direction, label, min, max, value,
 *                             onResize, onCommit })
 *
 *   axis      'x' | 'y' — which pointer delta drives the gesture.
 *   direction +1 when moving toward larger screen coordinates (down / right)
 *             enlarges the panel, -1 for panels anchored to the bottom or
 *             right edge, where the handle is on the leading side and the
 *             panel grows as the pointer moves up or left.
 *   label     aria-label for the handle; only the caller knows the panel name.
 *   min/max   inclusive pixel bounds, published as aria-valuemin/aria-valuemax.
 *   value     the panel's current size, already clamped by the caller.
 *   onResize  (targetPx) => appliedPx. The caller clamps and writes the size,
 *             then returns what it actually applied. Returning the applied
 *             value (rather than assuming the target took effect) is what keeps
 *             aria-valuenow honest when the target is out of bounds.
 *   onCommit  (appliedPx) => void. Fired once a gesture ends, so callers
 *             persist a settled size instead of every intermediate frame.
 *
 * Returns a detach function that removes every listener it installed.
 */

/** One canonical arrow-key increment; a drag-only control locks out keyboard operators. */
const KEYBOARD_STEP_PX = 16;

/** Clamps a size to an inclusive range, tolerating an inverted range from a tiny viewport. */
export function clampSize(value, min, max) {
  if (!Number.isFinite(value)) {
    throw new TypeError(`clampSize: value must be a finite number, got ${value}`);
  }
  if (!Number.isFinite(min) || !Number.isFinite(max)) {
    throw new TypeError(`clampSize: bounds must be finite numbers, got ${min}..${max}`);
  }
  // A viewport smaller than the minimum would invert the range; the minimum
  // wins so the panel stays operable rather than collapsing to nothing.
  if (max < min) return min;
  return Math.min(max, Math.max(min, value));
}

export function attachResizer(
  handleEl,
  { axis, direction, label, min, max, value, onResize, onCommit }
) {
  if (!(handleEl instanceof Element)) {
    throw new TypeError('attachResizer: handleEl must be an Element');
  }
  if (axis !== 'x' && axis !== 'y') {
    throw new TypeError(`attachResizer: axis must be 'x' or 'y', got ${axis}`);
  }
  if (direction !== 1 && direction !== -1) {
    throw new TypeError(`attachResizer: direction must be 1 or -1, got ${direction}`);
  }
  if (typeof label !== 'string' || label.length === 0) {
    throw new TypeError('attachResizer: label is required for aria-label');
  }
  if (!Number.isFinite(min) || !Number.isFinite(max) || min >= max) {
    throw new TypeError(`attachResizer: require finite min < max, got ${min}..${max}`);
  }
  if (!Number.isFinite(value)) {
    throw new TypeError(`attachResizer: value must be a finite number, got ${value}`);
  }
  if (typeof onResize !== 'function' || typeof onCommit !== 'function') {
    throw new TypeError('attachResizer: onResize and onCommit are required functions');
  }

  const horizontal = axis === 'x';

  handleEl.setAttribute('role', 'separator');
  handleEl.setAttribute('aria-label', label);
  // A handle that resizes height is itself a horizontal separator, and vice versa.
  handleEl.setAttribute('aria-orientation', horizontal ? 'vertical' : 'horizontal');
  handleEl.setAttribute('tabindex', '0');
  handleEl.setAttribute('aria-valuemin', String(Math.round(min)));
  handleEl.setAttribute('aria-valuemax', String(Math.round(max)));
  handleEl.setAttribute('aria-valuenow', String(Math.round(value)));

  let applied = value;
  let gestureBase = value;
  let originPx = 0;
  let activePointerId = null;

  const publish = (targetPx) => {
    const result = onResize(targetPx);
    if (!Number.isFinite(result)) {
      throw new TypeError('attachResizer: onResize must return the applied size in pixels');
    }
    applied = result;
    handleEl.setAttribute('aria-valuenow', String(Math.round(result)));
  };

  const suppressSelection = (on) => {
    // Without this a drag paints a selection across the whole page.
    document.body.style.userSelect = on ? 'none' : '';
    document.body.style.cursor = on ? (horizontal ? 'col-resize' : 'row-resize') : '';
  };

  const onPointerDown = (event) => {
    if (activePointerId !== null || event.button !== 0) return;
    activePointerId = event.pointerId;
    gestureBase = applied;
    originPx = horizontal ? event.clientX : event.clientY;
    // Capture keeps the gesture alive when a fast drag outruns the 6px handle.
    handleEl.setPointerCapture(activePointerId);
    handleEl.classList.add('is-dragging');
    suppressSelection(true);
    event.preventDefault();
  };

  const onPointerMove = (event) => {
    if (event.pointerId !== activePointerId) return;
    const travelled = (horizontal ? event.clientX : event.clientY) - originPx;
    publish(gestureBase + direction * travelled);
  };

  const endGesture = (event) => {
    if (event.pointerId !== activePointerId) return;
    if (handleEl.hasPointerCapture(activePointerId)) {
      handleEl.releasePointerCapture(activePointerId);
    }
    activePointerId = null;
    handleEl.classList.remove('is-dragging');
    suppressSelection(false);
    onCommit(applied);
  };

  const onKeyDown = (event) => {
    const travel = horizontal
      ? { ArrowLeft: -KEYBOARD_STEP_PX, ArrowRight: KEYBOARD_STEP_PX }
      : { ArrowUp: -KEYBOARD_STEP_PX, ArrowDown: KEYBOARD_STEP_PX };
    const travelled = travel[event.key];
    if (travelled === undefined) return;
    event.preventDefault();
    publish(applied + direction * travelled);
    onCommit(applied);
  };

  handleEl.addEventListener('pointerdown', onPointerDown);
  handleEl.addEventListener('pointermove', onPointerMove);
  handleEl.addEventListener('pointerup', endGesture);
  handleEl.addEventListener('pointercancel', endGesture);
  handleEl.addEventListener('keydown', onKeyDown);

  return () => {
    if (activePointerId !== null) {
      handleEl.releasePointerCapture(activePointerId);
      activePointerId = null;
      suppressSelection(false);
    }
    handleEl.removeEventListener('pointerdown', onPointerDown);
    handleEl.removeEventListener('pointermove', onPointerMove);
    handleEl.removeEventListener('pointerup', endGesture);
    handleEl.removeEventListener('pointercancel', endGesture);
    handleEl.removeEventListener('keydown', onKeyDown);
  };
}
