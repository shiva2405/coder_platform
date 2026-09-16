import { Language } from '../types';
import { getLanguages } from './api';

export const defaultSampleCodes: Record<string, string> = {
  java: `public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, World!");
    }
}`,
  python: `print("Hello, World!")`,
  javascript: `console.log("Hello, World!");`,
  typescript: `const greeting: string = "Hello, World!";
console.log(greeting);`,
  c: `#include <stdio.h>

int main() {
    printf("Hello, World!\\n");
    return 0;
}`,
  cpp: `#include <iostream>

int main() {
    std::cout << "Hello, World!" << std::endl;
    return 0;
}`,
  go: `package main

import "fmt"

func main() {
    fmt.Println("Hello, World!")
}`,
  rust: `fn main() {
    println!("Hello, World!");
}`,
  ruby: `puts "Hello, World!"`,
  php: `<?php
echo "Hello, World!\\n";
?>`,
  kotlin: `fun main() {
    println("Hello, World!")
}`,
  swift: `print("Hello, World!")`,
  perl: `print "Hello, World!\\n";`,
  bash: `#!/bin/bash
echo "Hello, World!"`,
};

const FALLBACK_LANGUAGE_META: Array<Pick<Language, 'id' | 'name' | 'extension'>> = [
  { id: 'java', name: 'Java', extension: '.java' },
  { id: 'python', name: 'Python', extension: '.py' },
  { id: 'javascript', name: 'JavaScript', extension: '.js' },
  { id: 'typescript', name: 'TypeScript', extension: '.ts' },
  { id: 'c', name: 'C', extension: '.c' },
  { id: 'cpp', name: 'C++', extension: '.cpp' },
  { id: 'go', name: 'Go', extension: '.go' },
  { id: 'rust', name: 'Rust', extension: '.rs' },
  { id: 'ruby', name: 'Ruby', extension: '.rb' },
  { id: 'php', name: 'PHP', extension: '.php' },
  { id: 'kotlin', name: 'Kotlin', extension: '.kt' },
  { id: 'swift', name: 'Swift', extension: '.swift' },
  { id: 'perl', name: 'Perl', extension: '.pl' },
  { id: 'bash', name: 'Bash', extension: '.sh' },
];

export function fallbackLanguages(): Language[] {
  return FALLBACK_LANGUAGE_META.map((meta) => ({
    ...meta,
    sampleCode: defaultSampleCodes[meta.id] || '',
  }));
}

export async function loadLanguages(
  fetchLanguages: () => Promise<Language[]> = getLanguages,
): Promise<{ languages: Language[]; offline: boolean }> {
  try {
    const languages = await fetchLanguages();
    if (!languages || languages.length === 0) {
      return { languages: fallbackLanguages(), offline: true };
    }
    return { languages, offline: false };
  } catch {
    return { languages: fallbackLanguages(), offline: true };
  }
}
