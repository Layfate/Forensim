-- Resumable cases and victims (2026-11):
--   * a case the student has opened stays in their Simulate tab ("My cases"): unfinished ones show
--     how far they got and can be resumed any time, finished ones show 100%;
--   * the work on an unfinished case is saved (event log + progress) so it resumes on any phone;
--   * practice cases now resume the unfinished attempt too (before, only graded ones did);
--   * a professor can put a victim's body in the scene: male or female, face up or face down.
-- The app mirrors this in SimulationRepository / StaffRepository and engine/scene/Victim.kt.

-- ---------------------------------------------------------------------------- columns
alter table public.case_sessions add column if not exists victim_sex text;
alter table public.case_sessions add column if not exists victim_pose text;
alter table public.case_sessions drop constraint if exists case_sessions_victim_check;
alter table public.case_sessions add constraint case_sessions_victim_check check (
  (victim_sex is null and victim_pose is null)
  or (victim_sex in ('male', 'female') and victim_pose in ('face_up', 'face_down'))
);

alter table public.simulation_attempts add column if not exists progress int not null default 0;
alter table public.simulation_attempts add column if not exists checkpoint jsonb;
alter table public.simulation_attempts add column if not exists checkpoint_at timestamptz;
alter table public.simulation_attempts drop constraint if exists simulation_attempts_progress_check;
alter table public.simulation_attempts add constraint simulation_attempts_progress_check check (progress between 0 and 100);
create index if not exists simulation_attempts_case_student_idx on public.simulation_attempts (case_session_id, student_id);

-- ---------------------------------------------------------------------------- create a case
-- Same as before plus the optional victim. The old 9-argument version is replaced (not
-- overloaded), so callers that don't send a victim still resolve to this one.
drop function if exists public.professor_create_case(uuid, text, text, text, int, text, int, jsonb, boolean);
create or replace function public.professor_create_case(
  p_class_id uuid, p_title text, p_case_type text, p_location text, p_location_version int,
  p_briefing text, p_seed int, p_manifest jsonb, p_graded boolean,
  p_victim_sex text default null, p_victim_pose text default null
) returns public.case_sessions
language plpgsql security definer set search_path = '' as $$
declare
  v_allowed text[] := private.case_evidence_types(p_case_type);
  v_count   int;
  v_code    text;
  v_row     public.case_sessions;
  v_alpha   constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  v_sex     text := nullif(lower(trim(coalesce(p_victim_sex, ''))), '');
  v_pose    text := nullif(lower(trim(coalesce(p_victim_pose, ''))), '');
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
                       'victim', case when v_sex is null then null else v_sex || ',' || v_pose end));
  return v_row;
end $$;
revoke all on function public.professor_create_case(uuid, text, text, text, int, text, int, jsonb, boolean, text, text) from public, anon;
grant execute on function public.professor_create_case(uuid, text, text, text, int, text, int, jsonb, boolean, text, text) to authenticated;

-- ---------------------------------------------------------------------------- open / resume a case
-- As before, but an unfinished attempt is resumed in practice mode too (it carries its saved
-- checkpoint), and the case info includes the victim so every phone builds the same scene.
create or replace function public.start_case(p_code text)
returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_case public.case_sessions;
  v_role text := public.my_role();
  v_row  public.simulation_attempts;
  v_mode text;
