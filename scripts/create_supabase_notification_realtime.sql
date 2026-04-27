-- Tabla espejo para notificaciones realtime (likes, decisiones, matches).
-- La fuente de verdad sigue en PostgreSQL del backend; esta tabla solo transporta eventos.

create table if not exists public.notification_events_realtime (
    id bigserial primary key,
    user_id bigint not null,
    type text not null,
    payload jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now()
);

create index if not exists idx_notification_events_realtime_user_created
    on public.notification_events_realtime (user_id, created_at desc);

alter table public.notification_events_realtime replica identity full;

do $$
begin
    if not exists (
        select 1
        from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'notification_events_realtime'
    ) then
        execute 'alter publication supabase_realtime add table public.notification_events_realtime';
    end if;
end
$$;
