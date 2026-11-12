-- ILM ETest Android invigilation setting
-- Run this once in Supabase SQL Editor before using the updated Android app.
alter table public.tests
add column if not exists camera_required boolean not null default false;

comment on column public.tests.camera_required is
'When true, the Android app requests camera and screen-sharing permissions for this assessment. When false, it does not request camera access.';
