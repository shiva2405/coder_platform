# Coder Platform - Online Code Playground

A full-stack code execution platform with a VS Code-like editor supporting 14 programming languages.

## Features

- **VS Code-like Editor**: Monaco Editor with syntax highlighting, auto-indentation, and IntelliSense
- **14 Supported Languages**: Java, Python, JavaScript, TypeScript, C, C++, Go, Rust, Ruby, PHP, Kotlin, Swift, Perl, Bash
- **Resource Limits**: Configurable timeout (default 30s) and memory limits (default 1MB)
- **Modern UI**: Dark/Light theme support, responsive design
- **Docker Ready**: Complete Docker setup for easy deployment

## Architecture

```
┌─────────────────────┐     ┌─────────────────────┐
│   React Frontend    │────▶│  Spring Boot API    │
│   (Monaco Editor)   │◀────│  (Code Execution)   │
└─────────────────────┘     └─────────────────────┘
         │                           │
         │                           ▼
         │                  ┌─────────────────────┐
         │                  │  Process Sandbox    │
         │                  │  (All Compilers)    │
         └─────────────────▶└─────────────────────┘
```

## Quick Start

### Using Docker Compose (Recommended)

```bash
# Clone the repository
git clone <repository-url>
cd coder_platform-1

# Start all services
docker-compose up --build

# Access the application
# Frontend: http://localhost:3000
# Backend API: http://localhost:8080
```

### Manual Development Setup

#### Backend (Java Spring Boot)

```bash
cd backend

# Build and run
./mvnw spring-boot:run

# Or with Maven
mvn spring-boot:run

# API will be available at http://localhost:8080
```

#### Frontend (React)

```bash
cd frontend

# Install dependencies
npm install

# Start development server
npm run dev

# Frontend will be available at http://localhost:3000
```

## API Endpoints

### Execute Code
```http
POST /api/execute
Content-Type: application/json

{
  "language": "python",
  "code": "print('Hello, World!')",
  "stdin": ""
}
```

Response:
```json
{
  "output": "Hello, World!\n",
  "error": "",
  "executionTime": 45,
  "status": "SUCCESS"
}
```

### Get Supported Languages
```http
GET /api/languages
```

### Health Check
```http
GET /api/health
```

## Supported Languages

| Language | Extension | Compiler/Interpreter |
|----------|-----------|---------------------|
| Java | .java | javac + java |
| Python | .py | python3 |
| JavaScript | .js | node |
| TypeScript | .ts | tsc + node |
| C | .c | gcc |
| C++ | .cpp | g++ |
| Go | .go | go run |
| Rust | .rs | rustc |
| Ruby | .rb | ruby |
| PHP | .php | php |
| Kotlin | .kt | kotlinc |
| Swift | .swift | swift |
| Perl | .pl | perl |
| Bash | .sh | bash |

## Configuration

### Backend Configuration (application.yml)

```yaml
execution:
  timeout: 30000          # 30 seconds
  memory-limit: 1048576   # 1MB
  max-output-size: 65536  # 64KB
```

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| EXECUTION_TIMEOUT | Max execution time (ms) | 30000 |
| EXECUTION_MEMORY_LIMIT | Max memory (bytes) | 1048576 |
| EXECUTION_MAX_OUTPUT_SIZE | Max output size (bytes) | 65536 |
| CORS_ALLOWED_ORIGINS | Allowed CORS origins | http://localhost:3000 |

## Project Structure

```
coder_platform-1/
├── backend/                    # Java Spring Boot backend
│   ├── src/main/java/com/coderplatform/
│   │   ├── config/            # Configuration classes
│   │   ├── controller/        # REST controllers
│   │   ├── model/             # Data models
│   │   └── service/           # Business logic
│   └── pom.xml
├── frontend/                   # React frontend
│   ├── src/
│   │   ├── components/        # React components
│   │   ├── services/          # API services
│   │   └── types/             # TypeScript types
│   └── package.json
├── docker/                     # Docker configurations
│   ├── Dockerfile.backend
│   ├── Dockerfile.frontend
│   ├── nginx.conf
│   └── compilers/
└── docker-compose.yml
```

## Security Considerations

- **Process Isolation**: Each code execution runs in a separate temp directory
- **Timeout Enforcement**: Processes are killed if they exceed the time limit
- **Memory Limits**: Memory restrictions via JVM flags and process limits
- **Output Limits**: Output is truncated to prevent memory exhaustion
- **No Network Access**: Executed code cannot make network requests
- **No File System Access**: Code can only access its temp directory

## Development

### Prerequisites

- Java 17+
- Node.js 18+
- Maven 3.8+
- Docker & Docker Compose (for containerized deployment)

### Running Tests

```bash
# Backend tests
cd backend
mvn test

# Frontend tests
cd frontend
npm test
```

## License

MIT License
