/*
 * Renders a UDMI device testing results page.
 *
 * A results page loads its data file (data/<page>.js) and then this script.
 * The data file sets window.DEVICE_TEST_RESULTS to:
 *
 *   {
 *     "generated": "2026-10-20T14:26:21.307Z",
 *     "updated": "Updated LOYTEC electronics LROC-400",
 *     "rows": [{"Bucket": "...", "Feature": "...", "Stage": "...", "<device id>": "...", ...}, ...]
 *   }
 *
 * "generated" is when the results were exported, and optional "updated" notes what
 * changed in that export. Every row has the same keys:
 * "Bucket", "Feature", "Stage", and one key for each device. The "Bucket" of a
 * row says what the row holds:
 *
 *   image, make, model
 *       What the device is. An image is a path under img/products/.
 *   test_date, udmi_release_version, vendor_firmware,
 *   vendor_udmi_module_version, compatibility
 *       Details of the device. "Feature" can hold the label to show for the row.
 *   notes
 *       Free text about the device.
 *   anything else
 *       A test in that bucket. "Feature" is the name of the test, "Stage" is its
 *       feature stage, and the value for each device is one of Pass, Fail, Skip
 *       or Untested, or is empty.
 *
 * Data that does not fit this format is reported on the page and not shown.
 */
