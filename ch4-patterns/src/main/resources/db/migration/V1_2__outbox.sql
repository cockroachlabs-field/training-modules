-- From a performance standpoint there should be one outbox table per aggregate that
-- will reduce the number of change feeds per range. In this demo however, there's
-- only one outbox table with a discriminator column for the aggregate type.

create table if not exists outbox
(
    id             uuid as ((payload ->> 'id')::UUID) stored,
    aggregate_type varchar(32) not null,
    payload        jsonb       not null,

    primary key (id)
);

alter table outbox set (ttl_expire_after = '1 hour');

set cluster setting kv.rangefeed.enabled = true;

create changefeed into '${cdc-sink-url}?topic_name=orders-outbox'
with diff as
         select id             as aggregate_id,
                aggregate_type as aggregate_type,
                event_op()     as event_type,
                payload
         from outbox
         where event_op() != 'delete'
           and aggregate_type = 'purchase_order';
