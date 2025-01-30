-- create schema and table for malicious events
create schema if not exists threat_detection;
create table if not exists threat_detection.malicious_event (
    id uuid primary key default uuid_generate_v4(),
    actor varchar(255) not null,
    filter_id varchar(255) not null,
    url varchar(1024),
    ip varchar(255),
    method varchar(255),
    timestamp bigint not null,
    orig text not null,
    api_collection_id int not null,
    created_at timestamp default (timezone('utc', now()))
);

-- add index on actor and filter_id and sort data by timestamp
create index malicious_events_actor_filter_id_timestamp_idx on threat_detection.malicious_event(actor, filter_id, timestamp desc);
