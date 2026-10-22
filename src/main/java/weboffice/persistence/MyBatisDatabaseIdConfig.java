package weboffice.persistence;

import java.util.Properties;

import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DBMS 벤더별 SQL 분기(databaseId)를 활성화한다.
 *
 * 이 빈이 없으면 MyBatis 는 databaseId 를 null 로 두고
 * databaseId 가 붙은 구문을 전부 무시한다.
 *
 * <p>운영은 PostgreSQL, 시험은 H2 다. 태그가 없는 문장이 H2 쪽이고
 * {@code databaseId="postgresql"} 이 붙은 것이 운영 쪽이다.
 */
@Configuration
public class MyBatisDatabaseIdConfig {

    @Bean
    public DatabaseIdProvider databaseIdProvider() {
        Properties properties = new Properties();
        properties.setProperty("PostgreSQL", "postgresql");
        properties.setProperty("H2", "h2");

        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
        provider.setProperties(properties);
        return provider;
    }
}
