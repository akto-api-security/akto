/**
 * Akto Guardrails Plugin for OpenCode
 * Validates prompts and tools, logs data to Akto
 * Supports both non-MCP tools (built-in like "read") and MCP tools (format: server_tool)
 * Data format: OpenCode-specific
 */

const fs = require('fs')
const path = require('path')
const https = require('https')
const http = require('http')
const { spawn } = require('child_process')

const LOG_DIR = `${process.env.HOME}/.config/opencode/akto/logs`
const OPENCODE_DIR = path.dirname(__filename)
const AKTO_DATA_INGESTION_URL = process.env.AKTO_DATA_INGESTION_URL || ''
const AKTO_SYNC_MODE = (process.env.AKTO_SYNC_MODE || 'true').toLowerCase() === 'true'
const AKTO_TIMEOUT = parseInt(process.env.AKTO_TIMEOUT || '5', 10) * 1000

function ensureLogDir() {
  if (!fs.existsSync(LOG_DIR)) {
    fs.mkdirSync(LOG_DIR, { recursive: true })
  }
}

function log(hookName, data) {
  ensureLogDir()
  const timestamp = new Date().toISOString()
  const logFile = path.join(LOG_DIR, 'akto-guardrails.log')
  const line = `[${timestamp}] [${hookName}] ${JSON.stringify(data)}\n`
  fs.appendFileSync(logFile, line)
}

// Send HTTP request to Akto - non-blocking
function sendToAkto(payload) {
  if (!AKTO_DATA_INGESTION_URL) {
    return
  }

  const url = new URL(AKTO_DATA_INGESTION_URL)
  const isHttps = url.protocol === 'https:'
  const client = isHttps ? https : http

  const options = {
    hostname: url.hostname,
    port: url.port,
    path: url.pathname + url.search,
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    timeout: 5000,
  }

  const payloadJson = JSON.stringify(payload)

  // Log curl-like request
  log('CURL_REQUEST', {
    method: 'POST',
    url: AKTO_DATA_INGESTION_URL,
    headers: options.headers,
    body: payload,
  })

  const req = client.request(options, (res) => {
    let data = ''
    res.on('data', (chunk) => {
      data += chunk
    })
    res.on('end', () => {
      log('CURL_RESPONSE', {
        statusCode: res.statusCode,
        headers: res.headers,
        body: data ? (data.length > 500 ? data.slice(0, 500) + '...' : data) : 'empty'
      })
    })
  })

  req.on('error', (error) => {
    log('AKTO_ERROR', { error: error.message })
  })

  req.on('timeout', () => {
    req.destroy()
    log('AKTO_TIMEOUT', {})
  })

  req.write(payloadJson)
  req.end()
}

// Detect if tool is MCP format (server_tool with single underscore)
function isMcpTool(toolName) {
  if (!toolName || typeof toolName !== 'string') return false
  // MCP tools have format: <server>_<tool> where server doesn't contain underscore
  const parts = toolName.split('_')
  return parts.length >= 2 && parts[0].length > 0
}

// Parse MCP tool name: calculator_add -> { server: 'calculator', tool: 'add' }
function parseMcpTool(toolName) {
  if (!toolName || typeof toolName !== 'string') {
    return { isMcp: false, server: '', tool: '' }
  }
  const parts = toolName.split('_')
  if (parts.length < 2) {
    return { isMcp: false, server: '', tool: '' }
  }
  return {
    isMcp: true,
    server: parts[0],
    tool: parts.slice(1).join('_'),
  }
}

// Run Python script synchronously for prompt validation - BLOCKING
// Returns promise with { decision, reason } or { decision: 'allow' }
function runPromptValidationSync(prompt) {
  return new Promise((resolve) => {
    const scriptPath = path.join(OPENCODE_DIR, 'akto-validate-prompt.py')

    if (!fs.existsSync(scriptPath)) {
      log('SCRIPT_NOT_FOUND', { script: 'akto-validate-prompt.py', path: scriptPath })
      resolve({ decision: 'allow' }) // Fail-open if script not found
      return
    }

    const pythonProcess = spawn('python3', [scriptPath], {
      stdio: ['pipe', 'pipe', 'pipe'],
      timeout: AKTO_TIMEOUT,
    })

    let stdoutData = ''
    let stderrData = ''

    const inputData = { prompt }

    pythonProcess.stdout.on('data', (data) => {
      stdoutData += data.toString()
    })

    pythonProcess.stderr.on('data', (data) => {
      stderrData += data.toString()
    })

    pythonProcess.on('error', (error) => {
      log('PROMPT_VALIDATION_ERROR', { error: error.message })
      resolve({ decision: 'allow' }) // Fail-open on error
    })

    pythonProcess.on('close', (code) => {
      if (stderrData) {
        log('PROMPT_VALIDATION_STDERR', { stderr: stderrData.substring(0, 500) })
      }

      try {
        const outputLines = stdoutData.trim().split('\n')
        const lastLine = outputLines[outputLines.length - 1]

        if (lastLine && lastLine.trim()) {
          const result = JSON.parse(lastLine)

          if (result.decision === 'block') {
            log('PROMPT_BLOCKED_BY_GUARDRAILS', { reason: result.reason })
            resolve({ decision: 'block', reason: result.reason })
            return
          }
        }
      } catch (e) {
        log('PROMPT_VALIDATION_PARSE_ERROR', { error: e.message })
      }

      log('PROMPT_ALLOWED', { statusCode: code })
      resolve({ decision: 'allow' })
    })

    // Timeout handler
    const timeoutHandle = setTimeout(() => {
      log('PROMPT_VALIDATION_TIMEOUT', { timeout: AKTO_TIMEOUT })
      pythonProcess.kill()
      resolve({ decision: 'allow' }) // Fail-open on timeout
    }, AKTO_TIMEOUT)

    pythonProcess.stdin.write(JSON.stringify(inputData))
    pythonProcess.stdin.end()

    // Clear timeout if process exits before timeout
    pythonProcess.once('exit', () => clearTimeout(timeoutHandle))
  })
}