(function () {
  'use strict';

  const ROW_KEYS = ['Bucket', 'Feature', 'Stage'];
  const IDENTITY_BUCKETS = ['image', 'make', 'model'];
  // In the order that the details are listed for a device.
  const DETAIL_BUCKETS = [
    'test_date',
    'udmi_release_version',
    'vendor_firmware',
    'vendor_udmi_module_version',
    'compatibility',
  ];
  // Shown at the top of the results table as well as in the device details.
  const MATRIX_DETAIL_BUCKETS = ['test_date', 'udmi_release_version', 'vendor_firmware'];
  const NOTES_BUCKET = 'notes';
  const DEVICE_BUCKETS = [...IDENTITY_BUCKETS, ...DETAIL_BUCKETS, NOTES_BUCKET];

  // The icon for each result value, in legend order.
  const RESULT_KINDS = [
    {kind: 'pass', value: 'Pass'},
    {kind: 'fail', value: 'Fail'},
    {kind: 'skip', value: 'Skip'},
    {kind: 'untested', value: 'Untested'},
  ];
  const RESULT_VALUES = RESULT_KINDS.map(({value}) => value);

  const IMAGE_PATH = /^img\/products\/[A-Za-z0-9_-]+(\.[A-Za-z0-9_-]+)*$/;
  const TIMESTAMP = /^(\d{4})-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])T\d{2}:\d{2}/;
  const MONTHS =
      ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  const GENERATED_SEQUENCES_URL = '../specs/sequences/generated.html';

  /** Checks the data against the format above and returns what the page shows. */
  function parse(data) {
    if (data === undefined) {
      throw new Error('the data file of this page did not set window.DEVICE_TEST_RESULTS.');
    }
    const {generated, updated, rows} = data;
    if (typeof generated !== 'string' || !TIMESTAMP.test(generated)) {
      throw new Error('"generated" has to be a timestamp like "2026-10-20T14:26:21.307Z".');
    }
    if (updated !== undefined && typeof updated !== 'string') {
      throw new Error('"updated" has to be text when present.');
    }
    if (!Array.isArray(rows) || rows.length === 0) {
      throw new Error('"rows" has to be a list with at least one row.');
    }
    const deviceIds = checkColumns(rows);
    const trimmed = rows.map((row) => Object.fromEntries(
        Object.entries(row).map(([key, value]) => [key, value.trim()])));
    return {
      generated,
      updated: updated === undefined ? '' : updated.trim(),
      devices: devicesIn(trimmed, deviceIds),
      groups: testGroupsIn(trimmed, deviceIds),
    };
  }

  /** Checks that all rows have the same columns, and returns the IDs of the devices. */
  function checkColumns(rows) {
    rows.forEach((row, index) => {
      ROW_KEYS.forEach((key) => {
        if (typeof row[key] !== 'string') {
          throw new Error(
              `row ${index + 1} has no "${key}" column. ` +
              `Its first columns are: ${Object.keys(row).slice(0, 4).join(', ')}.`);
        }
      });
    });
    const isDevice = (key) => !ROW_KEYS.includes(key);
    const deviceIds = Object.keys(rows[0]).filter(isDevice);
    if (deviceIds.length === 0) {
      throw new Error('row 1 has no device columns.');
    }
    rows.forEach((row, index) => {
      const where = `row ${index + 1} ("${row.Bucket}")`;
      const extra = Object.keys(row).filter(isDevice).find((key) => !deviceIds.includes(key));
      if (extra !== undefined) {
        throw new Error(`${where} has a device "${extra}" that row 1 does not have.`);
      }
      const missing = deviceIds.find((id) => typeof row[id] !== 'string');
      if (missing !== undefined) {
        throw new Error(`${where} has no text value for device "${missing}".`);
      }
    });
    return deviceIds;
  }

  /** Returns what the rows about the devices say about each device. */
  function devicesIn(rows, deviceIds) {
    const rowsIn = (bucket) => rows.filter((row) => row.Bucket === bucket);
    const required = (bucket) => {
      const found = rowsIn(bucket);
      if (found.length !== 1) {
        throw new Error(`there has to be one "${bucket}" row, but there are ${found.length}.`);
      }
      return found[0];
    };
    const optional = (bucket) => {
      const found = rowsIn(bucket);
      if (found.length > 1) {
        throw new Error(`there can only be one "${bucket}" row, but there are ${found.length}.`);
      }
      return found[0];
    };

    const [image, make, model] = IDENTITY_BUCKETS.map(required);
    const detailRows = DETAIL_BUCKETS.map(optional).filter((row) => row !== undefined);
    const notes = optional(NOTES_BUCKET);
    return deviceIds.map((id) => {
      if (image[id] !== '' && !IMAGE_PATH.test(image[id])) {
        throw new Error(
            `the image of device "${id}" is "${shorten(image[id])}", ` +
            'but it has to be a file under img/products/.');
      }
      const details = detailRows.map((row) => ({
        bucket: row.Bucket,
        label: row.Feature === '' ? labelFor(row.Bucket) : row.Feature,
        value: row[id],
      }));
      const searchable = [id, make[id], model[id], ...details.map(({value}) => value)];
      return {
        id,
        image: image[id],
        make: make[id],
        model: model[id],
        details,
        notes: notes === undefined ? '' : notes[id],
        searchText: searchable.join('\n').toLowerCase(),
      };
    });
  }

  /** Returns the tests of each bucket, in the order that the buckets first appear. */
  function testGroupsIn(rows, deviceIds) {
    const groups = [];
    rows.forEach((row, index) => {
      if (DEVICE_BUCKETS.includes(row.Bucket)) {
        return;
      }
      const where = `row ${index + 1} ("${row.Bucket}" / "${row.Feature}")`;
      if (row.Bucket === '' || row.Feature === '') {
        throw new Error(`${where} has to have both a "Bucket" and a "Feature".`);
      }
      deviceIds.forEach((id) => {
        if (row[id] !== '' && !RESULT_VALUES.includes(row[id])) {
          throw new Error(
              `${where} is read as a test, but its value for device "${id}" is ` +
              `"${shorten(row[id])}". ` +
              `A test result is one of ${RESULT_VALUES.join(', ')}, or empty. ` +
              `A row about the devices has one of these in "Bucket": ` +
              `${DEVICE_BUCKETS.join(', ')}.`);
        }
      });
      let group = groups.find(({bucket}) => bucket === row.Bucket);
      if (group === undefined) {
        group = {bucket: row.Bucket, tests: []};
        groups.push(group);
      }
      group.tests.push({bucket: row.Bucket, name: row.Feature, stage: row.Stage, results: row});
    });
    if (groups.length === 0) {
      throw new Error('there are no test rows.');
    }
    return groups;
  }

  function shorten(text) {
    return text.length > 40 ? `${text.slice(0, 40)}...` : text;
  }

  /** Turns the name of a bucket, like "vendor_firmware", into a label. */
  function labelFor(bucket) {
    const words = bucket.replace(/_/g, ' ').replace(/\budmi\b/g, 'UDMI');
    return words.charAt(0).toUpperCase() + words.slice(1);
  }

  /** Formats the date of a timestamp like "7 Apr 2026", or returns null if it is not one. */
  function formatDate(timestamp) {
    const match = TIMESTAMP.exec(timestamp);
    if (match === null) {
      return null;
    }
    return `${Number(match[3])} ${MONTHS[Number(match[2]) - 1]} ${match[1]}`;
  }

  function el(tag, attributes, ...children) {
    const node = document.createElement(tag);
    Object.entries(attributes).forEach(([name, value]) => node.setAttribute(name, value));
    node.append(...children);
    return node;
  }

  function dateEl(timestamp) {
    return el('time', {datetime: timestamp}, formatDate(timestamp));
  }

  function detailValueEl(bucket, value) {
    if (bucket === 'test_date' && formatDate(value) !== null) {
      return [dateEl(value)];
    }
    return value.split(/(_)/).flatMap(
        (part) => part === '_' ? [part, document.createElement('wbr')] : [part]);
  }

  function anchorOf(device) {
    return `device-${device.id.replace(/\s+/g, '-')}`;
  }

  function nameOf(device) {
    return [device.make, device.model].filter((text) => text !== '').join(' ') || device.id;
  }

  function imageEl(device, className, attributes) {
    const holder = el('span', {class: className});
    if (device.image !== '') {
      holder.append(el('img', {src: device.image, alt: '', ...attributes}));
    }
    return holder;
  }

  /** Lets a long test name wrap after its dots and underscores. */
  function breakable(name) {
    return name.split(/([._])/).flatMap(
        (part) => (part === '.' || part === '_') ? [part, document.createElement('wbr')] : [part]);
  }

  /** URL to a sequencer test's section in docs/specs/sequences/generated.md. */
  function sequenceUrl(name, stage) {
    const base = name.split('.')[0].toLowerCase().replace(/[^\w-]/g, '');
    const cleanStage = stage.toLowerCase().replace(/[^a-z0-9]/g, '');
    if (base === '' || cleanStage === '') {
      return GENERATED_SEQUENCES_URL;
    }
    return `${GENERATED_SEQUENCES_URL}#${base}-${cleanStage}`;
  }

  function resultCellEl(value, description) {
    const {kind} = RESULT_KINDS.find((entry) => entry.value === value);
    return el('span', {
      class: `status status-${kind}`,
      role: 'img',
      'aria-label': value,
      title: description,
    });
  }

  /** Lists the result values that appear on this page next to their icons. */
  function legendEl(devices, groups) {
    const used = new Set();
    groups.forEach(({tests}) => tests.forEach(
        ({results}) => devices.forEach(({id}) => used.add(results[id]))));
    const legend = el('ul', {class: 'legend', 'aria-label': 'Results'});
    RESULT_KINDS.forEach(({kind, value}) => {
      if (used.has(value)) {
        legend.append(el('li', {}, el('span', {class: `status status-${kind}`}), value));
      }
    });
    return legend;
  }

  function deviceHeading(device) {
    const link = el('a', {href: `#${anchorOf(device)}`, title: `${nameOf(device)} (${device.id})`},
        imageEl(device, 'device-image', {}),
        el('span', {class: 'device-make'}, device.make),
        el('span', {class: 'device-model'}, device.model));
    return el('th', {scope: 'col', class: 'device', 'data-device': device.id}, link);
  }

  function testRow(devices, {bucket, name, stage, results}) {
    const nameContent = bucket === 'manual'
        ? breakable(name)
        : [el('a', {
            class: 'test-link',
            href: sequenceUrl(name, stage),
            title: `View ${name.split('.')[0]} sequence specification`,
          }, ...breakable(name))];
    const label = el('th', {scope: 'row'}, ...nameContent);
    if (stage !== '' && stage !== '-') {
      const className = stage === 'stable' ? 'stage stage-stable' : 'stage';
      label.append(' ', el('span', {class: className}, stage));
    }
    const row = el('tr', {'data-bucket': bucket, 'data-stage': stage}, label);
    devices.forEach((device) => {
      const cell = el('td', {'data-device': device.id});
      const result = results[device.id];
      if (result !== '') {
        cell.append(resultCellEl(result, `${nameOf(device)}: ${name}: ${result}`));
      }
      row.append(cell);
    });
    return row;
  }

  /** Builds the table of tests against devices, returning the wrapper and score updater. */
  function matrixEl(devices, groups) {
    const table = el('table', {class: 'matrix'},
        el('caption', {class: 'sr-only'}, 'Test results for each device'),
        el('thead', {},
            el('tr', {},
                el('th', {scope: 'col', class: 'corner'}, 'Test'),
                ...devices.map(deviceHeading))));

    const detailRows = MATRIX_DETAIL_BUCKETS
        .filter((bucket) => devices[0].details.some((detail) => detail.bucket === bucket))
        .map((bucket) => {
          const detailOf = (device) => device.details.find((detail) => detail.bucket === bucket);
          const row = el('tr', {class: 'detail'},
              el('th', {scope: 'row'}, detailOf(devices[0]).label));
          devices.forEach((device) => {
            row.append(el('td', {'data-device': device.id},
                ...detailValueEl(bucket, detailOf(device).value)));
          });
          return row;
        });

    const scoreCells = new Map();
    const scoreRow = el('tr', {class: 'detail score-row'},
        el('th', {scope: 'row'}, 'Sequencer passed'));
    devices.forEach((device) => {
      const text = el('span', {class: 'score-text'});
      const fill = el('span', {class: 'score-fill'});
      const cell = el('td', {'data-device': device.id},
          text, el('span', {class: 'score-bar', 'aria-hidden': 'true'}, fill));
      scoreCells.set(device.id, {text, fill, cell});
      scoreRow.append(cell);
    });
    table.append(el('tbody', {class: 'details-body'}, ...detailRows, scoreRow));

    groups.forEach(({bucket, tests}) => {
      const heading = el('th', {scope: 'rowgroup', colspan: String(devices.length + 1)},
          el('span', {}, bucket));
      table.append(el('tbody', {class: 'group-body', 'data-bucket': bucket},
          el('tr', {class: 'group'}, heading),
          ...tests.map((test) => testRow(devices, test))));
    });

    // Subtle column highlight when hovering down a device column.
    let hoveredDevice = null;
    const setHovered = (id) => {
      if (hoveredDevice === id) {
        return;
      }
      if (hoveredDevice !== null) {
        table.querySelectorAll('.col-hover').forEach((node) => node.classList.remove('col-hover'));
      }
      hoveredDevice = id;
      if (id !== null) {
        table.querySelectorAll(`[data-device="${CSS.escape(id)}"]`)
            .forEach((node) => node.classList.add('col-hover'));
      }
    };
    table.addEventListener('mouseover', (event) => {
      const cell = event.target.closest('[data-device]');
      setHovered(cell === null ? null : cell.dataset.device);
    });
    table.addEventListener('mouseleave', () => setHovered(null));

    const updateScores = (visibleTests) => {
      const seqTests = visibleTests.filter(({bucket}) => bucket !== 'manual');
      scoreRow.hidden = seqTests.length === 0;
      if (seqTests.length === 0) {
        return;
      }
      devices.forEach((device) => {
        let passed = 0;
        let total = 0;
        seqTests.forEach(({results}) => {
          const val = results[device.id];
          if (val === 'Pass') {
            passed += 1;
            total += 1;
          } else if (val === 'Fail') {
            total += 1;
          }
        });
        const entry = scoreCells.get(device.id);
        entry.text.textContent = `${passed} / ${total}`;
        const pct = total === 0 ? 0 : Math.round((passed / total) * 100);
        entry.fill.style.width = `${pct}%`;
        entry.cell.title = `${nameOf(device)}: ${passed} of ${total} sequencer tests passed (${pct}%)`;
      });
    };

    // The table scrolls inside this element, which takes focus so that the keyboard can scroll it.
    const wrap = el('div',
        {class: 'matrix-wrap', tabindex: '0', role: 'region', 'aria-label': 'Test results'},
        table);
    return {wrap, updateScores};
  }

  function rowFilterEl(groups) {
    const select = el('select', {id: 'row-filter'},
        el('option', {value: 'all'}, 'All rows'),
        el('option', {value: 'sequencer'}, 'Sequencer tests'),
        el('option', {value: 'stable'}, 'Stable sequencer tests'));
    groups.forEach(({bucket}) => {
      select.append(el('option', {value: `bucket:${bucket}`}, `Bucket: ${bucket}`));
    });
    return select;
  }

  function matchesRowFilter(mode, bucket, stage) {
    if (mode === 'sequencer') {
      return bucket !== 'manual';
    }
    if (mode === 'stable') {
      return bucket !== 'manual' && stage === 'stable';
    }
    if (mode.startsWith('bucket:')) {
      return bucket === mode.slice('bucket:'.length);
    }
    return true;
  }

  function cardEl(device) {
    const card = el('article', {class: 'card', id: anchorOf(device), 'data-device': device.id},
        el('div', {class: 'card-header'},
            imageEl(device, 'card-image', {loading: 'lazy'}),
            el('div', {class: 'card-title'},
                el('h3', {}, device.make),
                el('p', {class: 'card-model'}, device.model)),
            el('span', {class: 'device-id', title: 'Device ID'}, device.id)));
    const shown = device.details.filter(({value}) => value !== '');
    if (shown.length > 0) {
      const list = el('dl', {});
      shown.forEach(({bucket, label, value}) => {
        list.append(el('dt', {}, label), el('dd', {}, ...detailValueEl(bucket, value)));
      });
      card.append(list);
    }
    if (device.notes !== '') {
      card.append(el('h4', {}, 'Notes'), el('p', {class: 'card-notes'}, device.notes));
    }
    card.append(el('a', {class: 'card-back', href: '#results'}, '↑ Back to table'));
    return card;
  }

  function render(root, {generated, updated, devices, groups}) {
    const rowFilter = rowFilterEl(groups);
    const filterInput = el('input', {
      type: 'search',
      id: 'device-filter',
      placeholder: 'Make, model, version...',
      autocomplete: 'off',
    });
    const count = el('span', {class: 'count', role: 'status'});
    const {wrap: matrix, updateScores} = matrixEl(devices, groups);
    const empty = el('p', {class: 'help', hidden: ''}, 'No devices match the filter.');
    const cards = el('div', {class: 'cards'}, ...devices.map(cardEl));
    const allTests = groups.flatMap(({tests}) => tests);

    const applyFilters = () => {
      const terms = filterInput.value.toLowerCase().split(/\s+/).filter((term) => term !== '');
      const matching = new Set(devices
          .filter(({searchText}) => terms.every((term) => searchText.includes(term)))
          .map(({id}) => id));
      root.querySelectorAll('[data-device]').forEach((node) => {
        node.hidden = !matching.has(node.dataset.device);
      });

      const mode = rowFilter.value;
      matrix.querySelectorAll('.group-body').forEach((tbody) => {
        let visibleInGroup = 0;
        tbody.querySelectorAll('tr[data-bucket]').forEach((row) => {
          const show = matchesRowFilter(mode, row.dataset.bucket, row.dataset.stage);
          row.hidden = !show;
          if (show) {
            visibleInGroup += 1;
          }
        });
        tbody.hidden = visibleInGroup === 0;
      });
      updateScores(allTests.filter(({bucket, stage}) => matchesRowFilter(mode, bucket, stage)));

      matrix.querySelectorAll('.group th').forEach((heading) => {
        heading.colSpan = matching.size + 1;
      });
      matrix.hidden = matching.size === 0;
      empty.hidden = matching.size !== 0;
      const total = `${devices.length} ${devices.length === 1 ? 'device' : 'devices'}`;
      count.textContent = matching.size === devices.length ? total : `${matching.size} of ${total}`;
    };
    rowFilter.addEventListener('change', applyFilters);
    filterInput.addEventListener('input', applyFilters);

    const summary = el('p', {class: 'summary'}, 'Results last updated ', dateEl(generated), '.');
    if (updated !== '') {
      summary.append(document.createElement('br'), 'Latest changes: ', updated);
    }
    root.replaceChildren(
        summary,
        el('div', {class: 'toolbar'},
            legendEl(devices, groups),
            el('div', {class: 'controls'},
                el('div', {class: 'control'},
                    el('label', {for: 'row-filter'}, 'Show'), rowFilter),
                el('div', {class: 'control'},
                    el('label', {for: 'device-filter'}, 'Filter devices'), filterInput),
                count)),
        matrix,
        empty,
        el('h2', {id: 'device-details'}, 'Device details'),
        cards);
    applyFilters();
  }

  const root = document.getElementById('results');
  try {
    render(root, parse(window.DEVICE_TEST_RESULTS));
  } catch (error) {
    root.replaceChildren(el('p', {class: 'error', role: 'alert'},
        `The test results cannot be shown: ${error.message}`));
    throw error;
  }
})();
