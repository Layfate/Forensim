-- Trusted server operations (plan sections 7.2, 8.3).
-- Quiz attempt lifecycle and grading run here, inside single transactions, so the client never
-- sees answer keys and never submits a score. Simulation rubric scoring runs in the
-- `submit-simulation` Edge Function; the AI policy gate runs in the `ai-tutor` Edge Function.

-- ---------------------------------------------------------------------------
-- Enrollment
-- ---------------------------------------------------------------------------
create or replace function public.join_class(p_code text)
returns public.enrollments
language plpgsql security definer set search_path = '' as $$
declare
  v_class public.classes;
  v_row   public.enrollments;
begin
  if public.my_role() is distinct from 'student' then
    raise exception 'Only active student accounts can join a class' using errcode = '42501';
  end if;
  select * into v_class from public.classes where upper(join_code) = upper(trim(p_code));
  if not found or not v_class.join_open then
    raise exception 'Join code not recognised or enrollment is closed' using errcode = 'P0002';
  end if;
  insert into public.enrollments (class_id, student_id)
  values (v_class.id, auth.uid())
  on conflict (class_id, student_id) do update set status = 'active'
  returning * into v_row;
  return v_row;
end $$;

-- ---------------------------------------------------------------------------
-- Graded quiz attempts
-- ---------------------------------------------------------------------------
create or replace function public.create_quiz_attempt(p_quiz_id uuid)
returns public.quiz_attempts
language plpgsql security definer set search_path = '' as $$
declare
  v_quiz    public.quizzes;
  v_open    public.quiz_attempts;
  v_count   int;
  v_attempt public.quiz_attempts;
begin
  if public.my_role() is distinct from 'student' then
    raise exception 'Only students can start graded attempts' using errcode = '42501';
  end if;
  select * into v_quiz from public.quizzes where id = p_quiz_id;
  if not found or not v_quiz.published or not public.can_view_module(v_quiz.module_id) then
    raise exception 'Quiz not available' using errcode = 'P0002';
  end if;
  if v_quiz.kind <> 'graded' then
    raise exception 'Practice quizzes do not use attempts' using errcode = '22023';
  end if;
  if v_quiz.available_from is not null and now() < v_quiz.available_from then
    raise exception 'Quiz is not open yet' using errcode = '22023';
  end if;
  if v_quiz.available_until is not null and now() > v_quiz.available_until then
    raise exception 'Quiz window has closed' using errcode = '22023';
  end if;

  -- Serialise attempt creation per student+quiz.
  perform pg_advisory_xact_lock(hashtextextended(p_quiz_id::text || auth.uid()::text, 0));

  select * into v_open from public.quiz_attempts
  where quiz_id = p_quiz_id and student_id = auth.uid() and status = 'in_progress';
  if found then
    if v_open.deadline_at is null or now() <= v_open.deadline_at then
      return v_open;  -- resume (idempotent)
    end if;
    perform public.submit_quiz_attempt(v_open.id);  -- auto-close an expired attempt
  end if;

  select count(*) into v_count from public.quiz_attempts where quiz_id = p_quiz_id and student_id = auth.uid();
  if v_quiz.max_attempts is not null and v_count >= v_quiz.max_attempts then
    raise exception 'No attempts remaining' using errcode = '22023';
  end if;

  insert into public.quiz_attempts (quiz_id, student_id, attempt_number, deadline_at)
  values (p_quiz_id, auth.uid(), v_count + 1,
          case when v_quiz.time_limit_seconds is null then null
               else least(now() + make_interval(secs => v_quiz.time_limit_seconds),
                          coalesce(v_quiz.available_until, 'infinity'::timestamptz)) end)
  returning * into v_attempt;
  return v_attempt;
end $$;

create or replace function public.save_quiz_response(p_attempt_id uuid, p_question_id uuid, p_option_id uuid)
returns void
language plpgsql security definer set search_path = '' as $$
declare
  v_attempt public.quiz_attempts;
begin
  select * into v_attempt from public.quiz_attempts where id = p_attempt_id for update;
  if not found or v_attempt.student_id <> auth.uid() then
    raise exception 'Attempt not found' using errcode = 'P0002';
  end if;
  if v_attempt.status <> 'in_progress' then
    raise exception 'Attempt already submitted' using errcode = '22023';
  end if;
  -- 15 s grace for network latency.
  if v_attempt.deadline_at is not null and now() > v_attempt.deadline_at + interval '15 seconds' then
    raise exception 'Time limit exceeded' using errcode = '22023';
  end if;
  if not exists (select 1 from public.questions q join public.question_options o on o.question_id = q.id
                 where q.id = p_question_id and q.quiz_id = v_attempt.quiz_id and o.id = p_option_id) then
    raise exception 'Invalid question or option' using errcode = '22023';
  end if;
  insert into public.quiz_responses (attempt_id, question_id, option_id)
  values (p_attempt_id, p_question_id, p_option_id)
  on conflict (attempt_id, question_id) do update set option_id = excluded.option_id, saved_at = now();
