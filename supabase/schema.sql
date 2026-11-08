-- ETest Service initial schema. Run in Supabase SQL Editor.
-- IMPORTANT: after creating your first admin Auth user, promote it using the final SQL below.
create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  full_name text not null default '',
  registration_id text not null unique,
  role text not null default 'student' check (role in ('student','admin')),
  status text not null default 'pending' check (status in ('pending','approved','rejected')),
  created_at timestamptz not null default now()
);
create table if not exists public.tests (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  test_code text not null unique,
  duration_minutes integer not null check (duration_minutes between 1 and 300),
  total_marks numeric(8,2) not null default 0,
  instructions text not null default '',
  status text not null default 'draft' check (status in ('draft','published','closed')),
  created_by uuid not null references public.profiles(id),
  created_at timestamptz not null default now()
);
create table if not exists public.questions (
  id uuid primary key default gen_random_uuid(),
  test_id uuid not null references public.tests(id) on delete cascade,
  prompt text not null,
  question_type text not null check (question_type in ('mcq','short','long','checkbox')),
  options jsonb,
  correct_option integer,
  marks numeric(8,2) not null default 1,
  position integer not null,
  created_at timestamptz not null default now()
);
-- Correct answers are stored separately so students cannot read answer keys.
create table if not exists public.answer_keys (
  question_id uuid primary key references public.questions(id) on delete cascade,
  correct_option integer
);
create table if not exists public.attempts (
  id uuid primary key default gen_random_uuid(),
  test_id uuid not null references public.tests(id),
  student_id uuid not null references public.profiles(id),
  status text not null default 'in_progress' check (status in ('in_progress','submitted','evaluated')),
  started_at timestamptz not null default now(),
  submitted_at timestamptz,
  unique(test_id,student_id)
);
create table if not exists public.answers (
  id uuid primary key default gen_random_uuid(),
  attempt_id uuid not null references public.attempts(id) on delete cascade,
  question_id uuid not null references public.questions(id),
  answer_text text,
  selected_option integer,
  created_at timestamptz not null default now(),
  unique(attempt_id,question_id)
);
create table if not exists public.results (
  id uuid primary key default gen_random_uuid(),
  attempt_id uuid not null unique references public.attempts(id) on delete cascade,
  marks_obtained numeric(8,2) not null default 0,
  correct_count integer not null default 0,
  wrong_count integer not null default 0,
  evaluated_by uuid references public.profiles(id),
  evaluated_at timestamptz
);
create table if not exists public.incidents (
  id uuid primary key default gen_random_uuid(),
  attempt_id uuid not null references public.attempts(id) on delete cascade,
  description text not null,
  evidence_url text,
  created_by uuid not null references public.profiles(id),
  created_at timestamptz not null default now()
);

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public
as $$
begin
  insert into public.profiles(id,full_name,registration_id)
  values(new.id, coalesce(new.raw_user_meta_data->>'full_name',''), coalesce(new.raw_user_meta_data->>'registration_id',''));
  return new;
end;
$$;
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
for each row execute procedure public.handle_new_user();

create or replace function public.is_admin()
returns boolean language sql stable security definer set search_path = public
as $$ select exists(select 1 from public.profiles where id = auth.uid() and role = 'admin'); $$;

-- Enable RLS on every application table.
alter table public.profiles enable row level security;
alter table public.tests enable row level security;
alter table public.questions enable row level security;
alter table public.answer_keys enable row level security;
alter table public.attempts enable row level security;
alter table public.answers enable row level security;
alter table public.results enable row level security;
alter table public.incidents enable row level security;

-- Least-privilege grants. RLS policies below further restrict rows.
revoke all on public.profiles,public.tests,public.questions,public.answer_keys,public.attempts,public.answers,public.results,public.incidents from anon, authenticated;
grant select,update on public.profiles to authenticated;
grant select,insert,update,delete on public.tests,public.questions to authenticated;
grant select,insert,update,delete on public.answer_keys to authenticated;
grant select,insert,update on public.attempts,public.answers to authenticated;
grant select,insert,update on public.results,public.incidents to authenticated;

create policy "profile read self or admin" on public.profiles for select to authenticated
using (id = (select auth.uid()) or public.is_admin());
create policy "profile update admin only" on public.profiles for update to authenticated
using (public.is_admin()) with check (public.is_admin());

create policy "students read published tests admins all" on public.tests for select to authenticated
using ((status='published' and exists(select 1 from public.profiles p where p.id=auth.uid() and p.status='approved')) or public.is_admin());
create policy "admins manage tests" on public.tests for all to authenticated
using (public.is_admin()) with check (public.is_admin());

create policy "approved students read published questions" on public.questions for select to authenticated
using (public.is_admin() or exists(select 1 from public.tests t join public.profiles p on p.id=auth.uid() where t.id=test_id and t.status='published' and p.status='approved'));
create policy "admins manage questions" on public.questions for all to authenticated
using (public.is_admin()) with check (public.is_admin());
create policy "admins manage answer keys" on public.answer_keys for all to authenticated
using (public.is_admin()) with check (public.is_admin());

create policy "student own attempts or admin" on public.attempts for select to authenticated
using (student_id=auth.uid() or public.is_admin());
create policy "approved student starts own attempt" on public.attempts for insert to authenticated
with check (student_id=auth.uid() and status='in_progress' and exists(select 1 from public.profiles p where p.id=auth.uid() and p.status='approved') and exists(select 1 from public.tests t where t.id=test_id and t.status='published'));
create policy "student submits own attempt or admin" on public.attempts for update to authenticated
using (student_id=auth.uid() or public.is_admin())
with check ((student_id=auth.uid() and status in ('in_progress','submitted')) or public.is_admin());

create policy "answers own or admin read" on public.answers for select to authenticated
using (public.is_admin() or exists(select 1 from public.attempts a where a.id=attempt_id and a.student_id=auth.uid()));
create policy "student inserts answers for own active attempt" on public.answers for insert to authenticated
with check (exists(select 1 from public.attempts a where a.id=attempt_id and a.student_id=auth.uid() and a.status='in_progress'));
create policy "admin manage answers" on public.answers for all to authenticated
using (public.is_admin()) with check (public.is_admin());

create policy "student own result or admin" on public.results for select to authenticated
using (public.is_admin() or exists(select 1 from public.attempts a where a.id=attempt_id and a.student_id=auth.uid()));
create policy "admin manage results" on public.results for all to authenticated
using (public.is_admin()) with check (public.is_admin());

create policy "admin manage incidents" on public.incidents for all to authenticated
using (public.is_admin()) with check (public.is_admin());

-- After creating your own administrator account through Supabase Auth, run:
-- update public.profiles set role='admin', status='approved'
-- where id=(select id from auth.users where email='YOUR_ADMIN_EMAIL');
-- Never store an admin password or service-role key in this repository.
