alter table threat_detection.malicious_event add column _alerted_to_backend boolean default false;

-- set all existing rows to false
update threat_detection.malicious_event set _alerted_to_backend = false;
