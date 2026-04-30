import http from "node:http";

const port = Number.parseInt(process.env.MOCK_OPENAI_PORT ?? "18080", 10);
const host = process.env.MOCK_OPENAI_HOST ?? "127.0.0.1";
const delayMs = Number.parseInt(process.env.MOCK_OPENAI_DELAY_MS ?? "1000", 10);
const completionTokens = Number.parseInt(process.env.MOCK_OPENAI_COMPLETION_TOKENS ?? "64", 10);
const logEvery = Number.parseInt(process.env.MOCK_OPENAI_LOG_EVERY ?? "100", 10);
const toolCallMode = process.env.MOCK_OPENAI_TOOL_CALL_MODE ?? "never";

let requestCount = 0;

const server = http.createServer(async (request, response) => {
  if (request.method !== "POST" || request.url !== "/v1/chat/completions") {
    return sendJson(response, 404, { error: { message: "Not found" } });
  }

  const startedAt = Date.now();
  const bodyText = await readBody(request);
  const requestId = ++requestCount;

  try {
    const body = JSON.parse(bodyText || "{}");
    await sleep(delayMs);

    if (body.stream === true) {
      return sendJson(response, 400, { error: { message: "stream is not supported by mock provider" } });
    }

    const providerResponse = shouldReturnToolCall(body)
      ? buildToolCallResponse(body)
      : buildFinalResponse(body);

    if (requestId % logEvery === 0) {
      const durationMs = Date.now() - startedAt;
      console.log(`[mock-openai] handled=${requestId} durationMs=${durationMs}`);
    }

    return sendJson(response, 200, providerResponse);
  } catch (error) {
    return sendJson(response, 400, {
      error: {
        message: error instanceof Error ? error.message : "Invalid request",
      },
    });
  }
});

server.listen(port, host, () => {
  console.log(`[mock-openai] listening on http://${host}:${port}`);
  console.log(`[mock-openai] delayMs=${delayMs} toolCallMode=${toolCallMode}`);
});

function shouldReturnToolCall(body) {
  if (toolCallMode !== "always") {
    return false;
  }
  const tools = Array.isArray(body.tools) ? body.tools : [];
  const messages = Array.isArray(body.messages) ? body.messages : [];
  const alreadyHasToolResult = messages.some((message) => message?.role === "tool");

  return tools.length > 0 && !alreadyHasToolResult;
}

function buildToolCallResponse(body) {
  const tool = body.tools[0];
  const toolName = tool?.function?.name ?? "mock_search";
  const query = lastUserContent(body.messages) || "KAIROS load test";

  return {
    id: `chatcmpl_mock_tool_${Date.now()}`,
    object: "chat.completion",
    created: Math.floor(Date.now() / 1000),
    model: body.model ?? "gpt-4o-mini",
    choices: [
      {
        index: 0,
        message: {
          role: "assistant",
          content: null,
          tool_calls: [
            {
              id: `call_mock_${Date.now()}`,
              type: "function",
              function: {
                name: toolName,
                arguments: JSON.stringify({ query }),
              },
            },
          ],
        },
        finish_reason: "tool_calls",
      },
    ],
    usage: usageFor(body, 8),
  };
}

function buildFinalResponse(body) {
  return {
    id: `chatcmpl_mock_${Date.now()}`,
    object: "chat.completion",
    created: Math.floor(Date.now() / 1000),
    model: body.model ?? "gpt-4o-mini",
    choices: [
      {
        index: 0,
        message: {
          role: "assistant",
          content: "mock provider response",
        },
        finish_reason: "stop",
      },
    ],
    usage: usageFor(body, completionTokens),
  };
}

function usageFor(body, outputTokens) {
  const promptTokens = estimatePromptTokens(body.messages);
  return {
    prompt_tokens: promptTokens,
    completion_tokens: outputTokens,
    total_tokens: promptTokens + outputTokens,
  };
}

function estimatePromptTokens(messages) {
  if (!Array.isArray(messages)) {
    return 1;
  }

  const chars = messages
    .map((message) => {
      const content = message?.content;
      if (typeof content === "string") {
        return content;
      }
      return JSON.stringify(content ?? "");
    })
    .join("\n").length;

  return Math.max(1, Math.ceil(chars / 4));
}

function lastUserContent(messages) {
  if (!Array.isArray(messages)) {
    return "";
  }

  return [...messages]
    .reverse()
    .find((message) => message?.role === "user")
    ?.content ?? "";
}

function readBody(request) {
  return new Promise((resolve, reject) => {
    let body = "";
    request.setEncoding("utf8");
    request.on("data", (chunk) => {
      body += chunk;
    });
    request.on("end", () => resolve(body));
    request.on("error", reject);
  });
}

function sendJson(response, statusCode, body) {
  response.writeHead(statusCode, { "content-type": "application/json" });
  response.end(JSON.stringify(body));
}

function sleep(ms) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}
