// Runs under Node 22.6+ (`node --test supabase/functions/submit-simulation/scoring.test.ts`)
// and Deno (`deno test`).
import { test } from "node:test";
import assert from "node:assert/strict";
import { type Rubric, type SimEvent, scoreAttempt, validateEvents } from "./scoring.ts";

const rubric: Rubric = {
  global: { sign_log_before_evidence: 2, submit_report: 1 },
  evidence: {
    cartridge_case: { collect_tool: "hand", package_tools: ["paper_bag", "plastic_bag"] },
    bloodstain: { collect_tool: "swab_kit", package_tools: ["paper_bag"] },
  },
  step_points: { marker: 1, photograph: 1, photograph_scale: 1, collect: 2, package: 2 },
  order: ["marker", "photograph", "photograph_scale", "collect", "package"],
  penalties: { no_gloves: 3, wrong_tool: 2, wrong_package: 2, out_of_order: 1 },
  pass_ratio: 0.7,
};
const manifest = [{ id: "ev_1", type: "cartridge_case" }, { id: "ev_2", type: "bloodstain" }];

function seq(actions: Array<[string, string?, string?]>): SimEvent[] {
  return actions.map(([action, evidence_id, tool], i) => ({ seq: i + 1, action, evidence_id, tool }));
}

const perfect = seq([
  ["sign_log"], ["don_gloves"],
  ["marker", "ev_1"], ["photograph", "ev_1"], ["photograph_scale", "ev_1"], ["collect", "ev_1", "hand"], ["package", "ev_1", "paper_bag"],
  ["marker", "ev_2"], ["photograph", "ev_2"], ["photograph_scale", "ev_2"], ["collect", "ev_2", "swab_kit"], ["package", "ev_2", "paper_bag"],
  ["submit_report"],
]);

test("perfect run earns full marks", () => {
  const r = scoreAttempt(perfect, manifest, rubric);
  assert.equal(r.max_score, 3 + 7 * 2);
  assert.equal(r.score, r.max_score);
  assert.equal(r.passed, true);
  assert.deepEqual(r.global_issues, []);
  assert.ok(r.evidence.every((e) => e.issues.length === 0 && !e.contaminated));
});

test("collecting without gloves is penalised and flagged as contamination", () => {
  const events = perfect.filter((e) => e.action !== "don_gloves").map((e, i) => ({ ...e, seq: i + 1 }));
  const r = scoreAttempt(events, manifest, rubric);
  assert.equal(r.score, r.max_score - 3 * 2);
  assert.ok(r.evidence.every((e) => e.contaminated));
});

test("bloodstain in plastic is wrong packaging", () => {
  const events = perfect.map((e) => (e.action === "package" && e.evidence_id === "ev_2" ? { ...e, tool: "plastic_bag" } : e));
  const r = scoreAttempt(events, manifest, rubric);
  const blood = r.evidence.find((e) => e.evidence_id === "ev_2")!;
  assert.equal(blood.earned, 7 - 2 - 2);
  assert.ok(blood.issues.some((i) => i.includes("unsuitable container")));
});

test("collecting before photographing is out of order", () => {
  const events = seq([
    ["sign_log"], ["don_gloves"],
    ["marker", "ev_1"], ["collect", "ev_1", "hand"], ["photograph", "ev_1"], ["photograph_scale", "ev_1"], ["package", "ev_1", "paper_bag"],
    ["submit_report"],
  ]);
  const r = scoreAttempt(events, [manifest[0]], rubric);
  const item = r.evidence[0];
  assert.equal(item.earned, 7 - 2); // two order violations: collect before photograph and before scale photo
});

test("signing the log late loses the security points", () => {
  const events = seq([["don_gloves"], ["marker", "ev_1"], ["sign_log"], ["submit_report"]]);
  const r = scoreAttempt(events, [manifest[0]], rubric);
  assert.ok(r.global_issues[0].includes("after evidence"));
});

