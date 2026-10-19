/**
 * Layer 1 — Physical device connection card for the Local Setup drawer.
 *
 * Pure renderer: takes the payload of `GET /api/testbed/connection` and
 * returns markup. Every value shown comes from that payload, which the backend
 * derives from the broker config, setup scripts and site model; anything it
 * could not determine arrives as 'unknown' and is shown as such.
 */

import { docHref } from './mantis-markdown.js';

const esc = (value) =>
  String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');

const row = (label, value) =>
  `<div class="conn-row"><span class="conn-label">${esc(label)}</span><code class="conn-value">${esc(value)}</code></div>`;

const yesNo = (flag) => (flag === 'unknown' ? 'unknown' : flag ? 'yes' : 'no');

/** Markup for the connection facts, or an explicit error/placeholder state. */
export function renderConnectionCard({ data, error, loading, reason }) {
  if (reason) return `<div class="conn-card conn-empty">${esc(reason)}</div>`;
  if (loading) return '<div class="conn-card conn-empty">Loading connection details…</div>';
  if (error) return `<div class="conn-card conn-error">Connection details unavailable: ${esc(error)}</div>`;

  const { broker, tls, identity, topics, device_key: key, docs, unknowns } = data;
  const hosts = broker.hosts
    .map((h) => `${h.host} (${h.interface}; in server cert: ${yesNo(h.in_server_cert)})`)
    .join('\n');

  return `
    <div class="conn-card">
      <div class="conn-title">Connect an external device to this broker</div>
      ${row('Broker host(s)', hosts || 'unknown')}
      ${row('MQTT port', broker.port)}
      ${row('Transport', tls.used ? 'TLS (ssl)' : 'plain TCP')}
      ${row('Client certificate required', yesNo(tls.client_certificate_required))}
      ${row('CA certificate', `${tls.ca_certificate}${tls.ca_exists ? '' : ' (not created yet)'}`)}
      ${row('Device certificate', tls.device_certificate)}
      ${row('Device private key (PEM)', tls.device_private_key_pem)}
      ${row('Client ID / username', identity.client_id)}
      ${row('Password', identity.password_rule)}
      ${row('Password command', identity.password_command)}
      ${identity.via_gateway ? row('Connects via gateway', identity.via_gateway) : ''}
      ${row('Broker account on start', identity.provisioning_note)}
      ${row('Publish topics', topics.publish.join('\n'))}
      ${row('Subscribe topics', topics.subscribe.join('\n'))}
      ${row('Device key (site model)', key.private_key)}
      <div class="conn-subtitle">Not determined</div>
      <ul class="conn-unknowns">${unknowns.map((u) => `<li>${esc(u)}</li>`).join('')}</ul>
      <div class="conn-subtitle">Docs (paths in this UDMI checkout)</div>
      <ul class="conn-docs">${docs
        .map((d) => {
          const href = docHref(d.path);
          const label = `<code>${esc(d.path)}</code>`;
          const link = href
            ? `<a href="${esc(href)}" target="_blank" rel="noopener noreferrer">${label}</a>`
            : label;
          return `<li>${link} — ${esc(d.title)}</li>`;
        })
        .join('')}</ul>
    </div>
  `;
}
