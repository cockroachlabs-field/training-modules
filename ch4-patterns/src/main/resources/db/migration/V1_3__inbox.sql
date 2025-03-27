create table if not exists inbox
(
    id             uuid as ((payload ->> 'id')::UUID) stored,
    aggregate_type varchar(32) not null,
    payload        jsonb       not null,

    primary key (id)
);

set cluster setting kv.rangefeed.enabled = true;

create changefeed into '${cdc-sink-url}?topic_name=orders-inbox'
with diff as
         select id             as aggregate_id,
                aggregate_type as aggregate_type,
                event_op()     as event_type,
                payload
         from outbox
         where event_op() != 'delete'
           and aggregate_type = 'purchase_order';
