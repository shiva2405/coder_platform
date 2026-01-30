#!/bin/bash

# Docker-based Load Test for Coder Platform
# Runs inside Docker container for accurate resource monitoring

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo -e "${GREEN}=== Docker Load Test for Coder Platform ===${NC}"
echo "Using 16GB RAM configuration"
echo ""

# Check Docker is running
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}Error: Docker is not running${NC}"
    exit 1
fi

# Function to monitor Docker container resources
monitor_resources() {
    local container=$1
    local duration=$2
    local interval=1
    
    echo -e "${BLUE}Monitoring container: $container${NC}"
    echo "Time,CPU%,Memory,MemLimit,MemUsage%"
    
    local end_time=$((SECONDS + duration))
    while [ $SECONDS -lt $end_time ]; do
        stats=$(docker stats --no-stream --format "{{.CPUPerc}},{{.MemUsage}},{{.MemPerc}}" "$container" 2>/dev/null)
        if [ -n "$stats" ]; then
            echo "$SECONDS,$stats"
        fi
        sleep $interval
    done
}

# Function to run load test
run_load_test() {
    local concurrent=$1
    local total=$2
    local lang=${3:-python}
    
    echo -e "${YELLOW}Running load test: $concurrent concurrent, $total total requests${NC}"
    
    # Prepare test payload
    case $lang in
        python)
            payload='{"language":"python","code":"import time\nstart = time.time()\nresult = sum(range(100000))\nprint(f\"Result: {result}\")\nprint(f\"Time: {time.time()-start:.4f}s\")"}'
            ;;
        java)
            payload='{"language":"java","code":"public class Main {\n    public static void main(String[] args) {\n        long start = System.nanoTime();\n        long sum = 0;\n        for(int i = 0; i < 100000; i++) sum += i;\n        System.out.println(\"Sum: \" + sum);\n        System.out.println(\"Time: \" + (System.nanoTime()-start)/1e9 + \"s\");\n    }\n}"}'
            ;;
        javascript)
            payload='{"language":"javascript","code":"const start = Date.now();\nlet sum = 0;\nfor(let i = 0; i < 100000; i++) sum += i;\nconsole.log(`Sum: ${sum}`);\nconsole.log(`Time: ${(Date.now()-start)/1000}s`);"}'
            ;;
        *)
            payload='{"language":"python","code":"print(\"Hello\")"}'
            ;;
    esac
    
    # Create temp directory for results
    results_dir="/tmp/coder_load_test_$$"
    mkdir -p "$results_dir"
    
    # Run concurrent requests
    local success=0
    local failed=0
    local total_time=0
    local min_time=999999
    local max_time=0
    
    echo "Starting requests..."
    local start_time=$(date +%s.%N)
    
    for batch in $(seq 1 $((total / concurrent))); do
        pids=()
        
        for i in $(seq 1 $concurrent); do
            req_id="${batch}_${i}"
            (
                req_start=$(date +%s.%N)
                response=$(curl -s -w "\n%{http_code}" \
                    -X POST "http://localhost:8080/api/execute" \
                    -H "Content-Type: application/json" \
                    -d "$payload" \
                    --connect-timeout 5 \
                    --max-time 60 2>/dev/null)
                req_end=$(date +%s.%N)
                
                http_code=$(echo "$response" | tail -n1)
                req_time=$(echo "$req_end - $req_start" | bc)
                
                # Extract status from JSON
                body=$(echo "$response" | head -n -1)
                status=$(echo "$body" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
                exec_time=$(echo "$body" | grep -o '"executionTime":[0-9]*' | cut -d':' -f2)
                
                echo "$req_id,$http_code,$req_time,$exec_time,$status" >> "$results_dir/results.csv"
            ) &
            pids+=($!)
        done
        
        # Wait for batch to complete
        for pid in "${pids[@]}"; do
            wait $pid 2>/dev/null
        done
        
        echo -n "."
    done
    
    local end_time=$(date +%s.%N)
    local total_duration=$(echo "$end_time - $start_time" | bc)
    
    echo ""
    echo ""
    
    # Analyze results
    while IFS=',' read -r req_id http_code req_time exec_time status; do
        if [ "$http_code" = "200" ] && [ "$status" = "SUCCESS" ]; then
            ((success++))
        else
            ((failed++))
        fi
        
        # Track timing stats
        total_time=$(echo "$total_time + $req_time" | bc 2>/dev/null || echo "$total_time")
        
        if [ $(echo "$req_time < $min_time" | bc) -eq 1 ]; then
            min_time=$req_time
        fi
        if [ $(echo "$req_time > $max_time" | bc) -eq 1 ]; then
            max_time=$req_time
        fi
    done < "$results_dir/results.csv"
    
    local total_reqs=$((success + failed))
    local avg_time=$(echo "scale=3; $total_time / $total_reqs" | bc 2>/dev/null || echo "N/A")
    local rps=$(echo "scale=2; $total_reqs / $total_duration" | bc 2>/dev/null || echo "N/A")
    
    echo -e "${GREEN}=== Load Test Results ===${NC}"
    echo "Total Requests:     $total_reqs"
    echo "Successful:         $success ($(echo "scale=1; $success * 100 / $total_reqs" | bc)%)"
    echo "Failed:             $failed"
    echo "Total Duration:     ${total_duration}s"
    echo "Requests/Second:    $rps"
    echo "Avg Response Time:  ${avg_time}s"
    echo "Min Response Time:  ${min_time}s"
    echo "Max Response Time:  ${max_time}s"
    
    # Cleanup
    rm -rf "$results_dir"
    
    return $failed
}