// Run Python script for MCP tool handling - non-blocking
function runPythonMcpScript(scriptName, toolName, toolInput, isAfterHook = false) {
  const scriptPath = path.join(OPENCODE_DIR, scriptName)

  if (!fs.existsSync(scriptPath)) {
    log('SCRIPT_NOT_FOUND', { script: scriptName, path: scriptPath })
    return
  }

  const pythonProcess = spawn('python3', [scriptPath], {
    stdio: ['pipe', 'pipe', 'pipe'],
    timeout: (parseInt(process.env.AKTO_TIMEOUT || '5', 10) + 2) * 1000,
  })

  const inputData = {
    tool_name: toolName,
    tool_input: toolInput,
    ...(isAfterHook && { tool_response: toolInput }), // For AFTER hook, input becomes response
  }

  pythonProcess.stdout.on('data', (data) => {
    const output = data.toString().trim()
    if (output) {
      log(`MCP_SCRIPT_OUTPUT_${scriptName}`, { output })
    }
  })

  pythonProcess.stderr.on('data', (data) => {
    log(`MCP_SCRIPT_STDERR_${scriptName}`, { error: data.toString() })
  })

  pythonProcess.on('error', (error) => {
    log('MCP_PYTHON_ERROR', { script: scriptName, error: error.message })
  })

  // Write to stdin, not directly to process
  pythonProcess.stdin.write(JSON.stringify(inputData))
  pythonProcess.stdin.end()

  // Timeout handler
  setTimeout(() => {
    try {
      pythonProcess.kill()
    } catch (e) {
      // Process already terminated
    }
  }, (parseInt(process.env.AKTO_TIMEOUT || '5', 10) + 2) * 1000)
}

