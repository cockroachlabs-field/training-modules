create table customer
(
    id         uuid         not null default gen_random_uuid(),
    first_name varchar(45),
    last_name  varchar(45),
    email      varchar(128) not null unique,
    address1   varchar(255) null,
    address2   varchar(255) null,
    postcode   varchar(16)  null,
    city       varchar(255) null,
    country    varchar(128) null,

    primary key (id)
);

create table product
(
    id        uuid           not null default gen_random_uuid(),
    version   int            not null default 0,
    inventory int            not null,
    name      varchar(128)   not null,
    price     numeric(19, 2) not null,
    sku       varchar(12)    not null unique,

    primary key (id)
);

create type if not exists shipment_status as enum ('placed', 'confirmed', 'cancelled','delivered');

-- Not supported
-- create cast (varchar as shipment_status) with inout as implicit;

create table purchase_order
(
    id             uuid            not null default gen_random_uuid(),
    customer_id    uuid            not null,
    total_price    numeric(19, 2)  not null,
    tags           varchar(128)    null,
    status         shipment_status not null default 'placed',
    date_placed    timestamptz     not null default clock_timestamp(),
    date_updated   timestamptz     not null default clock_timestamp(),

    deliv_address1 varchar(255)    null,
    deliv_address2 varchar(255)    null,
    deliv_postcode varchar(16)     null,
    deliv_city     varchar(255)    null,
    deliv_country  varchar(128)    null,

    primary key (id)
);

create index on purchase_order (status) storing (total_price);

create table purchase_order_item
(
    order_id   uuid           not null,
    product_id uuid           not null,
    quantity   int            not null,
    unit_price numeric(19, 2) not null,
    item_pos   int            not null,

    primary key (order_id, item_pos)
);

alter table product
    add constraint check_product_positive_inventory check (product.inventory >= 0);

alter table if exists purchase_order_item
    add constraint fk_order_item_ref_product
        foreign key (product_id)
            references product;

alter table if exists purchase_order_item
    add constraint fk_order_item_ref_order
        foreign key (order_id)
            references purchase_order;

alter table if exists purchase_order
    add constraint fk_order_ref_customer
        foreign key (customer_id)
            references customer;

-- Foreign key indexes
create index fk_order_item_ref_product_idx on purchase_order_item (product_id);
create index fk_order_ref_customer_idx on purchase_order (customer_id);