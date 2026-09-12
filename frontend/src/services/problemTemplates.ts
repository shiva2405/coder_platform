export function starterCode(languageId: string): string {
  switch (languageId) {
    case 'python':
      return `# Read from stdin and write the answer to stdout
data = input().split()
print()
`;
    case 'java':
      return `import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        // Read input and print the answer
    }
}
`;
    case 'javascript':
      return `const fs = require('fs');
const input = fs.readFileSync(0, 'utf8').trim().split(/\\s+/);
console.log();
`;
    case 'typescript':
      return `const fs = require('fs');
const input: string[] = fs.readFileSync(0, 'utf8').trim().split(/\\s+/);
console.log();
`;
    case 'c':
      return `#include <stdio.h>

int main() {
    // Read input and print the answer
    return 0;
}
`;
    case 'cpp':
      return `#include <iostream>
using namespace std;

int main() {
    // Read input and print the answer
    return 0;
}
`;
    case 'go':
      return `package main

import "fmt"

func main() {
    // Read input and print the answer
    fmt.Println()
}
`;
    case 'rust':
      return `use std::io::{self, Read};

fn main() {
    let mut input = String::new();
    io::stdin().read_to_string(&mut input).unwrap();
    println!();
}
`;
    case 'ruby':
      return `data = gets.split
puts
`;
    case 'php':
      return `<?php
$data = explode(" ", trim(fgets(STDIN)));
echo "\\n";
`;
    case 'kotlin':
      return `fun main() {
    val data = readln().split(" ")
    println()
}
`;
    case 'swift':
      return `import Foundation
let data = readLine()?.split(separator: " ") ?? []
print()
`;
    case 'perl':
      return `my @data = split /\\s+/, <STDIN>;
print "\\n";
`;
    case 'bash':
      return `#!/bin/bash
read -r line
echo
`;
    default:
      return '';
  }
}
