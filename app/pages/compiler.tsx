import React, { useState } from 'react';
import CompileError from '../components/CompileError';
import { Container, Box, Button, Paper, TextField } from '@mui/material';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';

interface CompileResponse {
  success: boolean;
  output?: string;
  error?: string;
  details?: Array<{
    line: number;
    column: number;
    message: string;
  }>;
}

const CompilerPage: React.FC = () => {
  const [code, setCode] = useState<string>('');
  const [isCompiling, setIsCompiling] = useState(false);
  const [compileResult, setCompileResult] = useState<CompileResponse | null>(null);

  const handleCompile = async () => {
    try {
      setIsCompiling(true);
      
      // API 호출
      const response = await fetch('/api/compile', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ code }),
      });

      const result: CompileResponse = await response.json();
      setCompileResult(result);
      
    } catch (error) {
      setCompileResult({
        success: false,
        error: '컴파일 요청 중 오류가 발생했습니다.',
        details: []
      });
    } finally {
      setIsCompiling(false);
    }
  };

  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Box sx={{ mb: 4 }}>
        <Paper elevation={3} sx={{ p: 2, bgcolor: '#1e1e1e' }}>
          <TextField
            fullWidth
            multiline
            rows={15}
            value={code}
            onChange={(e) => setCode(e.target.value)}
            placeholder="여기에 Java 코드를 입력하세요..."
            variant="outlined"
            disabled={isCompiling}
            sx={{
              '& .MuiInputBase-root': {
                fontFamily: 'monospace',
                fontSize: '14px',
                lineHeight: '1.5',
                color: '#d4d4d4',
                '& fieldset': {
                  borderColor: 'rgba(255, 255, 255, 0.1)',
                },
                '& textarea': {
                  padding: '1rem',
                }
              }
            }}
          />
          <Box sx={{ mt: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Box sx={{ color: '#d4d4d4' }}>
              {compileResult?.success && compileResult.output && (
                <pre style={{ margin: 0, padding: '0.5rem' }}>
                  {compileResult.output}
                </pre>
              )}
            </Box>
            <Button
              variant="contained"
              color="primary"
              startIcon={<PlayArrowIcon />}
              onClick={handleCompile}
              disabled={isCompiling || !code.trim()}
              sx={{ px: 4 }}
            >
              {isCompiling ? '컴파일 중...' : '컴파일 및 실행'}
            </Button>
          </Box>
        </Paper>
      </Box>

      {compileResult && !compileResult.success && (
        <CompileError 
          error={compileResult.error || '알 수 없는 오류가 발생했습니다.'} 
          details={compileResult.details || []}
        />
      )}
    </Container>
  );
};

export default CompilerPage; 