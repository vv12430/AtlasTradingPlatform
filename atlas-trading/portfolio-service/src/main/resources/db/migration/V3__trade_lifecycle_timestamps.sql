alter table trades add submitted_at timestamp;
alter table trades add risk_pending_at timestamp;
alter table trades add risk_decided_at timestamp;
alter table trades add execution_at timestamp;
alter table trades add accounting_at timestamp;

update trades
set submitted_at = created_at,
    risk_pending_at = created_at;

update trades
set risk_decided_at = created_at
where status in ('REJECTED', 'EXECUTED', 'FILLED', 'PARTIALLY_FILLED', 'CANCELLED');

update trades
set execution_at = created_at
where status in ('EXECUTED', 'FILLED', 'PARTIALLY_FILLED');

update trades
set accounting_at = created_at
where status in ('EXECUTED', 'FILLED', 'PARTIALLY_FILLED');
