// Deterministic rubric evaluation for simulation attempts (plan section 5.3 ProcedureEvaluator,
// section 7.2: the server computes the score, never the client).

export type SimEvent = {
  seq: number;
  action: string;
  evidence_id?: string | null;
  tool?: string | null;
  payload?: Record<string, unknown>;
  client_ts?: string | null;
};

export type ManifestItem = { id: string; type: string };

export type Rubric = {
  global: { sign_log_before_evidence: number; submit_report: number };
  evidence: Record<string, { collect_tool: string; package_tools: string[] }>;
  step_points: Record<string, number>;
  order: string[];
  penalties: { no_gloves: number; wrong_tool: number; wrong_package: number; out_of_order: number };
  pass_ratio: number;
  /** Item types that are red herrings: listed in feedback, but never scored. */
  red_herrings?: string[];
};

export type EvidenceFeedback = {
  evidence_id: string;
  type: string;
  earned: number;
  possible: number;
  steps_completed: string[];
  issues: string[];
  contaminated: boolean;
  red_herring?: boolean;
};

export type ScoreResult = {
  score: number;
  max_score: number;
  passed: boolean;
  global_issues: string[];
  evidence: EvidenceFeedback[];
};

export const KNOWN_ACTIONS = new Set([
  "sign_log", "don_gloves", "remove_gloves", "discover", "inspect",
  "marker", "photograph", "photograph_scale", "collect", "package", "submit_report", "conclude",
]);

const EVIDENCE_STEPS = new Set(["marker", "photograph", "photograph_scale", "collect", "package"]);

const LABELS: Record<string, string> = {
  marker: "evidence marker", photograph: "photograph", photograph_scale: "scaled photograph",
  collect: "collection", package: "packaging",
};

export function scoreAttempt(events: SimEvent[], manifest: ManifestItem[], rubric: Rubric): ScoreResult {
  const sorted = [...events].sort((a, b) => a.seq - b.seq);
  const globalIssues: string[] = [];
  let earned = 0;
  let max = rubric.global.sign_log_before_evidence + rubric.global.submit_report;

  // Scene log signed before touching any evidence.
  const firstLog = sorted.find((e) => e.action === "sign_log");
  const firstEvidenceAction = sorted.find((e) => EVIDENCE_STEPS.has(e.action));
  if (firstLog && (!firstEvidenceAction || firstLog.seq < firstEvidenceAction.seq)) {
    earned += rubric.global.sign_log_before_evidence;
  } else if (firstLog) {
    globalIssues.push("The scene entry log was signed after evidence had already been handled.");
  } else {
    globalIssues.push("The scene entry log was never signed.");
  }
  if (sorted.some((e) => e.action === "submit_report")) {
    earned += rubric.global.submit_report;
  } else {
    globalIssues.push("No case report was submitted.");
  }

  // Replay glove state alongside events.
  const glovesAt = new Map<number, boolean>();
  let gloves = false;
  for (const e of sorted) {
    if (e.action === "don_gloves") gloves = true;
    if (e.action === "remove_gloves") gloves = false;
    glovesAt.set(e.seq, gloves);
  }

  const perStepTotal = rubric.order.reduce((sum, step) => sum + (rubric.step_points[step] ?? 0), 0);
  const evidenceFeedback: EvidenceFeedback[] = [];
  const herrings = new Set(rubric.red_herrings ?? []);

  for (const item of manifest) {
    // Red herrings: processing one (or not) earns and costs nothing. Only citing it as proof in
    // the conclusion is penalised (scoreConclusion).
    if (herrings.has(item.type)) {
      const done = rubric.order.filter((step) => sorted.some((e) => e.evidence_id === item.id && e.action === step));
      evidenceFeedback.push({
        evidence_id: item.id, type: item.type, earned: 0, possible: 0, steps_completed: done, issues: [], contaminated: false, red_herring: true,
      });
      continue;
    }
    max += perStepTotal;
    const rule = rubric.evidence[item.type];
    const mine = sorted.filter((e) => e.evidence_id === item.id && EVIDENCE_STEPS.has(e.action));
    const first = new Map<string, SimEvent>();
    for (const e of mine) if (!first.has(e.action)) first.set(e.action, e);

    const issues: string[] = [];
    let itemEarned = 0;
    let contaminated = false;
    const completed: string[] = [];

    for (const step of rubric.order) {
      const ev = first.get(step);
      if (!ev) continue;
      let ok = true;
      if (step === "collect") {
        if (!glovesAt.get(ev.seq)) {
          contaminated = true;
          itemEarned -= rubric.penalties.no_gloves;
          issues.push("Collected without gloves: the item may be contaminated.");
        }
        if (rule && (ev.tool ?? "hand") !== rule.collect_tool) {
          ok = false;
          itemEarned -= rubric.penalties.wrong_tool;
          issues.push(`Collected with an unsuitable technique (${ev.tool ?? "hand"}).`);
        }
      }
      if (step === "package") {
        if (!first.has("collect")) {
          ok = false;
          issues.push("Packaging was recorded before the item was collected.");
        } else if (rule && !rule.package_tools.includes(ev.tool ?? "")) {
          ok = false;
          itemEarned -= rubric.penalties.wrong_package;
          issues.push(`Packaged in an unsuitable container (${ev.tool ?? "none"}).`);
        }
      }
      if (ok) {
        itemEarned += rubric.step_points[step] ?? 0;
        completed.push(step);
      }
    }

    // Order violations: any done step that happened before an earlier-ordered done step.
    for (let i = 0; i < rubric.order.length; i++) {
      for (let j = i + 1; j < rubric.order.length; j++) {
        const a = first.get(rubric.order[i]);
        const b = first.get(rubric.order[j]);
        if (a && b && b.seq < a.seq) {
          itemEarned -= rubric.penalties.out_of_order;
          issues.push(`${cap(LABELS[rubric.order[j]])} happened before ${LABELS[rubric.order[i]]}.`);
        }
      }
    }

    for (const step of rubric.order) {
      if (!first.has(step)) issues.push(`Missing ${LABELS[step]}.`);
    }

    earned += itemEarned;
    evidenceFeedback.push({
      evidence_id: item.id,
      type: item.type,
      earned: Math.max(0, itemEarned),
      possible: perStepTotal,
      steps_completed: completed,
      issues,
      contaminated,
    });
  }

  const score = Math.max(0, earned);
  return {
    score,
    max_score: max,
    passed: max > 0 && score / max >= rubric.pass_ratio,
    global_issues: globalIssues,
    evidence: evidenceFeedback,
  };
}

