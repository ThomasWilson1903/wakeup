package ru.twilson.wakeup.config;

import ru.twilson.wakeup.model.Machine;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "wakeup")
public record WakeupProperties(
        int wakePort,
        String defaultBroadcast,
        StatusCheck statusCheck,
        Ssh ssh,
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
        if (ssh == null) {
            ssh = new Ssh(null, 5000, 20000, "sudo systemctl poweroff");
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

    public record Ssh(String privateKey, int connectTimeoutMs, int commandTimeoutMs, String shutdownCommand) {
        public Ssh {
            if (connectTimeoutMs <= 0) {
                connectTimeoutMs = 5000;
            }
            if (commandTimeoutMs <= 0) {
                commandTimeoutMs = 20000;
            }
            if (shutdownCommand == null || shutdownCommand.isBlank()) {
                shutdownCommand = "sudo systemctl poweroff";
            }
        }
    }
}
