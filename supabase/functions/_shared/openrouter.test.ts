// deno test supabase/functions/_shared/openrouter.test.ts
// Fakes OpenRouter to check that a failing free model is skipped and the next one answers.
import { assertEquals } from "jsr:@std/assert@1";
import { freeChat } from "./openrouter.ts";

const models = ["a/one:free", "b/two:free", "c/three:free", "d/four:free"];
const ok = (body: unknown) => new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } });

function fakeOpenRouter(answers: Record<string, () => Response>) {
  const calls: string[] = [];
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    const url = String(input);
    if (url.endsWith("/models")) {
      return ok({ data: models.map((id) => ({ id, context_length: 100_000, pricing: { prompt: "0", completion: "0" }, supported_parameters: [] })) });
    }
    const body = JSON.parse(String(init?.body));
    assertEquals(body.provider.max_price, { prompt: 0, completion: 0 });
    calls.push(body.model);
    return (answers[body.model] ?? (() => new Response("down", { status: 503 })))();
  }) as typeof fetch;
  return calls;
}

const base = {
  keys: ["k"], messages: [{ role: "user" as const, content: "hi" }], preferred: models, needContext: 1000,
  maxTokens: 100, temperature: 0, maxAttempts: 6, totalBudgetMs: 60_000, perModelMs: 10_000, tag: "test",
};

Deno.test("skips rate-limited, errored and empty models, then answers with the next free one", async () => {
  const calls = fakeOpenRouter({
    "a/one:free": () => new Response("limit", { status: 429 }),
    "b/two:free": () => ok({ error: { code: 502, message: "upstream" } }),
    "c/three:free": () => ok({ choices: [{ message: { content: "<think>hmm</think>" } }] }),
    "d/four:free": () => ok({ model: "d/four:free", choices: [{ message: { content: "<think>x</think>Fingerprints are..." } }] }),
  });
  const r = await freeChat(base);
  assertEquals(r.ok && r.text, "Fingerprints are...");
  assertEquals(r.ok && r.model, "d/four:free");
  assertEquals(calls.slice(0, 4), models);
});

Deno.test("models that just failed are tried last on the next request", async () => {
  const calls = fakeOpenRouter({ "d/four:free": () => ok({ choices: [{ message: { content: "answer" } }] }) });
  const r = await freeChat(base);
  assertEquals(r.ok, true);
  assertEquals(calls[0], "d/four:free");
});

Deno.test("a rejected key stops instead of trying every model", async () => {
  const calls = fakeOpenRouter({ "d/four:free": () => new Response("bad key", { status: 401 }) });
  const r = await freeChat(base);
  assertEquals(r.ok ? "ok" : r.problem, "key");
  assertEquals(calls.length, 1);
});
