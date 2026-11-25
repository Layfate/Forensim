// ai-tutor: policy-gated, retrieval-grounded study assistant (plan section 7.3).
//
//   Student message -> verify user -> assessment lock + daily quota -> retrieve ONLY lessons the
//   user can already read (RLS, using the caller's JWT) -> add permitted performance aggregates
//   -> free OpenRouter model -> log usage metadata -> answer + lesson references.
//
// Answer keys and rubrics are never queried here, so they cannot reach the model.
// AI provider: OpenRouter, FREE models only (see ../_shared/openrouter.ts). If a free model is
// rate-limited, out of allowance, down or too slow, the next available free model answers instead.
// Needs the OPENROUTER_API_KEY secret (the same one ai-quiz uses). Without a key, or when every
// free model fails, the tutor still answers with the best-matching lesson excerpts.
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2.119.2";
import { type ChatMessage, freeChat, openRouterKeys } from "../_shared/openrouter.ts";

/** Free models that write good, grounded tutoring answers, best first. Other live free models follow. */
const PREFERRED = [
  "google/gemma-4-31b-it:free",
  "nvidia/nemotron-3.5-super-vl:free",
  "nvidia/nemotron-3-ultra-550b-a55b:free",
  "baidu/ernie-5.5-flash-preview:free",
  "inclusionai/ling-3.2-flash:free",
  "google/gemma-4-26b-a4b-it:free",
  "nvidia/nemotron-3.5-lightning:free",
];
// The OpenRouter key's free allowance is shared by every user (50 requests a day without credits),
// so one question tries at most 4 models.
const MAX_ATTEMPTS = 4;
// The app's HTTP read timeout is 90 s; stay well inside it across all model attempts.
const TOTAL_BUDGET_MS = 70_000;
const PER_MODEL_MS = 30_000;
const MAX_QUESTION_CHARS = 1500;
const MAX_HISTORY_TURNS = 8;

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });
}

const SYSTEM_PROMPT = `You are the Forensim study tutor for criminology students learning forensic science.

Ground every answer in the lesson excerpts provided in the user turn. Cite the excerpts you use with their bracketed tags, for example [L2]. If the excerpts do not cover the question, say so plainly, give at most a brief general orientation, and suggest which module to study or that the student ask their professor. Do not invent sources, statistics, case law or procedures.

Accuracy comes first. Never contradict the lesson excerpts. Use only well-established forensic science; if you are not sure a fact is correct, say that you are not sure instead of guessing. Use the correct technical terms and define any term the excerpts do not define.

When performance data is provided, explain it accurately and separate measured results (scores, completion) from your own study recommendations.

Forensim is a training simulation. Never present simulated outcomes or polygraph results as valid real-world forensic conclusions. Teach the scientific limitations where relevant.

Do not provide answers to graded quizzes or assessment rubrics. If a message looks like a graded question pasted for an answer, explain the underlying concept instead and encourage the student to reason it out.

Write for a mobile screen: short paragraphs or a few bullet points, under about 220 words unless the student asks for more depth.`;

type LessonRow = {
  id: string;
  title: string;
  sections: { heading: string; body: string }[];
  key_terms: { term: string; definition: string }[];
  modules: { title: string; position: number } | null;
};

type Chunk = { lessonId: string; lessonTitle: string; moduleTitle: string; heading: string; text: string };

const STOP = new Set(("a an and are as at be by can do does for from how i in is it of on or should the this to was what when where which who why with you your my me about").split(" "));

function tokens(s: string): string[] {
  return s.toLowerCase().replace(/[^a-z0-9\s-]/g, " ").split(/\s+/).filter((t) => t.length > 2 && !STOP.has(t));
}

function retrieve(question: string, lessons: LessonRow[], moduleHint: string | null, k = 5): Chunk[] {
  const chunks: Chunk[] = [];
  for (const l of lessons) {
    const moduleTitle = l.modules?.title ?? "";
    for (const s of l.sections ?? []) {
      chunks.push({ lessonId: l.id, lessonTitle: l.title, moduleTitle, heading: s.heading, text: s.body });
    }
    if (l.key_terms?.length) {
      chunks.push({
        lessonId: l.id, lessonTitle: l.title, moduleTitle, heading: "Key terms",
        text: l.key_terms.map((t) => `${t.term}: ${t.definition}`).join(" "),
      });
    }
  }
  const q = tokens(question);
  if (q.length === 0) return [];
  // Simple BM25-style scoring; adequate for a small, curated corpus.
  const docs = chunks.map((c) => tokens(`${c.lessonTitle} ${c.heading} ${c.text}`));
  const avgLen = docs.reduce((s, d) => s + d.length, 0) / Math.max(1, docs.length);
  const df = new Map<string, number>();
  for (const d of docs) for (const t of new Set(d)) df.set(t, (df.get(t) ?? 0) + 1);
  const scored = chunks.map((c, i) => {
    const d = docs[i];
    let score = 0;
    for (const t of new Set(q)) {
      const tf = d.filter((x) => x === t || (t.length > 4 && x.startsWith(t.slice(0, -1)))).length;
      if (!tf) continue;
      const idf = Math.log(1 + (docs.length - (df.get(t) ?? 0) + 0.5) / ((df.get(t) ?? 0) + 0.5));
      score += idf * (tf * 2.2) / (tf + 1.2 * (0.25 + 0.75 * d.length / avgLen));
    }
    if (moduleHint && c.moduleTitle === moduleHint) score *= 1.3;
    return { c, score };
  });
  return scored.filter((s) => s.score > 0).sort((a, b) => b.score - a.score).slice(0, k).map((s) => s.c);
}

