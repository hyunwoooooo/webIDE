import React, { useState } from 'react';
import { FileTreeNodeProps } from '../types';

const FileTreeNode: React.FC<FileTreeNodeProps> = ({ node, onFileSelect }) => {
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
};

export default FileTreeNode; 