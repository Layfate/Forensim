-- Deleting quizzes from a class (2026-11).
-- * A quiz the professor made in a class is deleted for good (its questions, attempts and answers
--   go with it through the existing ON DELETE CASCADE keys).
-- * A built-in quiz is shared by every class of the subject, so it is never deleted: it is removed
--   from that one class instead (hidden from the professor's list and locked for its students).
--   It can be put back later.

create table if not exists public.class_hidden_quizzes (
  class_id  uuid not null references public.classes (id) on delete cascade,
  quiz_id   uuid not null references public.quizzes (id) on delete cascade,
  hidden_at timestamptz not null default now(),
  primary key (class_id, quiz_id)
);
create index if not exists class_hidden_quizzes_quiz_idx on public.class_hidden_quizzes (quiz_id);
alter table public.class_hidden_quizzes enable row level security;

drop policy if exists class_hidden_quizzes_select on public.class_hidden_quizzes;
create policy class_hidden_quizzes_select on public.class_hidden_quizzes for select to authenticated
  using (public.is_admin() or public.is_class_professor(class_id));

grant select on public.class_hidden_quizzes to authenticated;
grant all on public.class_hidden_quizzes to service_role;

-- Deletes a quiz the caller created inside one of their classes.
create or replace function public.professor_delete_quiz(p_quiz_id uuid)
returns void
language plpgsql security definer set search_path = '' as $$
declare v_quiz public.quizzes;
begin
  select * into v_quiz from public.quizzes where id = p_quiz_id;
  if not found then raise exception 'That quiz no longer exists' using errcode = 'P0002'; end if;
  if v_quiz.class_id is null then
    raise exception 'Built-in quizzes can only be removed from a class, not deleted' using errcode = '42501';
  end if;
  if not (public.is_admin() or public.is_class_professor(v_quiz.class_id)) then
    raise exception 'Not permitted' using errcode = '42501';
  end if;
  delete from public.quizzes where id = p_quiz_id;
  perform private.audit('quiz.delete', 'quiz', p_quiz_id::text, jsonb_build_object('title', v_quiz.title, 'class', v_quiz.class_id));
end $$;

-- Removes a built-in quiz from one class (and locks it for that class's students), or puts it back.
create or replace function public.professor_set_quiz_hidden(p_class_id uuid, p_quiz_id uuid, p_hidden boolean)
returns void
language plpgsql security definer set search_path = '' as $$
begin
  if not (public.is_admin() or public.is_class_professor(p_class_id)) then
    raise exception 'Not permitted' using errcode = '42501';
  end if;
  if not exists (select 1 from public.quizzes q join public.classes k on k.module_id = q.module_id
                 where q.id = p_quiz_id and k.id = p_class_id and q.class_id is null) then
    raise exception 'That quiz is not a built-in quiz of this subject' using errcode = '22023';
  end if;
  if p_hidden then
    insert into public.class_hidden_quizzes (class_id, quiz_id) values (p_class_id, p_quiz_id) on conflict do nothing;
    delete from public.class_quiz_unlocks where class_id = p_class_id and quiz_id = p_quiz_id;
  else
    delete from public.class_hidden_quizzes where class_id = p_class_id and quiz_id = p_quiz_id;
  end if;
  perform private.audit(case when p_hidden then 'quiz.remove_from_class' else 'quiz.restore_to_class' end,
                        'quiz', p_quiz_id::text, jsonb_build_object('class', p_class_id));
end $$;

revoke execute on function public.professor_delete_quiz(uuid) from public, anon;
grant execute on function public.professor_delete_quiz(uuid) to authenticated;
revoke execute on function public.professor_set_quiz_hidden(uuid, uuid, boolean) from public, anon;
grant execute on function public.professor_set_quiz_hidden(uuid, uuid, boolean) to authenticated;

notify pgrst, 'reload schema';
