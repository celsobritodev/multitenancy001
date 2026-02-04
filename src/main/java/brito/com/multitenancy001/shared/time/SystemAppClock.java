package brito.com.multitenancy001.shared.time;

import java.time.Clock;

import org.springframework.stereotype.Component;

@Component
public class SystemAppClock implements AppClock {

    private final Clock clock;

    public SystemAppClock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Clock clock() {
        return clock;
    }
}

