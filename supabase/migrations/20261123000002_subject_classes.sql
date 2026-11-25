-- Subject classes (CodeChum style).
-- A professor opens a class for one of the seven forensic subjects (one class per subject per
-- professor), shares its join code, unlocks lessons one by one and adds quizzes that belong to
-- that class only. Students see a subject only through a class they joined, and inside it only
-- the lessons and shared quizzes the professor unlocked.

-- ---------------------------------------------------------------------------
-- Schema
-- ---------------------------------------------------------------------------
alter table public.classes add column if not exists module_id uuid references public.modules (id) on delete cascade;
-- Course-wide classes from before subject classes cannot be mapped to one subject (test data only).
delete from public.classes where module_id is null;
alter table public.classes alter column module_id set not null;
create unique index if not exists classes_professor_subject_uq on public.classes (professor_id, module_id);
create index if not exists classes_module_idx on public.classes (module_id);

-- A row means "unlocked for this class". No row = locked (the default for every new class).
create table if not exists public.class_lesson_unlocks (
  class_id    uuid not null references public.classes (id) on delete cascade,
  lesson_id   uuid not null references public.lessons (id) on delete cascade,
  unlocked_at timestamptz not null default now(),
  primary key (class_id, lesson_id)
);
create index if not exists class_lesson_unlocks_lesson_idx on public.class_lesson_unlocks (lesson_id);

-- Shared quizzes (class_id is null, e.g. the built-in practice quizzes) are also unlocked per class.
create table if not exists public.class_quiz_unlocks (
  class_id    uuid not null references public.classes (id) on delete cascade,
  quiz_id     uuid not null references public.quizzes (id) on delete cascade,
  unlocked_at timestamptz not null default now(),
  primary key (class_id, quiz_id)
);
create index if not exists class_quiz_unlocks_quiz_idx on public.class_quiz_unlocks (quiz_id);

-- Quizzes a professor creates inside a class belong to that class only.
alter table public.quizzes add column if not exists class_id uuid references public.classes (id) on delete cascade;
create index if not exists quizzes_class_idx on public.quizzes (class_id);

alter table public.class_lesson_unlocks enable row level security;
alter table public.class_quiz_unlocks enable row level security;

