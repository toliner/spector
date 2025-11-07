#!/usr/bin/env bash
#
# SessionStart Hook: Setup proxy for restricted network environments
#
# This hook starts a local proxy server to work around Java HTTP authentication
# issues when connecting through proxies that only advertise Bearer authentication.

# Only run in remote environments (Claude Code Remote)
if [ "$CLAUDE_CODE_REMOTE" != "true" ]; then
  exit 0
fi

echo "🔧 Setting up proxy configuration for remote environment..."

# Path to the local proxy script
PROXY_SCRIPT="$REPO_ROOT/local_proxy.py"
PROXY_PORT=8888

# Check if proxy script exists
if [ ! -f "$PROXY_SCRIPT" ]; then
  echo "⚠️  Proxy script not found at $PROXY_SCRIPT"
  exit 1
fi

# Check if proxy is already running
if lsof -i :$PROXY_PORT -sTCP:LISTEN -t >/dev/null 2>&1; then
  echo "✅ Local proxy already running on port $PROXY_PORT"
else
  echo "🚀 Starting local proxy server..."
  # Start proxy in background
  python3 "$PROXY_SCRIPT" > /tmp/local_proxy.log 2>&1 &
  PROXY_PID=$!

  # Wait a moment for the proxy to start
  sleep 2

  # Verify proxy is running
  if lsof -i :$PROXY_PORT -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo "✅ Local proxy started successfully (PID: $PROXY_PID)"
  else
    echo "❌ Failed to start local proxy. Check /tmp/local_proxy.log for details"
    exit 1
  fi
fi

# Export Java proxy settings for Gradle and other Java tools
# These will be picked up by the Gradle wrapper and all Java processes
export JAVA_TOOL_OPTIONS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=$PROXY_PORT -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=$PROXY_PORT"

echo "✅ Proxy configuration complete"
echo "   Java tools will use proxy at 127.0.0.1:$PROXY_PORT"
