// OpenRouter, FREE models only, shared by ai-tutor and ai-quiz.
//
// Secrets (Supabase -> Edge Functions -> Secrets):
//   OPENROUTER_API_KEY   the key (extra keys may come from Vault via public.ai_openrouter_keys(), if installed)
//   OPENROUTER_MODELS    optional, comma-separated preferred model ids (non-free ids are ignored)
//
// How a model is chosen: the live list of free models is read from OpenRouter (cached 30 min) and
// ordered: configured models, then the caller's preferred list, then every other live free model
// (largest context first), then OpenRouter's own free router. They are tried one after another.
// A model that is rate-limited, out of free allowance, down, too slow, or whose answer the caller
// rejects is skipped and "rested" for a few minutes, so the next request starts with one that works.
// Every request sets provider.max_price = 0, so a paid route can never be billed.

/** OpenRouter's own free router: last resort (it may pick a very small model). */
export const LAST_RESORT = "openrouter/free";
/** Tiny models can't follow instructions or a JSON format reliably. */
const TOO_SMALL = /(^|[^0-9.])(0\.\d+|1(\.\d+)?|2|3)b\b|nano|tiny|(^|[^a-z])mini\b/i;
/** Free models that don't write chat answers (e.g. Perplexity's Decider models return probabilities). */
const NOT_CHAT = /decider|embed|rerank|guard|moderation/i;
const MODELS_TTL_MS = 30 * 60_000;
const REST_MS = 3 * 60_000;
const API = "https://openrouter.ai/api/v1";

export const isFree = (id: string) => id.endsWith(":free") || id === LAST_RESORT;

export type FreeModel = { id: string; context: number; jsonMode: boolean };
export type ChatMessage = { role: "system" | "user" | "assistant"; content: string };
/** Why the last model failed, for the error message when every model failed. */
export type Problem = "key" | "rate" | "timeout" | "format" | "busy";

let modelCache: { at: number; models: FreeModel[] } | null = null;
/** Models that just failed or hit their limit, skipped until the time stored here (per warm instance). */
const resting = new Map<string, number>();

/** Free models OpenRouter lists right now (cached). Falls back to [fallback] if the list is unavailable. */
async function liveFreeModels(fallback: string[]): Promise<FreeModel[]> {
  if (modelCache && Date.now() - modelCache.at < MODELS_TTL_MS) return modelCache.models;
  const builtIn = fallback.map((id) => ({ id, context: 0, jsonMode: false }));
  try {
    const res = await fetch(`${API}/models`, { signal: AbortSignal.timeout(6_000) });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    // deno-lint-ignore no-explicit-any
    const list = ((await res.json())?.data ?? []) as any[];
    const models = list
      .filter((m) => typeof m?.id === "string" && isFree(m.id) && !NOT_CHAT.test(m.id))
      // Only models that answer in text.
      .filter((m) => !Array.isArray(m?.architecture?.output_modalities) || m.architecture.output_modalities.includes("text"))
      .filter((m) => Number(m?.pricing?.prompt ?? 1) === 0 && Number(m?.pricing?.completion ?? 1) === 0)
      .map((m) => ({
        id: m.id as string,
        context: Number(m.context_length ?? 0),
        jsonMode: Array.isArray(m.supported_parameters) && m.supported_parameters.includes("response_format"),
      }));
    if (models.length) modelCache = { at: Date.now(), models };
    return models.length ? models : builtIn;
  } catch (e) {
    console.warn(`openrouter: model list unavailable (${e}); using the built-in free list`);
    return modelCache?.models ?? builtIn;
  }
}

/** The models to try, best first: available ones first, resting ones only if nothing else is left. */
export async function modelQueue(preferred: string[], needContext: number, maxAttempts: number): Promise<FreeModel[]> {
  const live = await liveFreeModels(preferred);
  const byId = new Map(live.map((m) => [m.id, m]));
  const configured = (Deno.env.get("OPENROUTER_MODELS") ?? "").split(",").map((m) => m.trim()).filter((m) => m && isFree(m));
  const order: FreeModel[] = [];
  const add = (m: FreeModel | undefined) => { if (m && !order.some((o) => o.id === m.id)) order.push(m); };
  // Preferred ids that OpenRouter no longer lists are dropped; configured ones are always tried.
  for (const id of [...configured, ...preferred]) add(byId.get(id) ?? (configured.includes(id) ? { id, context: 0, jsonMode: false } : undefined));
  live.filter((m) => m.id !== LAST_RESORT && !TOO_SMALL.test(m.id)).sort((a, b) => b.context - a.context).forEach(add);
  add(byId.get(LAST_RESORT) ?? { id: LAST_RESORT, context: 0, jsonMode: false });
  const now = Date.now();
  const fits = order.filter((m) => !m.context || m.context >= needContext);
  const ready = fits.filter((m) => (resting.get(m.id) ?? 0) <= now);
  const tired = fits.filter((m) => (resting.get(m.id) ?? 0) > now).sort((a, b) => resting.get(a.id)! - resting.get(b.id)!);
  // Resting models go last (soonest-rested first) rather than being dropped, so a burst of
  // failures never leaves nothing to try.
  return [...ready, ...tired].slice(0, maxAttempts);
}

