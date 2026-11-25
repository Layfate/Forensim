-- Simulate: required training parts and professor-generated cases joined by code / QR.
--
-- Training: students must finish Part 1 -> 2 -> 3 in order. Completion is recorded here so
-- professors can see who has trained. Training is ungraded practice, so the app records a part
-- when its in-game goal is met; the server only enforces the order.
--
-- Cases: a professor generates a scene for one of their classes (case type, location, seed and
-- evidence list, validated in the app so every student gets the identical, solvable layout).
-- Students open it with a 6-character code (or its QR). The case type is never sent to students:
-- they must work out what happened, and submit-simulation scores both their scene processing and
-- their conclusion.

-- ---------------------------------------------------------------------------
-- Training progress
-- ---------------------------------------------------------------------------
create table if not exists public.training_progress (
  student_id   uuid not null default auth.uid() references auth.users (id) on delete cascade,
  part         int not null check (part between 1 and 3),
  completed_at timestamptz not null default now(),
  primary key (student_id, part)
);
alter table public.training_progress enable row level security;

drop policy if exists training_progress_select on public.training_progress;
create policy training_progress_select on public.training_progress for select to authenticated
  using (student_id = (select auth.uid()) or public.is_admin() or public.teaches_student(student_id));
grant select on public.training_progress to authenticated;
grant all on public.training_progress to service_role;

create or replace function public.record_training_part(p_part int)
returns int[]
language plpgsql security definer set search_path = '' as $$
begin
  if auth.uid() is null or public.my_role() is null then
    raise exception 'Account inactive' using errcode = '42501';
  end if;
  if p_part is null or p_part not between 1 and 3 then
    raise exception 'Unknown training part' using errcode = '22023';
  end if;
  if p_part > 1 and not exists (select 1 from public.training_progress where student_id = auth.uid() and part = p_part - 1) then
    raise exception 'Finish training part % first', p_part - 1 using errcode = '22023';
  end if;
  insert into public.training_progress (student_id, part) values (auth.uid(), p_part) on conflict do nothing;
  return array(select part from public.training_progress where student_id = auth.uid() order by part);
end $$;
revoke all on function public.record_training_part(int) from public, anon;
grant execute on function public.record_training_part(int) to authenticated;

-- ---------------------------------------------------------------------------
-- Professor-generated cases
-- ---------------------------------------------------------------------------
create table if not exists public.case_sessions (
  id               uuid primary key default gen_random_uuid(),
  code             text not null unique check (code ~ '^[A-HJ-NP-Z2-9]{6}$'),
  class_id         uuid not null references public.classes (id) on delete cascade,
  created_by       uuid references auth.users (id) on delete set null default auth.uid(),
  title            text not null check (char_length(title) between 3 and 80),
  case_type        text not null check (case_type in ('theft', 'burglary', 'assault', 'homicide', 'shooting', 'drug_possession')),
  location         text not null check (location ~ '^[a-z_]{3,40}$'),
  location_version int not null default 1,
  briefing         text not null check (char_length(briefing) between 10 and 600),
  seed             int not null,
  manifest         jsonb not null,
  graded           boolean not null default false,
  active           boolean not null default true,
  created_at       timestamptz not null default now()
);
create index if not exists case_sessions_class_idx on public.case_sessions (class_id, created_at desc);
alter table public.case_sessions enable row level security;

-- Only the class's professor (and admins) read sessions; students open them through start_case,
-- which never returns case_type.
drop policy if exists case_sessions_select on public.case_sessions;
create policy case_sessions_select on public.case_sessions for select to authenticated
  using (public.is_admin() or public.is_class_professor(class_id));
grant select on public.case_sessions to authenticated;
grant all on public.case_sessions to service_role;

-- Attempts made on a case point at it; the attempt keeps its own copy of seed + manifest.
alter table public.simulation_attempts add column if not exists case_session_id uuid references public.case_sessions (id) on delete set null;
create index if not exists simulation_attempts_case_idx on public.simulation_attempts (case_session_id);

-- Placeholder scenario row for case attempts (attempts require a scenario id). Unpublished, so it
-- never shows in the case list; its template lives in the app (location + seed + manifest).
insert into public.simulation_scenarios (id, title, summary, kind, module_slug, est_minutes, version, published, template)
values ('professor_case', 'Professor case', 'Scene generated by a professor and opened with a code.', 'practice',
        'crime-scene-investigation', 20, 1, false, '{}'::jsonb)
on conflict (id) do nothing;

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
    else array[]::text[]
  end
$$;

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
  if v_count not between 2 and 6 then
    raise exception 'A case needs 2 to 6 items of evidence' using errcode = '22023';
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

create or replace function public.professor_set_case_active(p_case_id uuid, p_active boolean)
returns public.case_sessions
language plpgsql security definer set search_path = '' as $$
declare v_row public.case_sessions;
begin
  select * into v_row from public.case_sessions where id = p_case_id;
  if not found or not (public.is_admin() or public.is_class_professor(v_row.class_id)) then
    raise exception 'Not permitted' using errcode = '42501';
  end if;
  update public.case_sessions set active = p_active where id = p_case_id returning * into v_row;
  return v_row;
end $$;
revoke all on function public.professor_set_case_active(uuid, boolean) from public, anon;
grant execute on function public.professor_set_case_active(uuid, boolean) to authenticated;

-- Opens a case by code. Students must be active members of the case's class. Graded cases allow
-- one submission (an unfinished attempt is resumed). Returns the attempt and the public case info
-- (never case_type).
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
  if v_mode = 'assessment' then
    if exists (select 1 from public.simulation_attempts where case_session_id = v_case.id and student_id = auth.uid() and status = 'scored') then
      raise exception 'You have already submitted this graded case.' using errcode = '22023';
    end if;
    select * into v_row from public.simulation_attempts
      where case_session_id = v_case.id and student_id = auth.uid() and status = 'in_progress'
      order by started_at desc limit 1;
  end if;
  if v_row.id is null then
    insert into public.simulation_attempts (scenario_id, scenario_version, student_id, mode, seed, manifest, case_session_id)
    values ('professor_case', 1, auth.uid(), v_mode, v_case.seed, v_case.manifest, v_case.id)
    returning * into v_row;
  end if;

  return jsonb_build_object(
    'attempt', to_jsonb(v_row),
    'case', jsonb_build_object('id', v_case.id, 'code', v_case.code, 'title', v_case.title, 'location', v_case.location,
                               'location_version', v_case.location_version, 'briefing', v_case.briefing, 'graded', v_mode = 'assessment')
  );
end $$;
revoke all on function public.start_case(text) from public, anon;
grant execute on function public.start_case(text) to authenticated;
