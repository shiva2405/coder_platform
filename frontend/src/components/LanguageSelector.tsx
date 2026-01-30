import React from 'react';
import { ChevronDown } from 'lucide-react';
import { Language } from '../types';

interface LanguageSelectorProps {
  languages: Language[];
  selectedLanguage: Language | null;
  onSelect: (language: Language) => void;
}

const LanguageSelector: React.FC<LanguageSelectorProps> = ({
  languages,
  selectedLanguage,
  onSelect,
}) => {
  const [isOpen, setIsOpen] = React.useState(false);

  const handleSelect = (language: Language) => {
    onSelect(language);
    setIsOpen(false);
  };

  return (
    <div className="relative">
      <button
        onClick={() => setIsOpen(!isOpen)}
        className="flex items-center gap-2 px-4 py-2 bg-editor-sidebar border border-editor-border rounded-md hover:bg-editor-border transition-colors min-w-[160px]"
      >
        <span className="flex-1 text-left">
          {selectedLanguage?.name || 'Select Language'}
        </span>
        <ChevronDown className={`w-4 h-4 transition-transform ${isOpen ? 'rotate-180' : ''}`} />
      </button>

      {isOpen && (
        <div className="absolute top-full left-0 mt-1 w-full max-h-96 overflow-y-auto bg-editor-sidebar border border-editor-border rounded-md shadow-lg z-50">
          {languages.map((language) => (
            <button
              key={language.id}
              onClick={() => handleSelect(language)}
              className={`w-full px-4 py-2 text-left hover:bg-editor-active transition-colors ${
                selectedLanguage?.id === language.id ? 'bg-editor-active' : ''
              }`}
            >
              <span className="font-medium">{language.name}</span>
              <span className="text-gray-400 text-sm ml-2">{language.extension}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
};

export default LanguageSelector;
