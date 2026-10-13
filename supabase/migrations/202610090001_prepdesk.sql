create table public.progress (
  user_id uuid primary key references auth.users(id) on delete cascade,
  revision bigint not null default 1 check (revision > 0),
  document jsonb not null check (octet_length(document::text) <= 2000000 and document->>'format' = '1'),
  updated_at timestamptz not null default now()
);
alter table public.progress enable row level security;
create policy "Read own progress" on public.progress for select to authenticated using ((select auth.uid()) = user_id);
revoke all on public.progress from anon, authenticated;
grant select on public.progress to authenticated;

-- A locked compare-and-swap stops two devices from silently overwriting each other.
create function public.save_progress(expected_revision bigint, new_document jsonb) returns bigint
language plpgsql security definer set search_path = '' as $$
declare uid uuid := auth.uid(); current_revision bigint; next_revision bigint;
begin
  if uid is null then raise sqlstate '42501' using message = 'Sign in required'; end if;
  if expected_revision is null or new_document is null or jsonb_typeof(new_document->'state') is distinct from 'object' or expected_revision < 0 or octet_length(new_document::text) > 2000000 or new_document->>'format' is distinct from '1' then
    raise sqlstate '22023' using message = 'Invalid progress document';
  end if;
  perform pg_advisory_xact_lock(hashtextextended(uid::text, 0));
  select revision into current_revision from public.progress where user_id = uid for update;
  if coalesce(current_revision, 0) <> expected_revision then
    raise sqlstate 'PT409' using message = 'Progress conflict';
  end if;
  next_revision := coalesce(current_revision, 0) + 1;
  insert into public.progress(user_id, revision, document) values (uid, next_revision, new_document)
    on conflict (user_id) do update set revision = excluded.revision, document = excluded.document, updated_at = now();
  return next_revision;
end $$;
revoke all on function public.save_progress(bigint, jsonb) from public, anon;
grant execute on function public.save_progress(bigint, jsonb) to authenticated;

create table public.content_releases (
  id bigint generated always as identity primary key,
  created_at timestamptz not null default now(),
  active boolean not null default false,
  document jsonb not null check (document->>'format' = '1' and octet_length(document::text) <= 4000000)
);
create unique index one_active_content_release on public.content_releases(active) where active;
alter table public.content_releases enable row level security;
create policy "Read published content" on public.content_releases for select to anon, authenticated using (active);
grant select on public.content_releases to anon, authenticated;
revoke insert, update, delete on public.content_releases from anon, authenticated;

-- Trusted question/rubric catalog for AI coaching. Clients cannot replace expected answers.
create table public.interview_questions (id text primary key, version integer not null check (version > 0), document jsonb not null);
alter table public.interview_questions enable row level security;
revoke all on public.interview_questions from anon, authenticated;

create function public.publish_content(new_document jsonb) returns bigint
language plpgsql security definer set search_path = '' as $$
declare result bigint; q jsonb;
begin
  if new_document->>'format' is distinct from '1' or jsonb_typeof(new_document->'questions') is distinct from 'array' or jsonb_array_length(new_document->'questions') = 0 then
    raise sqlstate '22023' using message = 'Invalid content release';
  end if;
  for q in select jsonb_array_elements(new_document->'questions') loop
    if exists (select 1 from public.interview_questions current where current.id = q->>'id' and current.version >= (q->>'version')::int and current.document <> q) then
      raise sqlstate '22023' using message = 'Content versions must increase when content changes';
    end if;
    insert into public.interview_questions(id, version, document) values(q->>'id', (q->>'version')::int, q)
    on conflict (id) do update set version = excluded.version, document = excluded.document
      where public.interview_questions.version < excluded.version;
  end loop;
  update public.content_releases set active = false where active;
  insert into public.content_releases(active, document) values(true, new_document) returning id into result;
  return result;
end $$;
revoke all on function public.publish_content(jsonb) from public, anon, authenticated;
grant execute on function public.publish_content(jsonb) to service_role;

create table public.service_usage (
  user_id uuid not null references auth.users(id) on delete cascade,
  action text not null, day date not null default current_date, count int not null default 1,
  primary key(user_id, action, day)
);
alter table public.service_usage enable row level security;
revoke all on public.service_usage from anon, authenticated;
create function public.consume_budget(uid uuid, service_action text, request_limit int) returns boolean
language plpgsql security definer set search_path = '' as $$
declare used int;
begin
  if request_limit < 1 or request_limit > 1000 then return false; end if;
  insert into public.service_usage(user_id, action, day, count) values(uid, service_action, current_date, 1)
  on conflict(user_id, action, day) do update set count = public.service_usage.count + 1
  returning count into used;
  return used <= request_limit;
end $$;
revoke all on function public.consume_budget(uuid, text, int) from public, anon, authenticated;
grant execute on function public.consume_budget(uuid, text, int) to service_role;

create table public.code_jobs (
  token text primary key, user_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now()
);
alter table public.code_jobs enable row level security;
revoke all on public.code_jobs from anon, authenticated;

-- Explicit server grants; client privileges remain restricted by grants and RLS above.
grant all on public.progress, public.content_releases, public.interview_questions, public.service_usage, public.code_jobs to service_role;
