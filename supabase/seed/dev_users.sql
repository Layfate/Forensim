-- DEVELOPMENT ONLY. Creates three confirmed test accounts (student/professor/admin), assigns roles,
-- makes the professor own the seed course/class and enrols the student.
-- Replace the :*_password placeholders before running (e.g. in the Supabase SQL editor).
-- Never run against production. Real professors/admins are promoted with public.admin_set_role.
do $$
declare
  r record;
  v_id uuid;
begin
  for r in select * from (values
    ('student@forensim.test',   ':student_password',   'Sam Student',      'student'),
    ('professor@forensim.test', ':professor_password', 'Prof. Dana Reyes', 'professor'),
    ('admin@forensim.test',     ':admin_password',     'Alex Admin',       'admin')) as t(email, pw, name, role)
  loop
    if exists (select 1 from auth.users where email = r.email) then continue; end if;
    v_id := gen_random_uuid();
    insert into auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
      raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
      confirmation_token, email_change, email_change_token_new, recovery_token)
    values ('00000000-0000-0000-0000-000000000000', v_id, 'authenticated', 'authenticated', r.email,
      extensions.crypt(r.pw, extensions.gen_salt('bf')), now(),
      '{"provider":"email","providers":["email"]}', jsonb_build_object('full_name', r.name), now(), now(), '', '', '', '');
    insert into auth.identities (id, user_id, provider_id, identity_data, provider, last_sign_in_at, created_at, updated_at)
    values (gen_random_uuid(), v_id, v_id::text,
      jsonb_build_object('sub', v_id::text, 'email', r.email, 'email_verified', true), 'email', now(), now(), now());
    update public.user_roles set role = r.role::public.app_role where user_id = v_id;
  end loop;

  update public.courses set owner_id = (select id from auth.users where email = 'professor@forensim.test');
  update public.classes set professor_id = (select id from auth.users where email = 'professor@forensim.test')
   where join_code = 'FSCI-3A26';
  insert into public.enrollments (class_id, student_id)
  select k.id, u.id from public.classes k, auth.users u
  where k.join_code = 'FSCI-3A26' and u.email = 'student@forensim.test'
  on conflict do nothing;
end $$;
