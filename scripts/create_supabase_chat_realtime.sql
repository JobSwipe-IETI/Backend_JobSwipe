-- Tabla espejo para eventos de chat en Supabase Realtime.
-- Esta tabla es solo para transporte realtime; la fuente de verdad sigue en chat_messages del backend.

create table if not exists public.chat_messages_realtime (
    id bigserial primary key,
    message_id bigint not null unique,
    conversation_id bigint not null,
    sender_id bigint not null,
    sender_role text not null,
    content text not null,
    client_message_id text,
    created_at timestamptz not null default now(),
    inserted_at timestamptz not null default now()
);

create index if not exists idx_chat_messages_realtime_conversation_created
    on public.chat_messages_realtime (conversation_id, created_at desc);

alter table public.chat_messages_realtime replica identity full;

do $$
begin
    if not exists (
        select 1
        from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'chat_messages_realtime'
    ) then
        execute 'alter publication supabase_realtime add table public.chat_messages_realtime';
    end if;
end
$$;

-- Recomendado: habilitar RLS y politica de solo lectura para anon/authenticated
-- segun tus reglas de seguridad y mapeo de usuarios.