/** The OPENROUTER_API_KEY secret, then any Vault keys from public.ai_openrouter_keys() (service role only). */
// deno-lint-ignore no-explicit-any
export async function openRouterKeys(admin: any): Promise<string[]> {
  const out: string[] = [];
  const env = Deno.env.get("OPENROUTER_API_KEY")?.trim();
  if (env) out.push(env);
  try {
    const { data } = await admin.rpc("ai_openrouter_keys");
    if (Array.isArray(data)) for (const k of data) if (typeof k === "string" && k.trim() && !out.includes(k.trim())) out.push(k.trim());
  } catch {
    // Vault helper not installed: the env secret alone is used.
  }
  return out;
}

/** Removes reasoning that some free models put in the answer itself. */
function stripThinking(text: string): string {
  return text.replace(/<think>[\s\S]*?<\/think>/gi, "").replace(/^[\s\S]*?<\/think>/i, "").trim();
}

export type ChatOptions = {
  keys: string[];
  messages: ChatMessage[];
  /** Best free models for this job, in order; other live free models follow them. */
  preferred: string[];
  /** Rough tokens the request needs (prompt + answer); models with less context are skipped. */
  needContext: number;
  maxTokens: number;
  temperature: number;
  /** Ask for a JSON object when the model supports it. */
  json?: boolean;
  maxAttempts: number;
  /** Time for all attempts together, and for one model. */
  totalBudgetMs: number;
  perModelMs: number;
  /** Return false to reject an answer (unreadable, too short...) and try the next model. */
  accept?: (text: string) => boolean;
  /** Log prefix, e.g. "ai-tutor". */
  tag: string;
};

export type ChatResult =
  | { ok: true; text: string; model: string; tokensIn: number | null; tokensOut: number | null; tried: number }
  | { ok: false; problem: Problem; tried: number };

/** Asks free models in turn until one gives an answer [ChatOptions.accept] takes. */
export async function freeChat(o: ChatOptions): Promise<ChatResult> {
  const queue = await modelQueue(o.preferred, o.needContext, o.maxAttempts);
  const deadline = Date.now() + o.totalBudgetMs;
  let keyIndex = 0;
  let problem: Problem = "busy";
  let tried = 0;

  for (const { id: model, jsonMode } of queue) {
    const remaining = deadline - Date.now();
    if (remaining < 6_000) break;
    const rest = () => resting.set(model, Date.now() + REST_MS);
    tried++;
    try {
      const res = await fetch(`${API}/chat/completions`, {
        method: "POST",
        headers: { "Authorization": `Bearer ${o.keys[keyIndex]}`, "Content-Type": "application/json", "X-Title": "ForenSim" },
        body: JSON.stringify({
          model, messages: o.messages, temperature: o.temperature, max_tokens: o.maxTokens,
          ...(o.json && jsonMode ? { response_format: { type: "json_object" } } : {}),
          // Hard guarantee: never route to a paid provider.
          provider: { max_price: { prompt: 0, completion: 0 } },
        }),
        signal: AbortSignal.timeout(Math.min(o.perModelMs, remaining)),
      });
      if (res.status === 401 || res.status === 403) {
        problem = "key";
        // A bad key fails the same way on every model: switch key if there is another, else stop.
        if (++keyIndex >= o.keys.length) break;
        continue;
      }
      if (!res.ok) {
        // 402: free allowance used up; 429: rate limited; 404/400/5xx: model gone, busy or unable.
        problem = res.status === 402 || res.status === 429 ? "rate" : "busy";
        const why = (await res.text().catch(() => "")).slice(0, 200);
        console.warn(`${o.tag}: ${model} -> HTTP ${res.status} ${why}; trying the next free model`);
        rest();
        continue;
      }
      const data = await res.json();
      if (data?.error) {
        // OpenRouter sometimes reports an upstream failure inside a 200 response.
        const code = Number(data.error.code);
        problem = code === 402 || code === 429 ? "rate" : "busy";
        console.warn(`${o.tag}: ${model} -> error ${data.error.code ?? ""} in reply; trying the next free model`);
        rest();
        continue;
      }
      const text = stripThinking(String(data?.choices?.[0]?.message?.content ?? ""));
      if (!text || (o.accept && !o.accept(text))) {
        problem = "format";
        console.warn(`${o.tag}: ${model} gave an empty or unusable answer; trying the next free model`);
        rest();
        continue;
      }
      return {
        ok: true, text, model: data?.model ?? model, tried,
        tokensIn: data?.usage?.prompt_tokens ?? null, tokensOut: data?.usage?.completion_tokens ?? null,
      };
    } catch (e) {
      problem = e instanceof DOMException && e.name === "TimeoutError" ? "timeout" : "busy";
      console.warn(`${o.tag}: ${model} failed: ${e}; trying the next free model`);
      rest();
    }
  }
  return { ok: false, problem, tried };
}
