import React from 'react';
import { Box, Typography, Paper } from '@mui/material';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';

interface CompileErrorProps {
  details: Array<{
    line: number;
    column: number;
    message: string;
  }>;
  error: string;
}

const CompileError: React.FC<CompileErrorProps> = ({ details, error }) => {
  return (
    <Paper 
      elevation={3} 
      sx={{ 
        p: 3, 
        mt: 2, 
        bgcolor: 'rgba(211, 47, 47, 0.05)',
        border: '1px solid rgba(211, 47, 47, 0.2)'
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', mb: 3 }}>
        <ErrorOutlineIcon color="error" sx={{ mr: 2 }} />
        <Typography variant="h6" color="error">
          {error}
        </Typography>
      </Box>

      <Box sx={{ ml: 4 }}>
        {details.map((detail, index) => (
          <Box key={index} sx={{ mb: 2 }}>
            <Typography color="error" sx={{ fontWeight: 500 }}>
              라인 {detail.line}, 열 {detail.column}
            </Typography>
            <Typography color="error.dark">
              {detail.message}
            </Typography>
          </Box>
        ))}
      </Box>

      <Typography sx={{ mt: 2, color: 'text.secondary' }}>
        발견된 오류: {details.length}개
      </Typography>
    </Paper>
  );
};

export default CompileError; 