test("score never goes negative", () => {
  const events = seq([["collect", "ev_1", "forceps"], ["package", "ev_1", "envelope"]]);
  const r = scoreAttempt(events, [manifest[0]], rubric);
  assert.equal(r.score, 0);
});

test("validation rejects unknown evidence, bad ordering and unknown actions", () => {
  assert.throws(() => validateEvents([{ seq: 1, action: "marker", evidence_id: "ev_9" }], manifest));
  assert.throws(() => validateEvents([{ seq: 2, action: "sign_log" }, { seq: 1, action: "sign_log" }], manifest));
  assert.throws(() => validateEvents([{ seq: 1, action: "teleport" }], manifest));
  assert.throws(() => validateEvents([{ seq: 1, action: "collect" }], manifest));
  assert.equal(validateEvents(perfect, manifest).length, perfect.length);
});

// ---------------------------------------------------------------- professor cases: conclusion
import { CASE_RUBRIC, RED_HERRINGS, scoreConclusion } from "./scoring.ts";

const caseManifest = [
  { id: "ev_1", type: "tool_mark" },
  { id: "ev_2", type: "broken_glass" },
  { id: "ev_3", type: "latent_print" },
];
const conclude = (caseType: string, evidence: string[]): SimEvent =>
  ({ seq: 99, action: "conclude", payload: { case_type: caseType, evidence } });

test("conclusion: right type and only the key evidence earns full marks", () => {
  const r = scoreConclusion([conclude("burglary", ["ev_1", "ev_2"])], caseManifest, "burglary");
  assert.equal(r.type_correct, true);
  assert.equal(r.supporting_possible, 2);
  assert.equal(r.earned, r.possible);
  assert.deepEqual(r.notes, []);
});

test("conclusion: wrong type scores only supporting evidence; wrong picks cancel right ones", () => {
  const r = scoreConclusion([conclude("theft", ["ev_1", "ev_3"])], caseManifest, "burglary");
  assert.equal(r.type_correct, false);
  assert.equal(r.supporting_correct, 1);
  assert.equal(r.supporting_wrong, 1);
  assert.equal(r.earned, 0);
});

test("conclusion: missing conclude event earns nothing and says so", () => {
  const r = scoreConclusion([], caseManifest, "burglary");
  assert.equal(r.chosen_type, null);
  assert.equal(r.earned, 0);
  assert.ok(r.notes[0].includes("No conclusion"));
});

test("conclude events pass validation and case rubric covers new evidence types", () => {
  const ev = validateEvents([{ seq: 1, action: "conclude", payload: { case_type: "burglary", evidence: ["ev_1"] } }], caseManifest);
  assert.equal(ev[0].action, "conclude");
  for (const t of ["shoe_print", "tool_mark", "broken_glass", "knife", "firearm", "bullet", "blunt_weapon", "drug_packet", "digital_scale"]) {
    assert.ok(CASE_RUBRIC.evidence[t], t);
  }
});

test("conclusion: new case types (robbery, vandalism) have an answer key, and repeated items each count", () => {
  const robbery = [
    { id: "ev_1", type: "firearm" }, { id: "ev_2", type: "questioned_document" },
    { id: "ev_3", type: "shoe_print" }, { id: "ev_4", type: "shoe_print" },
  ];
  const r = scoreConclusion([conclude("robbery", ["ev_1", "ev_2"])], robbery, "robbery");
  assert.equal(r.type_correct, true);
  assert.equal(r.supporting_possible, 2);
  assert.equal(r.earned, r.possible);

  const vandalism = [
    { id: "ev_1", type: "broken_glass" }, { id: "ev_2", type: "blunt_weapon" },
    { id: "ev_3", type: "tool_mark" }, { id: "ev_4", type: "tool_mark" }, { id: "ev_5", type: "latent_print" },
  ];
  const v = scoreConclusion([conclude("vandalism", ["ev_1", "ev_2", "ev_3", "ev_4"])], vandalism, "vandalism");
  assert.equal(v.supporting_possible, 4);
  assert.equal(v.earned, v.possible);
  assert.deepEqual(v.notes, []);
});

