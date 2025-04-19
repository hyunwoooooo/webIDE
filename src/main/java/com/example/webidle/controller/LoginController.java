package com.example.webidle.controller;

import com.example.webidle.dto.GoogleInfResponse;
import com.example.webidle.dto.GoogleRequest;
import com.example.webidle.dto.GoogleResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

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
    
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String token) {
        try {
            // 토큰에서 Bearer 제거
            String accessToken = token.replace("Bearer ", "");
            
            // Google OAuth 서버에 토큰 무효화 요청
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            
            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("token", accessToken);
            map.add("client_id", googleClientId);
            map.add("client_secret", googleClientPw);
            
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(
                "https://oauth2.googleapis.com/revoke",
                request,
                String.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK) {
                return ResponseEntity.ok().body("로그아웃 성공");
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("로그아웃 실패");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("로그아웃 중 오류 발생: " + e.getMessage());
        }
    }
}
