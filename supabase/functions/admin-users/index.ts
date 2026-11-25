// admin-users: administrators create and delete accounts (students, professors, admins).
// Creating/deleting auth users needs the service role, so this runs server-side only. The caller
// must be an active admin; every action is written to the audit log.
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.119.2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const ROLES = ["student", "professor", "admin"] as const;
type Role = (typeof ROLES)[number];
const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });
}

function strongPassword(p: string) {
  return p.length >= 10 && /[a-z]/.test(p) && /[A-Z]/.test(p) && /\d/.test(p) && /[^A-Za-z0-9]/.test(p);
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

  const { data: me, error: meErr } = await userClient.auth.getUser();
  if (meErr || !me.user) return json({ error: "Not signed in" }, 401);
  const { data: isAdmin } = await userClient.rpc("is_admin");
  if (isAdmin !== true) return json({ error: "Administrator role required" }, 403);
  const actor = me.user.id;

  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return json({ error: "Invalid JSON" }, 400);
  }

  if (body.action === "create") {
    const email = String(body.email ?? "").trim().toLowerCase();
    const password = String(body.password ?? "");
    const fullName = String(body.full_name ?? "").trim();
    const role = String(body.role ?? "student") as Role;
    if (!EMAIL.test(email)) return json({ error: "Enter a valid e-mail address." }, 422);
    if (fullName.length < 2) return json({ error: "Enter the person's full name." }, 422);
    if (!ROLES.includes(role)) return json({ error: "Choose student, professor or admin." }, 422);
    if (!strongPassword(password)) {
      return json({ error: "Password needs 10+ characters with upper and lower case letters, a number and a symbol." }, 422);
    }

    const { data: created, error } = await admin.auth.admin.createUser({
      email,
      password,
      email_confirm: true,
      user_metadata: { full_name: fullName },
    });
    if (error || !created.user) {
      const msg = /already|registered|exists/i.test(error?.message ?? "") ? "An account with this e-mail already exists." : "Could not create the account.";
      return json({ error: msg }, 409);
    }
    const id = created.user.id;
    // The on_auth_user_created trigger made the profile + student role; apply the chosen role.
    const { error: roleErr } = await admin.from("user_roles")
      .upsert({ user_id: id, role, status: "active", updated_at: new Date().toISOString() });
    if (roleErr) return json({ error: "Account created, but the role could not be set. Change it in the list." }, 500);
    await admin.rpc("service_audit", {
      p_actor: actor, p_action: "user.create", p_type: "user", p_id: id,
      p_details: { email, role },
    });
    return json({ user_id: id, email, role });
  }

  if (body.action === "delete") {
    const id = String(body.user_id ?? "");
    if (!/^[0-9a-f-]{36}$/i.test(id)) return json({ error: "user_id required" }, 400);
    if (id === actor) return json({ error: "You cannot delete your own account here." }, 422);
    const { data: target } = await admin.from("profiles").select("email").eq("id", id).maybeSingle();
    const { error } = await admin.auth.admin.deleteUser(id);
    if (error) return json({ error: "Could not delete the account." }, 500);
    await admin.rpc("service_audit", {
      p_actor: actor, p_action: "user.delete", p_type: "user", p_id: id,
      p_details: { email: target?.email ?? null },
    });
    return json({ deleted: id });
  }

  return json({ error: "Unknown action" }, 400);
});
