package io.cockroachdb.training.contention;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import javax.sql.DataSource;

import org.junit.jupiter.api.RepeatedTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import io.cockroachdb.training.Chapter2Application;
import io.cockroachdb.training.domain.test.AbstractIntegrationTest;

@SpringBootTest(classes = {Chapter2Application.class})
public class ModifyingCteTest extends AbstractIntegrationTest {
    private static final Logger logger = LoggerFactory.getLogger(ModifyingCteTest.class);

    private static final int CONCURRENCY = 32;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @RepeatedTest(value = 10)
    public void givenExplicitTransaction_thenExpectLotsOfContentionAndTransientErrors() {
        final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        final TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        IntStream.rangeClosed(1, CONCURRENCY).forEach(value -> {
            CompletableFuture<Void> f = CompletableFuture.runAsync(() -> {
                try {
                    transactionTemplate.executeWithoutResult(transactionStatus -> {
                        UUID id = UUID.randomUUID();

                        jdbcTemplate.update("""
                                insert into transfer (id) values (?) returning id,booking_date
                                """, id);
                        jdbcTemplate.update("""
                                insert into transfer_item (transfer_id, account_id, amount, running_balance)
                                    values (?, '10000000-0000-0000-0000-000000000000', 75.00,
                                            (select balance + 75.00 from account where id = '10000000-0000-0000-0000-000000000000'))
                                """, id);
                        jdbcTemplate.update("""
                                insert into transfer_item (transfer_id, account_id, amount, running_balance)
                                    values (?, '20000000-0000-0000-0000-000000000000', -75.00,
                                            (select balance - 75.00 from account where id = '20000000-0000-0000-0000-000000000000'))
                                """, id);
                        jdbcTemplate.update("""
                                update account set balance = balance + 75.00 where id = '10000000-0000-0000-0000-000000000000'
                                """);
                        jdbcTemplate.update("""
                                update account set balance = balance - 75.00 where id = '20000000-0000-0000-0000-000000000000'
                                """);
                    });
                } catch (TransactionException e) {
                    logger.error("", e);
                }
            });
            futures.add(f);
        });

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    @RepeatedTest(value = 10)
    public void givenImplicitTransaction_thenExpectNoContentionOrTransientErrors() {
        final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        IntStream.rangeClosed(1, CONCURRENCY).forEach(value -> {
            CompletableFuture<Void> f = CompletableFuture.runAsync(() -> {
                try {
                    jdbcTemplate.update("""
                            with head as (
                                insert into transfer (id) values (gen_random_uuid())
                                    returning id,booking_date),
                                 item1 as (
                                     insert into transfer_item (transfer_id, account_id, amount, running_balance)
                                         values ((select id from head),
                                                 '10000000-0000-0000-0000-000000000000',
                                                 75.00,
                                                 (select balance + 75.00 from account where id = '10000000-0000-0000-0000-000000000000'))
                                         returning transfer_id),
                                 item2 as (
                                     insert into transfer_item (transfer_id, account_id, amount, running_balance)
                                         values ((select id from head),
                                                 '20000000-0000-0000-0000-000000000000',
                                                 -75.00,
                                                 (select balance - 75.00 from account where id = '20000000-0000-0000-0000-000000000000'))
                                         returning transfer_id)
                            update account
                            set balance=account.balance + dt.balance
                            from (select unnest(array [75, -75])                                                                                   as balance,
                                         unnest(array ['10000000-0000-0000-0000-000000000000'::uuid,'20000000-0000-0000-0000-000000000000'::uuid]) as id) as dt
                            where account.id = dt.id
                            returning account.id, account.balance
                            """);
                } catch (DataAccessException e) {
                    logger.error("", e);
                }
            });
            futures.add(f);
        });

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }
}
