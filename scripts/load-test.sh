#!/bin/bash

# Load Test Script for Coder Platform
# Tests concurrent code execution and finds breaking point

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

API_URL="${API_URL:-http://localhost:8080/api/execute}"
CONCURRENT_REQUESTS="${CONCURRENT_REQUESTS:-10}"
TOTAL_REQUESTS="${TOTAL_REQUESTS:-100}"
RAMP_UP="${RAMP_UP:-false}"
MAX_CONCURRENT="${MAX_CONCURRENT:-100}"

echo -e "${GREEN}=== Coder Platform Load Test ===${NC}"
echo "API URL: $API_URL"
echo "Concurrent Requests: $CONCURRENT_REQUESTS"
echo "Total Requests: $TOTAL_REQUESTS"
echo ""

# Check if curl is available
if ! command -v curl &> /dev/null; then
    echo -e "${RED}Error: curl is required but not installed${NC}"
    exit 1
fi

# Check if jq is available for JSON parsing
if ! command -v jq &> /dev/null; then
    echo -e "${YELLOW}Warning: jq not found, some features may be limited${NC}"
    HAS_JQ=false
else
    HAS_JQ=true
fi

# Test payloads for different languages
PYTHON_PAYLOAD='{"language":"python","code":"import time\nstart = time.time()\nresult = sum(range(10000))\nprint(f\"Sum: {result}\")\nprint(f\"Time: {time.time()-start:.4f}s\")"}'

JAVA_PAYLOAD='{"language":"java","code":"public class Main {\n    public static void main(String[] args) {\n        long start = System.nanoTime();\n        long sum = 0;\n        for(int i = 0; i < 10000; i++) sum += i;\n        System.out.println(\"Sum: \" + sum);\n        System.out.println(\"Time: \" + (System.nanoTime()-start)/1e9 + \"s\");\n    }\n}"}'

JS_PAYLOAD='{"language":"javascript","code":"const start = Date.now();\nlet sum = 0;\nfor(let i = 0; i < 10000; i++) sum += i;\nconsole.log(`Sum: ${sum}`);\nconsole.log(`Time: ${(Date.now()-start)/1000}s`);"}'

# Simple hello world for quick tests
QUICK_PAYLOAD='{"language":"python","code":"print(\"Hello, World!\")"}'

# Function to make a single request and measure time
make_request() {
    local request_id=$1
    local payload=$2
    local start_time=$(date +%s.%N)
    
    response=$(curl -s -w "\n%{http_code}\n%{time_total}" \
        -X POST "$API_URL" \
        -H "Content-Type: application/json" \
        -d "$payload" 2>/dev/null)
    
    local end_time=$(date +%s.%N)
    local http_code=$(echo "$response" | tail -n2 | head -n1)
    local curl_time=$(echo "$response" | tail -n1)
    local body=$(echo "$response" | head -n -2)
    
    # Extract execution time from response if jq available
    local exec_time="N/A"
    local status="UNKNOWN"
    if [ "$HAS_JQ" = true ] && [ -n "$body" ]; then
        exec_time=$(echo "$body" | jq -r '.executionTime // "N/A"' 2>/dev/null)
        status=$(echo "$body" | jq -r '.status // "UNKNOWN"' 2>/dev/null)
    fi
    
    echo "$request_id,$http_code,$curl_time,$exec_time,$status"
}

