// Authorization code for basic dashboard.

var uiConfig = {
  signInSuccessUrl: window.location,
  signInOptions: [firebase.auth.GoogleAuthProvider.PROVIDER_ID]
};

initApp = function() {
  firebase.auth().onAuthStateChanged(function(user) {
    if (user) {
      document.querySelector('body').classList.add('authenticated');
      document.querySelector('body').classList.remove('unauthenticated');
      document.getElementById('user-name').textContent = user.displayName;
      document.getElementById('user-email').textContent = user.email;
      authenticated(user)

      document.getElementById('sign-out').addEventListener('click', function() {
        firebase.auth().signOut();
      });
    } else {
      document.querySelector('body').classList.add('unauthenticated')
      document.querySelector('body').classList.remove('authenticated')
      document.getElementById('user-name').textContent = '';
      document.getElementById('user-email').textContent = '';

      var ui = new firebaseui.auth.AuthUI(firebase.auth());
      ui.start('#firebaseui-auth-container', uiConfig);

      authenticated(null);
    }
  }, function(error) {
    console.log(error);
  });
};

window.addEventListener('load', function() {
  initApp();
});
