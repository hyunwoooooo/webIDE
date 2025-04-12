package com.example.webidle.controller;

import com.example.webidle.service.CodeExecutionService;
import org.springframework.web.bind.annotation.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api")
public class CodeExecutionController {
    private final CodeExecutionService codeExecutionService;
    private final String workspaceRoot = "workspace"; // 작업 디렉토리 경로

    public CodeExecutionController(CodeExecutionService codeExecutionService) {
        this.codeExecutionService = codeExecutionService;
        // 작업 디렉토리 생성
        new File(workspaceRoot).mkdirs();
    }

    @PostMapping("/execute")
    public String executeCode(@RequestBody CodeRequest request) {
        return codeExecutionService.executeCode(request.getCode(), request.getSessionId());
    }

    @PostMapping("/debug")
    public Map<String, Object> debugCode(@RequestBody DebugRequest request) {
        String result = codeExecutionService.debugCode(request.getCode(), request.getBreakpoints(), request.getSessionId());
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 이미 JSON 형식인지 확인
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> parsedResult = mapper.readValue(result, Map.class);
            return parsedResult;
        } catch (Exception e) {
            // JSON이 아닌 경우 새로운 형식으로 변환
            response.put("status", "디버깅 시작됨");
            response.put("output", result);
        }
        
        return response;
    }

    @PostMapping("/debug/continue")
    public Map<String, Object> continueDebug(@RequestBody Map<String, String> request) {
        String sessionId = request.get("sessionId");
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 디버깅 상태 확인 및 다음 브레이크포인트까지 실행
            String result = codeExecutionService.continueDebug(sessionId);
            
            try {
                ObjectMapper mapper = new ObjectMapper();
                return mapper.readValue(result, Map.class);
            } catch (Exception e) {
                response.put("status", "디버깅 진행 중");
                response.put("output", result);
            }
        } catch (Exception e) {
            response.put("error", "디버깅 계속 실행 중 오류 발생");
            response.put("message", e.getMessage());
        }
        
        return response;
    }

    @PostMapping("/save")
    public void saveFile(@RequestBody SaveFileRequest request) throws IOException {
        String fullPath = workspaceRoot + request.getPath();
        File file = new File(fullPath);
        file.getParentFile().mkdirs(); // 부모 디렉토리 생성
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(request.getContent());
        }
    }

    @GetMapping("/file")
    public String getFileContent(@RequestParam String path) throws IOException {
        String fullPath = workspaceRoot + path;
        return Files.readString(Paths.get(fullPath));
    }

    @GetMapping("/files")
    public FileNode getFileTree() {
        return createFileTree(new File(workspaceRoot));
    }

    private FileNode createFileTree(File file) {
        FileNode node = new FileNode();
        node.setName(file.getName());
        node.setPath(file.getPath().substring(workspaceRoot.length()));
        
        if (file.isDirectory()) {
            node.setType("directory");
            File[] children = file.listFiles();
            if (children != null) {
                node.setChildren(
                    Arrays.stream(children)
                        .map(this::createFileTree)
                        .collect(Collectors.toList())
                );
            }
        } else {
            node.setType("file");
        }
        
        return node;
    }
}

class CodeRequest {
    private String code;
    private String sessionId;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}

class DebugRequest {
    private String code;
    private List<Integer> breakpoints;
    private String sessionId;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public List<Integer> getBreakpoints() {
        return breakpoints;
    }

    public void setBreakpoints(List<Integer> breakpoints) {
        this.breakpoints = breakpoints;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}

class SaveFileRequest {
    private String path;
    private String content;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}

class FileNode {
    private String name;
    private String type;
    private String path;
    private List<FileNode> children;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public List<FileNode> getChildren() {
        return children;
    }

    public void setChildren(List<FileNode> children) {
        this.children = children;
    }
} 