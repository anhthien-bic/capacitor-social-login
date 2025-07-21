import { WebPlugin } from '@capacitor/core';

import { AppleSocialLogin } from './apple-provider';
import type {
  SocialLoginPlugin,
  InitializeOptions,
  LoginOptions,
  AuthorizationCode,
  AuthorizationCodeOptions,
  isLoggedInOptions,
  ProviderResponseMap,
  FacebookLoginOptions,
  XLoginOptions,
  ProviderSpecificCall,
  ProviderSpecificCallOptionsMap,
  ProviderSpecificCallResponseMap,
} from './definitions';
import { FacebookSocialLogin } from './facebook-provider';
import { GoogleSocialLogin } from './google-provider';
import { XSocialLogin } from './x-provider';

export class SocialLoginWeb extends WebPlugin implements SocialLoginPlugin {
  private static readonly OAUTH_STATE_KEY = 'social_login_oauth_pending';

  private googleProvider: GoogleSocialLogin;
  private appleProvider: AppleSocialLogin;
  private facebookProvider: FacebookSocialLogin;
  private xProvider: XSocialLogin;

  constructor() {
    super();

    this.googleProvider = new GoogleSocialLogin();
    this.appleProvider = new AppleSocialLogin();
    this.facebookProvider = new FacebookSocialLogin();
    this.xProvider = new XSocialLogin();

    // Set up listener for OAuth redirects if we have a pending OAuth flow
    if (localStorage.getItem(SocialLoginWeb.OAUTH_STATE_KEY)) {
      console.log('OAUTH_STATE_KEY found');
      this.handleOAuthRedirect().then((result) => {
        if (result) {
          window.opener?.postMessage(
            {
              type: 'oauth-response',
              ...result.result,
            },
            window.location.origin,
          );
          window.close();
        }
      });
    }
    
    // if (localStorage.getItem(SocialLoginWeb.OAUTH_STATE_KEY_X)) {
    //   console.log('OAUTH_STATE_KEY_X found');
    //   this.handleXOAuthRedirectX().then((result) => {
    //     if (result) {
    //       window.opener?.postMessage(
    //         {
    //           type: 'oauth-response',
    //           ...result.result,
    //         },
    //         window.location.origin,
    //       );
    //       window.close();
    //     }
    //   });
    // }
  }

  private async handleOAuthRedirect() {
    const url = new URL(window.location.href);
    const googleResult = this.googleProvider.handleOAuthRedirect(url);
    if (googleResult) return googleResult;
    const xResult = await this.xProvider.handleOAuthRedirect(url);
    if (xResult) return xResult;
    
    return null;
  }

  // private async handleXOAuthRedirectX() {
  //   const url = new URL(window.location.href);
  //   const xResult = await this.xProvider.handleOAuthRedirect(url);
  //   if (xResult) return xResult;
    
  //   return null;
  // }

  async initialize(options: InitializeOptions): Promise<void> {
    const initPromises: Promise<void>[] = [];

    if (options.google?.webClientId) {
      initPromises.push(
        this.googleProvider.initialize(
          options.google.webClientId,
          options.google.mode,
          options.google.hostedDomain,
          options.google.redirectUrl,
        ),
      );
    }

    if (options.apple?.clientId) {
      initPromises.push(this.appleProvider.initialize(options.apple.clientId, options.apple.redirectUrl));
    }

    if (options.facebook?.appId) {
      initPromises.push(this.facebookProvider.initialize(options.facebook.appId));
    }

    if (options.x?.clientId) {
      initPromises.push(this.xProvider.initialize(options.x.clientId, options.x.redirectUrl));
    }

    await Promise.all(initPromises);
  }

  async login<T extends LoginOptions['provider']>(
    options: Extract<LoginOptions, { provider: T }>,
  ): Promise<{ provider: T; result: ProviderResponseMap[T] }> {
    switch (options.provider) {
      case 'google':
        return this.googleProvider.login(options.options) as Promise<{ provider: T; result: ProviderResponseMap[T] }>;
      case 'apple':
        return this.appleProvider.login(options.options) as Promise<{ provider: T; result: ProviderResponseMap[T] }>;
      case 'facebook':
        return this.facebookProvider.login(options.options as FacebookLoginOptions) as Promise<{
          provider: T;
          result: ProviderResponseMap[T];
        }>;
      case 'x':
        return this.xProvider.login(options.options as XLoginOptions) as Promise<{ provider: T; result: ProviderResponseMap[T] }>;
      default:
        throw new Error(`Login for ${options.provider} is not implemented on web`);
    }
  }

  async logout(options: { provider: 'apple' | 'google' | 'facebook' | 'x' }): Promise<void> {
    switch (options.provider) {
      case 'google':
        return this.googleProvider.logout();
      case 'apple':
        return this.appleProvider.logout();
      case 'facebook':
        return this.facebookProvider.logout();
      case 'x':
        return this.xProvider.logout();
      default:
        throw new Error(`Logout for ${options.provider} is not implemented`);
    }
  }

  async isLoggedIn(options: isLoggedInOptions): Promise<{ isLoggedIn: boolean }> {
    switch (options.provider) {
      case 'google':
        return this.googleProvider.isLoggedIn();
      case 'apple':
        return this.appleProvider.isLoggedIn();
      case 'facebook':
        return this.facebookProvider.isLoggedIn();
      case 'x':
        return this.xProvider.isLoggedIn();
      default:
        throw new Error(`isLoggedIn for ${options.provider} is not implemented`);
    }
  }

  async getAuthorizationCode(options: AuthorizationCodeOptions): Promise<AuthorizationCode> {
    switch (options.provider) {
      case 'google':
        return this.googleProvider.getAuthorizationCode();
      case 'apple':
        return this.appleProvider.getAuthorizationCode();
      case 'facebook':
        return this.facebookProvider.getAuthorizationCode();
      case 'x':
        return this.xProvider.getAuthorizationCode();
      default:
        throw new Error(`getAuthorizationCode for ${options.provider} is not implemented`);
    }
  }

  async refresh(options: LoginOptions): Promise<void> {
    switch (options.provider) {
      case 'google':
        return this.googleProvider.refresh();
      case 'apple':
        return this.appleProvider.refresh();
      case 'facebook':
        return this.facebookProvider.refresh(options.options as FacebookLoginOptions);
      case 'x':
        return this.xProvider.refresh();
      default:
        throw new Error(`Refresh for ${(options as any).provider} is not implemented`);
    }
  }

  async providerSpecificCall<T extends ProviderSpecificCall>(options: {
    call: T;
    options: ProviderSpecificCallOptionsMap[T];
  }): Promise<ProviderSpecificCallResponseMap[T]> {
    throw new Error(`Provider specific call for ${options.call} is not implemented`);
  }
}
