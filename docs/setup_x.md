# X (Twitter) Provider Setup

This guide will help you set up X (Twitter) authentication using the PKCE OAuth 2.0 flow.

## Prerequisites

1. An X Developer Account
2. A registered X App in the X Developer Portal
3. Capacitor project with this plugin installed

## X Developer Portal Setup

### 1. Create a New App

1. Go to the [X Developer Portal](https://developer.x.com/)
2. Sign in with your X account
3. Click "Create App" or "New App"
4. Fill in the required information:
   - App name
   - App description
   - Website URL
   - Callback URLs (see below)

### 2. Configure OAuth 2.0 Settings

1. In your app settings, go to "User authentication settings"
2. Enable "OAuth 2.0"
3. Set the following:
   - **App type**: Choose "Native App" for mobile apps
   - **Callback URLs**: Add your redirect URLs:
     - For development: `your-app-scheme://oauth/callback`
     - For production: `your-app-scheme://oauth/callback`
   - **Website URL**: Your app's website URL
   - **Client type**: Public

### 3. Get Your Credentials

1. Go to "Keys and tokens" section
2. Copy your **Client ID** (you'll need this for configuration)
3. Note: For PKCE flow, you don't need a Client Secret

## Plugin Configuration

### 1. Initialize the Plugin

```typescript
import { SocialLogin } from '@capacitor-community/social-login';

await SocialLogin.initialize({
  x: {
    clientId: 'your-x-client-id',
    redirectUrl: 'your-app-scheme://oauth/callback'
  }
});
```

### 2. Login with X

```typescript
const result = await SocialLogin.login({
  provider: 'x',
  options: {
    scopes: ['tweet.read', 'users.read', 'offline.access'],
    state: 'optional-custom-state'
  }
});

console.log('Login result:', result);
// {
//   provider: 'x',
//   result: {
//     accessToken: {
//       token: 'access-token-here',
//       refreshToken: 'refresh-token-here'
//     },
//     profile: {
//       id: 'user-id',
//       username: 'username',
//       name: 'Display Name',
//       email: 'user@example.com',
//       profileImageUrl: 'https://...',
//       verified: true
//     }
//   }
// }
```

### 3. Check Login Status

```typescript
const status = await SocialLogin.isLoggedIn({
  provider: 'x'
});

console.log('Is logged in:', status.isLoggedIn);
```

### 4. Get Authorization Code

```typescript
const authCode = await SocialLogin.getAuthorizationCode({
  provider: 'x'
});

console.log('Access token:', authCode.accessToken);
```

### 5. Logout

```typescript
await SocialLogin.logout({
  provider: 'x'
});
```

## Platform-Specific Setup

### Android

1. **Add URL Scheme**: Add your redirect URL scheme to your Android app's manifest or configuration.

2. **Network Security**: Ensure your app can make HTTPS requests to X APIs.

### iOS

1. **URL Scheme**: Add your redirect URL scheme to your iOS app's Info.plist:

```xml
<key>CFBundleURLTypes</key>
<array>
  <dict>
    <key>CFBundleURLName</key>
    <string>your-app-scheme</string>
    <key>CFBundleURLSchemes</key>
    <array>
      <string>your-app-scheme</string>
    </array>
  </dict>
</array>
```

2. **Associated Domains**: If using universal links, add associated domains.

### Web

1. **Redirect URL**: Ensure your redirect URL is properly configured in your web app.
2. **CORS**: Make sure your web app can handle the OAuth callback.

## Available Scopes

The following scopes are available for X API access:

- `tweet.read` - Read tweets and tweet data
- `tweet.write` - Create and manage tweets
- `users.read` - Read user profile information
- `offline.access` - Get refresh tokens for long-term access
- `follows.read` - Read follow relationships
- `follows.write` - Manage follow relationships
- `like.read` - Read like information
- `like.write` - Manage likes
- `dm.read` - Read direct messages
- `dm.write` - Send direct messages
- `block.read` - Read block information
- `block.write` - Manage blocks
- `mute.read` - Read mute information
- `mute.write` - Manage mutes

## Error Handling

Common errors and solutions:

1. **"X Client ID not set"**: Make sure you've initialized the plugin with the correct client ID.
2. **"Invalid redirect URL"**: Ensure your redirect URL matches exactly what's configured in the X Developer Portal.
3. **"OAuth error"**: Check your app configuration in the X Developer Portal.
4. **"No authorization code received"**: The user may have cancelled the authentication flow.

## Security Considerations

1. **PKCE Flow**: This implementation uses PKCE (Proof Key for Code Exchange) which is secure for public clients.
2. **Token Storage**: Tokens are stored securely using platform-specific secure storage.
3. **HTTPS Only**: All API calls are made over HTTPS.
4. **State Parameter**: The state parameter is used to prevent CSRF attacks.

## Troubleshooting

1. **Authentication fails**: Check your client ID and redirect URL configuration.
2. **Callback not working**: Verify your URL scheme is properly configured.
3. **Token refresh issues**: Ensure you're requesting the `offline.access` scope for refresh tokens.
4. **API rate limits**: Be aware of X API rate limits and implement appropriate error handling.

## Example Implementation

Here's a complete example of how to implement X authentication:

```typescript
import { SocialLogin } from '@capacitor-community/social-login';

class XAuthService {
  async initialize() {
    try {
      await SocialLogin.initialize({
        x: {
          clientId: 'your-client-id',
          redirectUrl: 'your-app://oauth/callback'
        }
      });
      console.log('X provider initialized');
    } catch (error) {
      console.error('Failed to initialize X provider:', error);
    }
  }

  async login() {
    try {
      const result = await SocialLogin.login({
        provider: 'x',
        options: {
          scopes: ['tweet.read', 'users.read', 'offline.access']
        }
      });
      
      // Store user data
      this.storeUserData(result.result);
      return result;
    } catch (error) {
      console.error('X login failed:', error);
      throw error;
    }
  }

  async logout() {
    try {
      await SocialLogin.logout({ provider: 'x' });
      this.clearUserData();
    } catch (error) {
      console.error('X logout failed:', error);
    }
  }

  async checkLoginStatus() {
    try {
      const status = await SocialLogin.isLoggedIn({ provider: 'x' });
      return status.isLoggedIn;
    } catch (error) {
      console.error('Failed to check login status:', error);
      return false;
    }
  }

  private storeUserData(result: any) {
    // Store user data in your app's storage
    localStorage.setItem('x_user', JSON.stringify(result));
  }

  private clearUserData() {
    localStorage.removeItem('x_user');
  }
}

// Usage
const xAuth = new XAuthService();
await xAuth.initialize();

// Login
const user = await xAuth.login();
console.log('Logged in user:', user);

// Check status
const isLoggedIn = await xAuth.checkLoginStatus();
console.log('Is logged in:', isLoggedIn);
``` 