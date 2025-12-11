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
        <button class="tab" id="summary-tab">Summary</button>
        <button class="tab" id="edit-tab">Edit Device</button>
        <div class="tab-button-container">
            <button id="registrar-btn" class="tab-button">Run Registrar</button>
        </div>
        <div class="tab-button-container">
            <button id="validator-btn" class="tab-button">Start Validator</button>
        </div>
    </div>
</div>

<div id="summary-page" class="page${(page == 'summary')?then(' active','')}">
    <div id="dashboard-header">
        <h1>MQTT Monitor</h1>
        <h3>Broker Status: <span id="broker-status" class="badge badge-${brokerStatusColour}">${mqttConnectionStatus}</span>
         --- Validator Status: <span id="validator-status" class="badge badge-${validatorStatusColour}">${validatorStatus}</span></h3>
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
            <div class="stat-value" style="font-size: 1.8em;">${registrarRun}</div>
        </div>
    </div>

    <!--<div class="stats-grid">-->
    <!--    <button id="run-registrar">Run Registrar</button>-->
    <!--    <button id="start-validator">Start Validator</button>-->
    <!--</div>-->

    <div class="device-status-panel">
        <h2>Device Status <span class="small-text">Showing first 100 devices.</span> </h2>

        <div class="text-input search-group">
            <input type="text" id="device-status-search" name="deviceStatusSearch" placeholder="Search by Device Name...">
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

<div id="edit-device-page" class="page">
    <div id="edit-page-layout">

        <div class="device-status-panel">

            <div class="text-input search-group">
                <input type="text" id="device-search" name="deviceMetadataSearch" placeholder="Search by Device Name...">
                <button id="device-search-submit" class="submit">Submit</button>
                <button id="device-search-clear" class="clear">Clear</button>
            </div>

            <div class="device-list-container">
                <table>
                    <tbody id="device-table-body"></tbody>
                </table>
            </div>
        </div>

        <div id="textArea">
            <textarea class="fullscreen-input" id="deviceJson">heyyy</textarea>
        </div>
    </div>

</div>
<script src="js/app.js"></script>
</body>
</html>