export function validateEvents(events: unknown, manifest: ManifestItem[]): SimEvent[] {
  if (!Array.isArray(events)) throw new Error("events must be an array");
  if (events.length === 0 || events.length > 2000) throw new Error("events must contain 1-2000 items");
  const ids = new Set(manifest.map((m) => m.id));
  let lastSeq = 0;
  return events.map((raw) => {
    const e = raw as SimEvent;
    if (!Number.isInteger(e.seq) || e.seq <= lastSeq) throw new Error("event seq must be strictly increasing integers");
    lastSeq = e.seq;
    if (typeof e.action !== "string" || !KNOWN_ACTIONS.has(e.action)) throw new Error(`unknown action ${e.action}`);
    if (e.evidence_id != null && !ids.has(e.evidence_id)) throw new Error(`unknown evidence ${e.evidence_id}`);
    if (EVIDENCE_STEPS.has(e.action) && !e.evidence_id) throw new Error(`${e.action} requires evidence_id`);
    if (e.tool != null && (typeof e.tool !== "string" || e.tool.length > 40)) throw new Error("invalid tool");
    return {
      seq: e.seq,
      action: e.action,
      evidence_id: e.evidence_id ?? null,
      tool: e.tool ?? null,
      payload: typeof e.payload === "object" && e.payload !== null ? e.payload : {},
      client_ts: typeof e.client_ts === "string" ? e.client_ts : null,
    };
  });
}

// ---------------------------------------------------------------------------------------------
// Professor-generated cases (case_sessions): one rubric covers every evidence type, and the
// student's conclusion ("what happened, and which evidence shows it") is scored on top.

