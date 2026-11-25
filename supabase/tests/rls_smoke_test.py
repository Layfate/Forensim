"""
Forensim RLS / trusted-RPC smoke test (plan section 13: RLS/security tests).

Runs against a live Supabase project using ONLY the public publishable key and real user
sessions, exactly as the Android app would. Verifies role and classroom boundaries, answer-key
isolation and server-side grading.

Usage:
  python -I supabase/tests/rls_smoke_test.py
Credentials are read from supabase/seed/DEV_ACCOUNTS.local.md (git-ignored) or the env vars
FORENSIM_STUDENT_PW / FORENSIM_PROFESSOR_PW / FORENSIM_ADMIN_PW.

NOTE: it creates a real graded-quiz attempt for the student account (one of their attempts).
"""
import json
import os
import pathlib
import re
import sys
import urllib.error
import urllib.request

URL = os.environ.get("SUPABASE_URL", "https://dadmvplgluqewozshtdz.supabase.co")
KEY = os.environ.get("SUPABASE_PUBLISHABLE_KEY", "sb_publishable_r4vspIgXrAS_Px68yIzswg_pf4VTorH")

ROOT = pathlib.Path(__file__).resolve().parents[2]
failures = []


def passwords():
    pw = {r: os.environ.get(f"FORENSIM_{r.upper()}_PW") for r in ("student", "professor", "admin")}
    local = ROOT / "supabase" / "seed" / "DEV_ACCOUNTS.local.md"
    if local.exists():
        for role, value in re.findall(r"\| (\w+) \| \S+ \| `([^`]+)` \|", local.read_text()):
            pw.setdefault(role, value)
            pw[role] = pw[role] or value
    return pw


