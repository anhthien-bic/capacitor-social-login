import { SocialLogin } from '@capgo/capacitor-social-login';

window.loginFacebook = async () => {
  try {
    // Initialize the plugin with Facebook credentials
    await SocialLogin.initialize({
      facebook: {
        appId: '1640177526775785',
        clientToken: '621ef94157c7a8e58a0343918e9b6615',
      },
    });
    const result = await SocialLogin.login({
      provider: 'facebook',
      options: {
        permissions: ['email', 'public_profile'],
        limitedLogin: false,
      },
    });

    console.log('Facebook login successful:', result);
    alert('Login successful! User: ' + result.result.profile.name);
  } catch (error) {
    console.error('Facebook login failed:', error);
    alert('Login failed: ' + error.message);
  }
};

window.getProfile = async () => {
  try {
    // Check if the user is logged in before getting profile
    const loginStatus = await SocialLogin.isLoggedIn({
      provider: 'facebook',
    });

    if (!loginStatus.isLoggedIn) {
      alert('Please login first!');
      return;
    }

    // Get the profile information
    const result = await SocialLogin.providerSpecificCall({
      call: 'facebook#getProfile',
      options: {
        fields: ['email'],
      },
    });

    console.log('Profile retrieved:', result);

    // Display profile information
    const profile = result;

    alert('Profile Information:\n' + JSON.stringify(profile, null, 2));
  } catch (error) {
    console.error('Getting profile failed:', error);
    alert('Failed to get profile: ' + error.message);
  }
};

// Example of using forcePrompt to always show account selection
async function loginWithForcePrompt() {
  try {
    const result = await SocialLogin.login({
      provider: 'google',
      options: {
        forcePrompt: true, // Always show account selection prompt
        scopes: ['email', 'profile'],
      },
    });

    console.log('Login with force prompt successful:', result);
    document.getElementById('result').textContent = JSON.stringify(result, null, 2);
  } catch (error) {
    console.error('Login with force prompt failed:', error);
    document.getElementById('result').textContent = `Error: ${error.message}`;
  }
}

// Add button for force prompt login
document.getElementById('loginWithForcePrompt').addEventListener('click', loginWithForcePrompt);
