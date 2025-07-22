package ee.forgr.capacitor.social.login;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import ee.forgr.capacitor.social.login.helpers.SocialProvider;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.concurrent.CompletableFuture;

public class XProvider implements SocialProvider {
    private static final String TAG = "XProvider";
    private static final String OAUTH_URL = "https://x.com/i/oauth2/authorize";
    private static final String TOKEN_URL = "https://api.x.com/2/oauth2/token";
    private static final String USER_PROFILE_URL = "https://api.x.com/2/users/me";
    private static final int CUSTOM_TAB_REQUEST_CODE = 1001;
    
    private Activity activity;
    private Context context;
    private String clientId;
    private String redirectUrl;
    private String accessToken;
    private String refreshToken;
    private PluginCall currentCall;
    private boolean isHandlingOAuth = false; // Flag to prevent race condition
    
    public XProvider(Activity activity, Context context) {
        this.activity = activity;
        this.context = context;
    }
    
    public void initialize(String clientId, String redirectUrl) {
        this.clientId = clientId;
        this.redirectUrl = redirectUrl;
        // loadStoredTokens();
    }
    
    @Override
    public void login(PluginCall call, JSONObject config) {
        if (clientId == null || clientId.isEmpty()) {
            call.reject("X Client ID not set. Call initialize() first.");
            return;
        }
        
        try {
            // Generate PKCE parameters
            String codeVerifier = generateCodeVerifier();
            String codeChallenge = generateCodeChallenge(codeVerifier);
            String state = generateState();
            
            // Store code verifier for later use
            storeCodeVerifier(codeVerifier);
            
            // Build OAuth URL
            String scopes = "tweet.read users.read offline.access";
            if (config.has("scopes")) {
                scopes = config.getString("scopes");
            }
            
            String authUrl = buildAuthUrl(codeChallenge, state, scopes);
            
            // Show in-app browser
            showInAppBrowser(authUrl, call);
            
        } catch (Exception e) {
            Log.e(TAG, "Error during login", e);
            call.reject("Login failed: " + e.getMessage());
        }
    }
    
    @Override
    public void logout(PluginCall call) {
        clearCodeVerifier();
        call.resolve();
    }
    
    @Override
    public void getAuthorizationCode(PluginCall call) {
        if (accessToken == null || accessToken.isEmpty()) {
            call.reject("No X authorization code available");
            return;
        }
        
        JSObject result = new JSObject();
        result.put("accessToken", accessToken);
        call.resolve(result);
    }
    
    @Override
    public void isLoggedIn(PluginCall call) {
        // For X provider, we don't store tokens locally
        // Backend should handle token validation
        JSObject result = new JSObject();
        result.put("isLoggedIn", false);
        call.resolve(result);
    }
    
    @Override
    public void refresh(PluginCall call) {
        call.reject("Refresh not implemented for X provider");
    }
    
