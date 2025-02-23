--
-- Initial data
--
upsert into customer (id, email)
values
    ('00000000-0000-0000-0000-000000000000','user@gmail.com');

--
-- Run between each test
--
upsert into purchase_order (id, status, customer_id, total_price)
values
    ('00000000-0000-0000-0000-000000000001', 'placed','00000000-0000-0000-0000-000000000000', 100.00),
    ('00000000-0000-0000-0000-000000000002','placed','00000000-0000-0000-0000-000000000000', 200.00),
    ('00000000-0000-0000-0000-000000000003','placed','00000000-0000-0000-0000-000000000000', 300.00),
    ('00000000-0000-0000-0000-000000000004','placed','00000000-0000-0000-0000-000000000000', 400.00),
    ('00000000-0000-0000-0000-000000000005','placed','00000000-0000-0000-0000-000000000000', 500.00)
;

--
-- Non-repeatable read test cases
--

-- CockroachDB "serializable" prevents Lost Update (P4):
begin; set transaction isolation level serializable; -- T1
begin; set transaction isolation level serializable; -- T2
begin; set transaction isolation level serializable; -- T3
select * from purchase_order where id = '00000000-0000-0000-0000-000000000001'; -- T1, reads 'placed'
select * from purchase_order where id = '00000000-0000-0000-0000-000000000001'; -- T2, reads 'placed'
select * from purchase_order where id = '00000000-0000-0000-0000-000000000001'; -- T3, reads 'placed'
update purchase_order set status = 'confirmed' where id = '00000000-0000-0000-0000-000000000001'; -- T1
update purchase_order set status = 'cancelled' where id = '00000000-0000-0000-0000-000000000001'; -- T2, BLOCKS
commit; -- T1. T2 now prints out "ERROR: restart transaction: TransactionRetryWithProtoRefreshError: WriteTooOldError"
abort;  -- T2. There's nothing else we can do, this transaction has failed
select * from purchase_order where id = '00000000-0000-0000-0000-000000000001'; -- T3, reads 'confirmed'
commit; -- T3

-- CockroachDB "read committed" does not prevent Lost Update (P4):
begin; set transaction isolation level read committed; -- T1
begin; set transaction isolation level read committed; -- T2
begin; set transaction isolation level read committed; -- T3
select * from purchase_order where id = '00000000-0000-0000-0000-000000000001'; -- T1, reads 'placed'
select * from purchase_order where id = '00000000-0000-0000-0000-000000000001'; -- T2, reads 'placed'
select * from purchase_order where id = '00000000-0000-0000-0000-000000000001'; -- T3, reads 'placed'
update purchase_order set status = 'confirmed' where id = '00000000-0000-0000-0000-000000000001'; -- T1
update purchase_order set status = 'cancelled' where id = '00000000-0000-0000-0000-000000000001'; -- T2, BLOCKS
commit; -- T1. This unblocks T2, so T1's update to 'confirmed' is overwritten (aka a lost update aka last-write-wins)
select * from purchase_order where id = '00000000-0000-0000-0000-000000000001'; -- T3, reads 'confirmed'
commit; -- T2
select * from purchase_order where id = '00000000-0000-0000-0000-000000000001'; -- T3, reads 'cancelled'
commit; -- T3

-- CockroachDB "read committed" with SFU does prevent Lost Update (P4):
begin; set transaction isolation level read committed; -- T1
begin; set transaction isolation level read committed; -- T2
begin; set transaction isolation level read committed; -- T3
select * from purchase_order where id = '00000000-0000-0000-0000-000000000001' FOR UPDATE; -- T1, reads 'placed'
select * from purchase_order where id = '00000000-0000-0000-0000-000000000001' FOR UPDATE; -- T2, BLOCKS
select * from purchase_order where id = '00000000-0000-0000-0000-000000000001' FOR UPDATE; -- T3, BLOCKS
update purchase_order set status = 'confirmed' where id = '00000000-0000-0000-0000-000000000001'; -- T1
commit; -- T1. This unblocks T2, which reads T1's update to 'confirmed' that may cancel out the next update (app-tier)
update purchase_order set status = 'cancelled' where id = '00000000-0000-0000-0000-000000000001'; -- T2
update purchase_order set status = 'confirmed' where id = '00000000-0000-0000-0000-000000000001'; -- T2
commit; -- T2, unblocks T3 that reads 'cancelled'
select * from purchase_order where id = '00000000-0000-0000-0000-000000000001'; -- T3, reads 'cancelled'
commit; -- T3



