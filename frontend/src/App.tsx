import React, { useState, useEffect, useRef } from 'react';
import axios from 'axios';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import './App.css';
import CodeEditor from './components/CodeEditor';
import Editor from '@monaco-editor/react';

interface FileNode {
  name: string;
  type: 'file' | 'directory';
  children?: FileNode[];
  path: string;
}

interface CompileError {
  message: string;
  line?: number;
  column?: number;
}

interface ExecuteResponse {
  output: string;
}

function FileTree({ files, onFileSelect }: { files: FileNode[], onFileSelect: (path: string) => void }) {
  return (
    <div className="file-tree">
      {files.map((file) => (
        <FileTreeNode key={file.path} node={file} onFileSelect={onFileSelect} />
      ))}
    </div>
  );
}

function FileTreeNode({ node, onFileSelect }: { node: FileNode, onFileSelect: (path: string) => void }) {
  const [isExpanded, setIsExpanded] = useState(false);

  const handleClick = () => {
    if (node.type === 'directory') {
      setIsExpanded(!isExpanded);
    } else {
      onFileSelect(node.path);
    }
  };

  return (
    <div className="file-tree-node">
      <div className="file-tree-item" onClick={handleClick}>
        {node.type === 'directory' && (
          <span className="folder-icon">{isExpanded ? '📂' : '📁'}</span>
        )}
        {node.type === 'file' && <span className="file-icon">📄</span>}
        <span className="file-name">{node.name}</span>
      </div>
      {node.type === 'directory' && isExpanded && node.children && (
        <div className="file-tree-children">
          {node.children.map((child) => (
            <FileTreeNode key={child.path} node={child} onFileSelect={onFileSelect} />
          ))}
        </div>
      )}
    </div>
  );
}

interface ErrorDialogProps {
  error: string;
  onClose: () => void;
}

