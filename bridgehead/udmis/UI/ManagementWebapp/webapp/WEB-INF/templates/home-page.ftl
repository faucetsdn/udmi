<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>MQTT Connection Monitor</title>
    <link rel="stylesheet" href="css/styles.css">
</head>
<body>

<div class="tabs" id="mainNav" style="display:flex; align-items:center; justify-content:space-between;">
    <div style="display:flex; gap:10px; align-items:center;">
        <button class="tab active" id="summary-tab">Summary</button>
        <button class="tab" id="edit-tab">Edit Device</button>
        <div class="tab-button-container">
            <button id="registrar-btn" class="tab-button">Run Registrar</button>
        </div>
        <div class="tab-button-container">
            <button id="validator-btn" class="tab-button">Start Validator</button>
        </div>
    </div>
    <span id="message-box" style="color:white; padding-right:50px;"></span>
</div>

<div id="summary-page" class="page${(page == 'summary')?then(' active','')}">
    <div id="dashboard-header">
        <h1>Bridgehead Monitor</h1>
        <h3>MQTT Broker Status: <span id="broker-status"
                                 class="badge badge-${brokerStatusColour}">${mqttConnectionStatus}</span>
            --- Validator Status: <span id="validator-status" class="badge badge-${validatorStatusColour}">${validatorStatus}</span>
        </h3>
    </div>

    <div class="stats-grid">
        <div class="stat-panel">
            <h3>MQTT Active Clients Connected</h3>
            <div id="active-client-count" class="stat-value">${connectedClients}</div>
        </div>

        <div class="stat-panel">
            <h3>Number of Active MQTT Subscriptions</h3>
            <div id="subscription-count" class="stat-value">${subscriptionCount}</div>
        </div>

        <div class="stat-panel">
            <h3>Last Registrar Run </h3>
            <div id="registrar-loading" style="visibility: hidden; display: none;" class="loader"></div>
            <div id="registrar-time" class="stat-value" style="font-size: 1.8em;">${registrarRun}</div>
        </div>
    </div>

    <div class="device-status-panel" style="max-height: 400px;">
        <h2>Device Status <span class="small-text">Showing first 100 devices.</span></h2>

        <div class="text-input search-group">
            <input type="text" id="device-status-search" name="deviceStatusSearch"
                   placeholder="Search by Device Name...">
            <button id="device-status-search-submit" class="submit">Submit</button>
            <button id="device-status-search-clear" class="clear">Clear</button>
        </div>

        <div class="device-list-container">
            <table>
                <thead>
                <tr>
                    <th>Device Name</th>
                    <th>Last Seen</th>
                    <th>Status</th>
                </tr>
                </thead>
                <tbody id="device-status-table-body">
                ${deviceStatusBody}
                </tbody>
            </table>
        </div>
    </div>
</div>

<div id="edit-device-page" class="page${(page == 'edit')?then(' active','')}">
    <div id="edit-page-layout">

        <div class="device-status-panel" style="height: calc(100vh  - 80px); box-sizing: border-box;">

            <div class="text-input search-group">
                <input type="text" id="device-search" name="deviceMetadataSearch"
                       placeholder="Search by Device Name...">
                <button id="device-search-submit" class="submit">Submit</button>
                <button id="device-search-clear" class="clear">Clear</button>
            </div>

            <div class="device-list-container">
                <table>
                    <tbody id="device-table-body"></tbody>
                </table>
            </div>
        </div>
        <div id="left-pane">
            <div id="edit-bottom-layout">
                <button id="save-metadata" disabled>Save</button>
                <button id="reset-metadata" disabled>Reset</button>
            </div>
            <div id="text-area">
                <textarea class="fullscreen-input" id="device-json" disabled></textarea>
            </div>
        </div>
    </div>
</div>

<script src="js/app.js"></script>
</body>
</html>