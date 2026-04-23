package com.lms.www.leadmanagement.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

@Configuration
public class DatabaseConfig {

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.driver-class-name:com.mysql.cj.jdbc.Driver}")
    private String driverClassName;

    @Bean
    @Primary
    public DataSource dataSource() throws SQLException {
        // 1. Extract host and database name from URL to create DB if missing
        try {
            String cleanUrl = datasourceUrl.substring(datasourceUrl.indexOf("//") + 2);
            int slashIndex = cleanUrl.indexOf("/");
            int hookIndex = cleanUrl.indexOf("?");
            
            String host = cleanUrl.substring(0, slashIndex);
            String dbName = (hookIndex != -1) 
                    ? cleanUrl.substring(slashIndex + 1, hookIndex) 
                    : cleanUrl.substring(slashIndex + 1);

            String serverUrl = "jdbc:mysql://" + host + "/";
            if (hookIndex != -1) {
                serverUrl += datasourceUrl.substring(datasourceUrl.indexOf("?"));
            }

            // Connect to MySQL server (without database) to ensure DB exists
            try (Connection connection = DriverManager.getConnection(serverUrl, username, password);
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE DATABASE IF NOT EXISTS " + dbName);
                System.out.println("Database initialization check: " + dbName + " is ready.");
            }
        } catch (Exception e) {
            System.err.println("Warning: Programmatic database creation check failed: " + e.getMessage());
            // We continue anyway as the JDBC URL parameter createDatabaseIfNotExist=true might still work
        }

        // 2. Return a HikariDataSource for connection pooling
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(datasourceUrl);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setDriverClassName(driverClassName);
        
        // Optimal default pool settings
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setIdleTimeout(30000);
        hikariConfig.setConnectionTimeout(30000);
        
        return new HikariDataSource(hikariConfig);
    }
}
