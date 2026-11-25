// delete-account: a signed-in user permanently deletes their OWN account (Google Play's in-app
// account deletion requirement). Deleting an auth user needs the service role, so it runs here.
//
// Body: { "confirm": "DELETE" }. The caller is taken from their JWT, never from the body.
// Database rows cascade from auth.users (profile, roles, enrollments, progress, attempts, chat
// messages, daily activity); content they authored for a class (classes, lessons, cases) is kept
// with the author set to null so other students' coursework survives. Profile photos are removed.
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2.119.2";

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

  const url = Deno.env.get("SUPABASE_URL")!;
  const userClient = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, {
    global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } },
    auth: { persistSession: false },
  });
  const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });

  const { data: me, error: meErr } = await userClient.auth.getUser();
  if (meErr || !me.user) return json({ error: "Not signed in" }, 401);
  const id = me.user.id;

  let body: Record<string, unknown> = {};
  try { body = await req.json(); } catch { /* empty body */ }
  if (body.confirm !== "DELETE") return json({ error: "Type DELETE to confirm." }, 422);

  // Never leave the school without an administrator.
  const { data: myRole } = await admin.from("user_roles").select("role").eq("user_id", id).maybeSingle();
  if (myRole?.role === "admin") {
    const { count } = await admin.from("user_roles").select("user_id", { count: "exact", head: true })
      .eq("role", "admin").eq("status", "active").neq("user_id", id);
    if (!count) return json({ error: "You're the only administrator. Make someone else an administrator first." }, 409);
  }

  // Profile photos live in avatars/<user_id>/...
  const { data: files } = await admin.storage.from("avatars").list(id, { limit: 1000 });
  if (files?.length) await admin.storage.from("avatars").remove(files.map((f) => `${id}/${f.name}`));

  // Logged without personal details: the account (and its email) is gone after this.
  await admin.rpc("service_audit", {
    p_actor: id, p_action: "user.self_delete", p_type: "user", p_id: id, p_details: {},
  });
  const { error } = await admin.auth.admin.deleteUser(id);
  if (error) return json({ error: "Could not delete the account. Try again." }, 500);
  return json({ deleted: true });
});