-- ---------------------------------------------------------------------------
-- Access helpers
-- ---------------------------------------------------------------------------
-- Professors manage the subjects they hold a class for (and modules they created); admins manage all.
create or replace function public.can_manage_module(p_module uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select public.is_admin()
      or (public.my_role() = 'professor' and (
            exists (select 1 from public.classes k where k.module_id = p_module and k.professor_id = auth.uid())
         or exists (select 1 from public.modules m where m.id = p_module and m.created_by = auth.uid())))
$$;

-- Students see a subject only through an active enrollment in one of its classes.
create or replace function public.can_view_module(p_module uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select public.can_manage_module(p_module)
      or exists (select 1 from public.modules m
                 join public.classes k on k.module_id = m.id
                 join public.enrollments e on e.class_id = k.id
                 where m.id = p_module and m.published and e.student_id = auth.uid() and e.status = 'active')
$$;

create or replace function public.can_view_lesson(p_lesson uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.lessons l
    where l.id = p_lesson and (
      public.can_manage_module(l.module_id)
      or (l.published and exists (
            select 1 from public.class_lesson_unlocks u
            join public.classes k on k.id = u.class_id
            join public.modules m on m.id = k.module_id
            join public.enrollments e on e.class_id = k.id
            where u.lesson_id = l.id and m.published and e.student_id = auth.uid() and e.status = 'active'))))
$$;

create or replace function public.can_manage_quiz(p_quiz uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.quizzes q
    where q.id = p_quiz and (
      public.is_admin()
      or (q.class_id is not null and public.is_class_professor(q.class_id))
      or (q.class_id is null and public.can_manage_module(q.module_id))))
$$;

create or replace function public.can_view_quiz(p_quiz uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.quizzes q
    where q.id = p_quiz and (
      public.can_manage_quiz(q.id)
      or (q.published and exists (
            select 1 from public.enrollments e
            join public.classes k on k.id = e.class_id
            join public.modules m on m.id = k.module_id
            where e.student_id = auth.uid() and e.status = 'active' and m.published and k.module_id = q.module_id
              and (q.class_id = k.id
                   or (q.class_id is null and exists (select 1 from public.class_quiz_unlocks u
                                                      where u.class_id = k.id and u.quiz_id = q.id)))))))
$$;

-- Students read questions of a practice quiz they can see, or of a graded quiz once they hold an attempt.
create or replace function public.can_view_quiz_questions(p_quiz uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select public.can_manage_quiz(p_quiz)
      or exists (
        select 1 from public.quizzes q
        where q.id = p_quiz and public.can_view_quiz(q.id)
          and (q.kind = 'practice'
               or exists (select 1 from public.quiz_attempts a where a.quiz_id = q.id and a.student_id = auth.uid())))
$$;

create or replace function public.can_read_pdf(p_name text) returns boolean
language sql stable security definer set search_path = '' as $$
  select public.can_manage_pdf_folder(p_name)
      or exists (select 1 from public.lessons l where l.pdf_path = p_name and public.can_view_lesson(l.id))
$$;

-- ---------------------------------------------------------------------------
-- Row level security
-- ---------------------------------------------------------------------------
drop policy if exists classes_insert on public.classes;
create policy classes_insert on public.classes for insert to authenticated
  with check (public.is_admin());  -- professors use professor_create_class

drop policy if exists modules_insert on public.modules;
create policy modules_insert on public.modules for insert to authenticated
  with check (public.is_admin());
drop policy if exists modules_update on public.modules;
create policy modules_update on public.modules for update to authenticated
  using (public.can_manage_module(id)) with check (public.can_manage_module(id));

drop policy if exists lessons_select on public.lessons;
create policy lessons_select on public.lessons for select to authenticated
  using (public.can_view_lesson(id));

drop policy if exists lesson_progress_insert on public.lesson_progress;
create policy lesson_progress_insert on public.lesson_progress for insert to authenticated
  with check (student_id = (select auth.uid()) and public.can_view_lesson(lesson_id));

drop policy if exists quizzes_select on public.quizzes;
create policy quizzes_select on public.quizzes for select to authenticated
  using (public.can_view_quiz(id));
drop policy if exists quizzes_insert on public.quizzes;
create policy quizzes_insert on public.quizzes for insert to authenticated
  with check (public.is_admin()
              or (class_id is null and public.can_manage_module(module_id))
              or (class_id is not null and public.is_class_professor(class_id)));
drop policy if exists quizzes_update on public.quizzes;
create policy quizzes_update on public.quizzes for update to authenticated
  using (public.can_manage_quiz(id)) with check (public.can_manage_quiz(id));

drop policy if exists questions_write on public.questions;
create policy questions_write on public.questions for all to authenticated
  using (public.can_manage_quiz(quiz_id)) with check (public.can_manage_quiz(quiz_id));
drop policy if exists question_options_write on public.question_options;
create policy question_options_write on public.question_options for all to authenticated
  using (public.can_manage_quiz(public.question_quiz(question_id)))
  with check (public.can_manage_quiz(public.question_quiz(question_id)));

drop policy if exists class_lesson_unlocks_select on public.class_lesson_unlocks;
create policy class_lesson_unlocks_select on public.class_lesson_unlocks for select to authenticated
  using (public.is_admin() or public.is_class_professor(class_id) or public.is_class_member(class_id));
drop policy if exists class_quiz_unlocks_select on public.class_quiz_unlocks;
create policy class_quiz_unlocks_select on public.class_quiz_unlocks for select to authenticated
  using (public.is_admin() or public.is_class_professor(class_id) or public.is_class_member(class_id));

-- ---------------------------------------------------------------------------
-- Quiz RPCs that checked module visibility now check quiz visibility
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
  if not found or not v_quiz.published or not public.can_view_quiz(p_quiz_id) then
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

  perform pg_advisory_xact_lock(hashtextextended(p_quiz_id::text || auth.uid()::text, 0));

  select * into v_open from public.quiz_attempts
  where quiz_id = p_quiz_id and student_id = auth.uid() and status = 'in_progress';
  if found then
    if v_open.deadline_at is null or now() <= v_open.deadline_at then
      return v_open;
    end if;
    perform public.submit_quiz_attempt(v_open.id);
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

create or replace function public.check_practice_answer(p_question_id uuid, p_option_id uuid)
returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_quiz public.quizzes;
  v_key  private.answer_keys;
begin
  select z.* into v_quiz from public.quizzes z join public.questions q on q.quiz_id = z.id where q.id = p_question_id;
  if not found or v_quiz.kind <> 'practice' or not v_quiz.published or not public.can_view_quiz(v_quiz.id) then
    raise exception 'Practice question not available' using errcode = 'P0002';
  end if;
  select * into v_key from private.answer_keys where question_id = p_question_id;
  return jsonb_build_object(
    'correct', v_key.correct_option_id = p_option_id,
    'correct_option_id', v_key.correct_option_id,
    'explanation', v_key.explanation);
end $$;

create or replace function public.set_quiz_review_policy(p_quiz_id uuid, p_policy text)
returns public.quizzes
language plpgsql security definer set search_path = '' as $$
declare v_row public.quizzes;
begin
  if not public.can_manage_quiz(p_quiz_id) then
    raise exception 'Not permitted' using errcode = '42501';
  end if;
  update public.quizzes set review_policy = p_policy where id = p_quiz_id returning * into v_row;
  perform private.audit('quiz.review_policy', 'quiz', p_quiz_id::text, jsonb_build_object('policy', p_policy));
  return v_row;
end $$;

create or replace function public.professor_set_quiz_published(p_quiz_id uuid, p_published boolean)
returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not public.can_manage_quiz(p_quiz_id) then
    raise exception 'Not permitted' using errcode = '42501';
  end if;
  update public.quizzes set published = p_published where id = p_quiz_id;
  perform private.audit(case when p_published then 'quiz.publish' else 'quiz.unpublish' end, 'quiz', p_quiz_id::text, '{}'::jsonb);
end $$;

-- ---------------------------------------------------------------------------
-- Subject class RPCs
-- ---------------------------------------------------------------------------
-- The seven subjects a professor can open a class for, with the caller's class for each (if any).
create or replace function public.list_subjects()
returns table (module_id uuid, "position" int, slug text, title text, summary text, lesson_count int, class_id uuid)
language plpgsql stable security definer set search_path = '' as $$
begin
  if public.my_role() not in ('professor', 'admin') then
    raise exception 'Only professors can open subject classes' using errcode = '42501';
  end if;
  return query
    select m.id, m.position, m.slug, m.title, m.summary,
           (select count(*)::int from public.lessons l where l.module_id = m.id and l.published),
           (select k.id from public.classes k where k.module_id = m.id and k.professor_id = auth.uid())
    from public.modules m
    where m.published and m.created_by is null
    order by m.position;
end $$;

create or replace function public.professor_create_class(p_module_id uuid, p_section text default null, p_term text default null)
returns public.classes
language plpgsql security definer set search_path = '' as $$
declare
  v_module public.modules;
  v_row    public.classes;
  v_alpha  text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';  -- no 0/O or 1/I/L
  v_code   text;
  v_name   text;
begin
  if public.my_role() is distinct from 'professor' then
    raise exception 'Only professors can open subject classes' using errcode = '42501';
  end if;
  select * into v_module from public.modules where id = p_module_id and published and created_by is null;
  if not found then raise exception 'Choose one of the forensic subjects' using errcode = 'P0002'; end if;
  if exists (select 1 from public.classes where professor_id = auth.uid() and module_id = p_module_id) then
    raise exception 'You already have a class for %', v_module.title using errcode = '23505';
  end if;
  if (select count(*) from public.classes where professor_id = auth.uid()) >= 7 then
    raise exception 'You can open at most seven subject classes' using errcode = '22023';
  end if;
  loop
    v_code := '';
    for i in 1 .. 6 loop
      v_code := v_code || substr(v_alpha, 1 + floor(random() * length(v_alpha))::int, 1);
    end loop;
    exit when not exists (select 1 from public.classes where upper(join_code) = v_code);
  end loop;
  v_name := v_module.title || coalesce(' · ' || nullif(btrim(p_section), ''), '');
  insert into public.classes (course_id, module_id, professor_id, name, term, join_code, join_open)
  values (v_module.course_id, v_module.id, auth.uid(), left(v_name, 120), nullif(btrim(coalesce(p_term, '')), ''), v_code, true)
  returning * into v_row;
  perform private.audit('class.create', 'class', v_row.id::text, jsonb_build_object('subject', v_module.title, 'code', v_code));
  return v_row;
end $$;

create or replace function public.professor_delete_class(p_class_id uuid)
returns void
language plpgsql security definer set search_path = '' as $$
declare v_name text;
begin
  if not (public.is_admin() or public.is_class_professor(p_class_id)) then
    raise exception 'Not permitted' using errcode = '42501';
  end if;
  delete from public.classes where id = p_class_id returning name into v_name;
  perform private.audit('class.delete', 'class', p_class_id::text, jsonb_build_object('name', v_name));
end $$;

create or replace function public.professor_set_lesson_unlocked(p_class_id uuid, p_lesson_id uuid, p_unlocked boolean)
returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not (public.is_admin() or public.is_class_professor(p_class_id)) then
    raise exception 'Not permitted' using errcode = '42501';
  end if;
  if not exists (select 1 from public.lessons l join public.classes k on k.module_id = l.module_id
                 where l.id = p_lesson_id and k.id = p_class_id) then
    raise exception 'That lesson is not part of this subject' using errcode = '22023';
  end if;
  if p_unlocked then
    insert into public.class_lesson_unlocks (class_id, lesson_id) values (p_class_id, p_lesson_id) on conflict do nothing;
  else
    delete from public.class_lesson_unlocks where class_id = p_class_id and lesson_id = p_lesson_id;
  end if;
end $$;

create or replace function public.professor_set_all_lessons_unlocked(p_class_id uuid, p_unlocked boolean)
returns int
language plpgsql security definer set search_path = '' as $$
declare v_count int;
begin
  if not (public.is_admin() or public.is_class_professor(p_class_id)) then
    raise exception 'Not permitted' using errcode = '42501';
  end if;
  if p_unlocked then
    insert into public.class_lesson_unlocks (class_id, lesson_id)
    select p_class_id, l.id from public.lessons l join public.classes k on k.module_id = l.module_id
    where k.id = p_class_id and l.published
    on conflict do nothing;
  else
    delete from public.class_lesson_unlocks where class_id = p_class_id;
  end if;
  select count(*) into v_count from public.class_lesson_unlocks where class_id = p_class_id;
  perform private.audit(case when p_unlocked then 'class.unlock_all' else 'class.lock_all' end, 'class', p_class_id::text, '{}'::jsonb);
  return v_count;
end $$;

create or replace function public.professor_set_quiz_unlocked(p_class_id uuid, p_quiz_id uuid, p_unlocked boolean)
returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not (public.is_admin() or public.is_class_professor(p_class_id)) then
    raise exception 'Not permitted' using errcode = '42501';
  end if;
  if not exists (select 1 from public.quizzes q join public.classes k on k.module_id = q.module_id
                 where q.id = p_quiz_id and k.id = p_class_id and q.class_id is null) then
    raise exception 'That quiz is not a shared quiz of this subject' using errcode = '22023';
  end if;
  if p_unlocked then
    insert into public.class_quiz_unlocks (class_id, quiz_id) values (p_class_id, p_quiz_id) on conflict do nothing;
  else
    delete from public.class_quiz_unlocks where class_id = p_class_id and quiz_id = p_quiz_id;
  end if;
end $$;

-- Student view: joined subject classes with the professor's name and how much is unlocked.
create or replace function public.my_subject_classes()
returns table (class_id uuid, module_id uuid, name text, term text, professor_name text,
               "position" int, slug text, title text, total_lessons int, unlocked_lessons int, quiz_count int)
language sql stable security definer set search_path = '' as $$
  select k.id, m.id, k.name, k.term,
         coalesce(nullif(p.full_name, ''), 'Your professor'),
         m.position, m.slug, m.title,
         (select count(*)::int from public.lessons l where l.module_id = m.id and l.published),
         (select count(*)::int from public.class_lesson_unlocks u join public.lessons l on l.id = u.lesson_id
           where u.class_id = k.id and l.published),
         (select count(*)::int from public.quizzes q where q.module_id = m.id and q.published
           and (q.class_id = k.id or (q.class_id is null and exists (select 1 from public.class_quiz_unlocks u
                                                                      where u.class_id = k.id and u.quiz_id = q.id))))
  from public.enrollments e
  join public.classes k on k.id = e.class_id
  join public.modules m on m.id = k.module_id
  left join public.profiles p on p.id = k.professor_id
  where e.student_id = auth.uid() and e.status = 'active' and m.published
  order by m.position
$$;

-- Quiz creation and PDF upload learn about classes: a class quiz belongs to that class only, and a
-- PDF uploaded from a class is unlocked in that class straight away.
drop function if exists public.professor_create_quiz(uuid, text, text, int, int, jsonb);
create or replace function public.professor_create_quiz(
  p_module_id uuid, p_title text, p_kind text, p_time_limit_seconds int, p_max_attempts int, p_questions jsonb,
  p_class_id uuid default null)
returns public.quizzes
language plpgsql security definer set search_path = '' as $$
declare
  v_quiz public.quizzes;
  v_module uuid := p_module_id;
  v_title text := btrim(coalesce(p_title, ''));
  v_q jsonb;
  v_qpos int := 0;
  v_question_id uuid;
  v_option_id uuid;
  v_correct_id uuid;
  v_correct int;
  v_opts jsonb;
  v_opos int;
begin
  if p_class_id is not null then
    if not (public.is_admin() or public.is_class_professor(p_class_id)) then
      raise exception 'You can only add quizzes to your own classes' using errcode = '42501';
    end if;
    select module_id into v_module from public.classes where id = p_class_id;
  elsif not public.can_manage_module(p_module_id) then
    raise exception 'You can only add quizzes to subjects you teach' using errcode = '42501';
  end if;
  if length(v_title) < 3 then raise exception 'Enter a quiz title' using errcode = '22023'; end if;
  if p_kind not in ('practice', 'graded') then raise exception 'Quiz type must be practice or graded' using errcode = '22023'; end if;
  if jsonb_typeof(p_questions) <> 'array' or jsonb_array_length(p_questions) = 0 then
    raise exception 'Add at least one question' using errcode = '22023';
  end if;
  if jsonb_array_length(p_questions) > 100 then raise exception 'A quiz can have at most 100 questions' using errcode = '22023'; end if;

  insert into public.quizzes (module_id, class_id, title, kind, time_limit_seconds, max_attempts, published)
  values (v_module, p_class_id, v_title, p_kind,
          case when coalesce(p_time_limit_seconds, 0) > 0 then least(p_time_limit_seconds, 6 * 3600) end,
          case when coalesce(p_max_attempts, 0) > 0 then least(p_max_attempts, 20) end,
          true)
  returning * into v_quiz;

  for v_q in select * from jsonb_array_elements(p_questions) loop
    v_qpos := v_qpos + 1;
    v_opts := v_q -> 'options';
    v_correct := (v_q ->> 'correct')::int;
    if length(btrim(coalesce(v_q ->> 'prompt', ''))) = 0 then
      raise exception 'Question % has no text', v_qpos using errcode = '22023';
    end if;
    if jsonb_typeof(v_opts) <> 'array' or jsonb_array_length(v_opts) < 2 or jsonb_array_length(v_opts) > 8 then
      raise exception 'Question % needs 2 to 8 choices', v_qpos using errcode = '22023';
    end if;
    if v_correct is null or v_correct < 0 or v_correct >= jsonb_array_length(v_opts) then
      raise exception 'Mark the correct answer for question %', v_qpos using errcode = '22023';
    end if;

    insert into public.questions (quiz_id, position, prompt, points)
    values (v_quiz.id, v_qpos, btrim(v_q ->> 'prompt'), greatest(1, least(coalesce((v_q ->> 'points')::int, 1), 100)))
    returning id into v_question_id;

    v_correct_id := null;
    for v_opos in 0 .. jsonb_array_length(v_opts) - 1 loop
      if length(btrim(coalesce(v_opts ->> v_opos, ''))) = 0 then
        raise exception 'Question % has an empty choice', v_qpos using errcode = '22023';
      end if;
      insert into public.question_options (question_id, position, body)
      values (v_question_id, v_opos + 1, btrim(v_opts ->> v_opos))
      returning id into v_option_id;
      if v_opos = v_correct then v_correct_id := v_option_id; end if;
    end loop;

    insert into private.answer_keys (question_id, correct_option_id, explanation)
    values (v_question_id, v_correct_id, btrim(coalesce(v_q ->> 'explanation', '')));
  end loop;

  perform private.audit('quiz.create', 'quiz', v_quiz.id::text,
    jsonb_build_object('title', v_title, 'kind', p_kind, 'questions', v_qpos, 'class_id', p_class_id));
  return v_quiz;
end $$;

drop function if exists public.professor_add_pdf_lesson(uuid, text, text, int);
create or replace function public.professor_add_pdf_lesson(
  p_module_id uuid, p_title text, p_pdf_path text, p_reading_minutes int default 10, p_class_id uuid default null)
returns public.lessons
language plpgsql security definer set search_path = '' as $$
declare
  v_row public.lessons;
  v_title text := btrim(coalesce(p_title, ''));
begin
  if not public.can_manage_module(p_module_id) then
    raise exception 'You can only add lessons to subjects you teach' using errcode = '42501';
  end if;
  if p_class_id is not null and not exists (select 1 from public.classes k where k.id = p_class_id
                                             and k.module_id = p_module_id and k.professor_id = auth.uid()) then
    raise exception 'That class is not yours or is a different subject' using errcode = '42501';
  end if;
  if length(v_title) < 3 then raise exception 'Enter a lesson title' using errcode = '22023'; end if;
  if p_pdf_path is null or split_part(p_pdf_path, '/', 1) <> p_module_id::text or right(lower(p_pdf_path), 4) <> '.pdf' then
    raise exception 'Invalid PDF location' using errcode = '22023';
  end if;
  if not exists (select 1 from storage.objects o where o.bucket_id = 'lesson-pdfs' and o.name = p_pdf_path) then
    raise exception 'The PDF upload did not finish. Please try again.' using errcode = 'P0002';
  end if;
  insert into public.lessons (module_id, position, title, reading_minutes, pdf_path, review_status, published, created_by)
  values (p_module_id,
          coalesce((select max(position) from public.lessons where module_id = p_module_id), 0) + 1,
          v_title, greatest(1, least(coalesce(p_reading_minutes, 10), 240)), p_pdf_path, 'approved', true, auth.uid())
  returning * into v_row;
  if p_class_id is not null then
    insert into public.class_lesson_unlocks (class_id, lesson_id) values (p_class_id, v_row.id) on conflict do nothing;
  end if;
  update public.modules set updated_at = now() where id = p_module_id;
  perform private.audit('lesson.upload_pdf', 'lesson', v_row.id::text, jsonb_build_object('title', v_title, 'path', p_pdf_path));
  return v_row;
end $$;

-- ---------------------------------------------------------------------------
-- Privileges
-- ---------------------------------------------------------------------------
grant select on public.class_lesson_unlocks, public.class_quiz_unlocks to authenticated;
grant all on public.class_lesson_unlocks, public.class_quiz_unlocks to service_role;

do $$
declare f record;
begin
  for f in
    select p.oid::regprocedure as sig
    from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.prokind = 'f'
      and p.proname in ('can_manage_module', 'can_view_module', 'can_view_lesson', 'can_manage_quiz', 'can_view_quiz',
                        'can_view_quiz_questions', 'can_read_pdf', 'create_quiz_attempt', 'check_practice_answer',
                        'set_quiz_review_policy', 'professor_set_quiz_published', 'list_subjects',
                        'professor_create_class', 'professor_delete_class', 'professor_set_lesson_unlocked',
                        'professor_set_all_lessons_unlocked', 'professor_set_quiz_unlocked', 'my_subject_classes',
                        'professor_create_quiz', 'professor_add_pdf_lesson')
  loop
    execute format('revoke execute on function %s from public, anon', f.sig);
    execute format('grant execute on function %s to authenticated', f.sig);
  end loop;
end $$;

notify pgrst, 'reload schema';
