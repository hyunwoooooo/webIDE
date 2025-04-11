import React, { useState, useEffect } from 'react';
import Editor from '@monaco-editor/react';
import axios from 'axios';
import './App.css';

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

  useEffect(() => {
    loadFileTree();
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

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const response = await axios.post<string>('http://localhost:8080/api/execute', { code });
      
      // 응답이 JSON 문자열인 경우 파싱
      let outputData = response.data;
      try {
        const parsedData = JSON.parse(response.data);
        if (typeof parsedData === 'object') {
          outputData = JSON.stringify(parsedData, null, 2);
        }
      } catch (e) {
        // JSON 파싱 실패 시 원본 데이터 사용
      }
      
      setOutput(outputData);
      setError('');
      setShowErrorDialog(false);
    } catch (err: any) {
      let errorMessage = '코드 실행 중 오류가 발생했습니다.';
      
      if (err.response?.data) {
        try {
          // 에러 메시지가 JSON 문자열인 경우 파싱
          const errorData = typeof err.response.data === 'string' 
            ? JSON.parse(err.response.data)
            : err.response.data;
          
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
            errorMessage = errorData.error || errorData.message || JSON.stringify(err.response.data);
          }
        } catch (parseError) {
          // JSON 파싱 실패 시 원본 메시지 사용
          errorMessage = typeof err.response.data === 'string' 
            ? err.response.data 
            : JSON.stringify(err.response.data);
        }
      }
      
      setError(errorMessage);
      setShowErrorDialog(true);
      setOutput('');
    }
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
        <div className="editor-header">
          <h1>{currentFile || '새 파일'}</h1>
          <div className="editor-actions">
            <button onClick={handleSave} className="save-btn">저장</button>
            <button onClick={handleSubmit} className="run-btn">실행</button>
          </div>
        </div>
        <div className="editor-container">
          <Editor
            height="500px"
            defaultLanguage="java"
            value={code}
            onChange={(value) => setCode(value || '')}
            theme="vs-dark"
            options={{
              minimap: { enabled: true },
              lineNumbers: 'on',
              fontSize: 14,
              automaticLayout: true,
              scrollBeyondLastLine: false,
              folding: true,
              renderLineHighlight: 'all',
            }}
          />
        </div>
        {error && (
          <p className="error">{error}</p>
        )}
        {output && (
          <div className="output">
            <h2>출력:</h2>
            <pre className="output-content">
              {typeof output === 'object' ? JSON.stringify(output, null, 2) : output}
            </pre>
          </div>
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
