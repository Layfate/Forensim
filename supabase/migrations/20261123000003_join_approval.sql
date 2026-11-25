-- Join approval for subject classes.
-- "Anyone can join" on (default): the class code enrolls a student straight away.
-- Off: the code creates a pending request that the professor approves or declines.

alter table public.classes add column if not exists require_approval boolean not null default false;

alter table public.enrollments drop constraint if exists enrollments_status_check;
alter table public.enrollments add constraint enrollments_status_check check (status in ('active', 'pending', 'dropped'));

-- Pending students are not members yet: they cannot see unlocks or class content.
create or replace function public.is_class_member(p_class uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.enrollments e
                 where e.class_id = p_class and e.student_id = auth.uid() and e.status = 'active')
$$;

-- Students may still read the class row of a class they asked to join (to show "waiting for approval").
drop policy if exists classes_select on public.classes;
create policy classes_select on public.classes for select to authenticated
  using (public.is_admin() or professor_id = (select auth.uid())
         or exists (select 1 from public.enrollments e where e.class_id = classes.id and e.student_id = (select auth.uid())));

create or replace function public.join_class(p_code text)
returns public.enrollments
language plpgsql security definer set search_path = '' as $$
declare
  v_class  public.classes;
  v_row    public.enrollments;
  v_status text;
begin
  if public.my_role() is distinct from 'student' then
    raise exception 'Only active student accounts can join a class' using errcode = '42501';
  end if;
  select * into v_class from public.classes where upper(join_code) = upper(trim(p_code));
  if not found or not v_class.join_open then
    raise exception 'Join code not recognised or enrollment is closed' using errcode = 'P0002';
  end if;
  v_status := case when v_class.require_approval then 'pending' else 'active' end;
  insert into public.enrollments (class_id, student_id, status)
  values (v_class.id, auth.uid(), v_status)
  on conflict (class_id, student_id) do update
    -- Never downgrade a student who is already in; otherwise apply the class rule again.
    set status = case when public.enrollments.status = 'active' then 'active' else excluded.status end
  returning * into v_row;
  return v_row;
end $$;

-- Approve (or decline) one pending student, or every pending student when p_student_id is null.
create or replace function public.professor_review_join(p_class_id uuid, p_student_id uuid, p_approve boolean)
returns int
language plpgsql security definer set search_path = '' as $$
declare v_count int;
begin
  if not (public.is_admin() or public.is_class_professor(p_class_id)) then
    raise exception 'Not permitted' using errcode = '42501';
  end if;
  if p_approve then
    update public.enrollments set status = 'active', enrolled_at = now()
    where class_id = p_class_id and status = 'pending' and (p_student_id is null or student_id = p_student_id);
  else
    delete from public.enrollments
    where class_id = p_class_id and status = 'pending' and (p_student_id is null or student_id = p_student_id);
  end if;
  get diagnostics v_count = row_count;
  perform private.audit(case when p_approve then 'class.approve_join' else 'class.decline_join' end, 'class', p_class_id::text,
                        jsonb_build_object('student_id', p_student_id, 'count', v_count));
  return v_count;
end $$;

-- Student view now also lists classes waiting for approval (status column added).
drop function if exists public.my_subject_classes();
create or replace function public.my_subject_classes()
returns table (class_id uuid, module_id uuid, name text, term text, professor_name text,
               "position" int, slug text, title text, total_lessons int, unlocked_lessons int, quiz_count int, status text)
language sql stable security definer set search_path = '' as $$
  select k.id, m.id, k.name, k.term,
         coalesce(nullif(p.full_name, ''), 'Your professor'),
         m.position, m.slug, m.title,
         (select count(*)::int from public.lessons l where l.module_id = m.id and l.published),
         case when e.status = 'active' then
           (select count(*)::int from public.class_lesson_unlocks u join public.lessons l on l.id = u.lesson_id
             where u.class_id = k.id and l.published) else 0 end,
         case when e.status = 'active' then
           (select count(*)::int from public.quizzes q where q.module_id = m.id and q.published
             and (q.class_id = k.id or (q.class_id is null and exists (select 1 from public.class_quiz_unlocks u
                                                                        where u.class_id = k.id and u.quiz_id = q.id)))) else 0 end,
         e.status
  from public.enrollments e
  join public.classes k on k.id = e.class_id
  join public.modules m on m.id = k.module_id
  left join public.profiles p on p.id = k.professor_id
  where e.student_id = auth.uid() and e.status in ('active', 'pending') and m.published
  order by (e.status = 'pending'), m.position
$$;

do $$
declare f record;
begin
  for f in
    select p.oid::regprocedure as sig
    from pg_proc p join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public' and p.prokind = 'f'
      and p.proname in ('is_class_member', 'join_class', 'professor_review_join', 'my_subject_classes')
  loop
    execute format('revoke execute on function %s from public, anon', f.sig);
    execute format('grant execute on function %s to authenticated', f.sig);
  end loop;
end $$;

notify pgrst, 'reload schema';