export const CASE_RUBRIC: Rubric = {
  global: { sign_log_before_evidence: 2, submit_report: 1 },
  evidence: {
    cartridge_case: { collect_tool: "hand", package_tools: ["paper_bag", "plastic_bag", "evidence_box"] },
    latent_print: { collect_tool: "print_kit", package_tools: ["paper_bag"] },
    bloodstain: { collect_tool: "swab_kit", package_tools: ["paper_bag"] },
    questioned_document: { collect_tool: "hand", package_tools: ["paper_bag", "plastic_bag"] },
    hair_fiber: { collect_tool: "forceps", package_tools: ["paper_bag"] },
    shoe_print: { collect_tool: "casting_kit", package_tools: ["paper_bag", "evidence_box"] },
    tool_mark: { collect_tool: "casting_kit", package_tools: ["evidence_box"] },
    broken_glass: { collect_tool: "forceps", package_tools: ["evidence_box"] },
    knife: { collect_tool: "hand", package_tools: ["evidence_box"] },
    firearm: { collect_tool: "hand", package_tools: ["evidence_box"] },
    bullet: { collect_tool: "forceps", package_tools: ["evidence_box", "paper_bag"] },
    blunt_weapon: { collect_tool: "hand", package_tools: ["paper_bag"] },
    drug_packet: { collect_tool: "hand", package_tools: ["plastic_bag"] },
    digital_scale: { collect_tool: "hand", package_tools: ["plastic_bag", "evidence_box"] },
    // Added 2026-11 (mirrors EvidenceType in the app).
    cigarette_butt: { collect_tool: "forceps", package_tools: ["paper_bag"] },
    drinking_glass: { collect_tool: "hand", package_tools: ["evidence_box"] },
    cell_phone: { collect_tool: "hand", package_tools: ["evidence_box", "plastic_bag"] },
    wallet: { collect_tool: "hand", package_tools: ["paper_bag"] },
    syringe: { collect_tool: "hand", package_tools: ["evidence_box"] },
    pill_bottle: { collect_tool: "hand", package_tools: ["plastic_bag"] },
    crowbar: { collect_tool: "hand", package_tools: ["paper_bag", "evidence_box"] },
    screwdriver: { collect_tool: "hand", package_tools: ["evidence_box"] },
    glove: { collect_tool: "hand", package_tools: ["paper_bag"] },
    ski_mask: { collect_tool: "hand", package_tools: ["paper_bag"] },
    rope: { collect_tool: "hand", package_tools: ["paper_bag"] },
    magazine: { collect_tool: "hand", package_tools: ["evidence_box"] },
    bloody_cloth: { collect_tool: "hand", package_tools: ["paper_bag"] },
    shoe: { collect_tool: "hand", package_tools: ["paper_bag", "evidence_box"] },
    jewelry_box: { collect_tool: "hand", package_tools: ["evidence_box"] },
    torn_fabric: { collect_tool: "forceps", package_tools: ["paper_bag"] },
    cash_bundle: { collect_tool: "hand", package_tools: ["paper_bag"] },
    spray_can: { collect_tool: "hand", package_tools: ["plastic_bag"] },
    hammer: { collect_tool: "hand", package_tools: ["paper_bag"] },
    brick: { collect_tool: "hand", package_tools: ["paper_bag", "evidence_box"] },
    // Second set, 2026-11 (mirrors EvidenceType in the app).
    duct_tape: { collect_tool: "hand", package_tools: ["paper_bag"] },
    zip_ties: { collect_tool: "hand", package_tools: ["paper_bag"] },
    bolt_cutters: { collect_tool: "hand", package_tools: ["paper_bag", "evidence_box"] },
    flashlight: { collect_tool: "hand", package_tools: ["evidence_box", "paper_bag"] },
    handbag: { collect_tool: "hand", package_tools: ["paper_bag"] },
    broken_bottle: { collect_tool: "hand", package_tools: ["evidence_box"] },
    brass_knuckles: { collect_tool: "hand", package_tools: ["paper_bag", "evidence_box"] },
    shotgun_shell: { collect_tool: "hand", package_tools: ["paper_bag", "evidence_box"] },
    ammo_box: { collect_tool: "hand", package_tools: ["evidence_box"] },
    burnt_spoon: { collect_tool: "hand", package_tools: ["evidence_box"] },
    glass_pipe: { collect_tool: "hand", package_tools: ["evidence_box"] },
    paint_marker: { collect_tool: "hand", package_tools: ["plastic_bag"] },
  },
  step_points: { marker: 1, photograph: 1, photograph_scale: 1, collect: 2, package: 2 },
  order: ["marker", "photograph", "photograph_scale", "collect", "package"],
  penalties: { no_gloves: 3, wrong_tool: 2, wrong_package: 2, out_of_order: 1 },
  pass_ratio: 0.7,
};

/** Everyday objects placed to mislead (mirrors EvidenceType.redHerring and private.red_herring_types()). */
export const RED_HERRINGS: string[] = [
  "coffee_mug", "tv_remote", "house_keys", "eyeglasses", "newspaper", "soda_can", "water_bottle", "receipt", "pen", "book",
  "umbrella", "toy_car", "hairbrush", "lipstick", "coins", "headphones", "tissue_box", "candle", "sock", "cap",
  "matchbox", "phone_charger", "playing_cards", "food_wrapper", "apple", "ashtray", "paper_cup", "scissors", "notebook", "watch",
  "stapler", "calculator", "lighter", "hand_sanitizer", "game_controller", "sticky_notes", "tape_measure", "flip_flop",
];
CASE_RUBRIC.red_herrings = RED_HERRINGS;

