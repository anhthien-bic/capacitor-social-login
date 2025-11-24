# X Provider MainActivity Setup

## Vấn đề
Custom Tabs không tự động đóng và gửi result về app. Chúng ta cần setup MainActivity để handle callback URL từ Custom Tabs.

## Setup MainActivity.java

### 1. Cập nhật MainActivity.java

Mở file `android/app/src/main/java/com/your/package/MainActivity.java` và cập nhật như sau:

```java
package com.your.package; // Thay bằng package name của bạn

import com.getcapacitor.BridgeActivity;
import android.content.Intent;
import android.util.Log;

public class MainActivity extends BridgeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Handle OAuth callback khi app được mở từ Custom Tab
        Intent intent = getIntent();
        if (intent != null && intent.getData() != null) {
            String url = intent.getData().toString();
            if (url.startsWith("your-app-scheme://oauth/callback")) {
                // Handle X OAuth callback
                handleXOAuthCallback(intent);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        
        // Handle OAuth callback từ Custom Tab khi app đang chạy
        if (intent != null && intent.getData() != null) {
            String url = intent.getData().toString();
            if (url.startsWith("your-app-scheme://oauth/callback")) {
                handleXOAuthCallback(intent);
            }
        }
    }
    
    private void handleXOAuthCallback(Intent intent) {
        try {
            // Lấy SocialLoginPlugin instance
            com.getcapacitor.PluginHandle pluginHandle = getBridge().getPlugin("SocialLogin");
            if (pluginHandle == null) {
                Log.e("X OAuth", "SocialLogin plugin handle is null");
                return;
            }
            
            com.getcapacitor.Plugin plugin = pluginHandle.getInstance();
            if (!(plugin instanceof ee.forgr.capacitor.social.login.SocialLoginPlugin)) {
                Log.e("X OAuth", "SocialLogin plugin instance is not SocialLoginPlugin");
                return;
            }
            
            // Delegate callback handling cho XProvider
            ((ee.forgr.capacitor.social.login.SocialLoginPlugin) plugin).handleXLoginIntent(intent);
            
        } catch (Exception e) {
            Log.e("X OAuth", "Error handling X OAuth callback", e);
        }
    }
}
```

### 2. Thay đổi cần thiết

1. **Thay `your-app-scheme`** bằng scheme thực tế của app bạn
   - Ví dụ: `myapp://oauth/callback`
   - Hoặc: `com.yourcompany.yourapp://oauth/callback`

2. **Thay `com.your.package`** bằng package name thực tế của app bạn

### 3. Ví dụ hoàn chỉnh

```java
package com.example.myapp;

import com.getcapacitor.BridgeActivity;
import android.content.Intent;
import android.util.Log;

public class MainActivity extends BridgeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Handle OAuth callback khi app được mở từ Custom Tab
        Intent intent = getIntent();
        if (intent != null && intent.getData() != null) {
            String url = intent.getData().toString();
            if (url.startsWith("myapp://oauth/callback")) {
                handleXOAuthCallback(intent);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        
        // Handle OAuth callback từ Custom Tab khi app đang chạy
        if (intent != null && intent.getData() != null) {
            String url = intent.getData().toString();
            if (url.startsWith("myapp://oauth/callback")) {
                handleXOAuthCallback(intent);
            }
        }
    }
    
    private void handleXOAuthCallback(Intent intent) {
        try {
            com.getcapacitor.PluginHandle pluginHandle = getBridge().getPlugin("SocialLogin");
            if (pluginHandle == null) {
                Log.e("X OAuth", "SocialLogin plugin handle is null");
                return;
            }
            
            com.getcapacitor.Plugin plugin = pluginHandle.getInstance();
            if (!(plugin instanceof ee.forgr.capacitor.social.login.SocialLoginPlugin)) {
                Log.e("X OAuth", "SocialLogin plugin instance is not SocialLoginPlugin");
                return;
            }
            
            ((ee.forgr.capacitor.social.login.SocialLoginPlugin) plugin).handleXLoginIntent(intent);
            
        } catch (Exception e) {
            Log.e("X OAuth", "Error handling X OAuth callback", e);
        }
    }
}
```

## Bước 2: Cập nhật AndroidManifest.xml

Đảm bảo MainActivity có intent filter cho callback URL:

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:launchMode="singleTask">
    
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
    
    <!-- Intent filter cho X OAuth callback -->
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="myapp" /> <!-- Thay bằng scheme của bạn -->
    </intent-filter>
</activity>
```

## Bước 3: Cấu hình Plugin

Trong app của bạn, cấu hình X provider với redirect URL khớp với scheme:

```typescript
await SocialLogin.initialize({
  x: {
    clientId: 'your-x-client-id',
    redirectUrl: 'myapp://oauth/callback' // Khớp với scheme trong AndroidManifest.xml
  }
});
```

## Bước 4: Cấu hình X Developer Portal

Trong X Developer Portal, set callback URL là:
```
myapp://oauth/callback
```

## Flow hoạt động

1. **User click login** → Mở Custom Tab với X OAuth URL
2. **User đăng nhập** → User đăng nhập trên X
3. **X redirect** → X redirect về `myapp://oauth/callback?code=xxx&state=xxx`
4. **Android system** → Nhận intent và mở app
5. **MainActivity.onCreate/onNewIntent** → Handle callback URL
6. **SocialLoginPlugin.handleXLoginIntent** → Delegate cho XProvider
7. **XProvider.handleIntent** → Parse code và trả về result
8. **Custom Tab tự đóng** → User quay về app với result

## Testing

### 1. Test callback handling

```typescript
// Test login flow
const result = await SocialLogin.login({
  provider: 'x',
  options: { scopes: ['tweet.read', 'users.read'] }
});

console.log('X login result:', result);
```

### 2. Debug logging

Thêm logging để debug:

```java
Log.d("MainActivity", "Intent received: " + intent.getData());
Log.d("X OAuth", "Callback URL: " + url);
```

## Troubleshooting

### Custom Tab không đóng

- Kiểm tra intent filter trong AndroidManifest.xml
- Đảm bảo scheme khớp với redirect URL
- Kiểm tra MainActivity intent handling

### App không nhận được callback

- Kiểm tra package name và scheme
- Đảm bảo plugin được initialize
- Kiểm tra X Developer Portal callback URL

### App crash khi handle callback

- Kiểm tra null checks
- Đảm bảo plugin instance tồn tại
- Kiểm tra URL format

## So sánh với Google Provider

| Aspect | Google Provider | X Provider |
|--------|----------------|------------|
| **Intent Handling** | `onActivityResult` | `onNewIntent` |
| **Callback URL** | Google scheme | Custom app scheme |
| **Plugin Method** | `handleGoogleLoginIntent` | `handleXLoginIntent` |
| **Setup Complexity** | Medium | Simple |

X provider setup đơn giản hơn Google provider vì không cần handle activity result! 
