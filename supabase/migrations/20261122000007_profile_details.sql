-- Student-editable profile details: photo, gender and student ID.
-- E-mail and password changes go through Supabase Auth (confirmation / reset e-mails); the
-- profile's e-mail column follows auth.users so staff screens stay correct after a change.

alter table public.profiles
  add column if not exists gender text
    check (gender in ('female', 'male', 'non_binary', 'prefer_not_to_say')),
  add column if not exists student_id text
    check (student_id is null or char_length(student_id) between 1 and 32),
  add column if not exists avatar_path text;

-- Column-level grant: users may edit only these columns of their own row (RLS limits the row).
grant update (full_name, institution, gender, student_id, avatar_path, updated_at) on public.profiles to authenticated;

-- Keep profiles.email in step with the confirmed auth e-mail.
create or replace function private.handle_user_email_change() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if new.email is distinct from old.email then
    update public.profiles set email = new.email, updated_at = now() where id = new.id;
  end if;
  return new;
end $$;

drop trigger if exists on_auth_user_email_changed on auth.users;
create trigger on_auth_user_email_changed
  after update of email on auth.users
  for each row execute function private.handle_user_email_change();

-- ---------------------------------------------------------------------------
-- Avatars: private bucket, objects at <user_id>/<random>.jpg
-- ---------------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('avatars', 'avatars', false, 2097152, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update
  set public = false, file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;

-- Owner, admins and the student's professors may view an avatar.
create or replace function public.can_read_avatar(p_name text) returns boolean
language plpgsql stable security definer set search_path = '' as $$
declare
  v_owner uuid;
begin
  begin
    v_owner := ((storage.foldername(p_name))[1])::uuid;
  exception when others then
    return false;
  end;
  return v_owner = (select auth.uid()) or public.is_admin() or public.teaches_student(v_owner);
end $$;

-- Only signed-in users need it (storage policies below); never callable anonymously.
revoke execute on function public.can_read_avatar(text) from public, anon;
grant execute on function public.can_read_avatar(text) to authenticated;

drop policy if exists avatars_read on storage.objects;
drop policy if exists avatars_insert on storage.objects;
drop policy if exists avatars_delete on storage.objects;
create policy avatars_read on storage.objects for select to authenticated
  using (bucket_id = 'avatars' and public.can_read_avatar(name));
create policy avatars_insert on storage.objects for insert to authenticated
  with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);
create policy avatars_delete on storage.objects for delete to authenticated
  using (bucket_id = 'avatars' and (storage.foldername(name))[1] = (select auth.uid())::text);