# Function to run concurrent requests
run_concurrent_batch() {
    local concurrent=$1
    local payload=$2
    local batch_id=$3
    
    echo -e "${BLUE}Running batch $batch_id with $concurrent concurrent requests...${NC}"
    
    local pids=()
    local results_file="/tmp/load_test_results_$$.txt"
    > "$results_file"
    
    for i in $(seq 1 $concurrent); do
        (make_request "${batch_id}_${i}" "$payload" >> "$results_file") &
        pids+=($!)
    done
    
    # Wait for all requests to complete
    for pid in "${pids[@]}"; do
        wait $pid 2>/dev/null
    done
    
    # Parse results
    local success=0
    local failed=0
    local total_time=0
    local total_exec_time=0
    local count=0
    
    while IFS=',' read -r req_id http_code curl_time exec_time status; do
        if [ "$http_code" = "200" ] && [ "$status" = "SUCCESS" ]; then
            ((success++))
        else
            ((failed++))
        fi
        total_time=$(echo "$total_time + $curl_time" | bc 2>/dev/null || echo "$total_time")
        if [ "$exec_time" != "N/A" ] && [ "$exec_time" != "null" ]; then
            total_exec_time=$(echo "$total_exec_time + $exec_time" | bc 2>/dev/null || echo "$total_exec_time")
        fi
        ((count++))
    done < "$results_file"
    
    local avg_time=$(echo "scale=3; $total_time / $count" | bc 2>/dev/null || echo "N/A")
    local avg_exec=$(echo "scale=0; $total_exec_time / $count" | bc 2>/dev/null || echo "N/A")
    
    echo -e "  Success: ${GREEN}$success${NC} | Failed: ${RED}$failed${NC} | Avg Response: ${avg_time}s | Avg Exec: ${avg_exec}ms"
    
    rm -f "$results_file"
    
    # Return failure count
    echo "$failed"
}

# Function to find breaking point
find_breaking_point() {
    local payload=$1
    local start_concurrent=5
    local step=5
    local max=$MAX_CONCURRENT
    
    echo -e "${YELLOW}=== Finding Breaking Point ===${NC}"
    echo "Starting with $start_concurrent concurrent requests, stepping by $step"
    echo ""
    
    local current=$start_concurrent
    local last_success=$current
    
    while [ $current -le $max ]; do
        result=$(run_concurrent_batch $current "$payload" "bp_$current")
        failures=$(echo "$result" | tail -n1)
        
        if [ "$failures" -gt 0 ]; then
            echo -e "${RED}Breaking point found around $current concurrent requests${NC}"
            echo "Last successful: $last_success concurrent requests"
            return
        fi
        
        last_success=$current
        ((current += step))
        sleep 1  # Brief pause between batches
    done
    
    echo -e "${GREEN}No breaking point found up to $max concurrent requests${NC}"
}

# Main execution
case "$1" in
    quick)
        echo "Running quick test with 10 requests..."
        for i in $(seq 1 10); do
            result=$(make_request $i "$QUICK_PAYLOAD")
            echo "Request $i: $result"
        done
        ;;
    concurrent)
        echo "Running concurrent test..."
        run_concurrent_batch $CONCURRENT_REQUESTS "$PYTHON_PAYLOAD" "concurrent"
        ;;
    stress)
        echo "Running stress test with multiple batches..."
        for batch in $(seq 1 5); do
            run_concurrent_batch $CONCURRENT_REQUESTS "$PYTHON_PAYLOAD" "stress_$batch"
            sleep 1
        done
        ;;
    breaking)
        find_breaking_point "$QUICK_PAYLOAD"
        ;;
    ramp)
        echo "Running ramp-up test..."
        for concurrent in 5 10 20 30 50 75 100; do
            run_concurrent_batch $concurrent "$QUICK_PAYLOAD" "ramp_$concurrent"
            sleep 2
        done
        ;;
    mixed)
        echo "Running mixed language test..."
        echo -e "${BLUE}Python:${NC}"
        run_concurrent_batch 10 "$PYTHON_PAYLOAD" "python"
        sleep 1
        echo -e "${BLUE}JavaScript:${NC}"
        run_concurrent_batch 10 "$JS_PAYLOAD" "js"
        sleep 1
        echo -e "${BLUE}Java:${NC}"
        run_concurrent_batch 10 "$JAVA_PAYLOAD" "java"
        ;;
    *)
        echo "Usage: $0 <command>"
        echo ""
        echo "Commands:"
        echo "  quick     - Run 10 sequential requests (warmup)"
        echo "  concurrent - Run $CONCURRENT_REQUESTS concurrent requests"
        echo "  stress    - Run 5 batches of concurrent requests"
        echo "  breaking  - Find the breaking point (max concurrent)"
        echo "  ramp      - Gradually increase concurrent requests"
        echo "  mixed     - Test multiple languages"
        echo ""
        echo "Environment variables:"
        echo "  API_URL=$API_URL"
        echo "  CONCURRENT_REQUESTS=$CONCURRENT_REQUESTS"
        echo "  MAX_CONCURRENT=$MAX_CONCURRENT"
        ;;
esac
