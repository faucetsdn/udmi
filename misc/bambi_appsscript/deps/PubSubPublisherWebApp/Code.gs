/**
 * @fileoverview Script for a Google Apps Script Web App that publishes messages to Google Cloud Pub/Sub.
 */

/**
 * Handles POST requests to the Web App.
 * This function receives a JSON payload via HTTP POST, validates it,
 * and attempts to publish a message to Google Cloud Pub/Sub.
 * @param {GoogleAppsScript.Events.DoPost} e The event object containing the request data.
 * @return {GoogleAppsScript.Content.TextOutput} A JSON response indicating success or failure.
 */
function doPost(e) {
  try {
    // 1. Validate incoming request payload
    if (!e || !e.postData || !e.postData.contents) {
      Logger.log('[Web App doPost] Error: Missing postData in the request.');
      return createErrorResponse('Request payload is missing or malformed.', 400);
    }

    // 2. Parse JSON payload
    let requestData;
    try {
      requestData = JSON.parse(e.postData.contents);
    } catch (parseError) {
      Logger.log(`[Web App doPost] Error parsing JSON: ${parseError.message}. Payload: ${e.postData.contents}`);
      return createErrorResponse(`Invalid JSON payload: ${parseError.message}`, 400, { rawPayload: e.postData.contents });
    }

    // 3. Validate required parameters
    const requiredParams = ['projectId', 'registryId', 'importBranch', 'requestType', 'sourceUrl', 'userEmail', 'topicId'];
    const missingParams = [];
    for (const param of requiredParams) {
      if (!requestData[param]) {
        missingParams.push(param);
      }
    }

    if (missingParams.length > 0) {
      const errorMessage = `Missing required parameters: ${missingParams.join(', ')}.`;
      Logger.log(`[Web App doPost] Validation Error: ${errorMessage} Payload: ${JSON.stringify(requestData)}`);
      return createErrorResponse(errorMessage, 400, { missing: missingParams });
    }

    // 4. Extract validated parameters
    const projectId = requestData.projectId;
    const registryId = requestData.registryId;
    const importBranch = requestData.importBranch;
    const requestType = requestData.requestType;
    const sourceUrl = requestData.sourceUrl;
    const userEmail = requestData.userEmail;
    const topicId = requestData.topicId;

    // 5. Prepare attributes for Pub/Sub message
    const attributes = {
      "project_id": projectId,
      "request_type": requestType,
      "source": sourceUrl,
      "user": userEmail,
      "registry_id": registryId,
      "import_branch": importBranch
    };

    // 6. Publish to Pub/Sub using the internal helper function
    const result = _publishToPubSubInternal(projectId, topicId, attributes);

    if (result.success) {
      return createSuccessResponse(result.message, result.code, result.details);
    } else {
      return createErrorResponse(result.error, result.code, result.details);
    }

  } catch (err) {
    // Catch-all for any unexpected errors during doPost execution
    Logger.log(`[Web App doPost] Unhandled Exception: ${err.message} Stack: ${err.stack}`);
    return createErrorResponse(`An unexpected error occurred: ${err.message}`, 500, { stack: err.stack });
  }
}

/**
 * Internal helper function to handle the Pub/Sub publishing logic.
 * @param {string} projectId The Google Cloud Project ID.
 * @param {string} topicId The Pub/Sub Topic ID.
 * @param {Object} attributes The attributes for the Pub/Sub message.
 * @return {Object} An object indicating success or failure, with details.
 *                  Example: { success: true, message: "...", details: "...", code: 200 }
 *                           { success: false, error: "...", details: "...", code: 500 }
 */
function _publishToPubSubInternal(projectId, topicId, attributes) {
  try {
    const messageBody = ""; // As per original, message data is empty, relying on attributes
    const encodedMessageBody = Utilities.base64Encode(messageBody, Utilities.Charset.UTF_8);

    const pubsubMessage = {
      messages: [
        {
          data: encodedMessageBody,
          attributes: attributes
        }
      ]
    };

    let token;
    try {
      token = ScriptApp.getOAuthToken();
    } catch (authError) {
      Logger.log(`[_publishToPubSubInternal] Error getting OAuth token: ${authError.message} Stack: ${authError.stack}`);
      return {
        success: false,
        error: 'Failed to obtain OAuth token. Please ensure script permissions are granted and enabled.',
        code: 500, // Internal Server Error or could be 401/403 if more context
        details: { error_message: authError.message }
      };
    }

    const pubsubUrl = `https://pubsub.googleapis.com/v1/projects/${projectId}/topics/${topicId}:publish`;
    const options = {
      method: 'post',
      contentType: 'application/json',
      headers: {
        'Authorization': `Bearer ${token}`
      },
      payload: JSON.stringify(pubsubMessage),
      muteHttpExceptions: true // Crucial for capturing detailed API errors
    };

    const response = UrlFetchApp.fetch(pubsubUrl, options);
    const responseCode = response.getResponseCode();
    const responseBody = response.getContentText();

    if (responseCode >= 200 && responseCode < 300) {
      Logger.log(`[_publishToPubSubInternal] Successfully published to ${topicId}. Response Code: ${responseCode}. Body: ${responseBody}`);
      return {
        success: true,
        message: `Message successfully published to topic "${topicId}".`,
        code: responseCode,
        details: { pubsub_response: responseBody }
      };
    } else {
      Logger.log(`[_publishToPubSubInternal] Error publishing to ${topicId}. Code: ${responseCode}. Body: ${responseBody}`);
      return {
        success: false,
        error: `Failed to publish message to topic "${topicId}". Pub/Sub API Error.`,
        code: responseCode, // Use the actual API response code
        details: { pubsub_response: responseBody, request_payload: pubsubMessage }
      };
    }
  } catch (e) {
    // Catch-all for unexpected errors within this helper function
    Logger.log(`[_publishToPubSubInternal] Exception: ${e.message} Stack: ${e.stack}`);
    return {
      success: false,
      error: `An unexpected internal error occurred during publishing: ${e.message}`,
      code: 500,
      details: { error_message: e.message, stack: e.stack }
    };
  }
}

