import { BaseSocialLogin } from './base';
import type { XLoginOptions, LoginResult, ProviderResponseMap, AuthorizationCode } from './definitions';

export class XSocialLogin extends BaseSocialLogin {
  private clientId: string | null = null;
  private redirectUrl?: string;
  private readonly X_STATE_KEY = 'capgo_social_login_x_state';
  private readonly X_CODE_VERIFIER_KEY = 'capgo_social_login_x_code_verifier';

  async initialize(clientId: string | null, redirectUrl?: string): Promise<void> {
    this.clientId = clientId;
    this.redirectUrl = redirectUrl;
  }

  async login<T extends 'x'>(
    options: XLoginOptions,
  ): Promise<{ provider: T; result: ProviderResponseMap[T] }> {
    if (!this.clientId) {
      throw new Error('X Client ID not set. Call initialize() first.');
    }

    let scopes = options.scopes || ['users.email', 'users.read'];

    if (scopes.length === 0) {
        scopes = ['users.email', 'users.read'];
    }

    const state = options.state || Math.random().toString(36).substring(2);
    const codeVerifier = this.generateCodeVerifier();
    const codeChallenge = await this.generateCodeChallenge(codeVerifier);

    // Store code verifier for later use
    this.persistCodeVerifier(codeVerifier);

    return this.pkceOAuth({
      scopes,
      state,
      codeChallenge,
    });
  }

  async logout(): Promise<void> {
    // For X, we can clear local state
    this.clearStateX();
    this.clearCodeVerifier();
  }

  async isLoggedIn(): Promise<{ isLoggedIn: boolean }> {
    const state = this.getXState();
    if (!state) return { isLoggedIn: false };

    try {
      // Check if access token is still valid by making a test API call
      const response = await fetch('https://api.x.com/2/users/me', {
        headers: {
          'Authorization': `Bearer ${state.accessToken}`,
        },
      });

      if (response.ok) {
        return { isLoggedIn: true };
      } else {
        this.clearStateX();
        return { isLoggedIn: false };
      }
    } catch (e) {
      this.clearStateX();
      return { isLoggedIn: false };
    }
  }

  async getAuthorizationCode(): Promise<AuthorizationCode> {
    const state = this.getXState();
    if (!state) throw new Error('No X authorization code available');

    try {
      // Check if access token is still valid
      const response = await fetch('https://api.x.com/2/users/me', {
        headers: {
          'Authorization': `Bearer ${state.accessToken}`,
        },
      });

      if (response.ok) {
        return { accessToken: state.accessToken };
      } else {
        this.clearStateX();
        throw new Error('No X authorization code available');
      }
    } catch (e) {
      this.clearStateX();
      throw new Error('No X authorization code available');
    }
  }

  async refresh(): Promise<void> {
    // For X, we can prompt for re-authentication
    return Promise.reject('Not implemented');
  }

  async handleOAuthRedirect(url: URL): Promise<LoginResult | null> {
    const params = url.searchParams;
    const code = params.get('code');
    const state = params.get('state');
    const error = params.get('error');

    if (error) {
      console.error('X OAuth error:', error);
      return null;
    }

    if (code && state) {
      // Because of CORS issue: https://devcommunity.x.com/t/cors-error-in-oauth2-token/163898/21
      // Backend will exchange the code for access token
      localStorage.removeItem(BaseSocialLogin.OAUTH_STATE_KEY);
      return {
        provider: 'x',
        result: {
          token: code || '',
          code_verifier: this.getCodeVerifier() || '',
        },
      };
    }

    return null;
  }

//   private async exchangeCodeForToken(code: string, clientId: string): Promise<LoginResult | null> {
//     const codeVerifier = this.getCodeVerifier();
//     if (!codeVerifier) {
//       console.error('No code verifier found');
//       return null;
//     }

//     try {
//       const tokenResponse = await fetch('https://api.x.com/2/oauth2/token', {
//         method: 'POST',
//         headers: {
//           'Content-Type': 'application/x-www-form-urlencoded',
//         },
//         body: new URLSearchParams({
//           grant_type: 'authorization_code',
//           client_id: clientId,
//           code_verifier: codeVerifier,
//           code: code,
//           redirect_uri: this.redirectUrl || window.location.origin + window.location.pathname,
//         }),
//       });

//       if (!tokenResponse.ok) {
//         console.error('Failed to exchange code for token:', await tokenResponse.text());
//         return null;
//       }

//       const tokenData = await tokenResponse.json();
//       const { access_token } = tokenData;

//       // Get user profile
//       const profileResponse = await fetch('https://api.x.com/2/users/me', {
//         headers: {
//           'Authorization': `Bearer ${access_token}`,
//         },
//       });

//       if (!profileResponse.ok) {
//         console.error('Failed to get user profile:', await profileResponse.text());
//         return null;
//       }

//       const profileData = await profileResponse.json();
//       const profile = profileData.data;

//       // Store state
//     //   this.persistStateX(access_token, refresh_token);

//       // Clear code verifier
//       this.clearCodeVerifier();
//     //   return null;

//       return {
//         provider: 'x',
//         result: {
//           accessToken: {
//             token: access_token,
//           },
//           profile: {
//             id: profile.id || null,
//             username: profile.username || null,
//             name: profile.name || null,
//             email: profile.email || null,
//             profileImageUrl: profile.profile_image_url || null,
//             verified: profile.verified || null,
//           },
//         },
//       };
//     } catch (error) {
//       console.error('Error exchanging code for token:', error);
//       return null;
//     }
//   }

