import React, { useState, useRef, useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Editor from '@monaco-editor/react';
import './App.css';

import FileTree from './components/FileTree';
import ErrorDialog from './components/ErrorDialog';
import Login from './components/Login';
import ProtectedRoute from './components/ProtectedRoute';
import { AuthProvider } from './contexts/AuthContext';
import { useWebSocket } from './hooks/useWebSocket';
import { api } from './services/api';
import { formatErrorMessage } from './utils/errorUtils';
import { FileNode } from './types';

function App() {
  const [code, setCode] = useState(`// @maven org.apache.commons:commons-lang3:3.12.0

import org.apache.commons.lang3.StringUtils;

public class Main {
    public static void main(String[] args) {
        String text = "Hello, World!";
        
        // StringUtils를 사용하여 문자열 조작
        System.out.println("원본 텍스트: " + text);
        System.out.println("거꾸로: " + StringUtils.reverse(text));
        System.out.println("대문자로: " + StringUtils.upperCase(text));
        System.out.println("소문자로: " + StringUtils.lowerCase(text));
    }
}
`);
  const [output, setOutput] = useState('');
  const [error, setError] = useState('');
  const [showErrorDialog, setShowErrorDialog] = useState(false);
  const [fileTree, setFileTree] = useState<FileNode[]>([]);
  const [currentFile, setCurrentFile] = useState<string | null>(null);
  const [isDebugging, setIsDebugging] = useState(false);
  const [breakpoints, setBreakpoints] = useState<number[]>([]);
  const [debugStatus, setDebugStatus] = useState('');
  const editorRef = useRef<any>(null);
  const sessionId = useRef<string>(Math.random().toString(36).substring(7));
  const decorationsRef = useRef<string[]>([]);

  useWebSocket({
    sessionId: sessionId.current,
    onOutput: setOutput,
    onDebugStatus: setDebugStatus,
    onError: setError,
    onShowError: () => setShowErrorDialog(true),
  });

  useEffect(() => {
    loadFileTree();
  }, []);

  const loadFileTree = async () => {
    try {
      const data = await api.getFileTree();
      setFileTree([data]);
    } catch (err) {
      console.error('파일 트리 로딩 실패:', err);
    }
  };

  const handleFileSelect = async (path: string) => {
    try {
      const content = await api.getFile(path);
      setCode(content);
      setCurrentFile(path);
    } catch (err) {
      setError('파일을 불러오는데 실패했습니다.');
      setShowErrorDialog(true);
    }
  };

  const handleSave = async () => {
    if (!currentFile) {
      const fileName = prompt('파일 이름을 입력하세요:');
      if (!fileName) return;
      setCurrentFile('/src/main/java/' + fileName);
    }

    try {
      await api.saveFile(currentFile!, code);
      loadFileTree();
    } catch (err) {
      setError('파일 저장에 실패했습니다.');
      setShowErrorDialog(true);
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
    setOutput('');
    
    try {
      const response = await api.startDebug(code, breakpoints, sessionId.current);
      if (response.error) {
        setError(response.error);
        setShowErrorDialog(true);
        setIsDebugging(false);
      } else {
        setDebugStatus(response.status || '디버깅 진행 중...');
        if (response.output) {
          setOutput(response.output);
        }
      }
    } catch (err: any) {
      setError(err.response?.data?.message || '디버깅 중 오류가 발생했습니다.');
      setShowErrorDialog(true);
      setIsDebugging(false);
    }
  };

  const handleContinue = async () => {
    if (!isDebugging) return;

    try {
      const response = await api.continueDebug(sessionId.current);
      if (response.error) {
        setError(response.error);
        setShowErrorDialog(true);
      } else {
        setDebugStatus(response.status || '디버깅 계속 진행 중...');
        if (response.output) {
          setOutput(prevOutput => prevOutput + '\n' + response.output);
        }
        if (response.finished) {
          setIsDebugging(false);
          setDebugStatus('디버깅이 완료되었습니다.');
        }
      }
    } catch (err: any) {
      setError(err.response?.data?.message || '디버깅 계속 실행 중 오류가 발생했습니다.');
      setShowErrorDialog(true);
    }
  };

  const handleRun = async () => {
    try {
      setOutput('');
      const response = await api.executeCode(code, sessionId.current);
      
      if (response.error) {
        setError(formatErrorMessage(response));
          setShowErrorDialog(true);
      } else {
        setOutput(response.output || '');
        setError('');
      }
    } catch (error: any) {
      setError(formatErrorMessage(error.response?.data));
      setShowErrorDialog(true);
      setOutput('');
    }
  };

  return (
    <AuthProvider>
      <Router>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route
            path="/"
            element={
              <ProtectedRoute>
                <div className="App">
                  <div className="sidebar">
                    <div className="sidebar-header">
                      <h2>파일 탐색기</h2>
                      <button className="new-file-btn" onClick={() => setCurrentFile(null)}>새 파일</button>
                      <button className="save-file-btn" onClick={handleSave}>저장</button>
                    </div>
                    <FileTree files={fileTree} onFileSelect={handleFileSelect} />
                  </div>
                  <div className="main-content">
                    <div className="editor-container">
                      <div className="editor-header">
                        <div className="editor-controls">
                          <button onClick={handleRun} className="run-button" disabled={isDebugging}>실행</button>
                          <button onClick={handleDebug} className="debug-button" disabled={isDebugging}>디버그</button>
                          {isDebugging && <button onClick={handleContinue} className="continue-button">계속</button>}
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
                            glyphMargin: true,
                            lineDecorationsWidth: 0,
                            lineNumbersMinChars: 3,
                            folding: false,
                          }}
                          onMount={(editor, monaco) => {
                            editorRef.current = editor;
                            
                            const updateBreakpointDecorations = (lines: number[]) => {
                              if (!editorRef.current) return;
                              
                              const decorations = lines.map(line => ({
                                range: new monaco.Range(line, 1, line, 1),
                                options: {
                                  isWholeLine: false,
                                  glyphMarginClassName: 'breakpoint-glyph',
                                  glyphMarginHoverMessage: { value: `브레이크포인트 (라인 ${line})` }
                                }
                              }));

                              decorationsRef.current = editorRef.current.deltaDecorations(
                                decorationsRef.current || [],
                                decorations
                              );
                            };

                            editor.onMouseUp((e) => {
                              if (e.target.type === monaco.editor.MouseTargetType.GUTTER_GLYPH_MARGIN) {
                                const lineNumber = e.target.position?.lineNumber;
                                if (lineNumber) {
                                  setBreakpoints(prevBreakpoints => {
                                    const exists = prevBreakpoints.includes(lineNumber);
                                    const newBreakpoints = exists
                                      ? prevBreakpoints.filter(bp => bp !== lineNumber)
                                      : [...prevBreakpoints, lineNumber];

                                    updateBreakpointDecorations(newBreakpoints);
                                    return newBreakpoints;
                                  });
                                }
                              }
                            });

                            if (breakpoints.length > 0) {
                              updateBreakpointDecorations(breakpoints);
                            }

                            editor.onDidChangeModelContent(() => {
                              if (breakpoints.length > 0) {
                                updateBreakpointDecorations(breakpoints);
                              }
                            });
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
                  </div>
                </div>
              </ProtectedRoute>
            }
          />
        </Routes>
      </Router>
      {showErrorDialog && (
        <ErrorDialog
          error={error}
          onClose={() => setShowErrorDialog(false)}
        />
      )}
    </AuthProvider>
  );
}

export default App;
