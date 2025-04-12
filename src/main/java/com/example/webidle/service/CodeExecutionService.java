package com.example.webidle.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.repository.SimpleArtifactDescriptorPolicy;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.DefaultRepositorySystemSession;

import javax.tools.*;
import java.io.*;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class CodeExecutionService {
    private final SimpMessagingTemplate messagingTemplate;
    private final ExecutorService executorService;
    private static final Pattern BREAKPOINT_PATTERN = Pattern.compile("//\\s*breakpoint|//\\s*debug");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("import\\s+([\\w\\.]+);");
    private static final Pattern MAVEN_DEPENDENCY_PATTERN = Pattern.compile("//\\s*@maven\\s+([\\w\\.-]+:[\\w\\.-]+:[\\w\\.-]+)");
    private final File localRepository;
    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession repositorySystemSession;
    private final RemoteRepository mavenCentral;
    private final Map<String, Process> debugProcesses = new ConcurrentHashMap<>();
    private final Map<String, Integer> currentBreakpoints = new ConcurrentHashMap<>();

    public CodeExecutionService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
        this.executorService = Executors.newFixedThreadPool(1);
        this.localRepository = new File(System.getProperty("user.home"), ".m2/repository");
        
        try {
            // Maven Repository System 초기화
            DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
            
            // 필수 서비스 등록
            locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
            locator.addService(TransporterFactory.class, FileTransporterFactory.class);
            locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
            
            // 서비스 로케이터 초기화
            locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
                @Override
                public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                    System.err.println("서비스 생성 실패: " + type.getName() + " -> " + impl.getName());
                    exception.printStackTrace();
                }
            });
            
            this.repositorySystem = locator.getService(RepositorySystem.class);
            if (this.repositorySystem == null) {
                throw new IllegalStateException("RepositorySystem을 초기화할 수 없습니다.");
            }
            
            // Repository System Session 설정
            DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
            
            // 로컬 저장소 설정
            LocalRepository localRepo = new LocalRepository(localRepository);
            session.setLocalRepositoryManager(
                repositorySystem.newLocalRepositoryManager(session, localRepo)
            );
            
            // 의존성 해결 정책 설정
            session.setArtifactDescriptorPolicy(new SimpleArtifactDescriptorPolicy(true, true));
            
            // 오프라인 모드 비활성화
            session.setOffline(false);
            
            this.repositorySystemSession = session;
            
            // Maven Central Repository 설정
            this.mavenCentral = new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2/")
                .setPolicy(new org.eclipse.aether.repository.RepositoryPolicy(true, org.eclipse.aether.repository.RepositoryPolicy.UPDATE_POLICY_DAILY, org.eclipse.aether.repository.RepositoryPolicy.CHECKSUM_POLICY_WARN))
                .build();
                
        } catch (Exception e) {
            throw new RuntimeException("Maven Repository System 초기화 중 오류 발생: " + e.getMessage(), e);
        }
    }

    public String executeCode(String code, String sessionId) {
        System.out.println("코드 실행 시작");
        
        try {
            // Maven 의존성 처리
            List<String> dependencies = extractMavenDependencies(code);
            List<File> dependencyJars = new ArrayList<>();
            for (String dependency : dependencies) {
                try {
                    File jarFile = resolveMavenDependency(dependency);
                    if (jarFile != null) {
                        dependencyJars.add(jarFile);
                    }
                } catch (Exception e) {
                    System.err.println("의존성 해결 실패: " + dependency + " - " + e.getMessage());
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("error", "의존성 해결 실패");
                    errorResponse.put("message", "의존성 '" + dependency + "' 해결 중 오류 발생: " + e.getMessage());
                    String errorJson = new ObjectMapper().writeValueAsString(errorResponse);
                    // WebSocket으로 오류 전송
                    messagingTemplate.convertAndSend("/topic/error/" + sessionId, errorJson);
                    return errorJson;
                }
            }

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

            // 의존성 JAR 파일들을 클래스패스에 추가
            List<String> options = new ArrayList<>();
            if (!dependencyJars.isEmpty()) {
                StringBuilder classPath = new StringBuilder();
                for (File jar : dependencyJars) {
                    if (classPath.length() > 0) {
                        classPath.append(File.pathSeparator);
                    }
                    classPath.append(jar.getAbsolutePath());
                }
                options.addAll(Arrays.asList("-classpath", classPath.toString()));
            }
            
            Iterable<? extends JavaFileObject> compilationUnits = fileManager
                .getJavaFileObjectsFromFiles(Arrays.asList(sourceFile));
            
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);
            boolean success = task.call();
            
            if (!success) {
                Map<String, Object> errorResponse = new HashMap<>();
                List<Map<String, Object>> errorDetails = new ArrayList<>();
                
                for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
                    Map<String, Object> errorDetail = new HashMap<>();
                    errorDetail.put("line", diagnostic.getLineNumber());
                    errorDetail.put("column", diagnostic.getColumnNumber());
                    errorDetail.put("message", diagnostic.getMessage(null));
                    errorDetails.add(errorDetail);
                }
                
                errorResponse.put("error", "컴파일 오류");
                errorResponse.put("details", errorDetails);
                
                String errorJson = new ObjectMapper().writeValueAsString(errorResponse);
                System.err.println("컴파일 오류: " + errorJson);
                
                // WebSocket으로 오류 전송
                messagingTemplate.convertAndSend("/topic/error/" + sessionId, errorJson);
                
                return errorJson;
            }

            // 실행
            List<String> command = new ArrayList<>();
            command.add("java");
            if (!dependencyJars.isEmpty()) {
                command.add("-cp");
                StringBuilder classPath = new StringBuilder(tempDir.getAbsolutePath());
                for (File jar : dependencyJars) {
                    classPath.append(File.pathSeparator).append(jar.getAbsolutePath());
                }
                command.add(classPath.toString());
            } else {
                command.add("-cp");
                command.add(tempDir.getAbsolutePath());
            }
            command.add("Main");

            ProcessBuilder processBuilder = new ProcessBuilder(command);
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

            Map<String, Object> successResponse = new HashMap<>();
            successResponse.put("output", output.toString().trim());
            return new ObjectMapper().writeValueAsString(successResponse);
            
        } catch (Exception e) {
            System.err.println("코드 실행 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
            
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "실행 오류");
            errorResponse.put("message", e.getMessage());
            
            try {
                return new ObjectMapper().writeValueAsString(errorResponse);
            } catch (Exception jsonError) {
                return "{\"error\":\"실행 오류\",\"message\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}";
            }
        }
    }

    private List<String> extractMavenDependencies(String code) {
        List<String> dependencies = new ArrayList<>();
        Matcher matcher = MAVEN_DEPENDENCY_PATTERN.matcher(code);
        while (matcher.find()) {
            dependencies.add(matcher.group(1));
        }
        return dependencies;
    }

    private File resolveMavenDependency(String coordinates) throws Exception {
        String[] parts = coordinates.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("잘못된 Maven 좌표: " + coordinates);
        }

        String groupId = parts[0];
        String artifactId = parts[1];
        String version = parts[2];

        // 로컬 저장소에서 JAR 파일 찾기
        String relativePath = String.format("%s/%s/%s/%s-%s.jar",
            groupId.replace('.', '/'), artifactId, version, artifactId, version);
        File jarFile = new File(localRepository, relativePath);

        if (jarFile.exists()) {
            return jarFile;
        }

        try {
            // Maven Central에서 의존성 다운로드
            DefaultArtifact artifact = new DefaultArtifact(coordinates);
            Dependency dependency = new Dependency(artifact, JavaScopes.COMPILE);

            CollectRequest collectRequest = new CollectRequest();
            collectRequest.setRoot(dependency);
            collectRequest.addRepository(mavenCentral);
            
            DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);

            // 의존성 해결 시도
            repositorySystem.resolveDependencies(repositorySystemSession, dependencyRequest);

            // 파일이 존재하는지 다시 확인
            if (jarFile.exists()) {
                System.out.println("의존성 다운로드 성공: " + coordinates);
                return jarFile;
            }

            // 직접 다운로드 시도
            String mavenCentralUrl = String.format(
                "https://repo.maven.apache.org/maven2/%s/%s/%s/%s-%s.jar",
                groupId.replace('.', '/'), artifactId, version, artifactId, version
            );

            // 부모 디렉토리 생성
            jarFile.getParentFile().mkdirs();

            // 파일 다운로드
            try (InputStream in = new URL(mavenCentralUrl).openStream();
                 FileOutputStream out = new FileOutputStream(jarFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            System.out.println("의존성 직접 다운로드 성공: " + coordinates);
            return jarFile;

        } catch (Exception e) {
            System.err.println("의존성 다운로드 실패: " + coordinates + " - " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("의존성 다운로드 실패: " + coordinates, e);
        }
    }

    public String debugCode(String code, List<Integer> breakpoints, String sessionId) {
        System.out.println("디버깅 시작");
        
        try {
            // 기존 디버그 프로세스 정리
            if (debugProcesses.containsKey(sessionId)) {
                Process oldProcess = debugProcesses.get(sessionId);
                oldProcess.destroyForcibly();
                debugProcesses.remove(sessionId);
            }

            // Maven 의존성 처리
            List<String> dependencies = extractMavenDependencies(code);
            List<File> dependencyJars = new ArrayList<>();
            for (String dependency : dependencies) {
                try {
                    File jarFile = resolveMavenDependency(dependency);
                    if (jarFile != null) {
                        dependencyJars.add(jarFile);
                    }
                } catch (Exception e) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("error", "의존성 해결 실패");
                    errorResponse.put("message", "의존성 '" + dependency + "' 해결 중 오류 발생: " + e.getMessage());
                    return new ObjectMapper().writeValueAsString(errorResponse);
                }
            }

            // 임시 디렉토리 생성
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "webidle_debug_" + sessionId);
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

            // 의존성 JAR 파일들을 클래스패스에 추가
            List<String> options = new ArrayList<>();
            if (!dependencyJars.isEmpty()) {
                StringBuilder classPath = new StringBuilder();
                for (File jar : dependencyJars) {
                    if (classPath.length() > 0) {
                        classPath.append(File.pathSeparator);
                    }
                    classPath.append(jar.getAbsolutePath());
                }
                options.addAll(Arrays.asList("-classpath", classPath.toString()));
            }
            
            Iterable<? extends JavaFileObject> compilationUnits = fileManager
                .getJavaFileObjectsFromFiles(Arrays.asList(sourceFile));
            
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);
            boolean success = task.call();
            
            if (!success) {
                Map<String, Object> errorResponse = new HashMap<>();
                List<Map<String, Object>> errorDetails = new ArrayList<>();
                
                for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
                    Map<String, Object> errorDetail = new HashMap<>();
                    errorDetail.put("line", diagnostic.getLineNumber());
                    errorDetail.put("column", diagnostic.getColumnNumber());
                    errorDetail.put("message", diagnostic.getMessage(null));
                    errorDetails.add(errorDetail);
                }
                
                errorResponse.put("error", "컴파일 오류");
                errorResponse.put("details", errorDetails);
                
                return new ObjectMapper().writeValueAsString(errorResponse);
            }

            // 디버그 모드로 실행
            List<String> command = new ArrayList<>();
            command.add("jdb");
            if (!dependencyJars.isEmpty()) {
                command.add("-classpath");
                StringBuilder classPath = new StringBuilder(tempDir.getAbsolutePath());
                for (File jar : dependencyJars) {
                    classPath.append(File.pathSeparator).append(jar.getAbsolutePath());
                }
                command.add(classPath.toString());
            } else {
                command.add("-classpath");
                command.add(tempDir.getAbsolutePath());
            }
            command.add("Main");

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            debugProcesses.put(sessionId, process);
            
            // 브레이크포인트 설정
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                for (Integer line : breakpoints) {
                    writer.write("stop at Main:" + line + "\n");
                    writer.flush();
                }
                writer.write("run\n");
                writer.flush();
            }

            // 초기 출력 읽기
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null && !line.contains("Breakpoint hit")) {
                    output.append(line).append("\n");
                }
                if (line != null) {
                    output.append(line).append("\n");
                }
            }

            currentBreakpoints.put(sessionId, 0);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "브레이크포인트에 도달");
            response.put("output", output.toString());
            return new ObjectMapper().writeValueAsString(response);
            
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "디버깅 오류");
            errorResponse.put("message", e.getMessage());
            try {
                return new ObjectMapper().writeValueAsString(errorResponse);
            } catch (Exception jsonError) {
                return "{\"error\":\"디버깅 오류\",\"message\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}";
            }
        }
    }

    public String continueDebug(String sessionId) {
        Process process = debugProcesses.get(sessionId);
        if (process == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "디버그 세션이 존재하지 않습니다.");
            try {
                return new ObjectMapper().writeValueAsString(response);
            } catch (Exception e) {
                return "{\"error\":\"디버그 세션이 존재하지 않습니다.\"}";
            }
        }

        try {
            // cont 명령어 전송
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                writer.write("cont\n");
                writer.flush();
            }

            // 출력 읽기
            StringBuilder output = new StringBuilder();
            boolean breakpointHit = false;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    if (line.contains("Breakpoint hit")) {
                        breakpointHit = true;
                        break;
                    }
                    if (line.contains("The application exited")) {
                        Map<String, Object> response = new HashMap<>();
                        response.put("status", "디버깅 완료");
                        response.put("output", output.toString());
                        response.put("finished", true);
                        return new ObjectMapper().writeValueAsString(response);
                    }
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", breakpointHit ? "브레이크포인트에 도달" : "실행 중");
            response.put("output", output.toString());
            return new ObjectMapper().writeValueAsString(response);

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "디버깅 계속 실행 중 오류");
            errorResponse.put("message", e.getMessage());
            try {
                return new ObjectMapper().writeValueAsString(errorResponse);
            } catch (Exception jsonError) {
                return "{\"error\":\"디버깅 계속 실행 중 오류\",\"message\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}";
            }
        }
    }
} 