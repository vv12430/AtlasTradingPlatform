create table outbox(id varchar2(36) primary key,topic varchar2(100) not null,event_key varchar2(100) not null,payload varchar2(4000) not null,sent number(1) default 0 not null,created_at timestamp default current_timestamp not null);
create index ix_outbox_pending on outbox(sent,created_at);
create table inbox(id varchar2(36) primary key,created_at timestamp default current_timestamp not null);
create table policies(id varchar2(36) primary key,name varchar2(100) not null,max_notional number(19,2) not null,enabled number(1) default 1 not null,version number(10) default 0 not null);
create table decisions(id varchar2(36) primary key,trade_id varchar2(36) unique not null,portfolio_id varchar2(36) not null,notional number(19,2) not null,status varchar2(20) not null,reason varchar2(200) not null,created_at timestamp default current_timestamp not null);
insert into policies(id,name,max_notional) values('default','Maximum order notional (USD)',100000);
