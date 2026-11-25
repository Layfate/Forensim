-- Edge Functions run with the service_role key. New projects do not auto-grant it table access,
-- so grant it explicitly. service_role bypasses RLS and must never be shipped in the app.
grant usage on schema public to service_role;
grant all on all tables in schema public to service_role;
grant all on all sequences in schema public to service_role;

-- Server-only accessor for the private scoring rubric (used by submit-simulation).
create or replace function public.service_get_simulation_rubric(p_scenario_id text, p_version int)
returns jsonb
language sql stable security definer set search_path = '' as $$
  select r.rubric from private.simulation_rubrics r where r.scenario_id = p_scenario_id and r.version = p_version
$$;
revoke execute on function public.service_get_simulation_rubric(text, int) from public, anon, authenticated;
grant execute on function public.service_get_simulation_rubric(text, int) to service_role;
