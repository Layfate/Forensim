-- Students see their class's join code on their class cards (2026-11), so they can find it again
-- (e.g. to tell a classmate). my_subject_classes() gains a join_code column; everything else is the
-- same as 20261201000001_class_appearance.sql. Students only get the code of classes they joined or
-- asked to join, which they typed in themselves.

drop function if exists public.my_subject_classes();
create or replace function public.my_subject_classes()
returns table (class_id uuid, module_id uuid, name text, term text, professor_name text, "position" integer,
               slug text, title text, total_lessons integer, unlocked_lessons integer, quiz_count integer, status text,
               color text, cover_path text, join_code text)
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
         e.status,
         k.color, k.cover_path,
         k.join_code
  from public.enrollments e
  join public.classes k on k.id = e.class_id
  join public.modules m on m.id = k.module_id
  left join public.profiles p on p.id = k.professor_id
  where e.student_id = auth.uid() and e.status in ('active', 'pending') and m.published
  order by (e.status = 'pending'), m.position
$$;

revoke execute on function public.my_subject_classes() from public, anon;
grant execute on function public.my_subject_classes() to authenticated;

notify pgrst, 'reload schema';
