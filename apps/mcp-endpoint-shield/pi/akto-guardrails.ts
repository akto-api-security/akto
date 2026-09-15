/**
 * Akto guardrails for Pi.dev — local extension (no `pi install` required).
 *
 * Pi auto-discovers this file from ~/.pi/agent/extensions/. It spawns the Akto
 * Python hook wrappers with Claude Code-compatible JSON on stdin and maps the
 * JSON stdout back to Pi block/continue semantics.
 */
import type { ExtensionAPI } from "@earendil-works/pi-coding-agent";
import { spawn } from "node:child_process";
import { homedir } from "node:os";
import { join } from "node:path";

const HOOKS_DIR = join(homedir(), ".pi/hooks/akto");
const HOOK_TIMEOUT_MS = 10_000;

type HookRunResult = {
	stdout: string;
	stderr: string;
	exitCode: number;
};

function parseJson(stdout: string): Record<string, unknown> | undefined {
	const trimmed = stdout.trim();
	if (!trimmed) return undefined;
	try {
		return JSON.parse(trimmed) as Record<string, unknown>;
	} catch {
		return undefined;
	}
}

function hookSpecific(
	eventName: string,
	json: Record<string, unknown>,
): Record<string, unknown> | undefined {
	const raw = json.hookSpecificOutput;
	if (typeof raw !== "object" || raw === null) return undefined;
	const hs = raw as Record<string, unknown>;
	if (typeof hs.hookEventName === "string" && hs.hookEventName !== eventName) {
		return undefined;
	}
	return hs;
}

async function runWrapper(
	scriptName: string,
	input: Record<string, unknown>,
	cwd: string,
): Promise<HookRunResult> {
	const command = join(HOOKS_DIR, scriptName);
	const inputJson = JSON.stringify(input);

	return new Promise((resolve) => {
		const child = spawn("bash", [command], {
			cwd,
			stdio: ["pipe", "pipe", "pipe"],
		});

		let stdout = "";
		let stderr = "";
		let settled = false;

		const finish = (result: HookRunResult) => {
			if (settled) return;
			settled = true;
			resolve(result);
		};

		child.stdout.on("data", (chunk) => {
			stdout += chunk.toString();
		});
		child.stderr.on("data", (chunk) => {
			stderr += chunk.toString();
		});

		child.stdin.write(inputJson);
		child.stdin.end();

		const timer = setTimeout(() => {
			child.kill();
			finish({
				stdout,
				stderr: `${stderr}\n[Akto] Hook timed out`.trim(),
				exitCode: 1,
			});
		}, HOOK_TIMEOUT_MS);

		child.on("close", (code) => {
			clearTimeout(timer);
			finish({ stdout, stderr, exitCode: code ?? 1 });
		});

		child.on("error", (err) => {
			clearTimeout(timer);
			finish({ stdout, stderr: err.message, exitCode: 1 });
		});
	});
}

function sessionBase(ctx: { cwd: string; sessionManager: { getSessionFile(): string } }) {
	return {
		session_id: ctx.sessionManager.getSessionFile(),
		transcript_path: ctx.sessionManager.getSessionFile(),
		cwd: ctx.cwd,
	};
}

function textFromContent(content: unknown): string {
	if (typeof content === "string") return content;
	if (!Array.isArray(content)) return "";
	return content
		.map((part) => {
			if (typeof part !== "object" || part === null) return "";
			const p = part as { type?: string; text?: string };
			return p.type === "text" && typeof p.text === "string" ? p.text : "";
		})
		.filter(Boolean)
		.join("\n");
}

function lastAssistantText(messages: unknown[]): string {
	for (let i = messages.length - 1; i >= 0; i--) {
		const msg = messages[i] as { role?: string; content?: unknown };
		if (msg?.role === "assistant") return textFromContent(msg.content);
	}
	return "";
}

