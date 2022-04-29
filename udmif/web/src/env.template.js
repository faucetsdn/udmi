(function (window) {
  window.__env = window.__env || {};
  
  // Environment runtime variables.

  // Google client id
  window.__env.googleClientId = '${GOOGLE_CLIENT_ID}';
  
  // Api
  window.__env.apiUri = '${API_URI}';
}(this));
