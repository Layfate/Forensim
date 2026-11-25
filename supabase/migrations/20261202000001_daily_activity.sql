-- Home carousel "of the day" answers, stored per account instead of per phone, so the quiz-of-the-day
-- streak follows the student to a new phone and two students on one phone don't share it.
-- One row per student per day. Students only read their own rows; answers are written through the
-- functions below, which allow exactly one answer per day (one try) and only for today
-- (± 1 day for time zones), so past days can't be filled in to build a streak.

create table if not exists public.daily_activity (
  user_id uuid not null references auth.users(id) on delete cascade,
  day date not null,
  quiz_choice text,
  quiz_correct boolean,
  quiz_answered_at timestamptz,
  evidence_answer text,
  evidence_correct boolean,
  evidence_answered_at timestamptz,
  primary key (user_id, day)
);

alter table public.daily_activity enable row level security;

drop policy if exists daily_activity_own on public.daily_activity;
create policy daily_activity_own on public.daily_activity
  for select to authenticated using (user_id = (select auth.uid()));

grant select on public.daily_activity to authenticated;

create or replace function public.answer_daily_quiz(p_day date, p_choice text, p_correct boolean)
returns public.daily_activity
language plpgsql security definer set search_path = '' as $$
declare v_row public.daily_activity;
begin
  if auth.uid() is null then raise exception 'Not signed in' using errcode = '42501'; end if;
  if abs(p_day - current_date) > 1 then raise exception 'You can only answer today''s quiz' using errcode = '22023'; end if;
  if coalesce(length(trim(p_choice)), 0) = 0 or length(p_choice) > 200 then raise exception 'Choose an answer' using errcode = '22023'; end if;
  insert into public.daily_activity (user_id, day, quiz_choice, quiz_correct, quiz_answered_at)
  values (auth.uid(), p_day, trim(p_choice), p_correct, now())
  on conflict (user_id, day) do update
    set quiz_choice = excluded.quiz_choice, quiz_correct = excluded.quiz_correct, quiz_answered_at = excluded.quiz_answered_at
    where public.daily_activity.quiz_choice is null;  -- one try: an existing answer is never replaced
  select * into v_row from public.daily_activity where user_id = auth.uid() and day = p_day;
  return v_row;
end $$;

create or replace function public.answer_daily_evidence(p_day date, p_answer text, p_correct boolean)
returns public.daily_activity
language plpgsql security definer set search_path = '' as $$
declare v_row public.daily_activity;
begin
  if auth.uid() is null then raise exception 'Not signed in' using errcode = '42501'; end if;
  if abs(p_day - current_date) > 1 then raise exception 'You can only answer today''s evidence' using errcode = '22023'; end if;
  if coalesce(length(trim(p_answer)), 0) = 0 or length(p_answer) > 200 then raise exception 'Type an answer' using errcode = '22023'; end if;
  insert into public.daily_activity (user_id, day, evidence_answer, evidence_correct, evidence_answered_at)
  values (auth.uid(), p_day, trim(p_answer), p_correct, now())
  on conflict (user_id, day) do update
    set evidence_answer = excluded.evidence_answer, evidence_correct = excluded.evidence_correct,
        evidence_answered_at = excluded.evidence_answered_at
    where public.daily_activity.evidence_answer is null;
  select * into v_row from public.daily_activity where user_id = auth.uid() and day = p_day;
  return v_row;
end $$;

revoke all on function public.answer_daily_quiz(date, text, boolean) from public, anon;
revoke all on function public.answer_daily_evidence(date, text, boolean) from public, anon;
grant execute on function public.answer_daily_quiz(date, text, boolean) to authenticated;
grant execute on function public.answer_daily_evidence(date, text, boolean) to authenticated;
