package ru.twilson.wakeup.config;

import ru.twilson.wakeup.model.Machine;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "wakeup")
public record WakeupProperties(
        int wakePort,
        String defaultBroadcast,
        StatusCheck statusCheck,
        List<Machine> machines
) {
    public WakeupProperties {
        if (wakePort == 0) {
            wakePort = 9;
        }
        if (defaultBroadcast == null || defaultBroadcast.isBlank()) {
            defaultBroadcast = "255.255.255.255";
        }
        if (statusCheck == null) {
            statusCheck = new StatusCheck(1500);
        }
        if (machines == null) {
            machines = List.of();
        }
    }

    public record StatusCheck(int connectTimeoutMs) {
        public StatusCheck {
            if (connectTimeoutMs <= 0) {
                connectTimeoutMs = 1500;
            }
        }
    }
}