export default function (pi: ExtensionAPI) {
	let stopHookActive = false;

	pi.on("input", async (event, ctx) => {
		stopHookActive = false;

		const { stdout, stderr, exitCode } = await runWrapper(
			"akto-validate-prompt-wrapper.sh",
			{
				...sessionBase(ctx),
				hook_event_name: "UserPromptSubmit",
				prompt: event.text,
			},
			ctx.cwd,
		);

		if (exitCode !== 0) {
			if (stderr) ctx.ui.notify(`Akto prompt hook failed: ${stderr}`, "error");
			return { action: "continue" as const };
		}

		const json = parseJson(stdout);
		if (json?.decision === "block") {
			const reason =
				(typeof json.reason === "string" && json.reason) || "Prompt blocked by Akto";
			ctx.ui.notify(reason, "warning");
			return { action: "handled" as const };
		}

		return { action: "continue" as const };
	});

	pi.on("tool_call", async (event, ctx) => {
		const { stdout, stderr, exitCode } = await runWrapper(
			"akto-validate-mcp-request-wrapper.sh",
			{
				...sessionBase(ctx),
				hook_event_name: "PreToolUse",
				tool_name: event.toolName,
				tool_input: event.input,
				tool_use_id: event.toolCallId,
			},
			ctx.cwd,
		);

		if (exitCode === 2) {
			return { block: true, reason: stderr || "Tool blocked by Akto" };
		}

		if (exitCode !== 0) {
			if (stderr) ctx.ui.notify(`Akto pre-tool hook failed: ${stderr}`, "error");
			return undefined;
		}

		const json = parseJson(stdout);
		if (!json) return undefined;

		const hs = hookSpecific("PreToolUse", json);
		const decision = (hs?.permissionDecision ?? json.permissionDecision) as
			| string
			| undefined;

		if (decision === "deny") {
			const reason =
				(typeof hs?.permissionDecisionReason === "string" &&
					hs.permissionDecisionReason) ||
				(typeof json.permissionDecisionReason === "string" &&
					json.permissionDecisionReason) ||
				"Tool blocked by Akto";
			return { block: true, reason };
		}

		const updatedInput = (hs?.updatedInput ?? json.updatedInput) as
			| Record<string, unknown>
			| undefined;
		if (updatedInput && typeof updatedInput === "object") {
			Object.assign(event.input, updatedInput);
		}

		return undefined;
	});

	pi.on("tool_result", async (event, ctx) => {
		if (event.isError) return;

		const { stderr, exitCode } = await runWrapper(
			"akto-validate-mcp-response-wrapper.sh",
			{
				...sessionBase(ctx),
				hook_event_name: "PostToolUse",
				tool_name: event.toolName,
				tool_input: event.input,
				tool_use_id: event.toolCallId,
				tool_response: {
					content: event.content,
					details: event.details,
					is_error: event.isError,
					output: textFromContent(event.content),
				},
			},
			ctx.cwd,
		);

		if (exitCode !== 0 && stderr) {
			ctx.ui.notify(`Akto post-tool hook failed: ${stderr}`, "error");
		}
	});

	pi.on("agent_end", async (event, ctx) => {
		const { stdout, stderr, exitCode } = await runWrapper(
			"akto-validate-response-wrapper.sh",
			{
				...sessionBase(ctx),
				hook_event_name: "Stop",
				stop_hook_active: stopHookActive,
				last_assistant_message: lastAssistantText(event.messages),
			},
			ctx.cwd,
		);

		if (exitCode !== 0) {
			if (stderr) ctx.ui.notify(`Akto response hook failed: ${stderr}`, "error");
			stopHookActive = false;
			return;
		}

		const json = parseJson(stdout);
		if (json?.decision === "block") {
			const reason =
				(typeof json.reason === "string" && json.reason) ||
				"Response blocked by Akto — continuing for review";
			stopHookActive = true;
			pi.sendMessage(
				{
					customType: "akto-guardrails",
					content: reason,
					display: false,
					details: { hookEventName: "Stop", stopHookActive: true },
				},
				{ deliverAs: "followUp", triggerTurn: true },
			);
			return;
		}

		stopHookActive = false;
	});
}
