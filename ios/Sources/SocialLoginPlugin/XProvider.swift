import Foundation
import Capacitor
import AuthenticationServices
import CryptoKit
import Security

@objc(XProvider)
public class XProvider: NSObject {
    private static let TAG = "XProvider"
    private static let OAUTH_URL = "https://x.com/i/oauth2/authorize"
    private static let TOKEN_URL = "https://api.x.com/2/oauth2/token"
    private static let USER_PROFILE_URL = "https://api.x.com/2/users/me"
    
    private var clientId: String?
    private var redirectUrl: String?
    private var accessToken: String?
    private var refreshToken: String?
    private var webAuthSession: ASWebAuthenticationSession?
    
   func initialize(clientId: String, redirectUrl: String) {
        self.clientId = clientId
        self.redirectUrl = redirectUrl
        loadStoredTokens()
    }
    
   func login(payload: [String: Any], completion: @escaping (Result<XLoginResponse, Error>) -> Void) {
        guard let clientId = clientId, let redirectUrl = redirectUrl else {
            completion(.failure(XProviderError.notInitialized))
            return
        }
        
        // Generate PKCE parameters
        let codeVerifier = generateCodeVerifier()
        let codeChallenge = generateCodeChallenge(codeVerifier: codeVerifier)
        let state = generateState()
        
        // Store code verifier for later use
        storeCodeVerifier(codeVerifier)
        
        // Build OAuth URL
        let scopes = payload["scopes"] as? String ?? "tweet.read users.read offline.access"
        let authUrl = buildAuthUrl(codeChallenge: codeChallenge, state: state, scopes: scopes)
        
        // Start authentication session
        startAuthenticationSession(authUrl: authUrl, redirectUrl: redirectUrl) { [weak self] result in
            switch result {
            case .success(let code):
                self?.exchangeCodeForToken(code: code, completion: completion)
            case .failure(let error):
                completion(.failure(error))
            }
        }
    }
    
   func logout() {
        clearStoredTokens()
        accessToken = nil
        refreshToken = nil
    }
    
   func getAuthorizationCode() -> String? {
        return accessToken
    }
    
   func isLoggedIn() -> Bool {
        return accessToken != nil && !accessToken!.isEmpty
    }
    
    private func generateCodeVerifier() -> String {
        var bytes = [UInt8](repeating: 0, count: 32)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        let data = Data(bytes)
        return data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
    
    private func generateCodeChallenge(codeVerifier: String) -> String {
        let data = codeVerifier.data(using: .utf8)!
        let hash = SHA256.hash(data: data)
        let hashData = Data(hash)
        return hashData.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
    
    private func generateState() -> String {
        var bytes = [UInt8](repeating: 0, count: 16)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        let data = Data(bytes)
        return data.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }
    
    private func buildAuthUrl(codeChallenge: String, state: String, scopes: String) -> String {
        var components = URLComponents(string: XProvider.OAUTH_URL)!
        components.queryItems = [
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "client_id", value: clientId),
            URLQueryItem(name: "redirect_uri", value: redirectUrl),
            URLQueryItem(name: "scope", value: scopes),
            URLQueryItem(name: "state", value: state),
            URLQueryItem(name: "code_challenge", value: codeChallenge),
            URLQueryItem(name: "code_challenge_method", value: "S256")
        ]
        return components.url!.absoluteString
    }
    
    private func startAuthenticationSession(authUrl: String, redirectUrl: String, completion: @escaping (Result<String, Error>) -> Void) {
        guard let url = URL(string: authUrl) else {
            completion(.failure(XProviderError.invalidUrl))
            return
        }
        
        webAuthSession = ASWebAuthenticationSession(url: url, callbackURLScheme: URL(string: redirectUrl)?.scheme) { [weak self] callbackURL, error in
            if let error = error {
                completion(.failure(error))
                return
            }
            
            guard let callbackURL = callbackURL else {
                completion(.failure(XProviderError.noCallbackUrl))
                return
            }
            
            // Parse callback URL
            let components = URLComponents(url: callbackURL, resolvingAgainstBaseURL: false)
            let code = components?.queryItems?.first { $0.name == "code" }?.value
            let error = components?.queryItems?.first { $0.name == "error" }?.value
            
            if let error = error {
                completion(.failure(XProviderError.oauthError(error)))
                return
            }
            
            if let code = code {
                completion(.success(code))
            } else {
                completion(.failure(XProviderError.noAuthorizationCode))
            }
        }
        
        webAuthSession?.presentationContextProvider = self
        webAuthSession?.start()
    }
    
    private func exchangeCodeForToken(code: String, completion: @escaping (Result<XLoginResponse, Error>) -> Void) {
        guard let codeVerifier = getStoredCodeVerifier() else {
            completion(.failure(XProviderError.noCodeVerifier))
            return
        }
        
        var request = URLRequest(url: URL(string: XProvider.TOKEN_URL)!)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        
        let body = "grant_type=authorization_code" +
            "&client_id=\(clientId!.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)!)" +
            "&code_verifier=\(codeVerifier.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)!)" +
            "&code=\(code.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)!)" +
            "&redirect_uri=\(redirectUrl!.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)!)"
        
        request.httpBody = body.data(using: .utf8)
        
        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            if let error = error {
                completion(.failure(error))
                return
            }
            
            guard let data = data else {
                completion(.failure(XProviderError.noData))
                return
            }
            
            do {
                let tokenResponse = try JSONSerialization.jsonObject(with: data) as! [String: Any]
                let accessToken = tokenResponse["access_token"] as! String
                let refreshToken = tokenResponse["refresh_token"] as? String
                
                // Get user profile
                self?.getUserProfile(accessToken: accessToken, refreshToken: refreshToken, completion: completion)
                
            } catch {
                completion(.failure(error))
            }
        }.resume()
    }
    