const ErrorDialog: React.FC<ErrorDialogProps> = ({ error, onClose }) => {
  const parseError = (errorMessage: string): CompileError[] => {
    const errors: CompileError[] = [];
    
    try {
      // JSON 형식인 경우 파싱 시도
      const errorData = JSON.parse(errorMessage);
      if (errorData.error === '컴파일 오류' && errorData.details) {
        return errorData.details.map((detail: any) => ({
          line: detail.line,
          column: detail.column,
          message: detail.message
        }));
      }
      
      // JSON 파싱은 성공했지만 details가 없는 경우
      if (errorData.error || errorData.message) {
        return [{
          message: errorData.error || errorData.message
        }];
      }
    } catch (e) {
      // JSON 파싱 실패 시 일반 텍스트로 처리
      const lines = errorMessage.split('\n');
      for (const line of lines) {
        // "라인 X, 열 Y: 메시지" 형식 파싱
        const lineMatch = line.match(/라인\s+(\d+),\s*열\s+(\d+):\s*(.+)/);
        if (lineMatch) {
          errors.push({
            line: parseInt(lineMatch[1]),
            column: parseInt(lineMatch[2]),
            message: lineMatch[3]
          });
        }
        // "라인 X: 메시지" 형식 파싱
        else if (line.includes('라인')) {
          const simpleLineMatch = line.match(/라인\s+(\d+):\s*(.+)/);
          if (simpleLineMatch) {
            errors.push({
              line: parseInt(simpleLineMatch[1]),
              message: simpleLineMatch[2]
            });
          }
        }
        // 일반 오류 메시지
        else if (line.trim() && !line.includes('컴파일 오류:')) {
          errors.push({ message: line.trim() });
        }
      }
    }
    
    return errors;
  };

  const errors = parseError(error);
  
  return (
    <div className="error-dialog">
      <div className="error-dialog-content">
        <div className="error-dialog-header">
          <h3>오류 발생</h3>
          <button className="close-btn" onClick={onClose}>&times;</button>
        </div>
        <div className="error-dialog-body">
          {errors.length > 0 ? (
            errors.map((err, index) => (
              <div key={index} className="error-item">
                {err.line && (
                  <div className="error-location">
                    라인 {err.line}{err.column ? `, 열 ${err.column}` : ''}
                  </div>
                )}
                <div className="error-message">{err.message}</div>
              </div>
            ))
          ) : (
            <div className="error-item">
              <div className="error-message">{error}</div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

function App() {
  const [code, setCode] = useState('');
  const [output, setOutput] = useState('');
  const [error, setError] = useState('');
  const [showErrorDialog, setShowErrorDialog] = useState(false);
  const [fileTree, setFileTree] = useState<FileNode[]>([]);
  const [currentFile, setCurrentFile] = useState<string | null>(null);
  const [isDebugging, setIsDebugging] = useState(false);
  const [breakpoints, setBreakpoints] = useState<number[]>([]);
  const [debugStatus, setDebugStatus] = useState('');
  const stompClient = useRef<any>(null);
  const sessionId = useRef<string>(Math.random().toString(36).substring(7));

  useEffect(() => {
    loadFileTree();
    // WebSocket 연결 설정
    const socket = new SockJS('http://localhost:8080/ws');
    const client = new Client({
      webSocketFactory: () => socket,
      onConnect: () => {
        console.log('WebSocket 연결됨');
        
        // 출력 토픽 구독
        client.subscribe(`/topic/output/${sessionId.current}`, (message) => {
          setOutput(message.body);
        });

        // 디버깅 상태 토픽 구독
        client.subscribe(`/topic/debug/${sessionId.current}`, (message) => {
          setDebugStatus(message.body);
        });
        
        // 컴파일 오류 토픽 구독
        client.subscribe(`/topic/error/${sessionId.current}`, (message) => {
          try {
            const errorData = JSON.parse(message.body);
            if (errorData.error === '컴파일 오류' && errorData.details) {
              const errorDetails = errorData.details.map((detail: any) => {
                return `라인 ${detail.line}${detail.column ? `, 열 ${detail.column}` : ''}: ${detail.message}`;
              }).join('\n');
              setError(`컴파일 오류:\n${errorDetails}`);
              setShowErrorDialog(true);
            } else {
              setError(errorData.error || errorData.message || message.body);
              setShowErrorDialog(true);
            }
          } catch (e) {
            setError(message.body);
            setShowErrorDialog(true);
          }
        });
      }
    });

    client.activate();
    stompClient.current = client;

    return () => {
      if (stompClient.current) {
        stompClient.current.deactivate();
      }
    };
  }, []);

  const loadFileTree = async () => {
    try {
      const response = await axios.get<FileNode>('http://localhost:8080/api/files');
      setFileTree([response.data]);
    } catch (err) {
      console.error('파일 트리 로딩 실패:', err);
    }
  };

  const handleFileSelect = async (path: string) => {
    try {
      const response = await axios.get<string>('http://localhost:8080/api/file', {
        params: { path }
      });
      setCode(response.data);
      setCurrentFile(path);
    } catch (err) {
      setError('파일을 불러오는데 실패했습니다.');
    }
  };

  const handleSave = async () => {
    if (!currentFile) {
      const fileName = prompt('파일 이름을 입력하세요:');
      if (!fileName) return;
      setCurrentFile('/src/main/java/' + fileName);
    }

    try {
      await axios.post('http://localhost:8080/api/save', {
        path: currentFile,
        content: code
      });
      loadFileTree(); // 파일 트리 새로고침
      setError('');
    } catch (err) {
      setError('파일 저장에 실패했습니다.');
    }
  };

  const handleDebug = async () => {
    if (breakpoints.length === 0) {
      setError('디버깅을 시작하기 전에 최소 하나의 브레이크포인트를 설정해주세요.');
      setShowErrorDialog(true);
      return;
    }

    setIsDebugging(true);
    setDebugStatus('디버깅을 시작합니다...');
    
    try {
      stompClient.current.publish({
        destination: "/app/debug",
        headers: {
          "session-id": sessionId.current
        },
        body: JSON.stringify({
          code,
          breakpoints,
          sessionId: sessionId.current
        })
      });
    } catch (err: any) {
      setError(err.message || '디버깅 중 오류가 발생했습니다.');
      setShowErrorDialog(true);
      setIsDebugging(false);
    }
  };

  const handleContinue = () => {
    if (stompClient.current) {
      stompClient.current.publish({
        destination: "/app/continue",
        headers: {
          "session-id": sessionId.current
        }
      });
    }
  };

  const handleRun = async () => {
    try {
      setOutput(''); // 출력 초기화
      const response = await axios.post<ExecuteResponse>('http://localhost:8080/api/execute', {
        code: code,
        sessionId: sessionId.current
      });
      
      // 응답이 JSON 문자열인 경우 파싱
      let outputData = response.data.output;
      try {
        const parsedData = JSON.parse(response.data.output);
        if (typeof parsedData === 'object') {
          outputData = JSON.stringify(parsedData, null, 2);
        }
      } catch (e) {
        // JSON 파싱 실패 시 원본 데이터 사용
      }
      
      setOutput(outputData);
      setError('');
    } catch (error: any) {
      let errorMessage = '코드 실행 중 오류가 발생했습니다.';
      
      if (error.response?.data) {
        try {
          // 에러 메시지가 JSON 문자열인 경우 파싱
          const errorData = typeof error.response.data === 'string' 
            ? JSON.parse(error.response.data)
            : error.response.data;
          
          if (errorData.error === '컴파일 오류' && errorData.details) {
            const errorDetails = errorData.details.map((detail: any) => {
              return `라인 ${detail.line}${detail.column ? `, 열 ${detail.column}` : ''}: ${detail.message}`;
            }).join('\n');
            errorMessage = `컴파일 오류:\n${errorDetails}`;
          } else if (errorData.error === '의존성 해결 실패') {
            errorMessage = `의존성 오류:\n${errorData.message}`;
          } else if (errorData.error === '실행 오류') {
            errorMessage = `실행 오류:\n${errorData.message}`;
          } else {
            errorMessage = errorData.error || errorData.message || JSON.stringify(error.response.data);
          }
        } catch (parseError) {
          // JSON 파싱 실패 시 원본 메시지 사용
          errorMessage = typeof error.response.data === 'string' 
            ? error.response.data 
            : JSON.stringify(error.response.data);
        }
      }
      
      setError(errorMessage);
      setShowErrorDialog(true);
      setOutput('');
    }
  };

  const handleEditorChange = (newValue: string) => {
    setCode(newValue);
  };

  return (
    <div className="App">
      <div className="sidebar">
        <div className="sidebar-header">
          <h2>파일 탐색기</h2>
          <button className="new-file-btn" onClick={() => setCurrentFile(null)}>새 파일</button>
        </div>
        <FileTree files={fileTree} onFileSelect={handleFileSelect} />
      </div>
      <div className="main-content">
        <div className="editor-container">
          <div className="editor-header">
            <div className="editor-controls">
              <button onClick={handleRun} className="run-button">
                실행
              </button>
              <button onClick={handleDebug} className="debug-button">
                디버그
              </button>
              <button onClick={handleContinue} className="continue-button">
                계속
              </button>
            </div>
          </div>
          <div className="editor-content">
            <Editor
              height="400px"
              language="java"
              theme="vs-dark"
              value={code}
              onChange={(value) => setCode(value || '')}
              options={{
                minimap: { enabled: false },
                fontSize: 14,
                lineNumbers: 'on',
                roundedSelection: false,
                scrollBeyondLastLine: false,
                readOnly: false,
                automaticLayout: true,
              }}
            />
          </div>
          <div className="output-container">
            <div className="output-header">
              <h3>실행 결과</h3>
            </div>
            <div className="output-content">
              <pre>{output || '실행 결과가 여기에 표시됩니다.'}</pre>
            </div>
          </div>
        </div>
        <div className="debug-status">
          {debugStatus && <div className="status-message">{debugStatus}</div>}
        </div>
        {error && (
          <p className="error">{error}</p>
        )}
        {showErrorDialog && (
          <ErrorDialog 
            error={error} 
            onClose={() => setShowErrorDialog(false)} 
          />
        )}
      </div>
    </div>
  );
}

export default App;
