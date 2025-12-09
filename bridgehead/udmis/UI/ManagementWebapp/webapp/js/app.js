const socket = new WebSocket('ws://localhost:8080/bridgeheadManager/agent');
const activeCount = document.getElementById("active-client-count");
const subCount = document.getElementById("subscription-count");
const mqttStatus = document.getElementById('broker-status');

socket.onopen = function (e) {
  console.log('[open] Connection established');
};

socket.onmessage = function (event) {
  console.log(`Received data ${event.data}`);
  const data = JSON.parse(event.data);

  if (data && data.subject && data.data) {
    const messageData = data.data;
    switch (data.subject) {
      case "connectedClients": {
        activeCount.textContent = String(messageData);
      } break;
      case "subscriptionCount": {
        subCount.textContent = String(messageData);
      } break;
      case "mqttConnectionStatus": {
        updateMqttStatus(messageData)
      } break;
      case "deviceStatus": {
        const statusTable = document.getElementById('deviceTableBody');
        statusTable.innerHTML = messageData;
      } break;
    }
  };
}

function updateMqttStatus(status) {
  mqttStatus.className = 'badge';

  switch (status) {
    case "Connected": {
      mqttStatus.textContent = "Connected";
      mqttStatus.classList.add('badge-green');
    } break;
    case "Disconnected": {
      mqttStatus.textContent = "Disconnected";
      mqttStatus.classList.add('badge-red');
      activeCount.textContent = "0";
      subCount.textContent = "0";
    } break;
    default: {
      mqttStatus.textContent = "Connecting...";
      mqttStatus.classList.add('badge-yellow');
    }
  }
}