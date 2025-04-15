package com.example.webidle.controller;

import com.example.webidle.service.CodeExecutionService;
import com.example.webidle.model.DebugRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class EditorController {
    
    private final CodeExecutionService codeExecutionService;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public EditorController(CodeExecutionService codeExecutionService, SimpMessagingTemplate messagingTemplate) {
        this.codeExecutionService = codeExecutionService;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping("/")
    public String editor() {
        return "editor";
    }

    @MessageMapping("/execute")
    public void executeCode(@Payload String code, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getFirstNativeHeader("session-id");
        System.out.println("코드 실행 요청 받음 - 세션 ID: " + sessionId);
        
        try {
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = "default-session";
                System.out.println("세션 ID가 없어 기본값 사용: " + sessionId);
            }
            
            String result = codeExecutionService.executeCode(code, sessionId);
            System.out.println("실행 결과: " + result);
            
            String destination = "/topic/output/" + sessionId;
            System.out.println("메시지 전송 경로: " + destination);
            messagingTemplate.convertAndSend(destination, result);
            
            String debugInfo = "실행 결과:\n" + result;
            messagingTemplate.convertAndSend("/topic/debug/" + sessionId, debugInfo);
            
        } catch (Exception e) {
            System.err.println("코드 실행 중 오류 발생: " + e.getMessage());
            String errorMessage = "오류: " + e.getMessage();
            messagingTemplate.convertAndSend("/topic/output/" + sessionId, errorMessage);
            messagingTemplate.convertAndSend("/topic/debug/" + sessionId, errorMessage);
        }
    }

    @MessageMapping("/debug")
    public void debugCode(@Payload DebugRequest request, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getFirstNativeHeader("session-id");
        System.out.println("디버깅 요청 받음 - 세션 ID: " + sessionId);
        
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = "default-session";
        }
        
        try {
            messagingTemplate.convertAndSend("/topic/debug/" + sessionId, "디버깅을 시작합니다...");
            
            String result = codeExecutionService.debugCode(request.getCode(), request.getBreakpoints(), sessionId);
            
            messagingTemplate.convertAndSend("/topic/output/" + sessionId, result);
            messagingTemplate.convertAndSend("/topic/debug/" + sessionId, "디버깅이 완료되었습니다.");
            
        } catch (Exception e) {
            System.err.println("디버깅 중 오류 발생: " + e.getMessage());
            String errorMessage = "디버깅 오류: " + e.getMessage();
            messagingTemplate.convertAndSend("/topic/output/" + sessionId, errorMessage);
            messagingTemplate.convertAndSend("/topic/debug/" + sessionId, errorMessage);
        }
    }

    @MessageMapping("/continue")
    public void continueDebugging(SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getFirstNativeHeader("session-id");
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = "default-session";
        }
        
        messagingTemplate.convertAndSend("/topic/debug/" + sessionId, "디버깅을 계속합니다...");
    }
} 