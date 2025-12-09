<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>MQTT Connection Monitor</title>
    <link rel="stylesheet" href="css/styles.css">
</head>
<body>

<div id="dashboard-header">
    <h1>MQTT Monitor</h1>
    <h3>Broker Status: <span id="broker-status" class="badge badge-${brokerStatusColour}">${mqttConnectionStatus}</span></h3>
</div>

<div class="stats-grid">
    <div class="stat-panel">
        <h3>Active Clients Connected</h3>
        <div id="active-client-count" class="stat-value">${connectedClients}</div>
    </div>

    <div class="stat-panel">
        <h3>Number of Active Subscriptions</h3>
        <div id="subscription-count" class="stat-value">${subscriptionCount}</div>
    </div>
</div>

<div class="device-status-panel">
    <h2>Device Status</h2>

    <input type="text" id="deviceSearch" placeholder="Search by Device Name...">

    <div class="device-list-container">
        <table>
            <thead>
            <tr>
                <th>Device Name</th>
                <th>Last Seen</th>
                <th>Status</th>
            </tr>
            </thead>
            <tbody id="deviceTableBody">
            </tbody>
        </table>
    </div>

</div>

<script src="js/app.js"></script>
</body>
</html>