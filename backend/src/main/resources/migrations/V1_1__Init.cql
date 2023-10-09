CREATE KEYSPACE IF NOT EXISTS "s3_replication" WITH replication = {'class' : 'SimpleStrategy', 'replication_factor' : 1};

CREATE TABLE IF NOT EXISTS s3_replication.user
(
    login    text primary key,
    password text,
    roles    set<text>,
    isActive boolean,
    claims   set<text>,
)
