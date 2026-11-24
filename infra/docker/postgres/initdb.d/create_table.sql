CREATE TABLE IF NOT EXISTS dead_letter_entry (
    dead_letter_id varchar(255) NOT NULL,
    processing_group varchar(255) NOT NULL,
    sequence_identifier varchar(255) NOT NULL,
    sequence_index bigint NOT NULL,
    enqueued_at timestamp(6) with time zone NOT NULL,
    last_touched timestamp(6) with time zone,
    processing_started timestamp(6) with time zone,
    cause_type varchar(255),
    cause_message varchar(1023),
    diagnostics oid,
    message_type varchar(255) NOT NULL,
    event_identifier varchar(255) NOT NULL,
    time_stamp varchar(255) NOT NULL,
    payload_type varchar(255) NOT NULL,
    payload_revision varchar(255),
    payload oid NOT NULL,
    meta_data oid,
    type varchar(255),
    aggregate_identifier varchar(255),
    sequence_number bigint,
    token_type varchar(255),
    token oid,
    PRIMARY KEY (dead_letter_id),
    UNIQUE (processing_group, sequence_identifier, sequence_index)
);

CREATE INDEX IF NOT EXISTS dead_letter_entry_processing_group_idx
    ON dead_letter_entry (processing_group);

CREATE INDEX IF NOT EXISTS dead_letter_entry_processing_group_sequence_identifier_idx
    ON dead_letter_entry (processing_group, sequence_identifier);
