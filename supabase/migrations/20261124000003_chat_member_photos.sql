-- Chat profile photos: people who share a class (its professor and approved students) can see
-- each other's profile photo next to chat messages. Nothing else about them is exposed: no
-- e-mail, student ID or gender.

-- True when the signed-in user and p_user are both in some class (as its professor or as an
-- approved student).
create or replace function private.shares_class_with(p_user uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.classes k
    where (k.professor_id = p_user
           or exists (select 1 from public.enrollments e where e.class_id = k.id and e.student_id = p_user and e.status = 'active'))
      and (k.professor_id = (select auth.uid())
           or exists (select 1 from public.enrollments e where e.class_id = k.id and e.student_id = (select auth.uid()) and e.status = 'active'))
  )
$$;

-- Avatars: owner, admins, the student's professors, and now classmates / their professor.
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
  return v_owner = (select auth.uid()) or public.is_admin() or public.teaches_student(v_owner)
      or private.shares_class_with(v_owner);
end $$;

-- Chat members with name and photo path; empty unless the caller can use this class's chat.
create or replace function public.class_chat_members(p_class uuid)
returns table (user_id uuid, full_name text, avatar_path text)
language sql stable security definer set search_path = '' as $$
  select p.id, p.full_name, p.avatar_path
  from public.profiles p
  where public.can_chat(p_class)
    and (p.id = (select k.professor_id from public.classes k where k.id = p_class)
         or exists (select 1 from public.enrollments e where e.class_id = p_class and e.student_id = p.id and e.status = 'active'))
$$;
revoke all on function public.class_chat_members(uuid) from public, anon;
grant execute on function public.class_chat_members(uuid) to authenticated;
