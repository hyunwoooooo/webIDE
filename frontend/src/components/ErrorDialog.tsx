import React from 'react';
import { ErrorDialogProps, CompileError } from '../types';

const ErrorDialog: React.FC<ErrorDialogProps> = ({ error, onClose }) => {
  const parseError = (errorMessage: string): CompileError[] => {
    const errors: CompileError[] = [];
    
    try {
      const errorData = JSON.parse(errorMessage);
      if (errorData.error === '컴파일 오류' && errorData.details) {
        return errorData.details.map((detail: any) => ({
          line: detail.line,
          column: detail.column,
          message: detail.message
        }));
      }
      
      if (errorData.error || errorData.message) {
        return [{
          message: errorData.error || errorData.message
        }];
      }
    } catch (e) {
      const lines = errorMessage.split('\n');
      for (const line of lines) {
        const lineMatch = line.match(/라인\s+(\d+),\s*열\s+(\d+):\s*(.+)/);
        if (lineMatch) {
          errors.push({
            line: parseInt(lineMatch[1]),
            column: parseInt(lineMatch[2]),
            message: lineMatch[3]
          });
        } else if (line.includes('라인')) {
          const simpleLineMatch = line.match(/라인\s+(\d+):\s*(.+)/);
          if (simpleLineMatch) {
            errors.push({
              line: parseInt(simpleLineMatch[1]),
              message: simpleLineMatch[2]
            });
          }
        } else if (line.trim() && !line.includes('컴파일 오류:')) {
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

export default ErrorDialog; 