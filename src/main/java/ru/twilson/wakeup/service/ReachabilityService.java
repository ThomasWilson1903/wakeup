package ru.twilson.wakeup.service;

import ru.twilson.wakeup.config.WakeupProperties;
import ru.twilson.wakeup.model.Machine;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Socket;

@Service
public class ReachabilityService {

    private final WakeupProperties properties;

    public ReachabilityService(WakeupProperties properties) {
        this.properties = properties;
    }

    public boolean isOnline(Machine machine) {
        return isOnline(machine.ip(), machine.port());
    }

    public boolean isOnline(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), properties.statusCheck().connectTimeoutMs());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