const PERFORMANCE_HINT = /\b(score|scores|grade|grades|result|results|progress|weak|weakest|review|next|perform|performance|mistake|mistakes|improve|struggl)/i;

async function performanceSummary(db: SupabaseClient, userId: string): Promise<string | null> {
  const [quiz, sims, done] = await Promise.all([
    db.from("quiz_attempts").select("score, max_score, submitted_at, quizzes(title, modules(title))")
      .eq("student_id", userId).eq("status", "submitted").order("submitted_at", { ascending: false }).limit(20),
    db.from("simulation_attempts").select("score, max_score, mode, feedback, feedback_released, simulation_scenarios(title)")
      .eq("student_id", userId).eq("status", "scored").order("submitted_at", { ascending: false }).limit(10),
    db.from("lesson_progress").select("lesson_id", { count: "exact", head: true }).eq("student_id", userId),
  ]);
  const lines: string[] = [];
  lines.push(`Lessons completed: ${done.count ?? 0}`);
  // deno-lint-ignore no-explicit-any
  for (const a of (quiz.data ?? []) as any[]) {
    lines.push(`Graded quiz "${a.quizzes?.title}" (${a.quizzes?.modules?.title}): ${a.score}/${a.max_score}`);
  }
  // deno-lint-ignore no-explicit-any
  for (const s of (sims.data ?? []) as any[]) {
    let line = `Simulation "${s.simulation_scenarios?.title}" (${s.mode}): ${s.score}/${s.max_score}`;
    if (s.feedback_released && s.feedback) {
      const issues = [
        ...(s.feedback.global_issues ?? []),
        // deno-lint-ignore no-explicit-any
        ...((s.feedback.evidence ?? []) as any[]).flatMap((e) => e.issues ?? []),
      ].slice(0, 6);
      if (issues.length) line += `. Released feedback: ${issues.join(" ")}`;
    }
    lines.push(line);
  }
  return lines.length > 1 ? lines.join("\n") : `${lines[0]}\nNo graded quiz or simulation results yet.`;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  const url = Deno.env.get("SUPABASE_URL")!;
  const userClient = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } },
    auth: { persistSession: false },
  });
  const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });

  const { data: userData, error: userErr } = await userClient.auth.getUser();
  if (userErr || !userData.user) return json({ error: "Not signed in" }, 401);
  const userId = userData.user.id;

  let body: { question?: string; module_title?: string | null; history?: { role: string; content: string }[] };
  try {
    body = await req.json();
  } catch {
    return json({ error: "Invalid JSON" }, 400);
  }
  const question = (body.question ?? "").trim();
  if (!question) return json({ error: "question required" }, 400);
  if (question.length > MAX_QUESTION_CHARS) return json({ error: `Questions are limited to ${MAX_QUESTION_CHARS} characters` }, 400);

  const logUsage = (fields: Record<string, unknown>) =>
    admin.from("ai_usage").insert({ user_id: userId, ...fields }).then(() => undefined);

  // 1) Role + assessment lock (enforced here, not only in the app UI).
  const { data: role } = await userClient.rpc("my_role");
  if (!role) return json({ error: "Account inactive" }, 403);
  const mode = role === "student" ? "study" : "staff";
  if (role === "student") {
    const { data: locked, error: lockErr } = await userClient.rpc("has_active_assessment");
    if (lockErr) return json({ error: "Policy check failed" }, 500);
    if (locked) {
      await logUsage({ mode, blocked: true, reason: "active_assessment" });
      return json({
        blocked: true,
        reason: "active_assessment",
        answer: "The tutor is paused while you have a graded quiz or simulation assessment in progress. Submit it first, then come back to review.",
        references: [],
      });
    }
  }

  // 2) Daily quota.
  const { data: limitRow } = await admin.from("system_settings").select("value").eq("key", "ai_daily_limit").maybeSingle();
  const limit = Number(limitRow?.value ?? 40);
  const since = new Date(Date.now() - 24 * 3600 * 1000).toISOString();
  const { count } = await admin.from("ai_usage").select("id", { count: "exact", head: true })
    .eq("user_id", userId).eq("blocked", false).gte("created_at", since);
  if ((count ?? 0) >= limit) {
    await logUsage({ mode, blocked: true, reason: "daily_limit" });
    return json({ blocked: true, reason: "daily_limit", answer: `You have reached today's limit of ${limit} tutor questions. It resets within 24 hours.`, references: [] });
  }

  // 3) Retrieval from content the caller is already authorised to read (RLS applies).
  const { data: lessons, error: lessonErr } = await userClient
    .from("lessons").select("id, title, sections, key_terms, modules(title, position)").eq("published", true);
  if (lessonErr) return json({ error: "Could not load course content" }, 500);
  const chunks = retrieve(question, (lessons ?? []) as unknown as LessonRow[], body.module_title ?? null);
  const references = [...new Map(chunks.map((c) => [c.lessonId, { lesson_id: c.lessonId, title: c.lessonTitle, module_title: c.moduleTitle }])).values()];

  const perf = role === "student" && PERFORMANCE_HINT.test(question) ? await performanceSummary(userClient, userId) : null;

  // Lesson excerpts alone: used with no key, and when every free model fails.
  const excerptsOnly = (why: string) =>
    chunks.length
      ? `${why} Here are the most relevant parts of your lessons:\n\n` +
        chunks.slice(0, 3).map((c) => `${c.lessonTitle}: ${c.heading}\n${c.text}`).join("\n\n")
      : `${why} No lesson section matched your question either. Try different keywords or browse the modules.`;

  const keys = await openRouterKeys(admin);
  if (!keys.length) {
    await logUsage({ mode, blocked: false, reason: "retrieval_only" });
    return json({ blocked: false, retrieval_only: true, answer: excerptsOnly("The AI model is not set up yet."), references });
  }

  // 4) Model call.
  const excerpts = chunks.length
    ? chunks.map((c, i) => `[L${i + 1}] ${c.moduleTitle} / ${c.lessonTitle} / ${c.heading}\n${c.text}`).join("\n\n")
    : "(No lesson excerpt matched this question.)";
  const userTurn =
    `<lesson_excerpts>\n${excerpts}\n</lesson_excerpts>\n` +
    (perf ? `<performance_data>\n${perf}\n</performance_data>\n` : "") +
    `<question>\n${question}\n</question>`;

  const history: ChatMessage[] = (body.history ?? [])
    .filter((m) => (m.role === "user" || m.role === "assistant") && typeof m.content === "string" && m.content.trim())
    .slice(-MAX_HISTORY_TURNS)
    .map((m) => ({ role: m.role as "user" | "assistant", content: m.content.slice(0, 2000) }));
  while (history.length && history[0].role !== "user") history.shift();
  const messages: ChatMessage[] = [{ role: "system", content: SYSTEM_PROMPT }, ...history, { role: "user", content: userTurn }];
  const promptChars = messages.reduce((n, m) => n + m.content.length, 0);

  const result = await freeChat({
    keys, messages, preferred: PREFERRED,
    needContext: Math.ceil(promptChars / 4) + 2000,
    maxTokens: 1500, temperature: 0.2,
    maxAttempts: MAX_ATTEMPTS, totalBudgetMs: TOTAL_BUDGET_MS, perModelMs: PER_MODEL_MS,
    // A cut-off or near-empty reply is no answer: try the next free model.
    accept: (text) => text.trim().length >= 20,
    tag: "ai-tutor",
  });

  if (!result.ok) {
    if (result.problem === "key") {
      await logUsage({ mode, blocked: false, reason: "model_key_error" });
      return json({ error: "Tutor model credentials are invalid. Contact an administrator." }, 503);
    }
    // Every free model is busy or failed: still help, from the lessons.
    await logUsage({ mode, blocked: false, reason: `retrieval_only_${result.problem}` });
    const why = result.problem === "rate"
      ? "All free AI models are at their limit right now, so the tutor can't write an answer. Try again in a few minutes."
      : "The AI models are unavailable right now, so the tutor can't write an answer. Try again in a minute.";
    return json({ blocked: false, retrieval_only: true, answer: excerptsOnly(why), references });
  }

  await logUsage({ mode, blocked: false, reason: null, tokens_in: result.tokensIn, tokens_out: result.tokensOut });
  return json({ blocked: false, answer: result.text, references: chunks.length ? references : [], model: result.model });
});
