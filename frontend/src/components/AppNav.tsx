import { Link } from 'react-router-dom';

interface AppNavProps {
  current: 'playground' | 'problems';
}

const AppNav = ({ current }: AppNavProps) => {
  const linkClass = (active: boolean) =>
    `px-3 py-1.5 rounded-md text-sm transition-colors ${
      active ? 'bg-editor-border text-white' : 'text-gray-400 hover:text-white hover:bg-editor-border/60'
    }`;

  return (
    <div className="flex items-center gap-4">
      <Link to="/" className="flex items-center gap-2 hover:opacity-90">
        <div className="w-8 h-8 bg-gradient-to-br from-blue-500 to-purple-600 rounded-lg flex items-center justify-center">
          <span className="text-white font-bold text-sm">&lt;/&gt;</span>
        </div>
        <span className="font-semibold text-lg">Coder Platform</span>
      </Link>
      <div className="h-6 w-px bg-editor-border" />
      <nav className="flex items-center gap-1">
        <Link to="/" className={linkClass(current === 'playground')}>
          Playground
        </Link>
        <Link to="/problems" className={linkClass(current === 'problems')}>
          Problems
        </Link>
      </nav>
    </div>
  );
};

export default AppNav;
