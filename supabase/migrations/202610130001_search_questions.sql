-- Library search for the Ask PrepDesk agent: Gemini calls a search tool and the Edge Function runs it here.
-- Server-only, like the catalog itself: clients never read interview_questions directly.
create function public.search_questions(search text, max_results int default 5)
returns table(id text, document jsonb)
language sql stable security definer set search_path = '' as $$
  with query as (select websearch_to_tsquery('english', left(coalesce(search, ''), 200)) as q),
  ranked as (
    select c.id, c.document,
      setweight(to_tsvector('english', coalesce(c.document->>'q', '')), 'A') ||
      setweight(to_tsvector('english', coalesce(c.document->>'area', '') || ' ' || coalesce(c.document->>'subtopic', '')), 'B') ||
      setweight(to_tsvector('english', coalesce(c.document->>'a', '') || ' ' || coalesce(c.document->>'explanation', '')), 'C') as text
    from public.interview_questions c
  )
  select r.id, r.document from ranked r, query
  where r.text @@ query.q
  order by ts_rank(r.text, query.q) desc, r.id
  limit least(greatest(coalesce(max_results, 5), 1), 8)
$$;
revoke all on function public.search_questions(text, int) from public, anon, authenticated;
grant execute on function public.search_questions(text, int) to service_role;