def req(method, path, body=None, token=None):
    headers = {"apikey": KEY, "Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(URL + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            raw = resp.read()
            return resp.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode(errors="replace")[:300]


def check(name, condition, detail=""):
    print(("PASS " if condition else "FAIL ") + name + (f"  [{detail}]" if detail and not condition else ""))
    if not condition:
        failures.append(name)


def login(role, pw):
    status, body = req("POST", "/auth/v1/token?grant_type=password", {"email": f"{role}@forensim.test", "password": pw})
    check(f"{role} can sign in", status == 200, str(body))
    return body["access_token"] if status == 200 else None


def main():
    pw = passwords()
    student = login("student", pw["student"])
    professor = login("professor", pw["professor"])
    admin = login("admin", pw["admin"])
    if not (student and professor and admin):
        sys.exit(1)

    # --- anon has no access
    s, b = req("GET", "/rest/v1/modules?select=id")
    check("anon cannot read modules", s in (401, 403) or b == [], f"{s} {b}")

    # --- roles come from the server
    check("student role", req("POST", "/rest/v1/rpc/my_role", {}, student)[1] == "student")
    check("professor role", req("POST", "/rest/v1/rpc/my_role", {}, professor)[1] == "professor")
    check("admin role", req("POST", "/rest/v1/rpc/my_role", {}, admin)[1] == "admin")

    # --- content visibility
    s, mods = req("GET", "/rest/v1/modules?select=id,title&published=eq.true", None, student)
    check("enrolled student sees 7 published modules", s == 200 and len(mods) == 7, f"{s} {mods}")
    s, quizzes = req("GET", "/rest/v1/quizzes?select=id,title,kind", None, student)
    check("student sees quizzes", s == 200 and len(quizzes) >= 9, f"{s}")
    graded = next(q for q in quizzes if q["kind"] == "graded")
    practice = next(q for q in quizzes if q["kind"] == "practice")

    # --- answer keys are never reachable
    s, b = req("GET", "/rest/v1/answer_keys", None, student)
    check("answer_keys not exposed via API", s in (401, 403, 404), f"{s} {b}")
    s, b = req("GET", "/rest/v1/simulation_rubrics", None, admin)
    check("rubrics not exposed via API (even to admin)", s in (401, 403, 404), f"{s} {b}")
    s, b = req("GET", "/rest/v1/questions?select=*,answer_keys(*)", None, student)
    check("cannot embed answer_keys", s >= 400, f"{s}")

    # --- graded questions hidden until an attempt exists (fresh account only)
    s, attempts = req("GET", f"/rest/v1/quiz_attempts?quiz_id=eq.{graded['id']}&select=id", None, student)
    if s == 200 and not attempts:
        s, b = req("GET", f"/rest/v1/questions?quiz_id=eq.{graded['id']}&select=id", None, student)
        check("graded questions hidden before attempt", s == 200 and b == [], f"{s} {b}")

    # --- practice feedback works for practice, refused for graded
    s, pq = req("GET", f"/rest/v1/questions?quiz_id=eq.{practice['id']}&select=id,question_options(id)", None, student)
    check("practice questions visible", s == 200 and len(pq) > 0, f"{s}")
    s, fb = req("POST", "/rest/v1/rpc/check_practice_answer",
                {"p_question_id": pq[0]["id"], "p_option_id": pq[0]["question_options"][0]["id"]}, student)
    check("practice answer check returns feedback", s == 200 and "correct" in fb, f"{s} {fb}")

    # --- graded attempt lifecycle, server grading, replay safety
    s, attempt = req("POST", "/rest/v1/rpc/create_quiz_attempt", {"p_quiz_id": graded["id"]}, student)
    if s == 200:
        s2, again = req("POST", "/rest/v1/rpc/create_quiz_attempt", {"p_quiz_id": graded["id"]}, student)
        check("create attempt is idempotent while open", s2 == 200 and again["id"] == attempt["id"], f"{s2}")
        s, gq = req("GET", f"/rest/v1/questions?quiz_id=eq.{graded['id']}&select=id,question_options(id)", None, student)
        check("graded questions visible during attempt", s == 200 and len(gq) > 0)
        s, fb = req("POST", "/rest/v1/rpc/check_practice_answer",
                    {"p_question_id": gq[0]["id"], "p_option_id": gq[0]["question_options"][0]["id"]}, student)
        check("practice check refused for graded question", s >= 400, f"{s} {fb}")
        check("tutor lock active during attempt",
              req("POST", "/rest/v1/rpc/has_active_assessment", {}, student)[1] is True)
        for q in gq:
            req("POST", "/rest/v1/rpc/save_quiz_response",
                {"p_attempt_id": attempt["id"], "p_question_id": q["id"], "p_option_id": q["question_options"][0]["id"]}, student)
        s, res = req("POST", "/rest/v1/rpc/submit_quiz_attempt", {"p_attempt_id": attempt["id"]}, student)
        check("submit returns server score", s == 200 and res["score"] is not None, f"{s} {res}")
        s, res2 = req("POST", "/rest/v1/rpc/submit_quiz_attempt", {"p_attempt_id": attempt["id"]}, student)
        check("replayed submit returns same score", s == 200 and res2["score"] == res["score"], f"{res2}")
        s, b = req("POST", "/rest/v1/rpc/save_quiz_response",
                   {"p_attempt_id": attempt["id"], "p_question_id": gq[0]["id"], "p_option_id": gq[0]["question_options"][1]["id"]}, student)
        check("cannot change answers after submit", s >= 400, f"{s}")
        s, b = req("PATCH", f"/rest/v1/quiz_attempts?id=eq.{attempt['id']}", {"score": 999}, student)
        check("client cannot write a score", s >= 400 or b == [], f"{s} {b}")
        s, review = req("POST", "/rest/v1/rpc/get_attempt_review", {"p_attempt_id": attempt["id"]}, student)
        check("review released after submit", s == 200 and len(review) == len(gq), f"{s}")
        s, b = req("GET", f"/rest/v1/quiz_attempts?id=eq.{attempt['id']}&select=score", None, professor)
        check("professor sees own student's attempt", s == 200 and len(b) == 1, f"{s} {b}")
    else:
        print("SKIP attempt lifecycle (no attempts remaining):", attempt)

    # --- privilege boundaries
    s, b = req("POST", "/rest/v1/rpc/admin_platform_stats", {}, student)
    check("student cannot call admin stats", s >= 400, f"{s}")
    s, b = req("POST", "/rest/v1/rpc/admin_platform_stats", {}, admin)
    check("admin can call admin stats", s == 200, f"{s} {b}")
    s, b = req("GET", "/rest/v1/audit_logs?select=id", None, professor)
    check("professor cannot read audit log", s == 200 and b == [], f"{s} {b}")
    s, b = req("PATCH", "/rest/v1/user_roles?select=role", {"role": "admin"}, student)
    check("student cannot self-promote", s >= 400 or b == [], f"{s} {b}")
    s, me = req("GET", "/auth/v1/user", None, student)
    s, b = req("POST", "/rest/v1/rpc/admin_set_role", {"p_user_id": me["id"], "p_role": "admin"}, student)
    check("student cannot call admin_set_role", s >= 400, f"{s}")
    s, b = req("POST", "/rest/v1/rpc/start_simulation", {"p_scenario_id": "assessment_office", "p_mode": "practice"}, student)
    check("cannot start an assessment scenario in practice mode", s >= 400, f"{s}")

    print()
    print(f"{len(failures)} failure(s)" if failures else "All checks passed")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
