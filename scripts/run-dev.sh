#!/bin/bash

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo -e "${GREEN}=== Coder Platform Dev Server ===${NC}"

# Function to kill existing processes
kill_servers() {
    echo -e "${YELLOW}Stopping existing servers...${NC}"
    
    # Kill backend (Spring Boot on port 8080)
    BACKEND_PID=$(lsof -ti:8080 2>/dev/null)
    if [ -n "$BACKEND_PID" ]; then
        echo "Killing backend process(es): $BACKEND_PID"
        kill -9 $BACKEND_PID 2>/dev/null
    fi
    
    # Kill frontend (Vite on port 3000 or 5173)
    FRONTEND_PID=$(lsof -ti:3000 2>/dev/null)
    if [ -n "$FRONTEND_PID" ]; then
        echo "Killing frontend process(es) on 3000: $FRONTEND_PID"
        kill -9 $FRONTEND_PID 2>/dev/null
    fi
    
    FRONTEND_PID=$(lsof -ti:5173 2>/dev/null)
    if [ -n "$FRONTEND_PID" ]; then
        echo "Killing frontend process(es) on 5173: $FRONTEND_PID"
        kill -9 $FRONTEND_PID 2>/dev/null
    fi
    
    # Kill any Maven processes
    pkill -f "spring-boot:run" 2>/dev/null
    
    # Give processes time to die
    sleep 2
    echo -e "${GREEN}All servers stopped.${NC}"
}

# Function to start backend
start_backend() {
    echo -e "${YELLOW}Starting backend...${NC}"
    cd "$PROJECT_DIR/backend"
    
    # Run Maven in background
    mvn spring-boot:run -q &
    BACKEND_PID=$!
    
    echo "Backend starting with PID: $BACKEND_PID"
    
    # Wait for backend to be ready
    echo "Waiting for backend to be ready..."
    for i in {1..60}; do
        if curl -s http://localhost:8080/api/health > /dev/null 2>&1; then
            echo -e "${GREEN}Backend is ready!${NC}"
            return 0
        fi
        sleep 1
    done
    
    echo -e "${RED}Backend failed to start within 60 seconds${NC}"
    return 1
}

# Function to start frontend
start_frontend() {
    echo -e "${YELLOW}Starting frontend...${NC}"
    cd "$PROJECT_DIR/frontend"
    
    # Install dependencies if node_modules doesn't exist
    if [ ! -d "node_modules" ]; then
        echo "Installing frontend dependencies..."
        npm install
    fi
    
    # Run Vite in background
    npm run dev &
    FRONTEND_PID=$!
    
    echo "Frontend starting with PID: $FRONTEND_PID"
    
    # Wait for frontend to be ready
    echo "Waiting for frontend to be ready..."
    for i in {1..30}; do
        if curl -s http://localhost:3000 > /dev/null 2>&1 || curl -s http://localhost:5173 > /dev/null 2>&1; then
            echo -e "${GREEN}Frontend is ready!${NC}"
            return 0
        fi
        sleep 1
    done
    
    echo -e "${YELLOW}Frontend may still be starting...${NC}"
    return 0
}

# Parse arguments
case "$1" in
    stop)
        kill_servers
        ;;
    restart)
        kill_servers
        start_backend
        start_frontend
        echo -e "${GREEN}=== All servers restarted ===${NC}"
        echo "Backend:  http://localhost:8080"
        echo "Frontend: http://localhost:3000"
        ;;
    backend)
        BACKEND_PID=$(lsof -ti:8080 2>/dev/null)
        if [ -n "$BACKEND_PID" ]; then
            kill -9 $BACKEND_PID 2>/dev/null
            sleep 1
        fi
        start_backend
        ;;
    frontend)
        FRONTEND_PID=$(lsof -ti:3000 2>/dev/null)
        if [ -n "$FRONTEND_PID" ]; then
            kill -9 $FRONTEND_PID 2>/dev/null
        fi
        FRONTEND_PID=$(lsof -ti:5173 2>/dev/null)
        if [ -n "$FRONTEND_PID" ]; then
            kill -9 $FRONTEND_PID 2>/dev/null
        fi
        sleep 1
        start_frontend
        ;;
    *)
        # Default: restart everything
        kill_servers
        start_backend
        start_frontend
        echo -e "${GREEN}=== All servers started ===${NC}"
        echo "Backend:  http://localhost:8080"
        echo "Frontend: http://localhost:3000"
        echo ""
        echo "Use: $0 stop    - to stop all servers"
        echo "Use: $0 restart - to restart all servers"
        echo "Use: $0 backend - to restart backend only"
        echo "Use: $0 frontend - to restart frontend only"
        ;;
esac
