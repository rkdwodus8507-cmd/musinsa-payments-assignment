drop table if exists point_usage_cancellation;
drop table if exists point_usage;
drop table if exists earned_point;
drop table if exists point_transaction;
drop table if exists user_point_lock;
drop table if exists point_policy;

create table point_policy
(
    id                  bigint    not null,
    min_earn_amount     bigint    not null,
    max_earn_amount     bigint    not null,
    max_user_balance    bigint    not null,
    default_expire_days int       not null,
    min_expire_days     int       not null,
    max_expire_days     int       not null,
    updated_at          timestamp not null,
    primary key (id)
);

create table user_point_lock
(
    id         bigint auto_increment not null,
    user_id    bigint               not null,
    created_at timestamp            not null,
    primary key (id),
    constraint uk_user_point_lock_user_id unique (user_id)
);

create table point_transaction
(
    id                     bigint auto_increment not null,
    point_key              varchar(36)          not null,
    user_id                bigint               not null,
    type                   varchar(20)          not null,
    amount                 bigint               not null,
    order_id               varchar(64),
    related_transaction_id bigint,
    request_key            varchar(64),
    memo                   varchar(255),
    created_at             timestamp            not null,
    primary key (id),
    constraint uk_point_transaction_point_key unique (point_key),
    constraint uk_point_transaction_request_key unique (user_id, request_key)
);

create index idx_point_transaction_user_id on point_transaction (user_id, id desc);

create table earned_point
(
    id               bigint auto_increment not null,
    transaction_id   bigint               not null,
    user_id          bigint               not null,
    original_amount  bigint               not null,
    remaining_amount bigint               not null,
    manual           boolean              not null,
    status           varchar(20)          not null,
    expire_at        timestamp            not null,
    created_at       timestamp            not null,
    primary key (id),
    constraint uk_earned_point_transaction_id unique (transaction_id)
);

create index idx_earned_point_priority on earned_point (user_id, status, manual desc, expire_at asc, id asc);
create index idx_earned_point_expiration on earned_point (status, expire_at);

create table point_usage
(
    id                      bigint auto_increment not null,
    use_transaction_id      bigint               not null,
    earned_point_id         bigint               not null,
    order_id                varchar(64)          not null,
    amount                  bigint               not null,
    canceled_amount         bigint               not null,
    created_at              timestamp            not null,
    primary key (id)
);

create index idx_point_usage_transaction on point_usage (use_transaction_id, id);
create index idx_point_usage_earned_point on point_usage (earned_point_id);
create index idx_point_usage_order_id on point_usage (order_id);

create table point_usage_cancellation
(
    id                       bigint auto_increment not null,
    cancel_transaction_id    bigint               not null,
    point_usage_id           bigint               not null,
    amount                   bigint               not null,
    source_earned_point_id   bigint               not null,
    reissued_earned_point_id bigint,
    created_at               timestamp            not null,
    primary key (id)
);

create index idx_point_usage_cancellation_transaction on point_usage_cancellation (cancel_transaction_id, id);