test("red herrings are never scored for procedure, whether processed or ignored", () => {
  const m = [{ id: "ev_1", type: "cartridge_case" }, { id: "ev_2", type: "coffee_mug" }];
  const base = seq([
    ["sign_log"], ["don_gloves"],
    ["marker", "ev_1"], ["photograph", "ev_1"], ["photograph_scale", "ev_1"], ["collect", "ev_1", "hand"], ["package", "ev_1", "paper_bag"],
    ["submit_report"],
  ]);
  const ignored = scoreAttempt(base, m, CASE_RUBRIC);
  const handled = scoreAttempt([...base.slice(0, -1), ...seq([["marker", "ev_2"], ["collect", "ev_2", "forceps"], ["package", "ev_2", "plastic_bag"], ["submit_report"]])
    .map((e, i) => ({ ...e, seq: 100 + i }))], m, CASE_RUBRIC);
  for (const r of [ignored, handled]) {
    assert.equal(r.max_score, 3 + 7);
    assert.equal(r.score, r.max_score);
    const h = r.evidence.find((e) => e.evidence_id === "ev_2")!;
    assert.equal(h.red_herring, true);
    assert.equal(h.possible, 0);
    assert.deepEqual(h.issues, []);
  }
});

test("conclusion: citing a red herring as proof costs a point and is explained", () => {
  const m = [{ id: "ev_1", type: "tool_mark" }, { id: "ev_2", type: "broken_glass" }, { id: "ev_3", type: "receipt" }];
  const clean = scoreConclusion([conclude("burglary", ["ev_1", "ev_2"])], m, "burglary");
  const misled = scoreConclusion([conclude("burglary", ["ev_1", "ev_2", "ev_3"])], m, "burglary");
  assert.equal(clean.earned, clean.possible);
  assert.equal(misled.red_herrings_cited, 1);
  assert.equal(misled.supporting_wrong, 0);
  assert.equal(misled.earned, clean.earned - 1);
  assert.ok(misled.notes.some((n) => n.includes("red herring")));
});

test("case rubric and answer key cover every evidence type added in 2026-11", () => {
  const added = ["cigarette_butt", "drinking_glass", "cell_phone", "wallet", "syringe", "pill_bottle", "crowbar", "screwdriver", "glove", "ski_mask",
    "rope", "magazine", "bloody_cloth", "shoe", "jewelry_box", "torn_fabric", "cash_bundle", "spray_can", "hammer", "brick"];
  for (const t of added) assert.ok(CASE_RUBRIC.evidence[t], t);
  assert.equal(RED_HERRINGS.length, 38);
  for (const t of RED_HERRINGS) assert.ok(!CASE_RUBRIC.evidence[t], `${t} must not be scored as evidence`);
  const r = scoreConclusion([conclude("robbery", ["ev_1"])], [{ id: "ev_1", type: "ski_mask" }], "robbery");
  assert.equal(r.supporting_correct, 1);
});

test("case rubric and answer key cover the second set of evidence added in 2026-11", () => {
  const added = ["duct_tape", "zip_ties", "bolt_cutters", "flashlight", "handbag", "broken_bottle", "brass_knuckles",
    "shotgun_shell", "ammo_box", "burnt_spoon", "glass_pipe", "paint_marker"];
  for (const t of added) assert.ok(CASE_RUBRIC.evidence[t], t);
  for (const t of ["stapler", "calculator", "lighter", "hand_sanitizer", "game_controller", "sticky_notes", "tape_measure", "flip_flop"]) {
    assert.ok(RED_HERRINGS.includes(t), t);
    assert.ok(!CASE_RUBRIC.evidence[t], `${t} must not be scored as evidence`);
  }
  const r = scoreConclusion([conclude("shooting", ["ev_1", "ev_2"])], [{ id: "ev_1", type: "shotgun_shell" }, { id: "ev_2", type: "ammo_box" }], "shooting");
  assert.equal(r.supporting_correct, 2);
  assert.equal(r.earned, r.possible);
});
