-- Class appearance (2026-12): the professor's class color and cover photo now live on the class
-- row instead of on the professor's phone, so enrolled students see the same card.
--   * classes.color: one of the app's ClassColor keys (null = theme default);
--   * classes.cover_path: object in the private "class-covers" bucket at <class_id>/<random>.jpg;
--   * my_subject_classes() returns both so the student's class cards can show them.

alter table public.classes
  add column if not exists color text,
  add column if not exists cover_path text;

alter table public.classes drop constraint if exists classes_color_check;
alter table public.classes add constraint classes_color_check
  check (color is null or color in ('red', 'orange', 'yellow', 'green', 'teal', 'blue', 'purple', 'pink', 'gray'));
alter table public.classes drop constraint if exists classes_cover_path_check;
alter table public.classes add constraint classes_cover_path_check
  check (cover_path is null or (length(cover_path) <= 200 and split_part(cover_path, '/', 1) = id::text));

-- Updates go through classes_update (professor of the class, or admin); only these columns are new.
grant update (color, cover_path) on public.classes to authenticated;

-- ---------------------------------------------------------------------------- storage
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('class-covers', 'class-covers', false, 2097152, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update
  set public = false, file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;

-- The class a cover object belongs to (its folder), or null when the folder isn't a class id.
create or replace function public.class_cover_class(p_name text) returns uuid
language sql immutable set search_path = '' as $$
  select case when (storage.foldername(p_name))[1] ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
              then ((storage.foldername(p_name))[1])::uuid end
$$;

-- Anyone on the class's card may see the cover: its professor, admins, and students who joined
-- or are waiting for approval (they already see the card).
create or replace function public.can_view_class_cover(p_class uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select p_class is not null and (
    public.is_admin()
    or exists (select 1 from public.classes k where k.id = p_class and k.professor_id = (select auth.uid()))
    or exists (select 1 from public.enrollments e
               where e.class_id = p_class and e.student_id = (select auth.uid()) and e.status in ('active', 'pending')))
$$;

create or replace function public.can_manage_class_cover(p_class uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select p_class is not null and (
    public.is_admin()
    or exists (select 1 from public.classes k where k.id = p_class and k.professor_id = (select auth.uid())))
$$;

drop policy if exists class_covers_read on storage.objects;
drop policy if exists class_covers_insert on storage.objects;
drop policy if exists class_covers_delete on storage.objects;
create policy class_covers_read on storage.objects for select to authenticated
  using (bucket_id = 'class-covers' and public.can_view_class_cover(public.class_cover_class(name)));
create policy class_covers_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'class-covers' and public.can_manage_class_cover(public.class_cover_class(name)));
create policy class_covers_delete on storage.objects for delete to authenticated
  using (bucket_id = 'class-covers' and public.can_manage_class_cover(public.class_cover_class(name)));

-- ---------------------------------------------------------------------------- student cards
drop function if exists public.my_subject_classes();
create or replace function public.my_subject_classes()
returns table (class_id uuid, module_id uuid, name text, term text, professor_name text, "position" integer,
               slug text, title text, total_lessons integer, unlocked_lessons integer, quiz_count integer, status text,
               color text, cover_path text)
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
         k.color, k.cover_path
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
      and p.proname in ('my_subject_classes', 'class_cover_class', 'can_view_class_cover', 'can_manage_class_cover')
  loop
    execute format('revoke execute on function %s from public, anon', f.sig);
    execute format('grant execute on function %s to authenticated', f.sig);
  end loop;
end $$;

notify pgrst, 'reload schema';
