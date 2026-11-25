-- Professors publish their own teaching material: PDF modules and quizzes go live immediately,
-- with no administrator approval step. Admins manage accounts; professors manage content.

-- ---------------------------------------------------------------------------
-- PDF lessons
-- ---------------------------------------------------------------------------
alter table public.lessons add column if not exists pdf_path text;
alter table public.lessons add column if not exists created_by uuid references auth.users (id) on delete set null;
alter table public.modules add column if not exists created_by uuid references auth.users (id) on delete set null;

-- Private bucket. Objects live at <module_id>/<random>.pdf, so access follows module access.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('lesson-pdfs', 'lesson-pdfs', false, 26214400, array['application/pdf'])
on conflict (id) do update
  set public = false, file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;

create or replace function public.can_manage_pdf_folder(p_name text) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.modules m
    where m.id::text = (storage.foldername(p_name))[1] and public.can_manage_module(m.id))
$$;

create or replace function public.can_read_pdf(p_name text) returns boolean
language sql stable security definer set search_path = '' as $$
  select public.can_manage_pdf_folder(p_name)
      or exists (select 1 from public.lessons l
                 where l.pdf_path = p_name and l.published and public.can_view_module(l.module_id))
$$;

drop policy if exists lesson_pdfs_read on storage.objects;
drop policy if exists lesson_pdfs_insert on storage.objects;
drop policy if exists lesson_pdfs_delete on storage.objects;
create policy lesson_pdfs_read on storage.objects for select to authenticated
  using (bucket_id = 'lesson-pdfs' and public.can_read_pdf(name));
create policy lesson_pdfs_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'lesson-pdfs' and public.can_manage_pdf_folder(name));
create policy lesson_pdfs_delete on storage.objects for delete to authenticated
  using (bucket_id = 'lesson-pdfs' and public.can_manage_pdf_folder(name));

-- ---------------------------------------------------------------------------
-- Professor publishing RPCs (audited)
-- ---------------------------------------------------------------------------

-- New module in a course the caller teaches. Published straight away.
create or replace function public.professor_create_module(p_course_id uuid, p_title text, p_summary text default '')
returns public.modules
language plpgsql security definer set search_path = '' as $$
declare
  v_row public.modules;
  v_title text := btrim(coalesce(p_title, ''));
  v_slug text;
begin
  if not (public.is_admin() or public.teaches_course(p_course_id)) then
    raise exception 'You can only add modules to courses you teach' using errcode = '42501';
  end if;
  if length(v_title) < 3 then raise exception 'Enter a module title' using errcode = '22023'; end if;
  v_slug := trim(both '-' from regexp_replace(lower(v_title), '[^a-z0-9]+', '-', 'g'));
  v_slug := left(coalesce(nullif(v_slug, ''), 'module'), 48) || '-' || substr(md5(gen_random_uuid()::text), 1, 6);
  insert into public.modules (course_id, position, slug, title, summary, published, created_by)
  values (p_course_id,
          coalesce((select max(position) from public.modules where course_id = p_course_id), 0) + 1,
          v_slug, v_title, btrim(coalesce(p_summary, '')), true, auth.uid())
  returning * into v_row;
  perform private.audit('module.create', 'module', v_row.id::text, jsonb_build_object('title', v_title));
  return v_row;
end $$;

-- Attach an uploaded PDF to a module as a lesson. The file must already be in the module's folder.
create or replace function public.professor_add_pdf_lesson(p_module_id uuid, p_title text, p_pdf_path text, p_reading_minutes int default 10)
returns public.lessons
language plpgsql security definer set search_path = '' as $$
declare
  v_row public.lessons;
  v_title text := btrim(coalesce(p_title, ''));
begin
  if not public.can_manage_module(p_module_id) then
    raise exception 'You can only add lessons to modules you teach' using errcode = '42501';
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
  update public.modules set updated_at = now() where id = p_module_id;
  perform private.audit('lesson.upload_pdf', 'lesson', v_row.id::text, jsonb_build_object('title', v_title, 'path', p_pdf_path));
  return v_row;
end $$;

