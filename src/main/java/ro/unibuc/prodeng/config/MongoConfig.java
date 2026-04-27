package ro.unibuc.prodeng.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.event.ConnectionCheckedInEvent;
import com.mongodb.event.ConnectionCheckedOutEvent;
import com.mongodb.event.ConnectionPoolListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;

import java.util.Collection;
import java.util.Collections;

import ro.unibuc.prodeng.metrics.AppMetricsService;

@Configuration
public class MongoConfig extends AbstractMongoClientConfiguration {

    @Value("${mongodb.connection.url}")
    private String connectionURL;

    @Autowired(required = false)
    private AppMetricsService appMetricsService;

    @Override
    protected String getDatabaseName() {
        return "test";
    }

    @Override
    public MongoClient mongoClient() {
        MongoClientSettings mongoClientSettings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connectionURL))
                .applyToConnectionPoolSettings(builder -> builder.addConnectionPoolListener(new ConnectionPoolListener() {
                    @Override
                    public void connectionCheckedOut(ConnectionCheckedOutEvent event) {
                        if (appMetricsService != null) {
                            appMetricsService.incrementDbConnections();
                        }
                    }

                    @Override
                    public void connectionCheckedIn(ConnectionCheckedInEvent event) {
                        if (appMetricsService != null) {
                            appMetricsService.decrementDbConnections();
                        }
                    }
                }))
                .build();

        return MongoClients.create(mongoClientSettings);
    }

    @Override
    public Collection<String> getMappingBasePackages() {
        return Collections.singleton("ro.unibuc.prodeng.model");
    }
}
