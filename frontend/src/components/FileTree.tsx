import React from 'react';
import { FileTreeProps } from '../types';
import FileTreeNode from './FileTreeNode';

const FileTree: React.FC<FileTreeProps> = ({ files, onFileSelect }) => {
  return (
    <div className="file-tree">
      {files.map((file) => (
        <FileTreeNode key={file.path} node={file} onFileSelect={onFileSelect} />
      ))}
    </div>
  );
};

export default FileTree; 