# Coder Platform - Online Code Playground

A full-stack code execution platform with a VS Code-like editor supporting 14 programming languages.

## Features

- **VS Code-like Editor**: Monaco Editor with syntax highlighting, auto-indentation, and IntelliSense
- **14 Supported Languages**: Java, Python, JavaScript, TypeScript, C, C++, Go, Rust, Ruby, PHP, Kotlin, Swift, Perl, Bash
- **Live Execution**: Stream stdout/stderr as the process runs, type stdin interactively, and stop a run immediately
- **Resource Limits**: Configurable timeout (default 30s) and memory limits (default 1MB)
- **Admission Control**: Bounded concurrent executions, a wait queue with position and wait estimates, and 429s when the machine is full
- **Authentication**: GitHub login plus email/password for local development
- **Workspaces**: Signed-in users own snippets with public, unlisted, and private visibility
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
         │                  │                 │ │ Problems + │
         │                  │                 │ │ Users      │
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

Accepted work returns `202` with a ticket. Poll `GET /api/jobs/{id}` until `state` is `COMPLETED`. Cancel with `DELETE /api/jobs/{id}`.

```json
{
  "id": "…",
  "state": "QUEUED",
  "position": 2,
  "estimatedWaitMs": 4000
}
```

When the job finishes, the ticket includes the existing execution result:

```json
{
  "id": "…",
  "state": "COMPLETED",
  "result": {
    "output": "Hello, World!\n",
    "error": "",
    "executionTime": 45,
    "status": "SUCCESS"
  }
}
```

If the queue is full or the client is over its execution quota the API returns `429` with `Retry-After`. The buffered execute endpoint is the playground fallback when a live session cannot be opened. Queued work does not hold an HTTP request thread.

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
{"type":"queued","executionId":"...","position":2,"estimatedWaitMs":4000}
{"type":"started","executionId":"..."}
{"type":"stdout","data":"..."}
{"type":"stderr","data":"..."}
{"type":"done","status":"SUCCESS","executionTime":42,"output":"...","error":""}
{"type":"rejected","message":"...","retryAfterSeconds":3,"reason":"QUEUE_FULL"}
{"type":"error","message":"..."}
```

Active processes are tracked per WebSocket session and are killed when the program exits, the user sends `stop`, the time limit is hit, or the browser disconnects.

### Get Supported Languages
```http
GET /api/languages
```

### Authentication
```http
GET  /api/auth/providers
GET  /api/auth/me
POST /api/auth/register
POST /api/auth/login
POST /api/auth/logout
GET  /api/auth/github?next=/dashboard
GET  /api/auth/github/callback
```

Sessions are stored server-side and sent as an HttpOnly `cp_session` cookie. `GET /api/auth/me` restores the user after a refresh. Logout deletes the session and clears the cookie.

Email/password is enabled by default for local development (`AUTH_LOCAL_ENABLED=true`). Registering `admin@localhost` (see `AUTH_ADMIN_EMAILS`) grants the `ADMIN` role. GitHub login requires `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET`.

The anonymous playground still runs code without login. Saving or modifying snippets requires a signed-in user.

### Save Snippet
```http
POST /api/snippets
Content-Type: application/json

{
  "language": "python",
  "code": "print(input())",
  "stdin": "hello",
  "title": "Optional title",
  "visibility": "PUBLIC"
}
```

Requires login. Creates a new snippet owned by the current user and returns a short `slug`. Language must be one of the supported IDs. Code is limited to 256KB. Signed-in users can save 60 snippets per hour.

Visibility is `PUBLIC`, `UNLISTED`, or `PRIVATE`. Private snippets are only readable by their owner; everyone else gets a 404.

### Load Snippet
```http
GET /api/snippets/{slug}
```

Public and unlisted snippets can be viewed without login. Private snippets return 404 unless the owner is signed in. View count increments for non-owners.

### Fork / Update / Delete
```http
POST   /api/snippets/{slug}/fork
PATCH  /api/snippets/{slug}
DELETE /api/snippets/{slug}
GET    /api/me/snippets?q=&visibility=&sort=updatedAt&order=desc
```

Fork always creates a **new** snippet owned by the current user. The original is never modified. Update and delete require ownership. `/dashboard` lists the signed-in user's snippets.

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

Returns `202` and uses the same admission queue as `/api/execute`. Poll `GET /api/jobs/{id}` for the judge result. Compiles once, then runs only sample tests. Failures include expected vs actual output.

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
Send `X-Admin-Key` (default local value `dev-admin-key`, override with `ADMIN_API_KEY`) **or** sign in as a user with the `ADMIN` role.

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
  max-concurrent: 8
  max-heavy-concurrent: 3
  max-queue: 24
  max-heavy-queue: 8
  max-concurrent-per-client: 2
  max-queued-per-client: 3
  queue-timeout-ms: 20000
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
| AUTH_LOCAL_ENABLED | Enable email/password register and login | true |
| AUTH_ADMIN_EMAILS | Comma-separated emails granted ADMIN | admin@localhost |
| AUTH_COOKIE_SECURE | Set the session cookie Secure flag | false |
| FRONTEND_BASE_URL | Frontend origin for OAuth redirects | http://localhost:3000 |
| GITHUB_CLIENT_ID | GitHub OAuth app client id | |
| GITHUB_CLIENT_SECRET | GitHub OAuth app client secret | |
| GITHUB_REDIRECT_URI | GitHub OAuth callback | http://localhost:3000/api/auth/github/callback |

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
- **Session Auth**: HttpOnly session cookies; logout invalidates the server session
- **Private Snippets**: Non-owners receive 404 rather than a permission leak
- **Rate Limits**: Anonymous execution is 20/hour per IP; signed-in users get 120/hour; admins are not rate limited
- **Admission Control**: At most 8 programs run at once (3 of those may be compiled languages). Extra work waits in a bounded queue and is rejected with 429 when the queue is full
- **Client Fairness**: One client can run 2 programs and queue 3 more, so a single user cannot consume the machine
- **Trusted Proxy IPs**: Behind nginx the client IP comes from `X-Real-IP` / the right-most `X-Forwarded-For` hop

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
