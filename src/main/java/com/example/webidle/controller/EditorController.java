package com.example.webidle.controller;

import com.example.webidle.service.CodeExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class EditorController {
    
    @Autowired
    private CodeExecutionService codeExecutionService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @GetMapping("/")
    public String editor() {
        return "editor";
    }

    @MessageMapping("/execute")
    public void executeCode(@Payload String code, SimpMessageHeaderAccessor headerAccessor) {
        // 클라이언트에서 전송한 세션 ID 사용
        String sessionId = headerAccessor.getFirstNativeHeader("session-id");
        System.out.println("코드 실행 요청 받음 - 세션 ID: " + sessionId);
        
        try {
            String result = codeExecutionService.executeCode(code);
            System.out.println("실행 결과: " + result);
            
            // 세션 ID가 없는 경우 기본값 설정
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = "default-session";
                System.out.println("세션 ID가 없어 기본값 사용: " + sessionId);
            }
            
            // 메시지 전송 경로 수정
            String destination = "/topic/output/" + sessionId;
            System.out.println("메시지 전송 경로: " + destination);
            messagingTemplate.convertAndSend(destination, result);
            
            // 디버그 정보도 전송
            String debugInfo = "실행 결과:\n" + result;
            messagingTemplate.convertAndSend("/topic/debug/" + sessionId, debugInfo);
            
        } catch (Exception e) {
            System.err.println("코드 실행 중 오류 발생: " + e.getMessage());
            String errorMessage = "오류: " + e.getMessage();
            messagingTemplate.convertAndSend("/topic/output/" + sessionId, errorMessage);
            messagingTemplate.convertAndSend("/topic/debug/" + sessionId, errorMessage);
        }
    }

    @PostMapping("/debug")
    @ResponseBody
    public String debugCode(@RequestBody String code) {
        return codeExecutionService.debugCode(code);
    }
} 