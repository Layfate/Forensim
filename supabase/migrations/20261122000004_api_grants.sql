-- Explicit Data API privileges. New Supabase projects do not auto-grant table access to the
-- API roles, so each table is opted in here. RLS policies still decide which ROWS are visible.
-- anon gets nothing: every Forensim feature requires a signed-in user.

grant usage on schema public to authenticated;

grant select on
  public.profiles, public.user_roles, public.courses, public.classes, public.enrollments,
  public.modules, public.lessons, public.lesson_progress, public.quizzes, public.questions,
  public.question_options, public.quiz_attempts, public.quiz_responses, public.simulation_scenarios,
  public.simulation_attempts, public.simulation_events, public.ai_usage, public.audit_logs,
  public.system_settings
to authenticated;

-- Column-limited self-service profile edits.
grant update (full_name, institution, updated_at) on public.profiles to authenticated;

-- Staff content management (RLS restricts to admins/assigned professors).
grant insert, update on public.courses, public.classes, public.modules, public.lessons, public.quizzes to authenticated;
grant insert, update, delete on public.questions, public.question_options to authenticated;
grant update (status) on public.enrollments to authenticated;
grant delete on public.courses to authenticated;

-- Students record their own lesson completion.
grant insert on public.lesson_progress to authenticated;

-- Attempts, responses, simulation events, AI usage, audit logs, roles and settings are written
-- only by SECURITY DEFINER functions or Edge Functions using the service role.
