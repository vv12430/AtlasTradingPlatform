create table outbox(id varchar2(36) primary key,topic varchar2(100) not null,event_key varchar2(100) not null,payload varchar2(4000) not null,sent number(1) default 0 not null,created_at timestamp default current_timestamp not null);
create index ix_outbox_pending on outbox(sent,created_at);
create table inbox(id varchar2(36) primary key,created_at timestamp default current_timestamp not null);
create table accounts(id varchar2(36) primary key,name varchar2(100) not null,enabled number(1) default 1 not null,version number(10) default 0 not null);
create table journals(id varchar2(36) primary key,trade_id varchar2(36) unique not null,portfolio_id varchar2(36) not null,description varchar2(200) not null,created_at timestamp default current_timestamp not null);
create table journal_lines(id varchar2(36) primary key,journal_id varchar2(36) not null references journals(id),account_id varchar2(36) not null references accounts(id),debit number(19,2) not null,credit number(19,2) not null,check(debit>=0 and credit>=0),check((debit>0 and credit=0) or (credit>0 and debit=0)));
insert into accounts(id,name) values('cash','Cash settlement clearing');
insert into accounts(id,name) values('securities','Securities transaction clearing');
