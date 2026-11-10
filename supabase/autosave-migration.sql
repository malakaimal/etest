-- ETest autosave support
-- Run this once in Supabase SQL Editor before using the updated exam page.
-- Students can update only their own answers while their attempt is in progress.

drop policy if exists "student updates answers for own active attempt" on public.answers;

create policy "student updates answers for own active attempt"
on public.answers
for update
to authenticated
using (
  exists (
    select 1
    from public.attempts a
    where a.id = attempt_id
      and a.student_id = auth.uid()
      and a.status = 'in_progress'
  )
)
with check (
  exists (
    select 1
    from public.attempts a
    where a.id = attempt_id
      and a.student_id = auth.uid()
      and a.status = 'in_progress'
  )
);

-- Supports looking up a student's attempt for a particular test.
create index if not exists idx_attempts_student_test
on public.attempts (student_id, test_id);