export default async function aktoGuardrails(ctx) {
  log('PLUGIN_INIT', { message: 'Akto guardrails plugin initialized' })

  return {
    // Hook 1: Validate prompts BEFORE sending to AI
    "experimental.chat.messages.transform": async (input, output) => {
      try {
        if (!output?.messages || output.messages.length === 0) {
          return
        }

        const userMessage = output.messages.find((m) => m?.info?.role === 'user')
        if (!userMessage) {
          return
        }

        const content = userMessage?.parts?.[0]?.text || ''
        if (!content.trim()) {
          return
        }

        log('PROMPT_RECEIVED', { contentLength: content.length, preview: content.substring(0, 50) })

        // ============================================================
        // SYNC MODE: Validate prompt before sending to AI (BLOCKING)
        // ============================================================
        if (AKTO_SYNC_MODE && AKTO_DATA_INGESTION_URL) {
          log('PROMPT_VALIDATION_START', { syncMode: true })

          const validationResult = await runPromptValidationSync(content)
          log('VALIDATION_RESULT_RECEIVED', { decision: validationResult.decision })

          if (validationResult.decision === 'block') {
            log('PROMPT_BLOCKED_WITH_MESSAGE', { reason: validationResult.reason })

            // BLOCK THE PROMPT: Replace with blocking message
            // Show a clean UI message instead of exception
            const blockMessage = `🚫 Akto Guardrails\n\nYour prompt was blocked by Akto guardrails:\n"${validationResult.reason}"\n\nPlease rephrase your request and try again.`

            // Clear user message and show block notification
            output.messages = []

            // Add a system message showing the block reason
            output.messages.push({
              info: { role: 'assistant' },  // Show as assistant message for visibility
              parts: [{
                text: blockMessage
              }]
            })

            log('PROMPT_BLOCKED_MESSAGE_ADDED', {
              messageLength: blockMessage.length,
              reason: validationResult.reason
            })

            // Return the modified output - this prevents AI from responding
            // and shows the block message to user instead
            return output
          }

          log('PROMPT_VALIDATION_COMPLETE', { decision: 'allow' })
        }

        // ============================================================
        // LOGGING: Send to Akto (non-blocking, for audit trail)
        // ============================================================
        if (AKTO_DATA_INGESTION_URL) {
          const payload = {
            path: '/v1/chat/messages',
            method: 'POST',
            requestPayload: JSON.stringify({ body: content }),
            responsePayload: JSON.stringify({}),
            requestHeaders: JSON.stringify({
              'host': 'https://opencode.ai/',
              'x-opencode-hook': 'PromptValidation',
              'content-type': 'application/json',
            }),
            responseHeaders: JSON.stringify({
              'x-opencode-hook': 'PromptValidation',
            }),
            time: String(Date.now()),
            type: 'HTTP/1.1',
            statusCode: '200',
            tag: JSON.stringify({
              'gen-ai': 'Gen AI',
              'source': 'ENDPOINT',
              'hook-type': 'prompt',
            }),
          }
          sendToAkto(payload)
        }
      } catch (error) {
        log('PROMPT_ERROR_CAUGHT', { error: error.message })
        throw error
      }
    },

    // Hook 2: Validate tool BEFORE execution
    "tool.execute.before": async (input, output) => {
      try {
        const toolName = input?.tool || ''
        const toolArgs = output?.args || {}

        log('TOOL_EXECUTE_BEFORE', { tool: toolName, args: toolArgs })

        if (!toolName) {
          return
        }

        log('TOOL_FOUND', { tool: toolName })

        // Check if this is an MCP tool (format: server_tool)
        const mcpInfo = parseMcpTool(toolName)

        if (mcpInfo.isMcp) {
          // Handle MCP tool with Python script
          log('MCP_TOOL_DETECTED', { tool: toolName, server: mcpInfo.server, mcpTool: mcpInfo.tool })
          if (AKTO_DATA_INGESTION_URL) {
            runPythonMcpScript('akto-mcp-request.py', toolName, toolArgs, false)
          }
        } else {
          // Handle non-MCP tool (built-in like "read") with direct HTTP
          log('NON_MCP_TOOL_DETECTED', { tool: toolName })
          if (AKTO_DATA_INGESTION_URL) {
            log('SENDING_TO_AKTO', { tool: toolName, url: AKTO_DATA_INGESTION_URL })
            const payload = {
              path: '/v1/tools/execute',
              method: 'POST',
              requestPayload: JSON.stringify({ tool: toolName, args: toolArgs }),
              responsePayload: JSON.stringify({}),
              requestHeaders: JSON.stringify({
                'host': 'https://opencode.ai/',
                'x-opencode-hook': 'PreToolUse',
                'content-type': 'application/json',
              }),
              responseHeaders: JSON.stringify({
                'x-opencode-hook': 'PreToolUse',
              }),
              time: String(Date.now()),
              type: 'HTTP/1.1',
              statusCode: '200',
              tag: JSON.stringify({
                'gen-ai': 'Gen AI',
                'tool-use': 'Tool Execution',
                'tool-name': toolName,
                'source': 'ENDPOINT',
                'hook-type': 'tool-before',
              }),
            }
            sendToAkto(payload)
          }
        }
      } catch (error) {
        log('TOOL_BEFORE_ERROR', { error: error.message })
      }
    },

    // Hook 3: Log tool response AFTER execution
    "tool.execute.after": async (input, output) => {
      try {
        const toolName = input?.tool || ''
        const toolArgs = input?.args || {}
        const toolOutput = output?.output || ''

        log('TOOL_EXECUTE_AFTER', { tool: toolName, outputSize: String(toolOutput).length })

        if (!toolName) {
          return
        }

        log('TOOL_RESPONSE_FOUND', { tool: toolName })

        // Check if this is an MCP tool (format: server_tool)
        const mcpInfo = parseMcpTool(toolName)

        if (mcpInfo.isMcp) {
          // Handle MCP tool response with Python script
          log('MCP_TOOL_RESPONSE_DETECTED', { tool: toolName, server: mcpInfo.server })
          if (AKTO_DATA_INGESTION_URL) {
            runPythonMcpScript('akto-mcp-response.py', toolName, toolOutput, true)
          }
        } else {
          // Handle non-MCP tool response (built-in like "read") with direct HTTP
          log('NON_MCP_TOOL_RESPONSE_DETECTED', { tool: toolName })
          if (AKTO_DATA_INGESTION_URL) {
            log('SENDING_TOOL_RESPONSE_TO_AKTO', { tool: toolName })
            const payload = {
              path: '/v1/tools/execute',
              method: 'POST',
              requestPayload: JSON.stringify({ tool: toolName, args: toolArgs }),
              responsePayload: JSON.stringify({ output: toolOutput }),
              requestHeaders: JSON.stringify({
                'host': 'https://opencode.ai/',
                'x-opencode-hook': 'PostToolUse',
                'content-type': 'application/json',
              }),
              responseHeaders: JSON.stringify({
                'x-opencode-hook': 'PostToolUse',
              }),
              time: String(Date.now()),
              type: 'HTTP/1.1',
              statusCode: '200',
              tag: JSON.stringify({
                'gen-ai': 'Gen AI',
                'tool-use': 'Tool Execution',
                'tool-name': toolName,
                'source': 'ENDPOINT',
                'hook-type': 'tool-after',
              }),
            }
            sendToAkto(payload)
          }
        }
      } catch (error) {
        log('TOOL_AFTER_ERROR', { error: error.message })
      }
    },
  }
}
