package com.example.webidle.controller;

import com.example.webidle.dto.GoogleInfResponse;
import com.example.webidle.dto.GoogleRequest;
import com.example.webidle.dto.GoogleResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/oauth2")
public class LoginController {
    @Value("${google.client.id}")
    private String googleClientId;
    
    @Value("${google.client.pw}")
    private String googleClientPw;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/google")
    public ResponseEntity<Map<String, String>> getGoogleLoginUrl() {
        String reqUrl = "https://accounts.google.com/o/oauth2/v2/auth?client_id=" + googleClientId
                + "&redirect_uri=http://localhost:3000/login&response_type=code&scope=email%20profile%20openid&access_type=offline";
        
        Map<String, String> response = new HashMap<>();
        response.put("url", reqUrl);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/google")
    public ResponseEntity<?> loginGoogle(@RequestParam(value = "code") String authCode) {
        try {
            // 토큰 요청
            GoogleRequest googleOAuthRequestParam = GoogleRequest
                    .builder()
                    .clientId(googleClientId)
                    .clientSecret(googleClientPw)
                    .code(authCode)
                    .redirectUri("http://localhost:3000/login")
                    .grantType("authorization_code")
                    .build();

            ResponseEntity<GoogleResponse> tokenResponse = restTemplate.postForEntity(
                "https://oauth2.googleapis.com/token",
                googleOAuthRequestParam, 
                GoogleResponse.class
            );

            if (tokenResponse.getStatusCode() != HttpStatus.OK) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Failed to get access token");
            }

            String jwtToken = tokenResponse.getBody().getId_token();
            
            // 토큰 검증
            Map<String, String> tokenMap = new HashMap<>();
            tokenMap.put("id_token", jwtToken);
            
            ResponseEntity<GoogleInfResponse> userInfoResponse = restTemplate.postForEntity(
                "https://oauth2.googleapis.com/tokeninfo",
                tokenMap, 
                GoogleInfResponse.class
            );

            if (userInfoResponse.getStatusCode() != HttpStatus.OK) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Failed to verify token");
            }

            // 사용자 정보와 토큰 정보를 함께 반환
            Map<String, Object> response = new HashMap<>();
            
            // 사용자 정보
            Map<String, String> userInfo = new HashMap<>();
            userInfo.put("email", userInfoResponse.getBody().getEmail());
            userInfo.put("name", userInfoResponse.getBody().getName());
            userInfo.put("picture", userInfoResponse.getBody().getPicture());
            response.put("user", userInfo);
            
            // 토큰 정보
            response.put("accessToken", tokenResponse.getBody().getAccess_token());
            response.put("refreshToken", tokenResponse.getBody().getRefresh_token());
            response.put("expiresIn", tokenResponse.getBody().getExpires_in());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("An error occurred during authentication: " + e.getMessage());
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> request) {
        try {
            String refreshToken = request.get("refreshToken");
            
            GoogleRequest googleOAuthRequestParam = GoogleRequest
                    .builder()
                    .clientId(googleClientId)
                    .clientSecret(googleClientPw)
                    .refreshToken(refreshToken)
                    .grantType("refresh_token")
                    .build();

            ResponseEntity<GoogleResponse> tokenResponse = restTemplate.postForEntity(
                "https://oauth2.googleapis.com/token",
                googleOAuthRequestParam, 
                GoogleResponse.class
            );

            if (tokenResponse.getStatusCode() != HttpStatus.OK) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Failed to refresh token");
            }

            Map<String, Object> response = new HashMap<>();
            response.put("accessToken", tokenResponse.getBody().getAccess_token());
            response.put("refreshToken", tokenResponse.getBody().getRefresh_token());
            response.put("expiresIn", tokenResponse.getBody().getExpires_in());
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("An error occurred while refreshing token: " + e.getMessage());
        }
    }
}