-- Create a multiple-choice quiz with its answer key in one transaction. Answer keys go to the
-- private schema, so this must run as SECURITY DEFINER.
-- p_questions: [{"prompt": text, "options": [text, ...], "correct": int (0-based),
--                "explanation": text, "points": int}]
create or replace function public.professor_create_quiz(
  p_module_id uuid, p_title text, p_kind text, p_time_limit_seconds int, p_max_attempts int, p_questions jsonb)
returns public.quizzes
language plpgsql security definer set search_path = '' as $$
declare
  v_quiz public.quizzes;
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
  if not public.can_manage_module(p_module_id) then
    raise exception 'You can only add quizzes to modules you teach' using errcode = '42501';
  end if;
  if length(v_title) < 3 then raise exception 'Enter a quiz title' using errcode = '22023'; end if;
  if p_kind not in ('practice', 'graded') then raise exception 'Quiz type must be practice or graded' using errcode = '22023'; end if;
  if jsonb_typeof(p_questions) <> 'array' or jsonb_array_length(p_questions) = 0 then
    raise exception 'Add at least one question' using errcode = '22023';
  end if;
  if jsonb_array_length(p_questions) > 100 then raise exception 'A quiz can have at most 100 questions' using errcode = '22023'; end if;

  insert into public.quizzes (module_id, title, kind, time_limit_seconds, max_attempts, published)
  values (p_module_id, v_title, p_kind,
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
    jsonb_build_object('title', v_title, 'kind', p_kind, 'questions', v_qpos));
  return v_quiz;
end $$;

-- Show or hide a lesson or quiz without deleting student work.
create or replace function public.professor_set_lesson_published(p_lesson_id uuid, p_published boolean)
returns void
language plpgsql security definer set search_path = '' as $$
declare v_module uuid;
begin
  select module_id into v_module from public.lessons where id = p_lesson_id;
  if v_module is null or not public.can_manage_module(v_module) then
    raise exception 'Not permitted' using errcode = '42501';
  end if;
  update public.lessons set published = p_published, updated_at = now() where id = p_lesson_id;
  perform private.audit(case when p_published then 'lesson.publish' else 'lesson.unpublish' end, 'lesson', p_lesson_id::text, '{}'::jsonb);
end $$;

create or replace function public.professor_set_quiz_published(p_quiz_id uuid, p_published boolean)
returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not public.can_manage_module(public.quiz_module(p_quiz_id)) then
    raise exception 'Not permitted' using errcode = '42501';
  end if;
  update public.quizzes set published = p_published where id = p_quiz_id;
  perform private.audit(case when p_published then 'quiz.publish' else 'quiz.unpublish' end, 'quiz', p_quiz_id::text, '{}'::jsonb);
end $$;

-- ---------------------------------------------------------------------------
-- Admin account management helper (used by the admin-users Edge Function)
-- ---------------------------------------------------------------------------
create or replace function public.service_audit(p_actor uuid, p_action text, p_type text, p_id text, p_details jsonb)
returns void language sql security definer set search_path = '' as $$
  insert into public.audit_logs (actor_id, action, object_type, object_id, details)
  values (p_actor, p_action, p_type, p_id, coalesce(p_details, '{}'::jsonb))
$$;

-- ---------------------------------------------------------------------------
-- Privileges
-- ---------------------------------------------------------------------------
revoke execute on function public.professor_create_module(uuid, text, text) from public, anon;
revoke execute on function public.professor_add_pdf_lesson(uuid, text, text, int) from public, anon;
revoke execute on function public.professor_create_quiz(uuid, text, text, int, int, jsonb) from public, anon;
revoke execute on function public.professor_set_lesson_published(uuid, boolean) from public, anon;
revoke execute on function public.professor_set_quiz_published(uuid, boolean) from public, anon;
grant execute on function public.professor_create_module(uuid, text, text) to authenticated;
grant execute on function public.professor_add_pdf_lesson(uuid, text, text, int) to authenticated;
grant execute on function public.professor_create_quiz(uuid, text, text, int, int, jsonb) to authenticated;
grant execute on function public.professor_set_lesson_published(uuid, boolean) to authenticated;
grant execute on function public.professor_set_quiz_published(uuid, boolean) to authenticated;

revoke execute on function public.service_audit(uuid, text, text, text, jsonb) from public, anon, authenticated;
grant execute on function public.service_audit(uuid, text, text, text, jsonb) to service_role;
