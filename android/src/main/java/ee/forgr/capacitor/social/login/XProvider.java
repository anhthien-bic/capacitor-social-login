package ee.forgr.capacitor.social.login;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

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
    
    private Activity activity;
    private Context context;
    private String clientId;
    private String redirectUrl;
    private String accessToken;
    private String refreshToken;
    
    public XProvider(Activity activity, Context context) {
        this.activity = activity;
        this.context = context;
    }
    
    public void initialize(String clientId, String redirectUrl) {
        this.clientId = clientId;
        this.redirectUrl = redirectUrl;
        loadStoredTokens();
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
        // Clear stored tokens
        clearStoredTokens();
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
        if (accessToken == null || accessToken.isEmpty()) {
            JSObject result = new JSObject();
            result.put("isLoggedIn", false);
            call.resolve(result);
            return;
        }
        
        // Check if token is still valid
        checkTokenValidity(call);
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
        activity.runOnUiThread(() -> {
            // Create WebView for in-app browser
            WebView webView = new WebView(context);
            webView.getSettings().setJavaScriptEnabled(true);
            
            // Create progress bar
            ProgressBar progressBar = new ProgressBar(context);
            progressBar.setIndeterminate(true);
            
            // Create layout
            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.addView(progressBar);
            layout.addView(webView);
            
            // Create dialog
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
            builder.setTitle("Sign in with X");
            builder.setView(layout);
            builder.setCancelable(true);
            
            android.app.AlertDialog dialog = builder.create();
            
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    if (url.startsWith(redirectUrl)) {
                        // Handle OAuth callback
                        handleOAuthCallback(url, call, dialog);
                        return true;
                    }
                    return false;
                }
                
                @Override
                public void onPageFinished(WebView view, String url) {
                    progressBar.setVisibility(android.view.View.GONE);
                }
            });
            
            webView.loadUrl(authUrl);
            dialog.show();
        });
    }
    
    private void handleOAuthCallback(String url, PluginCall call, android.app.AlertDialog dialog) {
        Uri uri = Uri.parse(url);
        String code = uri.getQueryParameter("code");
        String state = uri.getQueryParameter("state");
        String error = uri.getQueryParameter("error");
        
        if (error != null) {
            Log.e(TAG, "OAuth error: " + error);
            dialog.dismiss();
            call.reject("OAuth error: " + error);
            return;
        }
        
        if (code != null && state != null) {
            // Exchange code for token
            exchangeCodeForToken(code, call, dialog);
        } else {
            dialog.dismiss();
            call.reject("Invalid OAuth callback");
        }
    }
    
    private void exchangeCodeForToken(String code, PluginCall call, android.app.AlertDialog dialog) {
        new Thread(() -> {
            try {
                String codeVerifier = getStoredCodeVerifier();
                if (codeVerifier == null) {
                    throw new Exception("No code verifier found");
                }
                
                // Build token request
                String postData = "grant_type=authorization_code" +
                        "&client_id=" + URLEncoder.encode(clientId, "UTF-8") +
                        "&code_verifier=" + URLEncoder.encode(codeVerifier, "UTF-8") +
                        "&code=" + URLEncoder.encode(code, "UTF-8") +
                        "&redirect_uri=" + URLEncoder.encode(redirectUrl, "UTF-8");
                
                // Make token request
                java.net.URL url = new java.net.URL(TOKEN_URL);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                connection.setDoOutput(true);
                
                try (java.io.OutputStream os = connection.getOutputStream()) {
                    byte[] input = postData.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                
                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    // Read response
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    
                    // Parse token response
                    JSONObject tokenResponse = new JSONObject(response.toString());
                    String accessToken = tokenResponse.getString("access_token");
                    String refreshToken = tokenResponse.optString("refresh_token", null);
                    
                    // Get user profile
                    getUserProfile(accessToken, refreshToken, call, dialog);
                    
                } else {
                    throw new Exception("Token request failed with code: " + responseCode);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error exchanging code for token", e);
                activity.runOnUiThread(() -> {
                    dialog.dismiss();
                    call.reject("Failed to exchange code for token: " + e.getMessage());
                });
            }
        }).start();
    }
    
    private void getUserProfile(String accessToken, String refreshToken, PluginCall call, android.app.AlertDialog dialog) {
        try {
            java.net.URL url = new java.net.URL(USER_PROFILE_URL);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                // Read response
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // Parse profile response
                JSONObject profileResponse = new JSONObject(response.toString());
                JSONObject profile = profileResponse.getJSONObject("data");
                
                // Store tokens
                this.accessToken = accessToken;
                this.refreshToken = refreshToken;
                storeTokens(accessToken, refreshToken);
                
                // Clear code verifier
                clearCodeVerifier();
                
                // Return result
                JSObject result = new JSObject();
                result.put("provider", "x");
                
                JSObject resultData = new JSObject();
                JSObject accessTokenObj = new JSObject();
                accessTokenObj.put("token", accessToken);
                if (refreshToken != null) {
                    accessTokenObj.put("refreshToken", refreshToken);
                }
                resultData.put("accessToken", accessTokenObj);
                
                JSObject profileObj = new JSObject();
                profileObj.put("id", profile.optString("id", null));
                profileObj.put("username", profile.optString("username", null));
                profileObj.put("name", profile.optString("name", null));
                profileObj.put("email", profile.optString("email", null));
                profileObj.put("profileImageUrl", profile.optString("profile_image_url", null));
                profileObj.put("verified", profile.optBoolean("verified", false));
                
                resultData.put("profile", profileObj);
                result.put("result", resultData);
                
                activity.runOnUiThread(() -> {
                    dialog.dismiss();
                    call.resolve(result);
                });
                
            } else {
                throw new Exception("Profile request failed with code: " + responseCode);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting user profile", e);
            activity.runOnUiThread(() -> {
                dialog.dismiss();
                call.reject("Failed to get user profile: " + e.getMessage());
            });
        }
    }
    
    private void checkTokenValidity(PluginCall call) {
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(USER_PROFILE_URL);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Authorization", "Bearer " + accessToken);
                
                int responseCode = connection.getResponseCode();
                JSObject result = new JSObject();
                
                if (responseCode == 200) {
                    result.put("isLoggedIn", true);
                } else {
                    clearStoredTokens();
                    result.put("isLoggedIn", false);
                }
                
                call.resolve(result);
                
            } catch (Exception e) {
                Log.e(TAG, "Error checking token validity", e);
                clearStoredTokens();
                JSObject result = new JSObject();
                result.put("isLoggedIn", false);
                call.resolve(result);
            }
        }).start();
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
    
    private void storeTokens(String accessToken, String refreshToken) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("XProvider", Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.putString("access_token", accessToken);
        if (refreshToken != null) {
            editor.putString("refresh_token", refreshToken);
        }
        editor.apply();
    }
    
    private void clearStoredTokens() {
        android.content.SharedPreferences prefs = context.getSharedPreferences("XProvider", Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.remove("access_token");
        editor.remove("refresh_token");
        editor.apply();
        
        this.accessToken = null;
        this.refreshToken = null;
    }
    
    private void loadStoredTokens() {
        android.content.SharedPreferences prefs = context.getSharedPreferences("XProvider", Context.MODE_PRIVATE);
        this.accessToken = prefs.getString("access_token", null);
        this.refreshToken = prefs.getString("refresh_token", null);
    }
} 