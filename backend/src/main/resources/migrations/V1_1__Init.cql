create keyspace "s3_replication" with replication = {'class' : 'SimpleStrategy', 'replication_factor' : 1};

create table if not exists s3_replication.user
(
    login    text primary key,
    password text,
    roles    set<text>,
    isActive boolean,
    claims   set<text>,
    ips      set<text>,
);

create custom index if not exists user_login_custom_index on s3_replication.user (login) using 'org.apache.cassandra.index.sasi.SASIIndex';

create table if not exists s3_replication.request_log
(
    id             uuid primary key,
    method         text,
    fileName       text,
    fileProperties map<text, text>,
    s3StorageName  text,
    timestamp      timestamp,
);

create index if not exists request_log_timestamp_index on s3_replication.request_log (timestamp);
create index if not exists request_log_filename_index on s3_replication.request_log (fileName);

create table if not exists s3_replication.request_error
(
    id             uuid primary key,
    method         text,
    fileName       text,
    fileProperties map<text, text>,
    s3StorageName  text,
    timestamp      timestamp,
);

create index if not exists request_error_timestamp_index on s3_replication.request_error (timestamp);
create index if not exists request_error_filename_index on s3_replication.request_error (fileName);

create table if not exists s3_replication.replication_lock
(
    name      text primary key,
    lockUntil timestamp,
    lockedAt  timestamp,
    lockedBy  text
);

create table if not exists s3_replication.replication_settings
(
    id      uuid primary key,
    enabled boolean,
    status  text,
);