    private func getUserProfile(accessToken: String, refreshToken: String?, completion: @escaping (Result<XLoginResponse, Error>) -> Void) {
        var request = URLRequest(url: URL(string: XProvider.USER_PROFILE_URL)!)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        
        URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            if let error = error {
                completion(.failure(error))
                return
            }
            
            guard let data = data else {
                completion(.failure(XProviderError.noData))
                return
            }
            
            do {
                let profileResponse = try JSONSerialization.jsonObject(with: data) as! [String: Any]
                let profile = profileResponse["data"] as! [String: Any]
                
                // Store tokens
                self?.accessToken = accessToken
                self?.refreshToken = refreshToken
                self?.storeTokens(accessToken: accessToken, refreshToken: refreshToken)
                
                // Clear code verifier
                self?.clearCodeVerifier()
                
                // Create response
                let accessTokenObj: [String: Any] = [
                    "token": accessToken,
                    "refreshToken": refreshToken ?? ""
                ]
                
                let profileObj: [String: Any] = [
                    "id": profile["id"] as? String ?? "",
                    "username": profile["username"] as? String ?? "",
                    "name": profile["name"] as? String ?? "",
                    "email": profile["email"] as? String ?? "",
                    "profileImageUrl": profile["profile_image_url"] as? String ?? "",
                    "verified": profile["verified"] as? Bool ?? false
                ]
                
                let response = XLoginResponse(
                    accessToken: accessTokenObj,
                    profile: profileObj
                )
                
                completion(.success(response))
                
            } catch {
                completion(.failure(error))
            }
        }.resume()
    }
    
    private func storeCodeVerifier(_ codeVerifier: String) {
        UserDefaults.standard.set(codeVerifier, forKey: "XProvider_code_verifier")
    }
    
    private func getStoredCodeVerifier() -> String? {
        return UserDefaults.standard.string(forKey: "XProvider_code_verifier")
    }
    
    private func clearCodeVerifier() {
        UserDefaults.standard.removeObject(forKey: "XProvider_code_verifier")
    }
    
    private func storeTokens(accessToken: String, refreshToken: String?) {
        UserDefaults.standard.set(accessToken, forKey: "XProvider_access_token")
        if let refreshToken = refreshToken {
            UserDefaults.standard.set(refreshToken, forKey: "XProvider_refresh_token")
        }
    }
    
    private func clearStoredTokens() {
        UserDefaults.standard.removeObject(forKey: "XProvider_access_token")
        UserDefaults.standard.removeObject(forKey: "XProvider_refresh_token")
    }
    
    private func loadStoredTokens() {
        accessToken = UserDefaults.standard.string(forKey: "XProvider_access_token")
        refreshToken = UserDefaults.standard.string(forKey: "XProvider_refresh_token")
    }
}

extension XProvider: ASWebAuthenticationPresentationContextProviding {
    public func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        return UIApplication.shared.windows.first!
    }
}

// Response types
@objc(XLoginResponse)
public class XLoginResponse: NSObject {
    public let accessToken: [String: Any]
    public let profile: [String: Any]
    
    init(accessToken: [String: Any], profile: [String: Any]) {
        self.accessToken = accessToken
        self.profile = profile
    }
}

// Error types
enum XProviderError: Error, LocalizedError {
    case notInitialized
    case invalidUrl
    case noCallbackUrl
    case noAuthorizationCode
    case noCodeVerifier
    case noData
    case oauthError(String)
    
    var errorDescription: String? {
        switch self {
        case .notInitialized:
            return "X Provider not initialized"
        case .invalidUrl:
            return "Invalid URL"
        case .noCallbackUrl:
            return "No callback URL received"
        case .noAuthorizationCode:
            return "No authorization code received"
        case .noCodeVerifier:
            return "No code verifier found"
        case .noData:
            return "No data received"
        case .oauthError(let error):
            return "OAuth error: \(error)"
        }
    }
} 