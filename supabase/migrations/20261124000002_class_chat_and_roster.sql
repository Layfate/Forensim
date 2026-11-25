-- Class chat (real time) and removing students from a class.
--
-- Chat: one conversation per subject class between the professor and the class's approved
-- students. Messages are pushed live through Supabase Realtime (the table is in the
-- supabase_realtime publication, and Realtime applies these RLS policies per subscriber).
-- Sender name and role are stamped by a trigger, so they can't be faked by the client.

create table if not exists public.class_messages (
  id          bigint generated always as identity primary key,
  class_id    uuid not null references public.classes (id) on delete cascade,
  sender_id   uuid not null default auth.uid() references auth.users (id) on delete cascade,
  sender_name text not null default '',
  sender_role text not null default 'student' check (sender_role in ('student', 'professor', 'admin')),
  body        text not null check (char_length(btrim(body)) between 1 and 2000),
  created_at  timestamptz not null default now()
);
create index if not exists class_messages_class_idx on public.class_messages (class_id, id desc);
alter table public.class_messages enable row level security;

-- Professor of the class, an admin, or an approved (active) student of the class.
create or replace function public.can_chat(p_class uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select public.is_admin() or public.is_class_professor(p_class)
      or exists (select 1 from public.enrollments e where e.class_id = p_class and e.student_id = auth.uid() and e.status = 'active')
$$;

create or replace function private.stamp_class_message() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  new.sender_id := auth.uid();
  new.sender_role := coalesce(public.my_role()::text, 'student');
  new.sender_name := coalesce(
    (select nullif(btrim(p.full_name), '') from public.profiles p where p.id = auth.uid()),
    (select split_part(u.email, '@', 1) from auth.users u where u.id = auth.uid()),
    'Member');
  new.body := btrim(new.body);
  new.created_at := now();
  return new;
end $$;

drop trigger if exists class_messages_stamp on public.class_messages;
create trigger class_messages_stamp before insert on public.class_messages
  for each row execute function private.stamp_class_message();

drop policy if exists class_messages_select on public.class_messages;
drop policy if exists class_messages_insert on public.class_messages;
drop policy if exists class_messages_delete on public.class_messages;
create policy class_messages_select on public.class_messages for select to authenticated
  using (public.can_chat(class_id));
create policy class_messages_insert on public.class_messages for insert to authenticated
  with check (public.can_chat(class_id));
-- Authors can delete their own messages; the class professor (and admins) can moderate.
create policy class_messages_delete on public.class_messages for delete to authenticated
  using (sender_id = (select auth.uid()) or public.is_class_professor(class_id) or public.is_admin());

grant select, insert, delete on public.class_messages to authenticated;
grant all on public.class_messages to service_role;

do $$
begin
  if not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = 'class_messages') then
    alter publication supabase_realtime add table public.class_messages;
  end if;
end $$;

-- ---------------------------------------------------------------------------
-- Remove an approved student from a class (they lose access; past results are kept).
-- ---------------------------------------------------------------------------
create or replace function public.professor_remove_student(p_class_id uuid, p_student_id uuid)
returns int
language plpgsql security definer set search_path = '' as $$
declare v_count int;
begin
  if not (public.is_admin() or public.is_class_professor(p_class_id)) then
    raise exception 'Not permitted' using errcode = '42501';
  end if;
  delete from public.enrollments where class_id = p_class_id and student_id = p_student_id;
  get diagnostics v_count = row_count;
  perform private.audit('class.remove_student', 'class', p_class_id::text, jsonb_build_object('student_id', p_student_id));
  return v_count;
end $$;
revoke all on function public.professor_remove_student(uuid, uuid) from public, anon;
grant execute on function public.professor_remove_student(uuid, uuid) to authenticated;
