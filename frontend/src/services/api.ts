import axios from 'axios';
import { FileNode, ApiResponse } from '../types';

const BASE_URL = 'http://localhost:8080/api';

export const api = {
  getFileTree: async (): Promise<FileNode> => {
    const response = await axios.get<FileNode>(`${BASE_URL}/files`);
    return response.data;
  },

  getFile: async (path: string): Promise<string> => {
    const response = await axios.get<string>(`${BASE_URL}/file`, {
      params: { path }
    });
    return response.data;
  },

  saveFile: async (path: string, content: string): Promise<void> => {
    await axios.post(`${BASE_URL}/save`, { path, content });
  },

  executeCode: async (code: string, sessionId: string): Promise<ApiResponse> => {
    const response = await axios.post<ApiResponse>(`${BASE_URL}/execute`, {
      code,
      sessionId
    });
    return response.data;
  },

  startDebug: async (code: string, breakpoints: number[], sessionId: string): Promise<ApiResponse> => {
    const response = await axios.post<ApiResponse>(`${BASE_URL}/debug`, { 
      code, 
      breakpoints, 
      sessionId 
    });
    return response.data;
  },

  continueDebug: async (sessionId: string): Promise<ApiResponse> => {
    const response = await axios.post<ApiResponse>(`${BASE_URL}/debug/continue`, { 
      sessionId,
      command: 'cont'
    });
    return response.data;
  }
}; 