/**
 * Akto Guardrails plugin for Amp (https://ampcode.com).
 *
 * Bridges Amp's plugin events onto the same Python validators the other Akto
 * connectors use:
 *
 *   session.start -> akto-hooks.py SessionStart   (observational)
 *   agent.start   -> akto-validate-prompt.py      (blocking: cancels the turn)
 *   tool.call     -> akto-validate-pre-tool.py    (blocking: reject-and-continue / modify)
 *   tool.result   -> akto-validate-post-tool.py   (observational)
 *   agent.end     -> akto-validate-response.py    (observational)
 *
 * Amp names MCP tools `mcp__<server>__<tool>` (same convention as Claude Code),
 * so the Python side routes MCP vs built-in tools off the tool name alone.
 *
 * Every path fails OPEN: if a validator is missing, errors, times out, or emits
 * unparseable output, the action is allowed. Guardrails must never wedge the agent.
 *
 * Plugins are executed by Bun, so this file is TypeScript and may use node: APIs.
 */

import type { PluginAPI, ThreadMessage, ThreadTextBlock } from '@ampcode/plugin'
import { spawn } from 'node:child_process'
import { appendFileSync, existsSync, mkdirSync, readFileSync } from 'node:fs'
import { homedir } from 'node:os'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

export const description = 'Validates Amp prompts and tool calls against Akto AI Guardrails'

const PLUGIN_DIR = dirname(fileURLToPath(import.meta.url))

function expandHome(p: string): string {
	return p.startsWith('~') ? join(homedir(), p.slice(1)) : p
}

/**
 * KEY=VALUE config read from ~/.config/amp/akto/config (override with AKTO_CONFIG_FILE).
 * Amp starts the plugin as a long-lived process and only passes on its own
 * environment, which a GUI-launched Amp does not inherit from a shell profile —
 * so the file is the reliable place to configure an enterprise install.
 */