/**
 * Publishes a message to Pub/Sub. This function can be called from other Apps Script code.
 * Note: The original function returned ContentService output. If this is not intended
 * to be an HTTP endpoint, consider returning a plain object or throwing an error.
 * For consistency with the original, it currently returns a `TextOutput`.
 *
 * @param {string} projectId The Google Cloud Project ID.
 * @param {string} registryId The ID of the registry.
 * @param {string} importBranch The branch for import.
 * @param {string} requestType The type of request.
 * @param {string} sourceUrl The source URL.
 * @param {string} userEmail The email of the user.
 * @param {string} topicId The Pub/Sub Topic ID.
 * @return {GoogleAppsScript.Content.TextOutput | Object} A JSON response or an error object.
 */
function publish(projectId, registryId, importBranch, requestType, sourceUrl, userEmail, topicId) {
  // 1. Validate parameters passed to this function
  const missingParams = [];
  if (!projectId) missingParams.push('projectId');
  if (!registryId) missingParams.push('registryId');
  if (!importBranch) missingParams.push('importBranch');
  if (!requestType) missingParams.push('requestType');
  if (!sourceUrl) missingParams.push('sourceUrl');
  if (!userEmail) missingParams.push('userEmail');
  if (!topicId) missingParams.push('topicId');

  if (missingParams.length > 0) {
      const errorMessage = `Missing required parameters in publish function: ${missingParams.join(', ')}.`;
      Logger.log(`[Publish Function] Validation Error: ${errorMessage}`);
      return createErrorResponse(errorMessage, 400, { missing: missingParams });
  }

  try {
    // 2. Prepare attributes
    const attributes = {
      "project_id": projectId,
      "request_type": requestType,
      "source": sourceUrl,
      "user": userEmail,
      "registry_id": registryId,
      "import_branch": importBranch
    };

    // 3. Publish using the internal helper
    const result = _publishToPubSubInternal(projectId, topicId, attributes);

    if (result.success) {
      return createSuccessResponse(result.message, result.code, result.details);
    } else {
      return createErrorResponse(result.error, result.code, result.details);
    }
  } catch (e) {
    // Catch-all for unexpected errors within this function's scope
    Logger.log(`[Publish Function] Exception: ${e.message} Stack: ${e.stack}`);
    return createErrorResponse(`An unexpected error occurred in publish function: ${e.message}`, 500, { stack: e.stack });
  }
}

/**
 * Helper function to create a standardized success JSON response.
 * @param {string} message The success message.
 * @param {number} [code=200] The HTTP-like status code for the operation.
 * @param {Object} [details={}] Additional details about the success.
 * @return {GoogleAppsScript.Content.TextOutput}
 */
function createSuccessResponse(message, code = 200, details = {}) {
  return ContentService.createTextOutput(
    JSON.stringify({
      status: 'SUCCESS',
      code: code,
      message: message,
      details: details
    })
  ).setMimeType(ContentService.MimeType.JSON);
}

/**
 * Helper function to create a standardized error JSON response.
 * @param {string} message The error message.
 * @param {number} [code=500] The HTTP-like status code for the error.
 * @param {Object} [details={}] Additional details about the error.
 * @return {GoogleAppsScript.Content.TextOutput}
 */
function createErrorResponse(message, code = 500, details = {}) {
  // Note: Apps Script Web Apps typically return HTTP 200 even for errors handled this way.
  // The 'code' in the JSON body is for the client to interpret.
  return ContentService.createTextOutput(
    JSON.stringify({
      status: 'ERROR',
      code: code,
      message: message,
      details: details
    })
  ).setMimeType(ContentService.MimeType.JSON);
}

/**
 * Handles GET requests to the Web App.
 * Useful for quickly checking if the web app is deployed and running.
 * @return {GoogleAppsScript.Content.TextOutput}
 */
function doGet() {
  return ContentService.createTextOutput("Web App for Pub/Sub publishing is running. Send a POST request with JSON payload to publish messages.");
}