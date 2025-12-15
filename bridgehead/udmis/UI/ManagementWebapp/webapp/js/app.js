/* Websocket */
const socket = new WebSocket('ws://localhost:8080/bridgeheadManager/agent');
socket.onopen = function (e) {
  console.log('[open] Connection established');
};


const activeCount = document.getElementById("active-client-count");
const subCount = document.getElementById("subscription-count");
const mqttStatus = document.getElementById('broker-status');
socket.onmessage = function (event) {
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
          getMetadataButtons.forEach(button => {
            button.addEventListener("click", () => {
              const path = button.dataset.path;
              if (deviceJson.hasAttribute('disabled')) {
                deviceJson.removeAttribute('disabled');
                saveJson.removeAttribute('disabled');
                resetJson.removeAttribute('disabled');
              }
              getMetadataJson(path);
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
  editPage.classList.remove('active');
  summaryPage.classList.add('active');
  editBtn.classList.remove('active');
  summaryBtn.classList.add('active');
})

editBtn.addEventListener("click", () => {
  summaryPage.classList.remove('active');
  editPage.classList.add('active');
  editBtn.classList.add('active');
  summaryBtn.classList.remove('active');
  const deviceList = document.getElementById('device-search');
  searchFunction(deviceList);
})


const deviceJson = document.getElementById('device-json');
deviceJson.addEventListener("keydown", function (e) {
  if (e.key === "Tab") {
    e.preventDefault();

    const start = this.selectionStart;
    const end = this.selectionEnd;

    this.value = this.value.substring(0, start) + "  " + this.value.substring(end);
    this.selectionStart = this.selectionEnd = start + 2;
  }
});


const saveJson = document.getElementById('save-metadata');
saveJson.addEventListener("click", () => {
  try {
    JSON.parse(deviceJson.value);
  } catch (error) {
    showErrorMessage("Not a valid json object: " + error);
    return;
  }

  fetch(`?action=saveMetadata&path=${deviceJson.dataset.path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: deviceJson.value
  }).then(response => {
    if (!response.ok) {
      throw new Error('Bad repsonse whilst daving metadata file: ' + response.statusText);
    }
    return response.json();
  })
    .then(data => {
      const message = data.message;
      if (data.messgeType === 'error') {
        showErrorMessage(message);
      } else {
        showInfoMessage(message);
      }
    })
    .catch(error => {
      showErrorMessage("Error occured whilst attempting save, file may not have been saved successfully")
      console.error('There was a problem with the fetch operation:', error);
    });
})


const resetJson = document.getElementById('reset-metadata');
resetJson.addEventListener("click", () => {
  getMetadataJson(deviceJson.dataset.path);
})


function getMetadataJson(path) {
  fetch(`?action=getMetadata&path=${path}`)
    .then(response => response.text())
    .then(metadataJson => {
      const metadataObject = JSON.parse(metadataJson);
      const prettyJsonString = JSON.stringify(metadataObject, null, 2);
      deviceJson.value = prettyJsonString;
      deviceJson.dataset.path = path;
    });
}

let timeoutId;
function showInfoMessage(message) {
  showMessage(message, "white")
}

function showErrorMessage(message, element = null) {
  showMessage(message, "#ff3030")
}

const messageBox = document.getElementById('message-box');
function showMessage(message, colour) {
  clearTimeout(timeoutId);
  messageBox.textContent = message;
  messageBox.style.color = colour;
  messageBox.classList.add("show");

  timeoutId = setTimeout(() => {
    messageBox.classList.remove("show");
    messageBox.textContent = "";
  }, 5000);
}

const registrarBtn = document.getElementById('registrar-btn');
const registrarTime = document.getElementById('registrar-time');
const registrarLoading = document.getElementById('registrar-loading');
registrarBtn.addEventListener("click", () => {
  show(registrarLoading);
  hide(registrarTime);
  fetch('?action=runRegistrar')
    .then(response => response.text())
    .then(html => {
      const registrarTime = document.getElementById('registrar-time');
      registrarTime.innerHTML = html;
      hide(registrarLoading);
      show(registrarTime);
    })
})

const validatorBtn = document.getElementById('validator-btn');
const validatorStatus = document.getElementById('validator-status');
validatorBtn.addEventListener("click", () => {
  fetch('?action=runValidator')
    .then(response => response.text())
    .then(status => {
      validatorStatus.className = 'badge';
      if (status === "Running") {
        validatorStatus.textContent = status;
        validatorStatus.classList.add('badge-green');
      } else {
        validatorStatus.textContent = status;
        validatorStatus.classList.add('badge-red');
      }
    })
})

function hide(element) {
  element.style.visibility = 'hidden';
  element.style.display = 'none';
}

function show(element) {
  element.style.visibility = 'visible';
  element.style.display = 'block';
}