-- In-app reports (2026-11), required by Google Play's User Generated Content and AI-Generated
-- Content policies: any signed-in user can report a class chat message, a classmate, an AI tutor
-- answer or an AI quiz draft without leaving the app.
--
-- Who sees what (RLS):
--   * reporters insert only as themselves, and only about classes they belong to or teach;
--   * reporters can read their own reports;
--   * administrators read and resolve every report;
--   * professors read and resolve reports about the classes they teach.
-- The reported text is copied into `excerpt`, so a report still makes sense after the message is
-- deleted. Deleting an account keeps its reports for moderation but drops the link to the account.

create table if not exists public.content_reports (
  id               bigint generated always as identity primary key,
  reporter_id      uuid default auth.uid() references auth.users (id) on delete set null,
  kind             text not null check (kind in ('chat_message', 'user', 'ai_tutor', 'ai_quiz')),
  reason           text not null check (reason in ('offensive', 'harassment', 'sexual', 'violent', 'hate', 'spam', 'wrong_info', 'other')),
  details          text check (details is null or char_length(details) <= 500),
  class_id         uuid references public.classes (id) on delete cascade,
  message_id       bigint, -- no foreign key: the message may be deleted after it is reported
  reported_user_id uuid references auth.users (id) on delete set null,
  excerpt          text check (excerpt is null or char_length(excerpt) <= 4000),
  status           text not null default 'open' check (status in ('open', 'resolved')),
  created_at       timestamptz not null default now(),
  resolved_at      timestamptz,
  resolved_by      uuid references auth.users (id) on delete set null
);
create index if not exists content_reports_open_idx on public.content_reports (status, created_at desc);
create index if not exists content_reports_class_idx on public.content_reports (class_id);
alter table public.content_reports enable row level security;

drop policy if exists content_reports_insert on public.content_reports;
drop policy if exists content_reports_select on public.content_reports;
drop policy if exists content_reports_update on public.content_reports;

create policy content_reports_insert on public.content_reports for insert to authenticated
  with check (
    reporter_id = (select auth.uid())
    and status = 'open' and resolved_at is null and resolved_by is null
    and (class_id is null or public.is_class_member(class_id) or public.is_class_professor(class_id))
  );

create policy content_reports_select on public.content_reports for select to authenticated
  using (
    reporter_id = (select auth.uid())
    or public.is_admin()
    or (class_id is not null and public.is_class_professor(class_id))
  );

create policy content_reports_update on public.content_reports for update to authenticated
  using (public.is_admin() or (class_id is not null and public.is_class_professor(class_id)))
  with check (public.is_admin() or (class_id is not null and public.is_class_professor(class_id)));

revoke all on public.content_reports from anon;
grant select, insert on public.content_reports to authenticated;
grant update (status, resolved_at, resolved_by) on public.content_reports to authenticated;
grant all on public.content_reports to service_role;

notify pgrst, 'reload schema';
