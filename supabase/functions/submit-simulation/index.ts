// submit-simulation: validate a simulation event log and compute the authoritative rubric score.
// The client never sends a score. Assessment feedback detail is withheld until the professor
// releases it (simulation_attempts.feedback_released).
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.119.2";
import { CASE_RUBRIC, type ManifestItem, type Rubric, scoreAttempt, scoreConclusion, validateEvents } from "./scoring.ts";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  const authHeader = req.headers.get("Authorization") ?? "";
  const url = Deno.env.get("SUPABASE_URL")!;
  const userClient = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: authHeader } },
    auth: { persistSession: false },
  });
  const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });

  const { data: userData, error: userErr } = await userClient.auth.getUser();
  if (userErr || !userData.user) return json({ error: "Not signed in" }, 401);
  const userId = userData.user.id;

  let body: { attempt_id?: string; events?: unknown };
  try {
    body = await req.json();
  } catch {
    return json({ error: "Invalid JSON" }, 400);
  }
  if (typeof body.attempt_id !== "string") return json({ error: "attempt_id required" }, 400);

  const { data: attempt, error: attemptErr } = await admin
    .from("simulation_attempts")
    .select("id, student_id, scenario_id, scenario_version, mode, manifest, status, score, max_score, feedback, feedback_released, case_session_id")
    .eq("id", body.attempt_id)
    .maybeSingle();
  if (attemptErr) return json({ error: "Lookup failed" }, 500);
  if (!attempt || attempt.student_id !== userId) return json({ error: "Attempt not found" }, 404);

  // Replay: return the stored result instead of re-scoring.
  if (attempt.status === "scored") return json(present(attempt));

  const manifest = attempt.manifest as ManifestItem[];
  let events;
  try {
    events = validateEvents(body.events, manifest);
  } catch (e) {
    return json({ error: (e as Error).message }, 422);
  }

  // Professor cases use the all-types case rubric plus the conclusion; catalogue scenarios use theirs.
  let rubric: Rubric;
  let caseType: string | null = null;
  if (attempt.case_session_id) {
    const { data: session, error: sessionErr } = await admin.from("case_sessions").select("case_type").eq("id", attempt.case_session_id).maybeSingle();
    if (sessionErr || !session) return json({ error: "Case unavailable" }, 500);
    caseType = session.case_type as string;
    rubric = CASE_RUBRIC;
  } else {
    const { data, error: rubricErr } = await admin.rpc("service_get_simulation_rubric", {
      p_scenario_id: attempt.scenario_id,
      p_version: attempt.scenario_version,
    });
    if (rubricErr || !data) return json({ error: "Rubric unavailable" }, 500);
    rubric = data as Rubric;
  }

  const rows = events.map((e) => ({ attempt_id: attempt.id, ...e }));
  const { error: insertErr } = await admin.from("simulation_events").upsert(rows, { onConflict: "attempt_id,seq", ignoreDuplicates: true });
  if (insertErr) return json({ error: "Could not store events" }, 500);

  const result: ReturnType<typeof scoreAttempt> & { conclusion?: ReturnType<typeof scoreConclusion> } = scoreAttempt(events, manifest, rubric);
  if (caseType) {
    const conclusion = scoreConclusion(events, manifest, caseType);
    result.score += conclusion.earned;
    result.max_score += conclusion.possible;
    result.passed = result.max_score > 0 && result.score / result.max_score >= rubric.pass_ratio;
    result.conclusion = conclusion;
  }
  const released = attempt.mode !== "assessment";

  // Conditional update guards against a concurrent double submit.
  const { data: updated, error: updErr } = await admin
    .from("simulation_attempts")
    .update({
      status: "scored",
      submitted_at: new Date().toISOString(),
      score: result.score,
      max_score: result.max_score,
      feedback: result,
      feedback_released: released,
    })
    .eq("id", attempt.id)
    .eq("status", "in_progress")
    .select("id, mode, status, score, max_score, feedback, feedback_released")
    .maybeSingle();
  if (updErr) return json({ error: "Could not save score" }, 500);
  if (!updated) {
    const { data: current } = await admin.from("simulation_attempts")
      .select("id, mode, status, score, max_score, feedback, feedback_released").eq("id", attempt.id).single();
    return json(present(current));
  }
  return json(present(updated));
});

// deno-lint-ignore no-explicit-any
function present(a: any) {
  return {
    attempt_id: a.id,
    status: a.status,
    score: a.score,
    max_score: a.max_score,
    passed: a.feedback?.passed ?? null,
    feedback_released: a.feedback_released,
    feedback: a.feedback_released ? a.feedback : null,
  };
}
