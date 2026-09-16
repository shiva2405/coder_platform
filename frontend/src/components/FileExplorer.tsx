import { useState } from 'react';
import { ChevronDown, ChevronRight, FilePlus, FolderPlus, FileCode, Folder, Star, Trash2 } from 'lucide-react';
import { FileTreeNode, buildFileTree, fileName } from '../services/projectFiles';
import { ProjectFile } from '../types';

interface FileExplorerProps {
  files: ProjectFile[];
  extraFolders?: string[];
  activePath: string;
  entrypoint: string;
  onOpen: (path: string) => void;
  onNewFile: (folder?: string) => void;
  onNewFolder: (folder?: string) => void;
  onRename: (path: string) => void;
  onDelete: (path: string) => void;
  onSetEntrypoint: (path: string) => void;
}

export default function FileExplorer({
  files,
  extraFolders = [],
  activePath,
  entrypoint,
  onOpen,
  onNewFile,
  onNewFolder,
  onRename,
  onDelete,
  onSetEntrypoint,
}: FileExplorerProps) {
  const tree = buildFileTree(files.map((file) => file.path), extraFolders);
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({});

  const toggle = (path: string) => {
    setCollapsed((current) => ({ ...current, [path]: !current[path] }));
  };

  return (
    <div className="flex h-full w-52 shrink-0 flex-col border-r border-editor-border bg-editor-sidebar">
      <div className="flex items-center justify-between px-3 py-2 text-xs uppercase tracking-wide text-gray-400">
        <span>Files</span>
        <span className="flex items-center gap-1">
          <button type="button" title="New file" onClick={() => onNewFile()} className="rounded p-1 hover:bg-editor-border hover:text-white">
            <FilePlus className="h-3.5 w-3.5" />
          </button>
          <button type="button" title="New folder" onClick={() => onNewFolder()} className="rounded p-1 hover:bg-editor-border hover:text-white">
            <FolderPlus className="h-3.5 w-3.5" />
          </button>
        </span>
      </div>
      <div className="flex-1 overflow-auto px-1 pb-2">
        {tree.map((node) => (
          <TreeNode
            key={node.path}
            node={node}
            depth={0}
            activePath={activePath}
            entrypoint={entrypoint}
            collapsed={collapsed}
            onToggle={toggle}
            onOpen={onOpen}
            onNewFile={onNewFile}
            onNewFolder={onNewFolder}
            onRename={onRename}
            onDelete={onDelete}
            onSetEntrypoint={onSetEntrypoint}
          />
        ))}
      </div>
    </div>
  );
}

function TreeNode({
  node,
  depth,
  activePath,
  entrypoint,
  collapsed,
  onToggle,
  onOpen,
  onNewFile,
  onNewFolder,
  onRename,
  onDelete,
  onSetEntrypoint,
}: {
  node: FileTreeNode;
  depth: number;
  activePath: string;
  entrypoint: string;
  collapsed: Record<string, boolean>;
  onToggle: (path: string) => void;
  onOpen: (path: string) => void;
  onNewFile: (folder?: string) => void;
  onNewFolder: (folder?: string) => void;
  onRename: (path: string) => void;
  onDelete: (path: string) => void;
  onSetEntrypoint: (path: string) => void;
}) {
  const isFolder = node.type === 'folder';
  const isOpen = !collapsed[node.path];
  const active = !isFolder && node.path === activePath;
  const isEntry = node.path === entrypoint;

  return (
    <div>
      <div
        className={`group flex items-center rounded-sm pr-1 text-sm ${
          active ? 'bg-editor-active text-white' : 'text-gray-300 hover:bg-editor-border/70'
        }`}
        style={{ paddingLeft: 8 + depth * 12 }}
      >
        {isFolder ? (
          <button type="button" className="flex min-w-0 flex-1 items-center gap-1 py-1" onClick={() => onToggle(node.path)}>
            {isOpen ? <ChevronDown className="h-3.5 w-3.5 shrink-0" /> : <ChevronRight className="h-3.5 w-3.5 shrink-0" />}
            <Folder className="h-3.5 w-3.5 shrink-0 text-sky-400" />
            <span className="truncate">{node.name}</span>
          </button>
        ) : (
          <button
            type="button"
            className="flex min-w-0 flex-1 items-center gap-1 py-1"
            onClick={() => onOpen(node.path)}
            onDoubleClick={() => onRename(node.path)}
            title={node.path}
          >
            <FileCode className="h-3.5 w-3.5 shrink-0 text-gray-400" />
            <span className="truncate">{fileName(node.path)}</span>
            {isEntry && <Star className="h-3 w-3 shrink-0 fill-amber-400 text-amber-400" />}
          </button>
        )}
        <span className="hidden items-center group-hover:flex">
          {isFolder ? (
            <>
              <button type="button" className="p-0.5 text-gray-400 hover:text-white" title="New file" onClick={() => onNewFile(node.path)}>
                <FilePlus className="h-3 w-3" />
              </button>
              <button type="button" className="p-0.5 text-gray-400 hover:text-white" title="New folder" onClick={() => onNewFolder(node.path)}>
                <FolderPlus className="h-3 w-3" />
              </button>
            </>
          ) : (
            <>
              {!isEntry && (
                <button type="button" className="p-0.5 text-gray-400 hover:text-amber-300" title="Set entrypoint" onClick={() => onSetEntrypoint(node.path)}>
                  <Star className="h-3 w-3" />
                </button>
              )}
              <button type="button" className="p-0.5 text-gray-400 hover:text-red-400" title="Delete" onClick={() => onDelete(node.path)}>
                <Trash2 className="h-3 w-3" />
              </button>
            </>
          )}
        </span>
      </div>
      {isFolder && isOpen && node.children?.map((child) => (
        <TreeNode
          key={child.path}
          node={child}
          depth={depth + 1}
          activePath={activePath}
          entrypoint={entrypoint}
          collapsed={collapsed}
          onToggle={onToggle}
          onOpen={onOpen}
          onNewFile={onNewFile}
          onNewFolder={onNewFolder}
          onRename={onRename}
          onDelete={onDelete}
          onSetEntrypoint={onSetEntrypoint}
        />
      ))}
    </div>
  );
}