end $$;

-- Grades from private.answer_keys. Replays return the already-computed result.
create or replace function public.submit_quiz_attempt(p_attempt_id uuid)
returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_attempt public.quiz_attempts;
  v_quiz    public.quizzes;
  v_score   numeric;
  v_max     numeric;
begin
  select * into v_attempt from public.quiz_attempts where id = p_attempt_id for update;
  if not found or (v_attempt.student_id <> auth.uid() and not public.is_admin()) then
    raise exception 'Attempt not found' using errcode = 'P0002';
  end if;
  select * into v_quiz from public.quizzes where id = v_attempt.quiz_id;

  if v_attempt.status = 'in_progress' then
    select coalesce(sum(q.points), 0),
           coalesce(sum(q.points) filter (where r.option_id = k.correct_option_id), 0)
      into v_max, v_score
    from public.questions q
    join private.answer_keys k on k.question_id = q.id
    left join public.quiz_responses r on r.question_id = q.id and r.attempt_id = v_attempt.id
    where q.quiz_id = v_attempt.quiz_id;

    update public.quiz_attempts
       set status = 'submitted', submitted_at = now(), score = v_score, max_score = v_max,
           late = (deadline_at is not null and now() > deadline_at + interval '15 seconds')
     where id = v_attempt.id
     returning * into v_attempt;
  end if;

  return jsonb_build_object(
    'attempt_id', v_attempt.id,
    'status', v_attempt.status,
    'score', v_attempt.score,
    'max_score', v_attempt.max_score,
    'late', v_attempt.late,
    'review_available', (v_quiz.review_policy = 'after_submit'
                         or (v_quiz.review_policy = 'after_close' and v_quiz.available_until is not null
                             and now() > v_quiz.available_until)));
end $$;

-- Per-question review, released according to the professor's review policy.
create or replace function public.get_attempt_review(p_attempt_id uuid)
returns table (question_id uuid, prompt text, chosen_option_id uuid, correct_option_id uuid, explanation text)
language plpgsql security definer set search_path = '' as $$
declare
  v_attempt public.quiz_attempts;
  v_quiz    public.quizzes;
  v_staff   boolean;
begin
  select * into v_attempt from public.quiz_attempts where id = p_attempt_id;
  if not found then raise exception 'Attempt not found' using errcode = 'P0002'; end if;
  v_staff := public.is_admin() or public.teaches_student(v_attempt.student_id);
  if v_attempt.student_id <> auth.uid() and not v_staff then
    raise exception 'Attempt not found' using errcode = 'P0002';
  end if;
  select * into v_quiz from public.quizzes where id = v_attempt.quiz_id;
  if not v_staff then
    if v_attempt.status <> 'submitted' then
      raise exception 'Review is available after submission' using errcode = '42501';
    end if;
    if v_quiz.review_policy = 'never'
       or (v_quiz.review_policy = 'after_close' and (v_quiz.available_until is null or now() <= v_quiz.available_until)) then
      raise exception 'Your professor has not released the review yet' using errcode = '42501';
    end if;
  end if;
  return query
    select q.id, q.prompt, r.option_id, k.correct_option_id, k.explanation
    from public.questions q
    join private.answer_keys k on k.question_id = q.id
    left join public.quiz_responses r on r.question_id = q.id and r.attempt_id = v_attempt.id
    where q.quiz_id = v_attempt.quiz_id
    order by q.position;
end $$;

-- Practice feedback: only for practice quizzes, never graded ones.
create or replace function public.check_practice_answer(p_question_id uuid, p_option_id uuid)
returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_quiz public.quizzes;
  v_key  private.answer_keys;
begin
  select z.* into v_quiz from public.quizzes z join public.questions q on q.quiz_id = z.id where q.id = p_question_id;
  if not found or v_quiz.kind <> 'practice' or not v_quiz.published or not public.can_view_module(v_quiz.module_id) then
    raise exception 'Practice question not available' using errcode = 'P0002';
  end if;
  select * into v_key from private.answer_keys where question_id = p_question_id;
  return jsonb_build_object(
    'correct', v_key.correct_option_id = p_option_id,
    'correct_option_id', v_key.correct_option_id,
    'explanation', v_key.explanation);
