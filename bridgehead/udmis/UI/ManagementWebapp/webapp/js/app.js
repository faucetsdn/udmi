
/* Websocket */
const socket = new WebSocket('ws://localhost:8080/bridgeheadManager/agent');
socket.onopen = function (e) {
  console.log('[open] Connection established');
};


const activeCount = document.getElementById("active-client-count");
const subCount = document.getElementById("subscription-count");
const mqttStatus = document.getElementById('broker-status');
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

document.addEventListener('DOMContentLoaded', function () {
  // Search bar groups
  const searchGroups = document.querySelectorAll('.search-group');
  searchGroups.forEach(group => {
    const input = group.querySelector('input[type="text"]');
    const submitButton = group.querySelector('.submit');
    const clearButton = group.querySelector('.clear');

    if (!input) {
      return;
    }

    input.addEventListener("keydown", function (e) {
      if (e.key === "Enter") {
        e.preventDefault();
        searchFunction(input);
      }
    });

    if (submitButton) {
      submitButton.addEventListener('click', function () {
        searchFunction(input);
      });
    }

    if (clearButton) {
      clearButton.addEventListener('click', function () {
        input.value = "";
        searchFunction(input);
      });
    }
  })

});

function searchFunction(input) {
  const name = input.name;
  fetch(`?action=${name}&deviceName=${input.value}`)
    .then(response => response.text())
    .then(html => {

      switch (name) {
        case "deviceStatusSearch": {
          const statusTable = document.getElementById('device-status-table-body');
          statusTable.innerHTML = html;
        } break;
        case "deviceMetadataSearch": {
          const deviceTable = document.getElementById('device-table-body');
          deviceTable.innerHTML = html;
          const getMetadataButtons = document.querySelectorAll('.get-metadata');
          const deviceMetadata = document.getElementById('deviceJson');
          getMetadataButtons.forEach(button => {
            button.addEventListener("click", () => {
              fetch(`?action=getMetadata&path=${button.dataset.path}`)
                .then(response => response.text())
                .then(metadataJson => {
                  const metadataObject = JSON.parse(metadataJson);                  
                  const prettyJsonString = JSON.stringify(metadataObject, null, 2); 
                  deviceMetadata.textContent = prettyJsonString;
                });
            })
          })
        } break;
      }
    })
    .catch(err => console.error("Error getting devices status':", err));
}

const summaryBtn = document.getElementById('summary-tab');
const editBtn = document.getElementById('edit-tab');
const summaryPage = document.getElementById('summary-page');
const editPage = document.getElementById('edit-device-page');
summaryBtn.addEventListener("click", () => {
  console.log("summary tab clicked");
  editPage.classList.remove('active');
  summaryPage.classList.add('active');
})

editBtn.addEventListener("click", () => {
  console.log("edit tab clicked");
  summaryPage.classList.remove('active');
  editPage.classList.add('active');
  const deviceList = document.getElementById('device-search');
  searchFunction(deviceList);
})



