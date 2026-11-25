-- Lesson media added by professors: YouTube videos and images attached to a lesson for ONE class.
--
-- Lessons are shared subject content (every class of a subject reads the same lesson), so media a
-- professor adds belongs to their class, like class quizzes: it never changes another professor's
-- class. Students see the media of the classes they are actively enrolled in, for lessons they can
-- already read. Images live in the private "lesson-media" bucket at <class_id>/<random>.<ext>.

create table if not exists public.lesson_media (
  id          uuid primary key default gen_random_uuid(),
  lesson_id   uuid not null references public.lessons (id) on delete cascade,
  class_id    uuid not null references public.classes (id) on delete cascade,
  kind        text not null check (kind in ('video', 'image')),
  youtube_id  text check (youtube_id ~ '^[A-Za-z0-9_-]{11}$'),
  image_path  text,
  title       text check (char_length(title) <= 200),
  caption     text check (char_length(caption) <= 500),
  position    int not null default 0,
  created_by  uuid references auth.users (id) on delete set null default auth.uid(),
  created_at  timestamptz not null default now(),
  constraint lesson_media_payload check (
    (kind = 'video' and youtube_id is not null and image_path is null)
    or (kind = 'image' and image_path is not null and youtube_id is null)
  ),
  -- The image must sit in this class's folder, so storage access and row access agree.
  constraint lesson_media_image_folder check (image_path is null or image_path like class_id::text || '/%')
);
create index if not exists lesson_media_lesson_class_idx on public.lesson_media (lesson_id, class_id, position);

alter table public.lesson_media enable row level security;

-- The lesson must belong to the class's subject.
create or replace function public.lesson_in_class(p_lesson uuid, p_class uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.lessons l join public.classes k on k.module_id = l.module_id
                 where l.id = p_lesson and k.id = p_class)
$$;

create or replace function public.can_manage_class_media(p_class uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select public.is_admin() or (public.my_role() = 'professor' and public.is_class_professor(p_class))
$$;

create or replace function public.can_view_class_media(p_class uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select public.can_manage_class_media(p_class)
      or exists (select 1 from public.enrollments e
                 where e.class_id = p_class and e.student_id = auth.uid() and e.status = 'active')
$$;

drop policy if exists lesson_media_select on public.lesson_media;
drop policy if exists lesson_media_insert on public.lesson_media;
drop policy if exists lesson_media_update on public.lesson_media;
drop policy if exists lesson_media_delete on public.lesson_media;
create policy lesson_media_select on public.lesson_media for select to authenticated
  using (public.can_view_class_media(class_id) and public.can_view_lesson(lesson_id));
create policy lesson_media_insert on public.lesson_media for insert to authenticated
  with check (public.can_manage_class_media(class_id) and public.lesson_in_class(lesson_id, class_id));
create policy lesson_media_update on public.lesson_media for update to authenticated
  using (public.can_manage_class_media(class_id))
  with check (public.can_manage_class_media(class_id) and public.lesson_in_class(lesson_id, class_id));
create policy lesson_media_delete on public.lesson_media for delete to authenticated
  using (public.can_manage_class_media(class_id));

grant select, insert, update, delete on public.lesson_media to authenticated;
grant all on public.lesson_media to service_role;

-- ---------------------------------------------------------------------------
-- Storage: private bucket, folder = class id.
-- ---------------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('lesson-media', 'lesson-media', false, 5242880, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update set public = false, file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

create or replace function public.lesson_media_folder_class(p_name text) returns uuid
language sql immutable set search_path = '' as $$
  select case when (storage.foldername(p_name))[1] ~ '^[0-9a-f-]{36}$'
              then ((storage.foldername(p_name))[1])::uuid end
$$;

drop policy if exists lesson_media_read on storage.objects;
drop policy if exists lesson_media_write on storage.objects;
drop policy if exists lesson_media_remove on storage.objects;
create policy lesson_media_read on storage.objects for select to authenticated
  using (bucket_id = 'lesson-media' and public.can_view_class_media(public.lesson_media_folder_class(name)));
create policy lesson_media_write on storage.objects for insert to authenticated
  with check (bucket_id = 'lesson-media' and public.can_manage_class_media(public.lesson_media_folder_class(name)));
create policy lesson_media_remove on storage.objects for delete to authenticated
  using (bucket_id = 'lesson-media' and public.can_manage_class_media(public.lesson_media_folder_class(name)));
