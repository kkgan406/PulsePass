package com.pulsepass.bench;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Explicit opt-in only. All SQL is confined to pulsepass_bench and a unique run ID. */
@EnabledIfSystemProperty(named = "pulsepass.bench", matches = "true")
class MysqlRaceExperimentIT {
    private static final String URL = "jdbc:mysql://127.0.0.1:3307/pulsepass_bench?allowPublicKeyRetrieval=true&sslMode=DISABLED";
    private Connection connection() throws SQLException {
        String password = System.getenv("DB_PASSWORD");
        if (password == null || password.isBlank()) throw new IllegalStateException("DB_PASSWORD required");
        return DriverManager.getConnection(URL, "pulsepass", password);
    }
    @Test void repeatRaceForAllThreeStrategies() throws Exception {
        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS lab_inventory (run_id CHAR(36) PRIMARY KEY, initial_stock INT NOT NULL, available INT NOT NULL CHECK (available >= 0)) ENGINE=InnoDB");
            s.execute("CREATE TABLE IF NOT EXISTS lab_order (id BIGINT AUTO_INCREMENT PRIMARY KEY, run_id CHAR(36) NOT NULL, user_id BIGINT NOT NULL, UNIQUE KEY uq_run_user(run_id,user_id), FOREIGN KEY (run_id) REFERENCES lab_inventory(run_id)) ENGINE=InnoDB");
            try (ResultSet r = s.executeQuery("SELECT @@transaction_isolation, VERSION()")) { r.next(); System.out.println("E1 mysql=" + r.getString(2) + " default_isolation=" + r.getString(1)); }
        }
        for (String strategy : List.of("A", "B", "C")) {
            for (int round = 1; round <= 3; round++) runRound(strategy, round);
        }
    }
    private void runRound(String strategy, int round) throws Exception {
        String run = UUID.randomUUID().toString();
        try (Connection c = connection(); PreparedStatement p = c.prepareStatement("INSERT INTO lab_inventory(run_id,initial_stock,available) VALUES (?,1,1)")) { p.setString(1,run); p.executeUpdate(); }
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2), bothRead = new CyclicBarrier(2);
        try {
            Future<Boolean> first = pool.submit(() -> purchase(strategy,run,1,start,bothRead));
            Future<Boolean> second = pool.submit(() -> purchase(strategy,run,2,start,bothRead));
            int success = (first.get(20,TimeUnit.SECONDS) ? 1 : 0) + (second.get(20,TimeUnit.SECONDS) ? 1 : 0);
            try (Connection c = connection(); PreparedStatement p = c.prepareStatement("SELECT available,(SELECT COUNT(*) FROM lab_order WHERE run_id=?) FROM lab_inventory WHERE run_id=?")) {
                p.setString(1,run);p.setString(2,run);
                try (ResultSet r=p.executeQuery()) { assertTrue(r.next()); int stock=r.getInt(1), orders=r.getInt(2);
                    System.out.printf("E1 scheme=%s round=%d initial=1 stock=%d orders=%d successes=%d oversold=%d%n",strategy,round,stock,orders,success,Math.max(0,orders-1));
                    assertEquals(0,stock); assertEquals(strategy.equals("A") ? 2 : 1,orders); assertEquals(orders,success);
                }
            }
        } finally {
            pool.shutdownNow();
            try (Connection c = connection()) {
                try (PreparedStatement p=c.prepareStatement("DELETE FROM lab_order WHERE run_id=?")) {p.setString(1,run);p.executeUpdate();}
                try (PreparedStatement p=c.prepareStatement("DELETE FROM lab_inventory WHERE run_id=?")) {p.setString(1,run);p.executeUpdate();}
            }
        }
    }
    private boolean purchase(String strategy,String run,long user,CyclicBarrier start,CyclicBarrier bothRead) throws Exception {
        try (Connection c=connection()) {
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ); c.setAutoCommit(false);
            try {
                start.await(10,TimeUnit.SECONDS);
                int affected;
                if (strategy.equals("B")) {
                    try (PreparedStatement p=c.prepareStatement("UPDATE lab_inventory SET available=available-1 WHERE run_id=? AND available>0")) {p.setString(1,run);affected=p.executeUpdate();}
                } else {
                    int stock;
                    try (PreparedStatement p=c.prepareStatement("SELECT available FROM lab_inventory WHERE run_id=?"+(strategy.equals("C") ? " FOR UPDATE" : ""))) {
                        p.setString(1,run);try(ResultSet r=p.executeQuery()){if(!r.next()) throw new SQLException("missing fixture");stock=r.getInt(1);}
                    }
                    if (strategy.equals("A")) bothRead.await(10,TimeUnit.SECONDS);
                    if (stock<=0) {c.rollback();return false;}
                    try (PreparedStatement p=c.prepareStatement("UPDATE lab_inventory SET available=? WHERE run_id=?")) {p.setInt(1,stock-1);p.setString(2,run);affected=p.executeUpdate();}
                }
                if (affected!=1) {c.rollback();return false;}
                try (PreparedStatement p=c.prepareStatement("INSERT INTO lab_order(run_id,user_id) VALUES (?,?)")) {p.setString(1,run);p.setLong(2,user);p.executeUpdate();}
                c.commit(); return true;
            } catch (Exception e) { c.rollback(); throw e; }
        }
    }
}
