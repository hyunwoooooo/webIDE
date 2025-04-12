import React, { useRef } from 'react';
import Editor, { Monaco } from '@monaco-editor/react';

interface CodeEditorProps {
  code: string;
  language?: string;
  theme?: string;
  readOnly?: boolean;
  onCodeChange?: (code: string) => void;
  onBreakpointChange?: (breakpoints: number[]) => void;
  breakpoints: number[];
}

const CodeEditor: React.FC<CodeEditorProps> = ({
  code,
  language = 'javascript',
  theme = 'vs-dark',
  readOnly = false,
  onCodeChange,
  onBreakpointChange,
  breakpoints
}) => {
  const editorRef = useRef<any>(null);

  const handleEditorDidMount = (editor: any, monaco: Monaco) => {
    editorRef.current = editor;

    // 디버깅 포인트 설정을 위한 이벤트 리스너
    editor.onMouseDown((e: any) => {
      const target = e.target;
      if (target && target.type === monaco.editor.MouseTargetType.GUTTER_GLYPH_MARGIN) {
        const lineNumber = target.position.lineNumber;
        const currentBreakpoints = editor.getModel()?.getAllDecorations()
          .filter((d: any) => d.options.glyphMarginClassName === 'breakpoint')
          .map((d: any) => d.range.startLineNumber);

        if (currentBreakpoints?.includes(lineNumber)) {
          // 이미 브레이크포인트가 있는 경우 제거
          editor.deltaDecorations(
            editor.getModel()?.getAllDecorations()
              .filter((d: any) => d.range.startLineNumber === lineNumber)
              .map((d: any) => d.id) || [],
            []
          );
          onBreakpointChange?.(breakpoints.filter(bp => bp !== lineNumber));
        } else {
          // 새로운 브레이크포인트 추가
          editor.deltaDecorations([], [{
            range: new monaco.Range(lineNumber, 1, lineNumber, 1),
            options: {
              glyphMarginClassName: 'breakpoint',
              glyphMarginHoverMessage: { value: '브레이크포인트' }
            }
          }]);
          onBreakpointChange?.([...breakpoints, lineNumber]);
        }
      }
    });
  };

  const handleChange = (value: string | undefined) => {
    const newCode = value || '';
    if (onCodeChange) {
      onCodeChange(newCode);
    }
  };

  return (
    <div className="code-editor" id="code-editor">
      <Editor
        height="500px"
        defaultLanguage={language}
        value={code}
        theme={theme}
        onChange={handleChange}
        onMount={handleEditorDidMount}
        options={{
          minimap: { enabled: false },
          fontSize: 14,
          readOnly,
          automaticLayout: true,
          glyphMargin: true,
          lineNumbers: 'on',
          folding: true,
          lineDecorationsWidth: 0,
          lineNumbersMinChars: 3,
          scrollBeyondLastLine: false,
        }}
      />
    </div>
  );
};

export default CodeEditor; 