drop table if exists point_lot_usage_cancel;
drop table if exists point_lot_usage;
drop table if exists point_lot;
drop table if exists point_transaction;
drop table if exists point_wallet;
drop table if exists point_policy;

create table point_policy
(
    id                  bigint       not null,
    min_earn_amount     bigint       not null,
    max_earn_amount     bigint       not null,
    max_user_balance    bigint       not null,
    default_expire_days int          not null,
    min_expire_days     int          not null,
    max_expire_days     int          not null,
    updated_at          timestamp    not null,
    primary key (id)
);

create table point_wallet
(
    id         bigint auto_increment not null,
    user_id    bigint               not null,
    created_at timestamp            not null,
    primary key (id),
    constraint uk_point_wallet_user_id unique (user_id)
);

create table point_transaction
(
    point_key              varchar(36)          not null,
    id                     bigint auto_increment not null,
    user_id                bigint               not null,
    type                   varchar(20)          not null,
    amount                 bigint               not null,
    order_id               varchar(64),
    related_transaction_id bigint,
    memo                   varchar(255),
    created_at             timestamp            not null,
    primary key (id),
    constraint uk_point_transaction_point_key unique (point_key)
);

create index idx_point_transaction_user_id on point_transaction (user_id, id desc);
create index idx_point_transaction_order_id on point_transaction (order_id);
create index idx_point_transaction_related on point_transaction (type, related_transaction_id);

create table point_lot
(
    id              bigint auto_increment not null,
    transaction_id  bigint               not null,
    user_id         bigint               not null,
    original_amount bigint               not null,
    remaining_amount bigint              not null,
    manual          boolean              not null,
    status          varchar(20)          not null,
    expire_at       timestamp            not null,
    created_at      timestamp            not null,
    primary key (id),
    constraint uk_point_lot_transaction_id unique (transaction_id)
);

create index idx_point_lot_usage_order on point_lot (user_id, status, manual desc, expire_at asc, id asc);
create index idx_point_lot_expiration on point_lot (status, expire_at);

create table point_lot_usage
(
    id                 bigint auto_increment not null,
    use_transaction_id bigint               not null,
    lot_id             bigint               not null,
    order_id           varchar(64)          not null,
    amount             bigint               not null,
    canceled_amount    bigint               not null,
    created_at         timestamp            not null,
    primary key (id)
);

create index idx_point_lot_usage_transaction on point_lot_usage (use_transaction_id, id);
create index idx_point_lot_usage_lot on point_lot_usage (lot_id);
create index idx_point_lot_usage_order_id on point_lot_usage (order_id);

create table point_lot_usage_cancel
(
    id                    bigint auto_increment not null,
    cancel_transaction_id bigint               not null,
    lot_usage_id          bigint               not null,
    amount                bigint               not null,
    restored_lot_id       bigint,
    reissued_lot_id       bigint,
    created_at            timestamp            not null,
    primary key (id)
);

create index idx_point_lot_usage_cancel_transaction on point_lot_usage_cancel (cancel_transaction_id);
create index idx_point_lot_usage_cancel_usage on point_lot_usage_cancel (lot_usage_id);
