create table remittance (amount bigint not null, from_membership_id bigint not null, requested_at timestamp(6) with time zone not null, to_membership_id bigint, updated_at timestamp(6) with time zone not null, remittance_id uuid not null, remittance_type varchar(16) not null, request_id varchar(64) not null unique, remittance_status varchar(255) not null check ((remittance_status in ('REQUESTED','DEBITED','TRANSFERRING','COMPLETED','FAILED','REFUNDING','REFUNDED'))), to_bank_account_number varchar(255), to_bank_name varchar(255), primary key (remittance_id), constraint remittance_amount_positive check (amount > 0), constraint remittance_receiver_matches_type check ((remittance_type = 'MEMBER' AND to_membership_id IS NOT NULL AND to_membership_id <> from_membership_id
    AND to_bank_name IS NULL AND to_bank_account_number IS NULL)
OR (remittance_type = 'BANK_ACCOUNT' AND to_membership_id IS NULL
    AND to_bank_name IS NOT NULL AND to_bank_account_number IS NOT NULL)));
create index remittance_sender on remittance (from_membership_id);
create index remittance_receiver on remittance (to_membership_id);
insert into remittance (amount, from_membership_id, requested_at, to_membership_id, updated_at, remittance_id, remittance_type, request_id, remittance_status) values (1000, 1, now(), 2, now(), gen_random_uuid(), 'MEMBER', 'upgrade-1', 'COMPLETED');