function loadConfigFile(): Record<string, string> {
	const path = expandHome(process.env.AKTO_CONFIG_FILE || '~/.config/amp/akto/config')
	const config: Record<string, string> = {}
	try {
		if (!existsSync(path)) return config
		for (const raw of readFileSync(path, 'utf8').split('\n')) {
			const line = raw.trim()
			if (!line || line.startsWith('#')) continue
			const eq = line.indexOf('=')
			if (eq < 1) continue
			config[line.slice(0, eq).trim()] = line.slice(eq + 1).trim().replace(/^["']|["']$/g, '')
		}
	} catch {
		// A malformed config must not stop Amp from starting.
	}
	return config
}

const FILE_CONFIG = loadConfigFile()

/** Real environment wins; the config file supplies defaults. */
function cfg(key: string, fallback = ''): string {
	const fromEnv = process.env[key]
	if (fromEnv !== undefined && fromEnv !== '') return fromEnv
	return FILE_CONFIG[key] ?? fallback
}

/** Environment handed to the Python validators, with config-file values filled in. */
const CHILD_ENV: NodeJS.ProcessEnv = { ...process.env }
for (const [key, value] of Object.entries(FILE_CONFIG)) {
	if (!CHILD_ENV[key]) CHILD_ENV[key] = value
}

const AKTO_DATA_INGESTION_URL = cfg('AKTO_DATA_INGESTION_URL')
const AKTO_SYNC_MODE = cfg('AKTO_SYNC_MODE', 'true').toLowerCase() === 'true'
// Seconds in env (matches every other connector), milliseconds internally.
const AKTO_TIMEOUT_MS = (parseFloat(cfg('AKTO_TIMEOUT', '5')) || 5) * 1000
const PYTHON_BIN = cfg('AKTO_PYTHON', 'python3')
const LOG_DIR = expandHome(cfg('LOG_DIR', '~/.config/amp/akto/logs'))
const LOG_LEVEL = cfg('LOG_LEVEL', 'INFO').toUpperCase()

const SCRIPTS = {
	hooks: 'akto-hooks.py',
	prompt: 'akto-validate-prompt.py',
	preTool: 'akto-validate-pre-tool.py',
	postTool: 'akto-validate-post-tool.py',
	response: 'akto-validate-response.py',
} as const

/** Decision emitted on the validator's last stdout line. Absent/unparseable => allow. */
interface Decision {
	decision?: 'block' | 'allow'
	reason?: string
	/** Guardrail-rewritten tool arguments, replaces the tool input wholesale. */
	updatedInput?: Record<string, unknown>
}

function log(event: string, data: Record<string, unknown> = {}): void {
	if (LOG_LEVEL === 'ERROR') return
	try {
		if (!existsSync(LOG_DIR)) mkdirSync(LOG_DIR, { recursive: true })
		const line = `${new Date().toISOString()} - [${event}] ${JSON.stringify(data)}\n`
		appendFileSync(join(LOG_DIR, 'akto-guardrails.log'), line)
	} catch {
		// Logging must never break the agent.
	}
}

/**
 * Concatenated text of the last assistant message in a turn. `agent.end` carries
 * the user prompt on `event.message`; the reply has to be read out of `messages`.
 */
function assistantText(messages: ThreadMessage[] | undefined): string {
	if (!Array.isArray(messages)) return ''
	for (let i = messages.length - 1; i >= 0; i--) {
		const message = messages[i]
		if (message?.role !== 'assistant') continue
		return (message.content || [])
			.filter((block): block is ThreadTextBlock => block?.type === 'text')
			.map((block) => block.text)
			.join('')
	}
	return ''
}

/**
 * Run a validator with `payload` on stdin and read its decision off the last
 * stdout line. Resolves to an allow decision on any failure (fail-open).
 */
function runValidator(
	script: string,
	payload: Record<string, unknown>,
	args: string[] = [],
): Promise<Decision> {
	return new Promise((resolve) => {
		const scriptPath = join(PLUGIN_DIR, script)
		if (!existsSync(scriptPath)) {
			log('SCRIPT_NOT_FOUND', { script, path: scriptPath })
			resolve({ decision: 'allow' })
			return
		}

		let settled = false
		const finish = (result: Decision) => {
			if (settled) return
			settled = true
			clearTimeout(timer)
			resolve(result)
		}

		const child = spawn(PYTHON_BIN, [scriptPath, ...args], {
			stdio: ['pipe', 'pipe', 'pipe'],
			env: CHILD_ENV,
		})

		const timer = setTimeout(() => {
			log('VALIDATION_TIMEOUT', { script, timeoutMs: AKTO_TIMEOUT_MS })
			child.kill('SIGKILL')
			finish({ decision: 'allow' })
		}, AKTO_TIMEOUT_MS)

		let stdout = ''
		let stderr = ''
		child.stdout.on('data', (chunk) => (stdout += chunk.toString()))
		child.stderr.on('data', (chunk) => (stderr += chunk.toString()))

		child.on('error', (error: Error) => {
			log('VALIDATION_ERROR', { script, error: error.message })
			finish({ decision: 'allow' })
		})

		child.on('close', (code) => {
			if (stderr.trim()) log('VALIDATION_STDERR', { script, stderr: stderr.slice(0, 500) })
			const lines = stdout.trim().split('\n')
			const lastLine = lines[lines.length - 1]
			if (lastLine && lastLine.trim()) {
				try {
					finish(JSON.parse(lastLine) as Decision)
					return
				} catch (e) {
					log('VALIDATION_PARSE_ERROR', { script, error: (e as Error).message })
				}
			}
			log('VALIDATION_ALLOWED', { script, exitCode: code })
			finish({ decision: 'allow' })
		})

		// stdin can EPIPE if the validator exits early; that is already a fail-open path.
		child.stdin.on('error', () => {})
		child.stdin.end(JSON.stringify(payload))
	})
}

export default function (amp: PluginAPI) {
	log('PLUGIN_INIT', {
		ingestionConfigured: Boolean(AKTO_DATA_INGESTION_URL),
		syncMode: AKTO_SYNC_MODE,
		pluginDir: PLUGIN_DIR,
	})

	if (!AKTO_DATA_INGESTION_URL) {
		amp.logger.log('Akto Guardrails: AKTO_DATA_INGESTION_URL is not set; plugin is inactive.')
		return
	}

	// Observability only, dispatched through the shared akto-hooks.py runner that the
	// other connectors use for their SessionStart hook.
	amp.on('session.start', async (event) => {
		log('SESSION_START', { threadId: event.thread.id })
		await runValidator(
			SCRIPTS.hooks,
			{
				session_id: event.thread.id,
				conversation_id: event.thread.id,
				cwd: process.cwd(),
				hook_event_name: 'SessionStart',
			},
			['SessionStart'],
		)
	})

	// Prompt guardrail. Amp's agent.start result can only APPEND context, so a real
	// block is `thread.cancel()` — documented as preventing the turn from starting.
	amp.on('agent.start', async (event, ctx) => {
		const prompt = event.message || ''
		if (!prompt.trim()) return

		const result = await runValidator(SCRIPTS.prompt, {
			prompt,
			session_id: event.thread.id,
			conversation_id: event.thread.id,
			message_id: event.id,
			cwd: process.cwd(),
			hook_event_name: 'UserPromptSubmit',
		})

		// In observe mode the validator only ingests; it never returns a block.
		if (AKTO_SYNC_MODE && result.decision === 'block') {
			const reason = result.reason || 'Policy violation'
			log('PROMPT_BLOCKED', { threadId: event.thread.id, reason })
			// Notify first: once the turn is cancelled the UI stops rendering this turn.
			// ASCII-only — some terminals mangle multi-byte emoji.
			await ctx.ui.notify(`Akto Guardrails blocked this prompt. ${reason}`).catch(() => {})
			await ctx.thread.cancel()
			return
		}

		log('PROMPT_ALLOWED', { threadId: event.thread.id })
	})

	// Tool guardrail. `reject-and-continue` is Amp's supported hard block: the tool
	// never runs and the model sees the reason and can choose another route.
	amp.on('tool.call', async (event) => {
		const result = await runValidator(SCRIPTS.preTool, {
			tool_name: event.tool,
			tool_input: event.input || {},
			tool_use_id: event.toolUseID,
			session_id: event.thread.id,
			conversation_id: event.thread.id,
			cwd: process.cwd(),
			hook_event_name: 'PreToolUse',
		})

		// In observe mode the validator only ingests; it never returns a block.
		if (AKTO_SYNC_MODE && result.decision === 'block') {
			const reason = result.reason || 'Policy violation'
			log('TOOL_BLOCKED', { tool: event.tool, reason })
			return { action: 'reject-and-continue' as const, message: reason }
		}

		if (AKTO_SYNC_MODE && result.updatedInput && typeof result.updatedInput === 'object') {
			log('TOOL_INPUT_MODIFIED', { tool: event.tool })
			return { action: 'modify' as const, input: result.updatedInput }
		}

		return { action: 'allow' as const }
	})

	// Observational: the tool already ran, so this only feeds Akto's audit trail.
	// Returning undefined keeps Amp's original result untouched.
	amp.on('tool.result', async (event) => {
		await runValidator(SCRIPTS.postTool, {
			tool_name: event.tool,
			tool_input: event.input || {},
			tool_response: event.output ?? {},
			status: event.status,
			error: event.error || '',
			tool_use_id: event.toolUseID,
			session_id: event.thread.id,
			conversation_id: event.thread.id,
			cwd: process.cwd(),
			hook_event_name: 'PostToolUse',
		})
		return undefined
	})

	// Observational: ingest the finished turn. Returning void ends the turn normally.
	amp.on('agent.end', async (event) => {
		await runValidator(SCRIPTS.response, {
			prompt: event.message || '',
			response: assistantText(event.messages),
			status: event.status,
			session_id: event.thread.id,
			conversation_id: event.thread.id,
			message_id: event.id,
			cwd: process.cwd(),
			hook_event_name: 'Stop',
		})
	})
}
