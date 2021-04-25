drop table map;
drop table category;


create table map_category
(
    id           serial primary key,
    name         varchar(32) unique not null check (length(name) > 2),
    date_created timestamptz        not null default now()
);

insert into map_category (name)
values ('BEST'),
       ('GOOD'),
       ('EXPERIMENTAL');

create table map_index
(
    id           serial primary key,
    map_name     varchar(256) not null,
    version      integer      not null check (version > 0),
    repo_url     varchar(256) not null unique check (repo_url like 'http%'),
    category_id  integer      not null references map_category (id),
    date_created timestamptz  not null default now(),
    date_updated timestamptz  not null default now()
);