# Build and start Docker containers
start_docker() {
    echo -e "${YELLOW}Building and starting Docker containers...${NC}"
    cd "$PROJECT_DIR"
    
    # Stop existing containers
    docker-compose down 2>/dev/null
    
    # Build and start
    docker-compose up --build -d
    
    # Wait for backend to be ready
    echo "Waiting for backend to be ready..."
    for i in {1..120}; do
        if curl -s http://localhost:8080/api/health > /dev/null 2>&1; then
            echo -e "${GREEN}Backend is ready!${NC}"
            return 0
        fi
        sleep 1
    done
    
    echo -e "${RED}Backend failed to start within 120 seconds${NC}"
    docker-compose logs backend
    return 1
}

# Stop Docker containers
stop_docker() {
    echo -e "${YELLOW}Stopping Docker containers...${NC}"
    cd "$PROJECT_DIR"
    docker-compose down
}

# Show Docker resource usage
show_stats() {
    echo -e "${BLUE}Current Docker Container Stats:${NC}"
    docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.NetIO}}" 2>/dev/null
}

# Main
case "$1" in
    start)
        start_docker
        ;;
    stop)
        stop_docker
        ;;
    stats)
        show_stats
        ;;
    quick)
        run_load_test 10 50 python
        ;;
    stress)
        echo "Running stress test: 50 concurrent, 500 total"
        run_load_test 50 500 python
        ;;
    heavy)
        echo "Running heavy test: 100 concurrent, 1000 total"
        run_load_test 100 1000 python
        ;;
    extreme)
        echo "Running extreme test: 200 concurrent, 2000 total"
        run_load_test 200 2000 python
        ;;
    monitor)
        duration=${2:-60}
        container=${3:-coder_platform-1-backend-1}
        monitor_resources "$container" "$duration"
        ;;
    full)
        echo "=== Full Load Test Suite ==="
        echo ""
        echo "Phase 1: Warmup (10 concurrent)"
        run_load_test 10 50 python
        sleep 5
        show_stats
        
        echo ""
        echo "Phase 2: Normal Load (25 concurrent)"
        run_load_test 25 250 python
        sleep 5
        show_stats
        
        echo ""
        echo "Phase 3: High Load (50 concurrent)"
        run_load_test 50 500 python
        sleep 5
        show_stats
        
        echo ""
        echo "Phase 4: Stress Test (100 concurrent)"
        run_load_test 100 500 python
        show_stats
        
        echo ""
        echo "Phase 5: Breaking Point Test (150 concurrent)"
        run_load_test 150 300 python
        show_stats
        ;;
    *)
        echo "Usage: $0 <command>"
        echo ""
        echo "Docker Commands:"
        echo "  start    - Build and start Docker containers"
        echo "  stop     - Stop Docker containers"
        echo "  stats    - Show current container resource usage"
        echo ""
        echo "Load Test Commands:"
        echo "  quick    - Quick test (10 concurrent, 50 total)"
        echo "  stress   - Stress test (50 concurrent, 500 total)"
        echo "  heavy    - Heavy test (100 concurrent, 1000 total)"
        echo "  extreme  - Extreme test (200 concurrent, 2000 total)"
        echo "  full     - Full test suite (warmup -> breaking point)"
        echo ""
        echo "Monitoring:"
        echo "  monitor [duration] [container] - Monitor container resources"
        echo ""
        echo "Example workflow:"
        echo "  $0 start    # Start Docker containers"
        echo "  $0 quick    # Run quick test"
        echo "  $0 stats    # Check resource usage"
        echo "  $0 full     # Run full test suite"
        echo "  $0 stop     # Stop containers"
        ;;
esac
