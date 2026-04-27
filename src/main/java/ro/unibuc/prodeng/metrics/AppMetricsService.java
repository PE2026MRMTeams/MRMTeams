package ro.unibuc.prodeng.metrics;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

@Service
public class AppMetricsService {

    private final MeterRegistry meterRegistry;
    private final Counter usersCreatedCounter;
    private final AtomicInteger activeDbConnections = new AtomicInteger(0);
    private final AtomicInteger itemsInCart = new AtomicInteger(0);

    public AppMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        this.usersCreatedCounter = Counter.builder("app_users_created_total")
                .description("Total number of user registrations")
                .register(meterRegistry);

        Gauge.builder("app_db_connections_active", activeDbConnections, AtomicInteger::get)
                .description("Currently active database connections")
                .register(meterRegistry);

        Gauge.builder("app_items_in_cart", itemsInCart, AtomicInteger::get)
                .description("Current number of items across all carts")
                .register(meterRegistry);
    }

    public void recordUserCreated() {
        usersCreatedCounter.increment();
    }

    public void recordRequestDuration(String endpoint, String method, int status, Duration duration) {
        Timer.builder("app_request_duration_seconds")
                .description("API endpoint response time")
                .tag("endpoint", endpoint)
                .tag("method", method)
                .tag("status", Integer.toString(status))
                .register(meterRegistry)
                .record(duration.toNanos(), TimeUnit.NANOSECONDS);
    }

    public void recordApplicationError(String type) {
        Counter.builder("app_errors_total")
                .description("Total application errors by type")
                .tag("type", type)
                .register(meterRegistry)
                .increment();
    }

    public void incrementDbConnections() {
        activeDbConnections.incrementAndGet();
    }

    public void decrementDbConnections() {
        activeDbConnections.updateAndGet(value -> Math.max(0, value - 1));
    }

    public void incrementItemsInCart() {
        itemsInCart.incrementAndGet();
    }

    public void decrementItemsInCart() {
        itemsInCart.updateAndGet(value -> Math.max(0, value - 1));
    }

    public void setItemsInCart(int count) {
        itemsInCart.set(Math.max(0, count));
    }
}