/** Points lost for each red herring cited as proof of what happened. */
const RED_HERRING_PENALTY = 1;

export const CASE_TYPES: Record<string, string> = {
  theft: "Theft", burglary: "Burglary", assault: "Assault", homicide: "Homicide",
  shooting: "Shooting", drug_possession: "Drug possession", robbery: "Robbery", vandalism: "Vandalism",
};

/** Evidence that actually points to each case type (the answer key for "which evidence shows it"). */
const INDICATIVE: Record<string, string[]> = {
  theft: ["latent_print", "questioned_document", "wallet", "jewelry_box", "handbag"],
  burglary: ["tool_mark", "broken_glass", "shoe_print", "crowbar", "screwdriver", "torn_fabric", "bolt_cutters"],
  assault: ["bloodstain", "blunt_weapon", "hair_fiber", "hammer", "bloody_cloth", "broken_bottle", "brass_knuckles"],
  homicide: ["bloodstain", "knife", "rope", "bloody_cloth", "duct_tape", "zip_ties"],
  shooting: ["cartridge_case", "bullet", "firearm", "magazine", "shotgun_shell", "ammo_box"],
  drug_possession: ["drug_packet", "digital_scale", "questioned_document", "syringe", "pill_bottle", "cash_bundle", "burnt_spoon", "glass_pipe"],
  robbery: ["firearm", "questioned_document", "ski_mask", "zip_ties"],
  vandalism: ["broken_glass", "blunt_weapon", "tool_mark", "spray_can", "brick", "paint_marker"],
};

const TYPE_POINTS = 4;

export type ConclusionResult = {
  chosen_type: string | null;
  correct_type: string;
  type_correct: boolean;
  supporting_correct: number;
  supporting_wrong: number;
  supporting_possible: number;
  red_herrings_cited: number;
  earned: number;
  possible: number;
  notes: string[];
};

/** Scores the last "conclude" event against the case's real type. */
export function scoreConclusion(events: SimEvent[], manifest: ManifestItem[], caseType: string): ConclusionResult {
  const last = [...events].sort((a, b) => a.seq - b.seq).filter((e) => e.action === "conclude").pop();
  const chosen = typeof last?.payload?.case_type === "string" ? String(last.payload.case_type) : null;
  const picked = Array.isArray(last?.payload?.evidence) ? (last!.payload!.evidence as unknown[]).map(String) : [];
  const indicative = new Set(INDICATIVE[caseType] ?? []);
  const supportive = manifest.filter((m) => indicative.has(m.type));
  const typeOf = new Map(manifest.map((m) => [m.id, m.type]));
  const herrings = new Set(RED_HERRINGS);
  let right = 0;
  let wrong = 0;
  let herringsCited = 0;
  for (const id of new Set(picked)) {
    const t = typeOf.get(id);
    if (!t) continue;
    if (indicative.has(t)) right++;
    else if (herrings.has(t)) herringsCited++;
    else wrong++;
  }
  const typeCorrect = chosen === caseType;
  const supportEarned = Math.max(0, right - wrong);
  const notes: string[] = [];
  if (!chosen) notes.push("No conclusion was given about what happened.");
  else if (!typeCorrect) {
    notes.push(`The evidence points to ${(CASE_TYPES[caseType] ?? caseType).toLowerCase()}, not ${(CASE_TYPES[chosen] ?? chosen).toLowerCase()}.`);
  }
  if (right < supportive.length) notes.push(`${supportive.length - right} key item(s) that show what happened were not cited.`);
  if (wrong > 0) notes.push(`${wrong} cited item(s) don't support the conclusion on their own.`);
  if (herringsCited > 0) notes.push(`${herringsCited} cited item(s) were red herrings with no connection to the case.`);
  return {
    chosen_type: chosen,
    correct_type: caseType,
    type_correct: typeCorrect,
    supporting_correct: right,
    supporting_wrong: wrong,
    supporting_possible: supportive.length,
    red_herrings_cited: herringsCited,
    earned: Math.max(0, (typeCorrect ? TYPE_POINTS : 0) + supportEarned - herringsCited * RED_HERRING_PENALTY),
    possible: TYPE_POINTS + supportive.length,
    notes,
  };
}

function cap(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}
