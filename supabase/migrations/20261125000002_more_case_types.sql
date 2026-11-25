-- More case variety (2026-11):
--   * two new case types: robbery (handgun + demand note) and vandalism (broken glass + bat);
--   * cases can hold up to 7 items of evidence (supporting items may repeat, e.g. two prints);
--   * two new rooms (convenience store, warehouse) live in the app; the location check
--     already accepts any lowercase id, so nothing changes for them here.
-- The app mirrors these pools in CaseCatalog.kt, and submit-simulation/scoring.ts holds the
-- answer key for the new types.

-- Allow the new case types.
do $$
declare c record;
begin
  for c in
    select conname from pg_constraint
    where conrelid = 'public.case_sessions'::regclass and contype = 'c'
      and pg_get_constraintdef(oid) ilike '%case_type%'
  loop
    execute format('alter table public.case_sessions drop constraint %I', c.conname);
  end loop;
end $$;
alter table public.case_sessions add constraint case_sessions_case_type_check
  check (case_type in ('theft', 'burglary', 'assault', 'homicide', 'shooting', 'drug_possession', 'robbery', 'vandalism'));

-- Evidence types each case type may contain (mirrors the app's case catalogue).
create or replace function private.case_evidence_types(p_case_type text) returns text[]
language sql immutable set search_path = '' as $$
  select case p_case_type
    when 'theft'           then array['latent_print', 'questioned_document', 'shoe_print', 'hair_fiber', 'tool_mark']
    when 'burglary'        then array['tool_mark', 'broken_glass', 'shoe_print', 'latent_print', 'hair_fiber']
    when 'assault'         then array['bloodstain', 'blunt_weapon', 'hair_fiber', 'shoe_print', 'latent_print']
    when 'homicide'        then array['bloodstain', 'knife', 'latent_print', 'hair_fiber', 'shoe_print']
    when 'shooting'        then array['cartridge_case', 'bullet', 'firearm', 'bloodstain', 'latent_print']
    when 'drug_possession' then array['drug_packet', 'digital_scale', 'questioned_document', 'latent_print', 'hair_fiber']
    when 'robbery'         then array['firearm', 'questioned_document', 'shoe_print', 'latent_print', 'hair_fiber']
    when 'vandalism'       then array['broken_glass', 'blunt_weapon', 'shoe_print', 'latent_print', 'tool_mark']
    else array[]::text[]
  end
$$;

-- Up to 7 items per case (was 6). Same function otherwise.
create or replace function public.professor_create_case(
  p_class_id uuid, p_title text, p_case_type text, p_location text, p_location_version int,
  p_briefing text, p_seed int, p_manifest jsonb, p_graded boolean
) returns public.case_sessions
language plpgsql security definer set search_path = '' as $$
declare
  v_allowed text[] := private.case_evidence_types(p_case_type);
  v_count   int;
  v_code    text;
  v_row     public.case_sessions;
  v_alpha   constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
begin
  if not (public.is_admin() or (public.my_role() = 'professor' and public.is_class_professor(p_class_id))) then
    raise exception 'You can only create cases for your own classes' using errcode = '42501';
  end if;
  if coalesce(array_length(v_allowed, 1), 0) = 0 then
    raise exception 'Unknown case type' using errcode = '22023';
  end if;
  if jsonb_typeof(p_manifest) <> 'array' then
    raise exception 'Invalid evidence list' using errcode = '22023';
  end if;
  v_count := jsonb_array_length(p_manifest);
  if v_count not between 2 and 7 then
    raise exception 'A case needs 2 to 7 items of evidence' using errcode = '22023';
  end if;
  if exists (
    select 1 from jsonb_array_elements(p_manifest) e
    where jsonb_typeof(e) <> 'object' or (e ->> 'id') !~ '^ev_[0-9]{1,2}$' or not ((e ->> 'type') = any (v_allowed))
  ) or (select count(distinct e ->> 'id') from jsonb_array_elements(p_manifest) e) <> v_count then
    raise exception 'Evidence does not match the case type' using errcode = '22023';
  end if;

  loop
    select string_agg(substr(v_alpha, 1 + floor(random() * 32)::int, 1), '') into v_code from generate_series(1, 6);
    exit when not exists (select 1 from public.case_sessions where code = v_code);
  end loop;

  insert into public.case_sessions (code, class_id, title, case_type, location, location_version, briefing, seed, manifest, graded)
  values (v_code, p_class_id, trim(p_title), p_case_type, p_location, coalesce(p_location_version, 1), trim(p_briefing), p_seed,
          (select jsonb_agg(jsonb_build_object('id', e ->> 'id', 'type', e ->> 'type')) from jsonb_array_elements(p_manifest) e),
          coalesce(p_graded, false))
  returning * into v_row;
  perform private.audit('case.create', 'case_session', v_row.id::text,
    jsonb_build_object('code', v_row.code, 'class_id', p_class_id, 'case_type', p_case_type, 'graded', v_row.graded));
  return v_row;
end $$;
revoke all on function public.professor_create_case(uuid, text, text, text, int, text, int, jsonb, boolean) from public, anon;
grant execute on function public.professor_create_case(uuid, text, text, text, int, text, int, jsonb, boolean) to authenticated;
