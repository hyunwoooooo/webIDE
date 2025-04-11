package com.example.webidle.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import javax.tools.*;
import java.io.*;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.regex.Pattern;

@Service
public class CodeExecutionService {
    private final SimpMessagingTemplate messagingTemplate;
    private final ExecutorService executorService;
    private static final Pattern BREAKPOINT_PATTERN = Pattern.compile("//\\s*breakpoint|//\\s*debug");

    public CodeExecutionService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.executorService = Executors.newFixedThreadPool(1);
    }

    public String executeCode(String code) {
        System.out.println("코드 실행 시작");
        
        try {
            // 임시 디렉토리 생성
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "webidle_" + System.currentTimeMillis());
            tempDir.mkdirs();
            
            // 소스 파일 생성
            File sourceFile = new File(tempDir, "Main.java");
            try (FileWriter writer = new FileWriter(sourceFile)) {
                writer.write(code);
            }

            // 컴파일
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
            
            Iterable<? extends JavaFileObject> compilationUnits = fileManager
                .getJavaFileObjectsFromFiles(Arrays.asList(sourceFile));
            
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, null, null, compilationUnits);
            boolean success = task.call();
            
            if (!success) {
                StringBuilder errors = new StringBuilder();
                for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
                    errors.append(diagnostic.getMessage(null)).append("\n");
                }
                throw new RuntimeException("컴파일 오류:\n" + errors.toString());
            }

            // 실행
            ProcessBuilder processBuilder = new ProcessBuilder("java", "-cp", tempDir.getAbsolutePath(), "Main");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // 출력 읽기
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // 프로세스 종료 대기
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new RuntimeException("실행 시간 초과");
            }

            // 임시 파일 정리
            sourceFile.delete();
            new File(tempDir, "Main.class").delete();
            tempDir.delete();

            return output.toString().trim();
            
        } catch (Exception e) {
            System.err.println("코드 실행 중 오류 발생: " + e.getMessage());
            throw new RuntimeException("코드 실행 중 오류 발생: " + e.getMessage());
        }
    }

    public String debugCode(String code) {
        StringBuilder debugOutput = new StringBuilder();
        String[] lines = code.split("\n");
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (BREAKPOINT_PATTERN.matcher(line).find()) {
                debugOutput.append("브레이크포인트 발견 - 라인 ").append(i + 1).append(": ").append(line.trim()).append("\n");
                
                // 변수 상태 출력 (간단한 구현)
                if (line.contains("int") || line.contains("String") || line.contains("double")) {
                    String[] parts = line.split("=");
                    if (parts.length > 1) {
                        debugOutput.append("  변수 값: ").append(parts[1].trim()).append("\n");
                    }
                }
            }
        }
        
        return debugOutput.toString();
    }
} 