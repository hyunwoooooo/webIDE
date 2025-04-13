export interface FileNode {
  name: string;
  type: 'file' | 'directory';
  children?: FileNode[];
  path: string;
}

export interface CompileError {
  message: string;
  line?: number;
  column?: number;
}

export interface ErrorDialogProps {
  error: string;
  onClose: () => void;
}

export interface FileTreeProps {
  files: FileNode[];
  onFileSelect: (path: string) => void;
}

export interface FileTreeNodeProps {
  node: FileNode;
  onFileSelect: (path: string) => void;
}

export interface ApiResponse {
  error?: string;
  message?: string;
  details?: any[];
  output?: string;
  status?: string;
  finished?: boolean;
  variables?: string;
}

export interface WebSocketMessage {
  body: string;
} 