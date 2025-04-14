# WebIDE Project

웹 기반 통합 개발 환경(IDE) 프로젝트입니다.

## 기술 스택

### 백엔드
- Java: 17
- Spring Boot: 3.2.4
- Spring Dependency Management: 1.1.4
- Maven 관련 라이브러리: 3.3.9
- Lombok: 최신 버전
- JUnit: 5.8.1

### 프론트엔드
- React: 18.2.0
- TypeScript: 4.9.5
- Monaco Editor: 4.7.0
- StompJS: 7.1.1
- Axios: 1.6.7

## 시작하기

### 백엔드 실행
```bash
./gradlew bootRun
```

### 프론트엔드 실행
```bash
cd frontend
npm install
npm start
```

## 주요 기능
- 실시간 코드 실행
- 디버깅 지원
- 파일 시스템 관리
- Maven 의존성 관리 