end $$;

-- Used by the ai-tutor Edge Function (via the caller's JWT) to enforce the assessment lock.
create or replace function public.has_active_assessment()
returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.quiz_attempts a
                 where a.student_id = auth.uid() and a.status = 'in_progress'
                   and (a.deadline_at is null or now() <= a.deadline_at + interval '15 seconds'))
      or exists (select 1 from public.simulation_attempts s
                 where s.student_id = auth.uid() and s.mode = 'assessment' and s.status = 'in_progress'
                   and s.started_at > now() - interval '3 hours')
$$;

-- ---------------------------------------------------------------------------
-- Simulation
-- ---------------------------------------------------------------------------
-- Issues a versioned attempt with a server seed and a server-chosen evidence manifest.
create or replace function public.start_simulation(p_scenario_id text, p_mode text)
returns public.simulation_attempts
language plpgsql security definer set search_path = '' as $$
declare
  v_s        public.simulation_scenarios;
  v_seed     int;
  v_count    int;
  v_manifest jsonb;
  v_row      public.simulation_attempts;
begin
  if public.my_role() is null then
    raise exception 'Account inactive' using errcode = '42501';
  end if;
  select * into v_s from public.simulation_scenarios where id = p_scenario_id and published;
  if not found then raise exception 'Scenario not available' using errcode = 'P0002'; end if;
  if p_mode not in ('tutorial', 'practice', 'assessment') then
    raise exception 'Invalid mode' using errcode = '22023';
  end if;
  if (v_s.kind = 'assessment') <> (p_mode = 'assessment') then
    raise exception 'Mode does not match scenario type' using errcode = '22023';
  end if;
  if p_mode = 'assessment' and public.my_role() <> 'student' then
    raise exception 'Only students take simulation assessments' using errcode = '42501';
  end if;

  v_seed  := (v_s.template ->> 'fixed_seed')::int;
  if v_seed is null then v_seed := floor(random() * 2147483646)::int + 1; end if;
  v_count := coalesce((v_s.template ->> 'evidence_count')::int, 3);

  -- Deterministic selection from the evidence pool given the seed.
  select coalesce(jsonb_agg(jsonb_build_object('id', 'ev_' || rn, 'type', t) order by rn), '[]'::jsonb)
    into v_manifest
  from (
    select t, row_number() over (order by case when coalesce((e ->> 'required')::boolean, false) then 0 else 1 end,
                                          md5(v_seed::text || t)) as rn
    from jsonb_array_elements(v_s.template -> 'evidence_pool') e,
         lateral (select e ->> 'type' as t) x
  ) ranked
  where rn <= v_count;

  insert into public.simulation_attempts (scenario_id, scenario_version, student_id, mode, seed, manifest)
  values (v_s.id, v_s.version, auth.uid(), p_mode, v_seed, v_manifest)
  returning * into v_row;
  return v_row;
end $$;

-- ---------------------------------------------------------------------------
-- Professor and admin actions (audited)
-- ---------------------------------------------------------------------------
create or replace function public.set_module_published(p_module_id uuid, p_published boolean)
returns public.modules
language plpgsql security definer set search_path = '' as $$
declare v_row public.modules;
begin
  if not public.can_manage_module(p_module_id) then
    raise exception 'Not permitted' using errcode = '42501';
  end if;
  update public.modules set published = p_published, updated_at = now() where id = p_module_id returning * into v_row;
  perform private.audit(case when p_published then 'module.publish' else 'module.unpublish' end,
                        'module', p_module_id::text, jsonb_build_object('title', v_row.title));
  return v_row;
end $$;

create or replace function public.set_quiz_review_policy(p_quiz_id uuid, p_policy text)
returns public.quizzes
language plpgsql security definer set search_path = '' as $$
declare v_row public.quizzes;
begin
  if not public.can_manage_module(public.quiz_module(p_quiz_id)) then
    raise exception 'Not permitted' using errcode = '42501';
  end if;
  update public.quizzes set review_policy = p_policy where id = p_quiz_id returning * into v_row;
  perform private.audit('quiz.review_policy', 'quiz', p_quiz_id::text, jsonb_build_object('policy', p_policy));
  return v_row;
end $$;

