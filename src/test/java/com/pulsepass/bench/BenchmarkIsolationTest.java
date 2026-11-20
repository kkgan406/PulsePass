package com.pulsepass.benchmark;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.pulsepass.benchmark.BenchmarkController;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;

class BenchmarkIsolationTest {
    @Test void normalLocalProfileDoesNotRegisterLabController() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("local");
            context.register(BenchmarkController.class);
            context.refresh();
            assertTrue(context.getBeansOfType(BenchmarkController.class).isEmpty());
        }
    }
    @Test void rejectsNonLoopbackAndDevelopmentDatabase() throws Exception {
        DataSource source=mock(DataSource.class);
        var env=new MockEnvironment(); env.setActiveProfiles("benchmark"); env.setProperty("server.address","0.0.0.0");
        var controller=new BenchmarkController(mock(JdbcTemplate.class),mock(PlatformTransactionManager.class),source,env);
        assertThrows(IllegalStateException.class,controller::afterPropertiesSet);
        verifyNoInteractions(source);
        env.setProperty("server.address","127.0.0.1");
        Connection connection=mock(Connection.class); DatabaseMetaData meta=mock(DatabaseMetaData.class);
        when(source.getConnection()).thenReturn(connection); when(connection.getCatalog()).thenReturn("pulsepass");
        when(connection.getMetaData()).thenReturn(meta); when(meta.getURL()).thenReturn("jdbc:mysql://127.0.0.1:3307/pulsepass?x=1");
        assertThrows(IllegalStateException.class,controller::afterPropertiesSet);
    }
}