    private String generateCodeVerifier() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] codeVerifier = new byte[32];
        secureRandom.nextBytes(codeVerifier);
        return Base64.encodeToString(codeVerifier, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }
    
    private String generateCodeChallenge(String codeVerifier) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(hash, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }
    
    private String generateState() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] state = new byte[16];
        secureRandom.nextBytes(state);
        return Base64.encodeToString(state, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }
    
    private String buildAuthUrl(String codeChallenge, String state, String scopes) {
        Uri.Builder builder = Uri.parse(OAUTH_URL).buildUpon();
        builder.appendQueryParameter("response_type", "code");
        builder.appendQueryParameter("client_id", clientId);
        builder.appendQueryParameter("redirect_uri", redirectUrl);
        builder.appendQueryParameter("scope", scopes);
        builder.appendQueryParameter("state", state);
        builder.appendQueryParameter("code_challenge", codeChallenge);
        builder.appendQueryParameter("code_challenge_method", "S256");
        
        return builder.build().toString();
    }
    
    private void showInAppBrowser(String authUrl, PluginCall call) {
        this.currentCall = call;
        
        activity.runOnUiThread(() -> {
            try {
                // Create Custom Tabs Intent
                CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
                builder.setToolbarColor(ContextCompat.getColor(context, android.R.color.white));
                builder.setShowTitle(true);
                builder.setUrlBarHidingEnabled(false);
                
                CustomTabsIntent customTabsIntent = builder.build();
                customTabsIntent.intent.setData(Uri.parse(authUrl));
                
                // Launch Custom Tab
                activity.startActivity(customTabsIntent.intent);
                
                // Set timeout to handle case when user closes tab manually
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (currentCall != null) {
                        Log.w(TAG, "OAuth timeout - user may have closed the tab manually");
                        currentCall.reject("OAuth cancelled by user");
                        currentCall = null;
                        clearCodeVerifier();
                    }
                }, 300000); // 5 minutes timeout
                
            } catch (Exception e) {
                Log.e(TAG, "Error launching Custom Tab", e);
                call.reject("Failed to launch browser: " + e.getMessage());
                currentCall = null;
            }
        });
    }
    
    private void handleOAuthCallback(String url) {
        if (currentCall == null || isHandlingOAuth) {
            Log.e(TAG, "No current call available or already handling OAuth");
            return;
        }
        
        isHandlingOAuth = true;
        
        Uri uri = Uri.parse(url);
        String code = uri.getQueryParameter("code");
        String state = uri.getQueryParameter("state");
        String error = uri.getQueryParameter("error");
        
        if (error != null) {
            Log.e(TAG, "OAuth error: " + error);
            currentCall.reject("OAuth error: " + error);
            currentCall = null;
            isHandlingOAuth = false;
            return;
        }
        
        if (code != null && state != null) {
            // Return code and code_verifier for backend to exchange
            String codeVerifier = getStoredCodeVerifier();
            if (codeVerifier == null) {
                currentCall.reject("No code verifier found");
                currentCall = null;
                isHandlingOAuth = false;
                return;
            }
            
            // Clear code verifier
            clearCodeVerifier();
            
            // Return result
            JSObject result = new JSObject();
            result.put("provider", "x");
            
            JSObject resultData = new JSObject();
            resultData.put("token", code);
            resultData.put("code_verifier", codeVerifier);
            
            result.put("result", resultData);
            
            currentCall.resolve(result);
            currentCall = null;
            isHandlingOAuth = false;
        } else {
            currentCall.reject("Invalid OAuth callback");
            currentCall = null;
            isHandlingOAuth = false;
        }
    }
    
    public boolean handleIntent(Intent intent) {
        if (intent != null && intent.getData() != null) {
            String url = intent.getData().toString();
            if (url.startsWith(redirectUrl)) {
                handleOAuthCallback(url);
                return true;
            }
        }
        return false;
    }
    
    /**
     * Handle app resume - check if OAuth was cancelled by user
     */
    public void onAppResume() {
        // If we have a current call but no redirect happened, user probably cancelled
        if (currentCall != null && !isHandlingOAuth) {
            // Check if we have a stored code verifier (meaning OAuth was started)
            String codeVerifier = getStoredCodeVerifier();
            if (codeVerifier != null) {
                // User probably closed the tab manually, clear everything
                Log.w(TAG, "App resumed with pending OAuth call - user may have cancelled");
                currentCall.reject("OAuth cancelled by user");
                currentCall = null;
                clearCodeVerifier();
            }
        }
    }

    private void storeCodeVerifier(String codeVerifier) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("XProvider", Context.MODE_PRIVATE);
        prefs.edit().putString("code_verifier", codeVerifier).apply();
    }
    
    private String getStoredCodeVerifier() {
        android.content.SharedPreferences prefs = context.getSharedPreferences("XProvider", Context.MODE_PRIVATE);
        return prefs.getString("code_verifier", null);
    }
    
    private void clearCodeVerifier() {
        android.content.SharedPreferences prefs = context.getSharedPreferences("XProvider", Context.MODE_PRIVATE);
        prefs.edit().remove("code_verifier").apply();
    }
}
