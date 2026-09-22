create table outbox(id varchar2(36) primary key,topic varchar2(100) not null,event_key varchar2(100) not null,payload varchar2(4000) not null,sent number(1) default 0 not null,created_at timestamp default current_timestamp not null);
create index ix_outbox_pending on outbox(sent,created_at);
create table inbox(id varchar2(36) primary key,created_at timestamp default current_timestamp not null);
create table portfolios(id varchar2(36) primary key,name varchar2(100) not null,cash number(19,2) not null,active number(1) default 1 not null,version number(10) default 0 not null);
create table trades(id varchar2(36) primary key,client_key varchar2(100) unique not null,portfolio_id varchar2(36) not null references portfolios(id),symbol varchar2(12) not null,side varchar2(4) not null,quantity number(19,6) not null,price number(19,6) not null,status varchar2(20) not null,reason varchar2(200),created_at timestamp default current_timestamp not null);
create table positions(portfolio_id varchar2(36) references portfolios(id),symbol varchar2(12),quantity number(19,6) not null,cost number(19,2) not null,realized_pnl number(19,2) default 0 not null,primary key(portfolio_id,symbol));
insert into portfolios(id,name,cash) values('demo','Global Opportunities',1000000);