begin
  if v_role is null then raise exception 'Account inactive' using errcode = '42501'; end if;
  select * into v_case from public.case_sessions where code = upper(regexp_replace(coalesce(p_code, ''), '[^A-Za-z0-9]', '', 'g'));
  if not found then raise exception 'No case found with that code. Check it and try again.' using errcode = 'P0002'; end if;
  if not v_case.active then raise exception 'This case has been closed by your professor.' using errcode = '22023'; end if;

  if v_role = 'student' then
    if not exists (select 1 from public.enrollments e where e.class_id = v_case.class_id and e.student_id = auth.uid() and e.status = 'active') then
      raise exception 'This case belongs to a class you have not joined. Join the class first.' using errcode = '42501';
    end if;
  elsif not (public.is_admin() or public.is_class_professor(v_case.class_id)) then
    raise exception 'Not permitted' using errcode = '42501';
  end if;

  v_mode := case when v_case.graded and v_role = 'student' then 'assessment' else 'practice' end;
  if v_mode = 'assessment' and exists (
    select 1 from public.simulation_attempts where case_session_id = v_case.id and student_id = auth.uid() and status = 'scored'
  ) then
    raise exception 'You have already submitted this graded case.' using errcode = '22023';
  end if;
  select * into v_row from public.simulation_attempts
    where case_session_id = v_case.id and student_id = auth.uid() and status = 'in_progress' and mode = v_mode
    order by started_at desc limit 1;
  if v_row.id is null then
    insert into public.simulation_attempts (scenario_id, scenario_version, student_id, mode, seed, manifest, case_session_id)
    values ('professor_case', 1, auth.uid(), v_mode, v_case.seed, v_case.manifest, v_case.id)
    returning * into v_row;
  end if;

  return jsonb_build_object(
    'attempt', to_jsonb(v_row),
    'case', jsonb_build_object('id', v_case.id, 'code', v_case.code, 'title', v_case.title, 'location', v_case.location,
                               'location_version', v_case.location_version, 'briefing', v_case.briefing, 'graded', v_mode = 'assessment',
                               'victim_sex', v_case.victim_sex, 'victim_pose', v_case.victim_pose)
  );
end $$;
revoke all on function public.start_case(text) from public, anon;
grant execute on function public.start_case(text) to authenticated;

-- ---------------------------------------------------------------------------- save progress
-- Saves the student's own unfinished attempt: its progress (0-99) and the event log so far.
-- Nothing here is trusted for scoring: submit-simulation scores the full log sent at the end.
create or replace function public.save_case_progress(p_attempt_id uuid, p_progress int, p_events jsonb)
returns void
language plpgsql security definer set search_path = '' as $$
begin
  if p_events is null or jsonb_typeof(p_events) <> 'array' then
    raise exception 'Invalid progress' using errcode = '22023';
  end if;
  if jsonb_array_length(p_events) > 4000 or pg_column_size(p_events) > 1048576 then
    raise exception 'Progress is too large to save' using errcode = '22023';
  end if;
  update public.simulation_attempts
     set progress = least(greatest(coalesce(p_progress, 0), 0), 99),
         checkpoint = p_events,
         checkpoint_at = now()
   where id = p_attempt_id and student_id = auth.uid() and status = 'in_progress';
  if not found then
    raise exception 'This case is already submitted or is not yours.' using errcode = '22023';
  end if;
end $$;
revoke all on function public.save_case_progress(uuid, int, jsonb) from public, anon;
grant execute on function public.save_case_progress(uuid, int, jsonb) to authenticated;

-- ---------------------------------------------------------------------------- my cases
-- One row per professor case the caller has opened: their unfinished attempt if there is one,
-- otherwise the latest finished one (progress 100). Never returns case_type.
create or replace function public.my_cases()
returns table (
  case_id uuid, code text, title text, location text, graded boolean, active boolean,
  attempt_id uuid, status text, progress int, score numeric, max_score numeric, feedback_released boolean,
  started_at timestamptz, submitted_at timestamptz
)
language sql stable security definer set search_path = '' as $$
  select l.case_id, l.code, l.title, l.location, l.graded, l.active, l.attempt_id, l.status, l.progress,
         l.score, l.max_score, l.feedback_released, l.started_at, l.submitted_at
  from (
    select distinct on (k.id)
      k.id as case_id, k.code, k.title, k.location, (a.mode = 'assessment') as graded, k.active,
      a.id as attempt_id, a.status, (case when a.status = 'scored' then 100 else a.progress end) as progress,
      a.score, a.max_score, a.feedback_released, a.started_at, a.submitted_at
    from public.simulation_attempts a
    join public.case_sessions k on k.id = a.case_session_id
    where a.student_id = auth.uid()
    order by k.id, (a.status = 'in_progress') desc, a.started_at desc
  ) l
  order by (l.status = 'in_progress') desc, coalesce(l.submitted_at, l.started_at) desc
$$;
revoke all on function public.my_cases() from public, anon;
grant execute on function public.my_cases() to authenticated;
