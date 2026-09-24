-- Add support for partial fills, cancellations, and fees
alter table trades add filled_quantity number(19,6) default 0 not null;
alter table trades add remaining_quantity number(19,6) not null;
alter table trades add fees number(19,2) default 0 not null;
alter table trades add cancelled_at timestamp;
alter table trades add cancellation_reason varchar2(200);

-- Update existing trades to have filled_quantity = 0, remaining_quantity = quantity
update trades set filled_quantity = 0, remaining_quantity = quantity where filled_quantity = 0;
