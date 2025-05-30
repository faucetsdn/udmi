function requestImport() {
  var requestParams = extractInputValues();
  if (Object.keys(requestParams).length > 0) {
    logOperation(OperationType.REQUEST_IMPORT_SITE_MODEL);
    publishMessageWithAttributesFromSheet(requestParams["project_id"], requestParams["registry_id"], requestParams["udmi_namespace"], requestParams["import_branch"], "import");
  }
}

function requestMerge() {
  var requestParams = extractInputValues();
  if (Object.keys(requestParams).length > 0) {
    logOperation(OperationType.REQUEST_MERGE_SITE_MODEL);
    publishMessageWithAttributesFromSheet(requestParams["project_id"], requestParams["registry_id"], requestParams["udmi_namespace"], requestParams["import_branch"], "merge");
  }
}

function extractInputValues() {
  const targetKeys = [
    "project_id",
    "registry_id",
    "udmi_namespace",
    "import_branch"
  ];
  const extractedValues = {};
  targetKeys.forEach(key => {
    extractedValues[key] = null;
  });

  try {
    const ss = SpreadsheetApp.getActiveSpreadsheet();
    const sheet = ss.getSheetByName(BAMBI_CONFIG_SHEET);

    if (!sheet) {
      Logger.log(`Error: Sheet named "${sheetName}" not found.`);
      SpreadsheetApp.getUi().alert(`Error! Sheet named "${sheetName}" not found. Please ensure it exists.`, SpreadsheetApp.getUi().ButtonSet.OK);
      return {};
    }
    const data = sheet.getDataRange().getValues();
    for (let i = 0; i < data.length; i++) {
      const row = data[i];
      const keyInSheet = String(row[0]).trim();
      const valueInSheet = row[1];
      if (targetKeys.includes(keyInSheet)) {
        extractedValues[keyInSheet] = (valueInSheet !== undefined && valueInSheet !== null && String(valueInSheet).trim() !== "") ? String(valueInSheet).trim() : null;
      }
    }
    Logger.log("Extracted values: " + JSON.stringify(extractedValues));
    return extractedValues;
  } catch (e) {
    Logger.log(`An error occurred: ${e.toString()}`);
    SpreadsheetApp.getUi().alert(`Error! An unexpected error occurred: ${e.toString()}`, SpreadsheetApp.getUi().ButtonSet.OK);
    return extractedValues;
  }
}

function publishMessageWithAttributesFromSheet(projectId, registryId, udmiNamespace, importBranch, requestType) {
  const ui = SpreadsheetApp.getUi();

  if (!projectId) {
    Logger.log('Error: Project ID is missing.');
    logOperation(OperationType.REQUEST_NOT_SENT);
    ui.alert('Project ID is required in bambi_config. Please provide a valid GCP Project ID.', ui.ButtonSet.OK);
    return;
  }
  if (!registryId) {
    Logger.log('Error: Registry ID is missing.');
    logOperation(OperationType.REQUEST_NOT_SENT);
    ui.alert('Registry ID is required in bambi_config. Please provide a valid Registry ID.', ui.ButtonSet.OK);
    return;
  }

  let topicId = BAMBI_REQUESTS_TOPIC;
  if (udmiNamespace) {
    topicId = `${udmiNamespace}~${topicId}`;
  }
  if (!importBranch) {
    importBranch = "main";
  }

  const sourceUrl = SpreadsheetApp.getActiveSpreadsheet().getUrl();
  const userEmail = Session.getActiveUser().getEmail();

  const payloadToWebApp = {
    projectId: projectId,
    registryId: registryId,
    importBranch: importBranch,
    requestType: requestType,
    sourceUrl: sourceUrl,
    userEmail: userEmail,
    topicId: topicId
  };

  try {
    const options = {
      method: 'post',
      contentType: 'application/json',
      payload: JSON.stringify(payloadToWebApp),
      muteHttpExceptions: true,
      headers: {
        'Authorization': 'Bearer ' + ScriptApp.getOAuthToken()
      }
    };

    Logger.log(`[publishMessage] Calling Web App: ${PUBSUB_WEB_APP_URL} with payload: ${JSON.stringify(payloadToWebApp)}`);
    const httpResponse = UrlFetchApp.fetch(PUBSUB_WEB_APP_URL, options);
    const responseText = httpResponse.getContentText();
    const httpResponseCode = httpResponse.getResponseCode();

    let parsedResponse;
    try {
      parsedResponse = JSON.parse(responseText);
    } catch (jsonParseError) {
      Logger.log(`[publishMessage] Error parsing JSON response from Web App. HTTP Code: ${httpResponseCode}. Response: ${responseText}. Error: ${jsonParseError}`);
      logOperation(OperationType.REQUEST_FAILED);
      ui.alert(`Received an invalid response from the publishing service. Please check the logs.\nDetails: ${responseText}`, ui.ButtonSet.OK);
      return;
    }

    Logger.log(`[publishMessage] Web App Response (HTTP ${httpResponseCode}): ${JSON.stringify(parsedResponse)}`);

    if (parsedResponse.status === 'SUCCESS') {
      Logger.log(`[publishMessage] Success from Web App: ${parsedResponse.message}`);
      logOperation(OperationType.REQUEST_SENT);
      ui.alert(`Success! ${parsedResponse.message}\nTopic: "${topicId}"\nDetails: ${JSON.stringify(parsedResponse.details)}`);
    } else if (parsedResponse.status === 'ERROR') {
      Logger.log(`[publishMessage] Error from Web App: Code ${parsedResponse.code}, Message: ${parsedResponse.message}, Details: ${JSON.stringify(parsedResponse.details)}`);
      logOperation(OperationType.REQUEST_FAILED);
      let alertMessage = `Error Publishing Message (Code: ${parsedResponse.code}):\n${parsedResponse.message}`;
      if (parsedResponse.details && parsedResponse.details.missing) {
        alertMessage += `\nMissing parameters: ${parsedResponse.details.missing.join(', ')}`;
      } else if (parsedResponse.details && parsedResponse.details.pubsub_response) {
        alertMessage += `\nPub/Sub Service Details: ${parsedResponse.details.pubsub_response}.`;
      }
      ui.alert(alertMessage, ui.ButtonSet.OK);
    } else {
      Logger.log(`[publishMessage] Unexpected response structure from Web App: ${responseText}`);
      logOperation(OperationType.REQUEST_FAILED);
      ui.alert(`Received an unexpected response format from the publishing service. Response: ${responseText}`, ui.ButtonSet.OK);
    }

  } catch (e) {
    Logger.log(`[publishMessage] Exception during Web App call: ${e.message} Stack: ${e.stack}`);
    logOperation(OperationType.REQUEST_FAILED);
    ui.alert(`An unexpected error occurred while trying to publish the message: ${e.message}`, ui.ButtonSet.OK);
  }
}

