import http from "k6/http";
import { check, sleep } from "k6";

const baseUrl = __ENV.KAIROS_BASE_URL || "http://localhost:8080";
const apiKey = __ENV.KAIROS_API_KEY;
const model = __ENV.KAIROS_AI_MODEL || "gpt-4o-mini";
const thinkTimeSeconds = Number(__ENV.KAIROS_K6_THINK_TIME_SECONDS || "1");
const requestTimeout = __ENV.KAIROS_K6_TIMEOUT || "60s";

if (!apiKey) {
  throw new Error("KAIROS_API_KEY is required. Use a project API key, not a JWT access token.");
}

export const options = {
  scenarios: {
    chat_completions: {
      executor: "ramping-vus",
      stages: [
        { duration: __ENV.KAIROS_K6_RAMP_UP || "30s", target: Number(__ENV.KAIROS_K6_TARGET_VUS || "50") },
        { duration: __ENV.KAIROS_K6_HOLD || "1m", target: Number(__ENV.KAIROS_K6_TARGET_VUS || "50") },
        { duration: __ENV.KAIROS_K6_RAMP_DOWN || "30s", target: 0 },
      ],
      gracefulRampDown: "10s",
    },
  },
  thresholds: {
    http_req_failed: [`rate<${__ENV.KAIROS_K6_MAX_FAILURE_RATE || "0.01"}`],
    http_req_duration: [`p(95)<${__ENV.KAIROS_K6_P95_MS || "5000"}`],
  },
};

export default function () {
  const payload = JSON.stringify({
    model,
    messages: [
      {
        role: "user",
        content: `KAIROS load test request vu=${__VU} iter=${__ITER}`,
      },
    ],
    temperature: 0.2,
    max_tokens: 128,
    stream: false,
  });

  const response = http.post(`${baseUrl}/api/v1/chat/completions`, payload, {
    headers: {
      "content-type": "application/json",
      authorization: `Bearer ${apiKey}`,
    },
    timeout: requestTimeout,
    tags: {
      endpoint: "chat_completions",
      model,
    },
  });

  check(response, {
    "status is 200": (result) => result.status === 200,
    "has response id": (result) => {
      try {
        return Boolean(result.json("id"));
      } catch {
        return false;
      }
    },
  });

  sleep(thinkTimeSeconds);
}
