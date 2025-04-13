export const formatErrorMessage = (errorData: any): string => {
  if (typeof errorData === 'string') {
    try {
      const parsedError = JSON.parse(errorData);
      return parseErrorObject(parsedError);
    } catch (e) {
      return errorData;
    }
  }
  return parseErrorObject(errorData);
};

const parseErrorObject = (errorData: any): string => {
  if (errorData.error === '컴파일 오류' && errorData.details) {
    return `컴파일 오류:\n${errorData.details.map((detail: any) =>
      `라인 ${detail.line}${detail.column ? `, 열 ${detail.column}` : ''}: ${detail.message}`
    ).join('\n')}`;
  } else if (errorData.error === '의존성 해결 실패') {
    return `의존성 오류:\n${errorData.message}`;
  } else if (errorData.error === '실행 오류') {
    return `실행 오류:\n${errorData.message}`;
  }
  return errorData.error || errorData.message || JSON.stringify(errorData);
}; 