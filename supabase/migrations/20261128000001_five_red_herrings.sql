-- Up to 5 red herrings per case (2026-11). Same as 20261127000001 except the red-herring limit:
-- a case holds 2 to 7 items of evidence plus at most 5 red herrings (12 items in all). The app's
-- rooms gained spots for this (engine/scene/CaseCatalog.kt, layout versions 3 / 2); the limit
-- mirrors CaseFactory.MAX_RED_HERRINGS.

-- Remove the old 9-argument overload if it is still there (it is when 20261126000001 was skipped):
-- alongside this 11-argument version, a call without a victim matches both and Postgres fails with
-- "Could not choose the best candidate function".
drop function if exists public.professor_create_case(uuid, text, text, text, int, text, int, jsonb, boolean);

create or replace function public.professor_create_case(
  p_class_id uuid, p_title text, p_case_type text, p_location text, p_location_version int,
  p_briefing text, p_seed int, p_manifest jsonb, p_graded boolean,
  p_victim_sex text default null, p_victim_pose text default null
) returns public.case_sessions
language plpgsql security definer set search_path = '' as $$
declare
  v_pool     text[] := private.case_evidence_types(p_case_type);
  v_herrings text[] := private.red_herring_types();
  v_count    int;
  v_red      int;
  v_code     text;
  v_row      public.case_sessions;
  v_alpha    constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  v_sex      text := nullif(lower(trim(coalesce(p_victim_sex, ''))), '');
  v_pose     text := nullif(lower(trim(coalesce(p_victim_pose, ''))), '');
begin
  if not (public.is_admin() or (public.my_role() = 'professor' and public.is_class_professor(p_class_id))) then
    raise exception 'You can only create cases for your own classes' using errcode = '42501';
  end if;
  if coalesce(array_length(v_pool, 1), 0) = 0 then
    raise exception 'Unknown case type' using errcode = '22023';
  end if;
  if jsonb_typeof(p_manifest) <> 'array' then
    raise exception 'Invalid evidence list' using errcode = '22023';
  end if;
  v_count := jsonb_array_length(p_manifest);
  select count(*) into v_red from jsonb_array_elements(p_manifest) e where (e ->> 'type') = any (v_herrings);
  if v_count - v_red not between 2 and 7 then
    raise exception 'A case needs 2 to 7 items of evidence' using errcode = '22023';
  end if;
  if v_red > 5 then
    raise exception 'A case can have at most 5 red herrings' using errcode = '22023';
  end if;
  if exists (
    select 1 from jsonb_array_elements(p_manifest) e
    where jsonb_typeof(e) <> 'object' or (e ->> 'id') !~ '^ev_[0-9]{1,2}$'
       or not ((e ->> 'type') = any (v_pool) or (e ->> 'type') = any (v_herrings))
  ) or (select count(distinct e ->> 'id') from jsonb_array_elements(p_manifest) e) <> v_count then
    raise exception 'Evidence does not match the case type' using errcode = '22023';
  end if;
  if v_sex is null then
    v_pose := null;
  elsif v_sex not in ('male', 'female') or coalesce(v_pose, '') not in ('face_up', 'face_down') then
    raise exception 'Invalid victim' using errcode = '22023';
  end if;

  loop
    select string_agg(substr(v_alpha, 1 + floor(random() * 32)::int, 1), '') into v_code from generate_series(1, 6);
    exit when not exists (select 1 from public.case_sessions where code = v_code);
  end loop;

  insert into public.case_sessions (code, class_id, title, case_type, location, location_version, briefing, seed, manifest, graded,
                                    victim_sex, victim_pose)
  values (v_code, p_class_id, trim(p_title), p_case_type, p_location, coalesce(p_location_version, 1), trim(p_briefing), p_seed,
          (select jsonb_agg(jsonb_build_object('id', e ->> 'id', 'type', e ->> 'type')) from jsonb_array_elements(p_manifest) e),
          coalesce(p_graded, false), v_sex, v_pose)
  returning * into v_row;
  perform private.audit('case.create', 'case_session', v_row.id::text,
    jsonb_build_object('code', v_row.code, 'class_id', p_class_id, 'case_type', p_case_type, 'graded', v_row.graded,
                       'red_herrings', v_red,
                       'victim', case when v_sex is null then null else v_sex || ',' || v_pose end));
  return v_row;
end $$;
revoke all on function public.professor_create_case(uuid, text, text, text, int, text, int, jsonb, boolean, text, text) from public, anon;
grant execute on function public.professor_create_case(uuid, text, text, text, int, text, int, jsonb, boolean, text, text) to authenticated;
