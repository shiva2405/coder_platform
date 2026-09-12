# Coder Platform - Online Code Playground

A full-stack code execution platform with a VS Code-like editor supporting 14 programming languages.

## Features

- **VS Code-like Editor**: Monaco Editor with syntax highlighting, auto-indentation, and IntelliSense
- **14 Supported Languages**: Java, Python, JavaScript, TypeScript, C, C++, Go, Rust, Ruby, PHP, Kotlin, Swift, Perl, Bash
- **Live Execution**: Stream stdout/stderr as the process runs, type stdin interactively, and stop a run immediately
- **Resource Limits**: Configurable timeout (default 30s) and memory limits (default 1MB)
- **Save & Share**: Persist snippets in PostgreSQL and share them with a short URL
- **Problem Solving**: Problem statements, sample tests, and automated judging
- **Compile-once Judge**: Compile submitted code once, then run every test case against the same binary
- **Local Drafts**: Unsaved editor changes are restored after refresh
- **Modern UI**: Dark/Light theme support, responsive design
- **Docker Ready**: Complete Docker setup for easy deployment

## Architecture

```
┌─────────────────────┐     ┌─────────────────────┐
│   React Frontend    │────▶│  Spring Boot API    │
│   (Monaco Editor)   │◀────│  (Execute + Judge)  │
└─────────────────────┘     └─────────────────────┘
         │                           │
         │                           ├───────────────┐
         │                           ▼               ▼
         │                  ┌─────────────────┐ ┌────────────┐
         │                  │ Process Sandbox │ │ PostgreSQL │
         │                  │ (Compilers)     │ │ Snippets + │
         │                  │                 │ │ Problems   │
         └─────────────────▶└─────────────────┘ └────────────┘
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

#### PostgreSQL

Local backend runs expect PostgreSQL on `localhost:5432` (database `coder_platform`, user/password `coder`). The easiest option is:

```bash
docker compose up -d postgres
```

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

The buffered `POST /api/execute` endpoint remains available and is the playground fallback when a live session cannot be opened.

### Live Execution
```text
WebSocket /ws/execute
```

Client messages:

```json
{"type":"start","language":"python","code":"print(input())","stdin":""}
{"type":"stdin","data":"hello\\n"}
{"type":"eof"}
{"type":"stop"}
```

Server messages:

```json
{"type":"started","executionId":"..."}
{"type":"stdout","data":"..."}
{"type":"stderr","data":"..."}
{"type":"done","status":"SUCCESS","executionTime":42,"output":"...","error":""}
{"type":"error","message":"..."}
```

Active processes are tracked per WebSocket session and are killed when the program exits, the user sends `stop`, the time limit is hit, or the browser disconnects.

### Get Supported Languages
```http
GET /api/languages
```

### Save Snippet
```http
POST /api/snippets
Content-Type: application/json

{
  "language": "python",
  "code": "print(input())",
  "stdin": "hello",
  "title": "Optional title"
}
```

Creates a new snippet and returns a short `slug`. Language must be one of the supported IDs. Code is limited to 256KB. Creation is limited to 20 snippets per hour per IP.

### Load Snippet
```http
GET /api/snippets/{slug}
```

Increments the view count and returns language, code, stdin, title, timestamps, and view count.

### Fork Snippet
```http
POST /api/snippets/{slug}/fork
Content-Type: application/json

{
  "language": "python",
  "code": "print('forked')",
  "stdin": ""
}
```

Always creates a **new** snippet. The original is never modified. Omitted fields are copied from the original.

Shared links use `/s/{slug}` on the frontend.

### List Problems
```http
GET /api/problems
```

Returns title, difficulty, tags, limits, sample count, and total test count. Hidden test data is never included.

### View Problem
```http
GET /api/problems/{slug}
```

Returns the statement, limits, and **sample** tests only. Hidden tests appear only as `hiddenTestCount`.

### Run Samples
```http
POST /api/problems/{slug}/run-samples
Content-Type: application/json

{
  "language": "python",
  "code": "print(sum(map(int, input().split())))"
}
```

Compiles once, then runs only sample tests. Failures include expected vs actual output.

### Submit Solution
```http
POST /api/problems/{slug}/submissions
Content-Type: application/json

{
  "language": "python",
  "code": "print(sum(map(int, input().split())))"
}
```

Compiles once, then runs every test case using the problem's time and memory limits. Stores language, code, verdict, runtime, and passed-count. Hidden case input/output is never returned.

### Submission Results
```http
GET /api/problems/{slug}/submissions
GET /api/submissions/{id}
```

### Admin Problem APIs
Send `X-Admin-Key` (default local value `dev-admin-key`, override with `ADMIN_API_KEY`).

```http
GET    /api/admin/problems
POST   /api/admin/problems
GET    /api/admin/problems/{slug}
PUT    /api/admin/problems/{slug}
DELETE /api/admin/problems/{slug}
POST   /api/admin/problems/{slug}/test-cases
PUT    /api/admin/problems/{slug}/test-cases/{id}
DELETE /api/admin/problems/{slug}/test-cases/{id}
```

Admin GET includes hidden test input and expected output. Public APIs never do.

Starter problems: `a-plus-b`, `fizzbuzz`, `palindrome-string`, `maximum-of-n`.

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
| SPRING_DATASOURCE_URL | PostgreSQL JDBC URL | jdbc:postgresql://localhost:5432/coder_platform |
| SPRING_DATASOURCE_USERNAME | PostgreSQL user | coder |
| SPRING_DATASOURCE_PASSWORD | PostgreSQL password | coder |
| ADMIN_API_KEY | Admin API key for problem management | dev-admin-key |

## Project Structure

```
coder_platform-1/
├── backend/                    # Java Spring Boot backend
│   ├── src/main/java/com/coderplatform/
│   │   ├── config/            # Configuration classes
│   │   ├── controller/        # REST controllers
│   │   ├── model/             # Data models
│   │   ├── repository/        # JPA repositories
│   │   └── service/           # Business logic
│   ├── src/main/resources/db/migration/
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
