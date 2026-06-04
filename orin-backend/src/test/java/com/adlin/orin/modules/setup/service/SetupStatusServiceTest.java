package com.adlin.orin.modules.setup.service;

import com.adlin.orin.modules.system.entity.SystemConfigEntity;
import com.adlin.orin.modules.system.repository.SystemConfigRepository;
import com.adlin.orin.security.EncryptionUtil;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SetupStatusServiceTest {

    @Test
    void reportsIncompleteDevSetupAsInitializable() throws Exception {
        SystemConfigRepository configRepository = mock(SystemConfigRepository.class);
        when(configRepository.findByConfigKey(SetupStatusService.SETUP_COMPLETED_KEY)).thenReturn(Optional.empty());
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});

        SetupStatusService service = service(configRepository, environment);
        ReflectionTestUtils.setField(service, "setupEnabledOverride", "");
        ReflectionTestUtils.setField(service, "jwtSecret", "test-secret-key-with-enough-length-1234567890");
        ReflectionTestUtils.setField(service, "allowedOrigins", "http://localhost:5173");
        ReflectionTestUtils.setField(service, "defaultAdminPassword", "");

        var status = service.getStatus();

        assertThat(status.isCompleted()).isFalse();
        assertThat(status.isSetupEnabled()).isTrue();
        assertThat(status.isCanInitialize()).isTrue();
        assertThat(status.getSecurity()).anyMatch(check -> "jwt-secret".equals(check.getKey()) && "ok".equals(check.getStatus()));
    }

    @Test
    void disablesWritesByDefaultInProd() throws Exception {
        SystemConfigRepository configRepository = mock(SystemConfigRepository.class);
        when(configRepository.findByConfigKey(SetupStatusService.SETUP_COMPLETED_KEY)).thenReturn(Optional.empty());
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        SetupStatusService service = service(configRepository, environment);
        ReflectionTestUtils.setField(service, "setupEnabledOverride", "");

        assertThat(service.isSetupWriteEnabled()).isFalse();
    }

    @Test
    void detectsCompletedSetupFlag() throws Exception {
        SystemConfigRepository configRepository = mock(SystemConfigRepository.class);
        SystemConfigEntity flag = new SystemConfigEntity();
        flag.setConfigKey(SetupStatusService.SETUP_COMPLETED_KEY);
        flag.setConfigValue("true");
        when(configRepository.findByConfigKey(SetupStatusService.SETUP_COMPLETED_KEY)).thenReturn(Optional.of(flag));
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});

        SetupStatusService service = service(configRepository, environment);

        assertThat(service.isSetupCompleted()).isTrue();
    }

    @SuppressWarnings("unchecked")
    private SetupStatusService service(SystemConfigRepository configRepository, Environment environment) throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection sqlConnection = mock(Connection.class);
        when(sqlConnection.isValid(1)).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(sqlConnection);

        ObjectProvider<StringRedisTemplate> redisProvider = mock(ObjectProvider.class);
        ObjectProvider<ConnectionFactory> rabbitProvider = mock(ObjectProvider.class);
        EncryptionUtil encryptionUtil = mock(EncryptionUtil.class);
        when(encryptionUtil.isEncryptionEnabled()).thenReturn(true);

        SetupStatusService service = new SetupStatusService(
                configRepository,
                dataSource,
                redisProvider,
                rabbitProvider,
                encryptionUtil,
                environment);
        ReflectionTestUtils.setField(service, "aiEngineUrl", "http://127.0.0.1:1");
        return service;
    }
}
