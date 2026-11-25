-- Forensim core schema (plan section 8).
-- Every public table has RLS enabled with default deny; access is granted by explicit policies.
-- Answer keys and simulation rubrics live in the `private` schema, which PostgREST does not expose.

create schema if not exists private;
revoke all on schema private from public;
revoke all on schema private from anon, authenticated;

create type public.app_role as enum ('student', 'professor', 'admin');

-- ---------------------------------------------------------------------------
-- Identity
-- ---------------------------------------------------------------------------
create table public.profiles (
  id          uuid primary key references auth.users (id) on delete cascade,
  full_name   text not null default '',
  email       text,
  institution text,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);

-- One role per user. Changed only through public.admin_set_role (audited).
create table public.user_roles (
  user_id    uuid primary key references auth.users (id) on delete cascade,
  role       public.app_role not null default 'student',
  status     text not null default 'active' check (status in ('active', 'suspended')),
  updated_at timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- LMS
-- ---------------------------------------------------------------------------
create table public.courses (
  id          uuid primary key default gen_random_uuid(),
  title       text not null,
  description text not null default '',
  institution text,
  owner_id    uuid references auth.users (id) on delete set null,
  state       text not null default 'active' check (state in ('draft', 'active', 'archived')),
  created_at  timestamptz not null default now()
);

create table public.classes (
  id           uuid primary key default gen_random_uuid(),
  course_id    uuid not null references public.courses (id) on delete cascade,
  professor_id uuid references auth.users (id) on delete set null,
  name         text not null,
  term         text,
  join_code    text not null unique,
  join_open    boolean not null default true,
  created_at   timestamptz not null default now()
);
create index classes_course_idx on public.classes (course_id);
create index classes_professor_idx on public.classes (professor_id);

create table public.enrollments (
  class_id    uuid not null references public.classes (id) on delete cascade,
  student_id  uuid not null references auth.users (id) on delete cascade,
  status      text not null default 'active' check (status in ('active', 'dropped')),
  enrolled_at timestamptz not null default now(),
  primary key (class_id, student_id)
);
create index enrollments_student_idx on public.enrollments (student_id);

create table public.modules (
  id         uuid primary key default gen_random_uuid(),
  course_id  uuid not null references public.courses (id) on delete cascade,
  position   int not null,
  slug       text not null,
  title      text not null,
  summary    text not null default '',
  companion  text not null default '',  -- 3D/AR companion activity description
  published  boolean not null default false,
  updated_at timestamptz not null default now(),
  unique (course_id, slug)
);

create table public.lessons (
  id              uuid primary key default gen_random_uuid(),
  module_id       uuid not null references public.modules (id) on delete cascade,
  position        int not null,
  title           text not null,
  reading_minutes int not null default 5,
  sections        jsonb not null default '[]'::jsonb,  -- [{heading, body}]
  key_terms       jsonb not null default '[]'::jsonb,  -- [{term, definition}]
  citations       jsonb not null default '[]'::jsonb,  -- [text]
  version         int not null default 1,
  review_status   text not null default 'pending_review' check (review_status in ('pending_review', 'approved')),
  published       boolean not null default false,
  updated_at      timestamptz not null default now()
);
create index lessons_module_idx on public.lessons (module_id);

create table public.lesson_progress (
  student_id   uuid not null references auth.users (id) on delete cascade default auth.uid(),
  lesson_id    uuid not null references public.lessons (id) on delete cascade,
  completed_at timestamptz not null default now(),
  primary key (student_id, lesson_id)
);

-- ---------------------------------------------------------------------------
-- Assessment
-- ---------------------------------------------------------------------------
create table public.quizzes (
  id                 uuid primary key default gen_random_uuid(),
  module_id          uuid not null references public.modules (id) on delete cascade,
  title              text not null,
  kind               text not null check (kind in ('practice', 'graded')),
  time_limit_seconds int,
  max_attempts       int,
  available_from     timestamptz,
  available_until    timestamptz,
  review_policy      text not null default 'after_submit' check (review_policy in ('after_submit', 'after_close', 'never')),
  published          boolean not null default false,
  created_at         timestamptz not null default now()
);
create index quizzes_module_idx on public.quizzes (module_id);

create table public.questions (
  id        uuid primary key default gen_random_uuid(),
  quiz_id   uuid not null references public.quizzes (id) on delete cascade,
  position  int not null,
  prompt    text not null,
  objective text not null default '',
  points    int not null default 1
);
create index questions_quiz_idx on public.questions (quiz_id);

create table public.question_options (
  id          uuid primary key default gen_random_uuid(),
  question_id uuid not null references public.questions (id) on delete cascade,
  position    int not null,
  body        text not null
);
create index question_options_question_idx on public.question_options (question_id);

-- SERVER ONLY. Never granted to anon/authenticated.
create table private.answer_keys (
  question_id       uuid primary key references public.questions (id) on delete cascade,
  correct_option_id uuid not null references public.question_options (id) on delete cascade,
  explanation       text not null default ''
);

create table public.quiz_attempts (
  id             uuid primary key default gen_random_uuid(),
  quiz_id        uuid not null references public.quizzes (id) on delete cascade,
  student_id     uuid not null references auth.users (id) on delete cascade,
  attempt_number int not null,
  status         text not null default 'in_progress' check (status in ('in_progress', 'submitted')),
  started_at     timestamptz not null default now(),
  deadline_at    timestamptz,
  submitted_at   timestamptz,
  late           boolean not null default false,
  score          numeric,
  max_score      numeric,
  unique (quiz_id, student_id, attempt_number)
);
create unique index quiz_attempts_one_open on public.quiz_attempts (quiz_id, student_id) where status = 'in_progress';
create index quiz_attempts_student_idx on public.quiz_attempts (student_id);

create table public.quiz_responses (
  attempt_id  uuid not null references public.quiz_attempts (id) on delete cascade,
  question_id uuid not null references public.questions (id) on delete cascade,
  option_id   uuid not null references public.question_options (id) on delete cascade,
  saved_at    timestamptz not null default now(),
  primary key (attempt_id, question_id)
);

-- ---------------------------------------------------------------------------
-- Simulation
-- ---------------------------------------------------------------------------
create table public.simulation_scenarios (
  id          text primary key,
  title       text not null,
  summary     text not null default '',
  kind        text not null check (kind in ('tutorial', 'practice', 'assessment')),
  module_slug text,
  est_minutes int not null default 10,
  template    jsonb not null,  -- public layout + evidence pool; contains no scoring secrets
  version     int not null default 1,
  published   boolean not null default false
);

-- SERVER ONLY scoring rubric per scenario version.
create table private.simulation_rubrics (
  scenario_id text not null references public.simulation_scenarios (id) on delete cascade,
  version     int not null,
  rubric      jsonb not null,
  primary key (scenario_id, version)
);

create table public.simulation_attempts (
  id                uuid primary key default gen_random_uuid(),
  scenario_id       text not null references public.simulation_scenarios (id),
  scenario_version  int not null,
  student_id        uuid not null references auth.users (id) on delete cascade,
  mode              text not null check (mode in ('tutorial', 'practice', 'assessment')),
  seed              int not null,
  manifest          jsonb not null,  -- server-chosen evidence list [{id, type}]
  status            text not null default 'in_progress' check (status in ('in_progress', 'scored')),
  started_at        timestamptz not null default now(),
  submitted_at      timestamptz,
  score             numeric,
  max_score         numeric,
  feedback          jsonb,
  feedback_released boolean not null default false
);
create index simulation_attempts_student_idx on public.simulation_attempts (student_id);

create table public.simulation_events (
  attempt_id  uuid not null references public.simulation_attempts (id) on delete cascade,
  seq         int not null,
  action      text not null,
  evidence_id text,
  tool        text,
  payload     jsonb not null default '{}'::jsonb,
  client_ts   timestamptz,
  received_at timestamptz not null default now(),
  primary key (attempt_id, seq)
);

-- ---------------------------------------------------------------------------
-- AI, audit, settings
-- ---------------------------------------------------------------------------
create table public.ai_usage (
  id         bigint generated always as identity primary key,
  user_id    uuid not null references auth.users (id) on delete cascade,
  mode       text not null,
  blocked    boolean not null default false,
  reason     text,
  tokens_in  int,
  tokens_out int,
  created_at timestamptz not null default now()
);
create index ai_usage_user_time_idx on public.ai_usage (user_id, created_at desc);

create table public.audit_logs (
  id          bigint generated always as identity primary key,
  actor_id    uuid references auth.users (id) on delete set null,
  action      text not null,
  object_type text not null,
  object_id   text,
  details     jsonb not null default '{}'::jsonb,
  created_at  timestamptz not null default now()
);
create index audit_logs_time_idx on public.audit_logs (created_at desc);

create table public.system_settings (
  key        text primary key,
  value      jsonb not null,
  updated_at timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- Authorization helpers (SECURITY DEFINER to avoid RLS recursion; read-only)
-- ---------------------------------------------------------------------------
create or replace function public.my_role() returns public.app_role
language sql stable security definer set search_path = '' as $$
  select r.role from public.user_roles r where r.user_id = auth.uid() and r.status = 'active'
$$;

create or replace function public.is_admin() returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.user_roles r
                 where r.user_id = auth.uid() and r.role = 'admin' and r.status = 'active')
$$;

create or replace function public.teaches_course(p_course uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.user_roles r
    where r.user_id = auth.uid() and r.role = 'professor' and r.status = 'active'
      and (exists (select 1 from public.courses c where c.id = p_course and c.owner_id = auth.uid())
           or exists (select 1 from public.classes k where k.course_id = p_course and k.professor_id = auth.uid())))
$$;

create or replace function public.enrolled_in_course(p_course uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.enrollments e
    join public.classes k on k.id = e.class_id
    join public.user_roles r on r.user_id = e.student_id and r.status = 'active'
    where e.student_id = auth.uid() and e.status = 'active' and k.course_id = p_course)
$$;

create or replace function public.teaches_student(p_student uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.enrollments e
    join public.classes k on k.id = e.class_id
    where e.student_id = p_student and k.professor_id = auth.uid())
$$;

create or replace function public.is_class_professor(p_class uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.classes k where k.id = p_class and k.professor_id = auth.uid())
$$;

create or replace function public.is_class_member(p_class uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.enrollments e where e.class_id = p_class and e.student_id = auth.uid())
$$;

create or replace function public.module_course(p_module uuid) returns uuid
language sql stable security definer set search_path = '' as $$
  select m.course_id from public.modules m where m.id = p_module
$$;

create or replace function public.can_manage_module(p_module uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select public.is_admin() or public.teaches_course(public.module_course(p_module))
$$;

create or replace function public.can_view_module(p_module uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select public.can_manage_module(p_module)
      or exists (select 1 from public.modules m
                 where m.id = p_module and m.published and public.enrolled_in_course(m.course_id))
$$;

create or replace function public.quiz_module(p_quiz uuid) returns uuid
language sql stable security definer set search_path = '' as $$
  select q.module_id from public.quizzes q where q.id = p_quiz
$$;

-- Students may read questions of a practice quiz they can see, or of a graded quiz once they hold an attempt.
create or replace function public.can_view_quiz_questions(p_quiz uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select public.can_manage_module(public.quiz_module(p_quiz))
      or exists (
        select 1 from public.quizzes q
        where q.id = p_quiz and q.published and public.can_view_module(q.module_id)
          and (q.kind = 'practice'
               or exists (select 1 from public.quiz_attempts a where a.quiz_id = q.id and a.student_id = auth.uid())))
$$;

create or replace function public.question_quiz(p_question uuid) returns uuid
language sql stable security definer set search_path = '' as $$
  select q.quiz_id from public.questions q where q.id = p_question
$$;

create or replace function private.audit(p_action text, p_type text, p_id text, p_details jsonb)
returns void language sql security definer set search_path = '' as $$
  insert into public.audit_logs (actor_id, action, object_type, object_id, details)
  values (auth.uid(), p_action, p_type, p_id, coalesce(p_details, '{}'::jsonb))
$$;

-- New auth user => profile + student role. Professors/admins are promoted by an admin.
create or replace function private.handle_new_user() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  insert into public.profiles (id, full_name, email)
  values (new.id, coalesce(new.raw_user_meta_data ->> 'full_name', ''), new.email)
  on conflict (id) do nothing;
  insert into public.user_roles (user_id, role) values (new.id, 'student')
  on conflict (user_id) do nothing;
  return new;
end $$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function private.handle_new_user();

-- ---------------------------------------------------------------------------
-- Row Level Security
-- ---------------------------------------------------------------------------
alter table public.profiles             enable row level security;
alter table public.user_roles           enable row level security;
alter table public.courses              enable row level security;
alter table public.classes              enable row level security;
alter table public.enrollments          enable row level security;
alter table public.modules              enable row level security;
alter table public.lessons              enable row level security;
alter table public.lesson_progress      enable row level security;
alter table public.quizzes              enable row level security;
alter table public.questions            enable row level security;
alter table public.question_options     enable row level security;
alter table public.quiz_attempts        enable row level security;
alter table public.quiz_responses       enable row level security;
alter table public.simulation_scenarios enable row level security;
alter table public.simulation_attempts  enable row level security;
alter table public.simulation_events    enable row level security;
alter table public.ai_usage             enable row level security;
alter table public.audit_logs           enable row level security;
alter table public.system_settings      enable row level security;

-- profiles
create policy profiles_select on public.profiles for select to authenticated
  using (id = (select auth.uid()) or public.is_admin() or public.teaches_student(id));
create policy profiles_update_self on public.profiles for update to authenticated
  using (id = (select auth.uid())) with check (id = (select auth.uid()));

-- user_roles (read only; writes go through admin_set_role)
create policy user_roles_select on public.user_roles for select to authenticated
  using (user_id = (select auth.uid()) or public.is_admin());

-- courses
create policy courses_select on public.courses for select to authenticated
  using (public.is_admin() or public.teaches_course(id) or (state = 'active' and public.enrolled_in_course(id)));
create policy courses_admin_write on public.courses for all to authenticated
  using (public.is_admin()) with check (public.is_admin());

-- classes
create policy classes_select on public.classes for select to authenticated
  using (public.is_admin() or professor_id = (select auth.uid()) or public.is_class_member(id));
create policy classes_insert on public.classes for insert to authenticated
  with check (public.is_admin() or (professor_id = (select auth.uid()) and public.teaches_course(course_id)));
create policy classes_update on public.classes for update to authenticated
  using (public.is_admin() or professor_id = (select auth.uid()))
  with check (public.is_admin() or professor_id = (select auth.uid()));

-- enrollments (students join through join_class)
create policy enrollments_select on public.enrollments for select to authenticated
  using (student_id = (select auth.uid()) or public.is_admin() or public.is_class_professor(class_id));
create policy enrollments_update on public.enrollments for update to authenticated
  using (public.is_admin() or public.is_class_professor(class_id))
  with check (public.is_admin() or public.is_class_professor(class_id));

-- modules
create policy modules_select on public.modules for select to authenticated
  using (public.can_view_module(id));
create policy modules_insert on public.modules for insert to authenticated
  with check (public.is_admin() or public.teaches_course(course_id));
create policy modules_update on public.modules for update to authenticated
  using (public.can_manage_module(id)) with check (public.is_admin() or public.teaches_course(course_id));

-- lessons
create policy lessons_select on public.lessons for select to authenticated
  using (public.can_manage_module(module_id) or (published and public.can_view_module(module_id)));
create policy lessons_insert on public.lessons for insert to authenticated
  with check (public.can_manage_module(module_id));
create policy lessons_update on public.lessons for update to authenticated
  using (public.can_manage_module(module_id)) with check (public.can_manage_module(module_id));

-- lesson progress
create policy lesson_progress_select on public.lesson_progress for select to authenticated
  using (student_id = (select auth.uid()) or public.is_admin() or public.teaches_student(student_id));
create policy lesson_progress_insert on public.lesson_progress for insert to authenticated
  with check (student_id = (select auth.uid())
              and exists (select 1 from public.lessons l where l.id = lesson_id and l.published and public.can_view_module(l.module_id)));

-- quizzes
create policy quizzes_select on public.quizzes for select to authenticated
  using (public.can_manage_module(module_id) or (published and public.can_view_module(module_id)));
create policy quizzes_insert on public.quizzes for insert to authenticated
  with check (public.can_manage_module(module_id));
create policy quizzes_update on public.quizzes for update to authenticated
  using (public.can_manage_module(module_id)) with check (public.can_manage_module(module_id));

-- questions / options (no answer data lives here)
create policy questions_select on public.questions for select to authenticated
  using (public.can_view_quiz_questions(quiz_id));
create policy questions_write on public.questions for all to authenticated
  using (public.can_manage_module(public.quiz_module(quiz_id)))
  with check (public.can_manage_module(public.quiz_module(quiz_id)));
create policy question_options_select on public.question_options for select to authenticated
  using (public.can_view_quiz_questions(public.question_quiz(question_id)));
create policy question_options_write on public.question_options for all to authenticated
  using (public.can_manage_module(public.quiz_module(public.question_quiz(question_id))))
  with check (public.can_manage_module(public.quiz_module(public.question_quiz(question_id))));

-- attempts / responses: read only for clients; all writes go through RPCs
create policy quiz_attempts_select on public.quiz_attempts for select to authenticated
  using (student_id = (select auth.uid()) or public.is_admin() or public.teaches_student(student_id));
create policy quiz_responses_select on public.quiz_responses for select to authenticated
  using (exists (select 1 from public.quiz_attempts a where a.id = attempt_id
                 and (a.student_id = (select auth.uid()) or public.is_admin() or public.teaches_student(a.student_id))));

-- simulation
create policy scenarios_select on public.simulation_scenarios for select to authenticated
  using (published or public.is_admin() or public.my_role() = 'professor');
create policy sim_attempts_select on public.simulation_attempts for select to authenticated
  using (student_id = (select auth.uid()) or public.is_admin() or public.teaches_student(student_id));
create policy sim_events_select on public.simulation_events for select to authenticated
  using (exists (select 1 from public.simulation_attempts a where a.id = attempt_id
                 and (a.student_id = (select auth.uid()) or public.is_admin() or public.teaches_student(a.student_id))));

-- ai usage / audit / settings
create policy ai_usage_select on public.ai_usage for select to authenticated
  using (user_id = (select auth.uid()) or public.is_admin());
create policy audit_select on public.audit_logs for select to authenticated
  using (public.is_admin());
create policy settings_select on public.system_settings for select to authenticated
  using (true);
