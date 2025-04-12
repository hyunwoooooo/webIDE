package com.example.webidle.controller;

import com.example.webidle.service.CodeExecutionService;
import org.springframework.web.bind.annotation.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
    public String debugCode(@RequestBody DebugRequest request) {
        return codeExecutionService.debugCode(request.getCode(), request.getBreakpoints(), request.getSessionId());
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