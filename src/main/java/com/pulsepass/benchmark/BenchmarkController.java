package com.pulsepass.benchmark;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

/** Local-only lab endpoint: never enabled by the normal local/default profiles. */
@RestController
@Profile("benchmark")
@RequestMapping("/benchmark")
public class BenchmarkController implements InitializingBean {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final DataSource dataSource;
    private final Environment environment;

    public BenchmarkController(JdbcTemplate jdbc, org.springframework.transaction.PlatformTransactionManager manager,
                               DataSource dataSource, Environment environment) {
        this.jdbc=jdbc; this.dataSource=dataSource; this.environment=environment;
        this.transactions=new TransactionTemplate(manager);
        this.transactions.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    @Override public void afterPropertiesSet() throws Exception {
        if (!"127.0.0.1".equals(environment.getProperty("server.address")) ||
            environment.getActiveProfiles().length != 1 || !"benchmark".equals(environment.getActiveProfiles()[0]))
            throw new IllegalStateException("Benchmark requires only benchmark profile and loopback binding");
        try (Connection c=dataSource.getConnection()) {
            String url=c.getMetaData().getURL();
            if (!"pulsepass_bench".equals(c.getCatalog()) || !url.startsWith("jdbc:mysql://127.0.0.1:3307/pulsepass_bench?"))
                throw new IllegalStateException("Benchmark requires the isolated local database");
        }
    }
    public record Request(String runId, Long userId, String strategy) { }
    @PostMapping("/purchase")
    public ResponseEntity<Map<String,String>> purchase(@RequestBody Request request) {
        try {
            UUID.fromString(request.runId());
            if (request.userId()==null || request.userId()<=0 || !List.of("A","B","C").contains(request.strategy()))
                return result(400,"INVALID_ARGUMENT");
        } catch (RuntimeException e) { return result(400,"INVALID_ARGUMENT"); }
        try {
            String outcome=transactions.execute(status -> buy(request));
            return result("OK".equals(outcome) ? 201 : 409,outcome);
        } catch (DataAccessException e) {
            Throwable root=e.getMostSpecificCause();
            if (root instanceof SQLException sql) {
                if (sql.getErrorCode()==1062 && sql.getMessage().contains("uq_run_user")) return result(409,"DUPLICATE_ORDER");
                if (sql.getErrorCode()==1213) return result(503,"DEADLOCK");
                if (sql.getErrorCode()==1205) return result(503,"LOCK_TIMEOUT");
                if (sql.getErrorCode()==3819) return result(500,"INTEGRITY_ERROR");
                if (sql.getSQLState()!=null && sql.getSQLState().startsWith("23")) return result(500,"INTEGRITY_ERROR");
            }
            return result(500,"SYSTEM_ERROR");
        }
    }
    private String buy(Request r) {
        Integer existing=jdbc.queryForObject("SELECT COUNT(*) FROM lab_order WHERE run_id=? AND user_id=?",Integer.class,r.runId(),r.userId());
        if (existing!=null && existing>0) return "DUPLICATE_ORDER";
        int changed;
        if ("B".equals(r.strategy())) {
            // Same conditional decrement and affected-row check as production InventoryMapper.
            changed=jdbc.update("UPDATE lab_inventory SET available=available-1 WHERE run_id=? AND available>0",r.runId());
        } else {
            List<Integer> rows=jdbc.queryForList("SELECT available FROM lab_inventory WHERE run_id=?"+("C".equals(r.strategy()) ? " FOR UPDATE" : ""),Integer.class,r.runId());
            if (rows.isEmpty()) throw new IllegalArgumentException("missing run");
            int available=rows.get(0);
            if (available<=0) return "OUT_OF_STOCK";
            changed=jdbc.update("UPDATE lab_inventory SET available=? WHERE run_id=?",available-1,r.runId());
        }
        if (changed==0) return "OUT_OF_STOCK";
        jdbc.update("INSERT INTO lab_order(run_id,user_id) VALUES (?,?)",r.runId(),r.userId());
        return "OK";
    }
    private ResponseEntity<Map<String,String>> result(int status,String code) { return ResponseEntity.status(status).body(Map.of("code",code)); }
}