  private generateCodeVerifier(): string {
    const array = new Uint8Array(32);
    crypto.getRandomValues(array);
    return this.base64URLEncode(array);
  }

  private async generateCodeChallenge(codeVerifier: string): Promise<string> {
    const encoder = new TextEncoder();
    const data = encoder.encode(codeVerifier);
    const digest = await crypto.subtle.digest('SHA-256', data);
    return this.base64URLEncode(new Uint8Array(digest));
  }

  private base64URLEncode(buffer: Uint8Array): string {
    const base64 = btoa(String.fromCharCode(...buffer));
    return base64
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=/g, '');
  }

  private async pkceOAuth<T extends 'x'>({
    scopes,
    state,
    codeChallenge,
  }: XLoginOptions & { state: string; codeChallenge: string }): Promise<{ provider: T; result: ProviderResponseMap[T] }> {
    const params = new URLSearchParams({
      response_type: 'code',
      client_id: this.clientId!,
      redirect_uri: this.redirectUrl || window.location.origin + window.location.pathname,
      scope: (scopes || []).join(' '),
      state: state,
      code_challenge: codeChallenge,
      code_challenge_method: 'S256',
    });

    const url = `https://x.com/i/oauth2/authorize?${params.toString()}`;
    const width = 500;
    const height = 600;
    const left = window.screenX + (window.outerWidth - width) / 2;
    const top = window.screenY + (window.outerHeight - height) / 2;
    
    localStorage.setItem(BaseSocialLogin.OAUTH_STATE_KEY, 'true');
    const popup = window.open(url, 'X Sign In', `width=${width},height=${height},left=${left},top=${top},popup=1`);

    let popupClosedInterval: ReturnType<typeof setInterval>;
    let timeoutHandle: ReturnType<typeof setTimeout>;

    return new Promise((resolve, reject) => {
      if (!popup) {
        reject(new Error('Failed to open popup'));
        return;
      }

      const handleMessage = (event: MessageEvent) => {
        if (event.origin !== window.location.origin || event.data?.source?.startsWith('angular')) return;

        if (event.data?.type === 'oauth-response') {
          window.removeEventListener('message', handleMessage);
          clearInterval(popupClosedInterval);

          const result = event.data;
          if (result.token && result.code_verifier) {
            resolve({
              provider: 'x' as T,
              result: {
                token: result.token,
                code_verifier: result.code_verifier,
              },
            });
          } else {
            reject(new Error('Invalid OAuth response'));
          }
        }
      };

      window.addEventListener('message', handleMessage);

      // Timeout after 5 minutes
      timeoutHandle = setTimeout(() => {
        clearTimeout(timeoutHandle);
        window.removeEventListener('message', handleMessage);
        popup.close();
        reject(new Error('OAuth timeout'));
      }, 300000);

      popupClosedInterval = setInterval(() => {
        if (popup.closed) {
          clearInterval(popupClosedInterval);
          reject(new Error('Popup closed'));
        }
      }, 1000);
    });
  }

//   private persistStateX(accessToken: string, refreshToken?: string) {
//     try {
//       window.localStorage.setItem(this.X_STATE_KEY, JSON.stringify({ accessToken, refreshToken }));
//     } catch (e) {
//       console.error('Cannot persist state X', e);
//     }
//   }

  private clearStateX() {
    try {
      window.localStorage.removeItem(this.X_STATE_KEY);
    } catch (e) {
      console.error('Cannot clear state X', e);
    }
  }

  private getXState(): { accessToken: string; refreshToken?: string } | null {
    try {
      const state = window.localStorage.getItem(this.X_STATE_KEY);
      if (!state) return null;
      const { accessToken, refreshToken } = JSON.parse(state);
      return { accessToken, refreshToken };
    } catch (e) {
      console.error('Cannot get state X', e);
      return null;
    }
  }

  private persistCodeVerifier(codeVerifier: string) {
    try {
      window.localStorage.setItem(this.X_CODE_VERIFIER_KEY, codeVerifier);
    } catch (e) {
      console.error('Cannot persist code verifier', e);
    }
  }

  private clearCodeVerifier() {
    try {
      window.localStorage.removeItem(this.X_CODE_VERIFIER_KEY);
    } catch (e) {
      console.error('Cannot clear code verifier', e);
    }
  }

  private getCodeVerifier(): string | null {
    try {
      return window.localStorage.getItem(this.X_CODE_VERIFIER_KEY);
    } catch (e) {
      console.error('Cannot get code verifier', e);
      return null;
    }
  }
} 