create or replace function public.release_simulation_feedback(p_attempt_id uuid, p_released boolean)
returns void
language plpgsql security definer set search_path = '' as $$
declare v_student uuid;
begin
  select student_id into v_student from public.simulation_attempts where id = p_attempt_id;
  if not (public.is_admin() or public.teaches_student(v_student)) then
    raise exception 'Not permitted' using errcode = '42501';
  end if;
  update public.simulation_attempts set feedback_released = p_released where id = p_attempt_id;
  perform private.audit('simulation.feedback_release', 'simulation_attempt', p_attempt_id::text,
                        jsonb_build_object('released', p_released));
end $$;

create or replace function public.admin_set_role(p_user_id uuid, p_role public.app_role, p_status text default 'active')
returns public.user_roles
language plpgsql security definer set search_path = '' as $$
declare
  v_old public.user_roles;
  v_row public.user_roles;
begin
  if not public.is_admin() then
    raise exception 'Administrator role required' using errcode = '42501';
  end if;
  if p_user_id = auth.uid() and (p_role <> 'admin' or p_status <> 'active') then
    raise exception 'Administrators cannot demote or suspend themselves' using errcode = '22023';
  end if;
  select * into v_old from public.user_roles where user_id = p_user_id for update;
  if not found then raise exception 'User not found' using errcode = 'P0002'; end if;
  update public.user_roles set role = p_role, status = p_status, updated_at = now()
   where user_id = p_user_id returning * into v_row;
  perform private.audit('user.role_change', 'user', p_user_id::text,
    jsonb_build_object('from_role', v_old.role, 'to_role', p_role, 'from_status', v_old.status, 'to_status', p_status));
  return v_row;
end $$;

create or replace function public.admin_list_users()
returns table (user_id uuid, full_name text, email text, role public.app_role, status text, created_at timestamptz)
language plpgsql stable security definer set search_path = '' as $$
begin
  if not public.is_admin() then raise exception 'Administrator role required' using errcode = '42501'; end if;
  return query
    select p.id, p.full_name, p.email, r.role, r.status, p.created_at
    from public.profiles p join public.user_roles r on r.user_id = p.id
    order by p.created_at desc;
end $$;

create or replace function public.admin_update_setting(p_key text, p_value jsonb)
returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not public.is_admin() then raise exception 'Administrator role required' using errcode = '42501'; end if;
  insert into public.system_settings (key, value, updated_at) values (p_key, p_value, now())
  on conflict (key) do update set value = excluded.value, updated_at = now();
  perform private.audit('settings.update', 'setting', p_key, jsonb_build_object('value', p_value));
end $$;

create or replace function public.admin_platform_stats()
returns jsonb
language plpgsql stable security definer set search_path = '' as $$
begin
  if not public.is_admin() then raise exception 'Administrator role required' using errcode = '42501'; end if;
  return jsonb_build_object(
    'students',            (select count(*) from public.user_roles where role = 'student'),
    'professors',          (select count(*) from public.user_roles where role = 'professor'),
    'admins',              (select count(*) from public.user_roles where role = 'admin'),
    'classes',             (select count(*) from public.classes),
    'published_modules',   (select count(*) from public.modules where published),
    'quiz_attempts',       (select count(*) from public.quiz_attempts),
    'simulation_attempts', (select count(*) from public.simulation_attempts),
    'ai_requests_7d',      (select count(*) from public.ai_usage where created_at > now() - interval '7 days'),
    'ai_blocked_7d',       (select count(*) from public.ai_usage where blocked and created_at > now() - interval '7 days'));
end $$;

-- ---------------------------------------------------------------------------
-- Execution grants: authenticated only; helpers in `private` are not callable by clients.
-- ---------------------------------------------------------------------------
do $$
declare f record;
begin
  for f in
    select p.oid::regprocedure as sig
    from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.prokind = 'f'
      and p.proname in ('join_class','create_quiz_attempt','save_quiz_response','submit_quiz_attempt',
                        'get_attempt_review','check_practice_answer','has_active_assessment','start_simulation',
                        'set_module_published','set_quiz_review_policy','release_simulation_feedback',
                        'admin_set_role','admin_list_users','admin_update_setting','admin_platform_stats',
                        'my_role','is_admin','teaches_course','enrolled_in_course','teaches_student',
                        'is_class_professor','is_class_member','module_course','can_manage_module',
                        'can_view_module','quiz_module','can_view_quiz_questions','question_quiz')
  loop
    execute format('revoke execute on function %s from public, anon', f.sig);
    execute format('grant execute on function %s to authenticated', f.sig);
  end loop;
end $$;

revoke execute on function private.audit(text, text, text, jsonb) from public, anon, authenticated;
revoke execute on function private.handle_new_user() from public, anon, authenticated;
