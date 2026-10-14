package ru.twilson.wakeup.service;

import ru.twilson.wakeup.config.WakeupProperties;
import ru.twilson.wakeup.model.Machine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

@Service
public class WakeOnLanService {

    private static final Logger log = LoggerFactory.getLogger(WakeOnLanService.class);
    private static final int MAC_BYTES = 6;
    private static final int MAC_REPETITIONS = 16;
    private static final int[] FALLBACK_PORTS = {7};

    private final WakeupProperties properties;

    public WakeOnLanService(WakeupProperties properties) {
        this.properties = properties;
    }

    public void sendWake(Machine machine) {
        byte[] packet = buildMagicPacket(machine.mac());
        String broadcast = (machine.broadcast() == null || machine.broadcast().isBlank())
                ? properties.defaultBroadcast()
                : machine.broadcast();
        send(packet, broadcast, properties.wakePort());
        for (int port : FALLBACK_PORTS) {
            if (port != properties.wakePort()) {
                send(packet, broadcast, port);
            }
        }
        log.info("Sent WoL magic packet for '{}' (mac={}) to {}:{}", machine.name(), machine.mac(), broadcast, properties.wakePort());
    }

    static byte[] buildMagicPacket(String mac) {
        byte[] macBytes = parseMac(mac);
        byte[] packet = new byte[6 + MAC_BYTES * MAC_REPETITIONS];
        for (int i = 0; i < 6; i++) {
            packet[i] = (byte) 0xFF;
        }
        for (int rep = 0; rep < MAC_REPETITIONS; rep++) {
            System.arraycopy(macBytes, 0, packet, 6 + rep * MAC_BYTES, MAC_BYTES);
        }
        return packet;
    }

    private static byte[] parseMac(String mac) {
        String cleaned = mac.replace(":", "").replace("-", "").replace(".", "").trim();
        if (cleaned.length() != MAC_BYTES * 2) {
            throw new IllegalArgumentException("Invalid MAC address: " + mac);
        }
        byte[] result = new byte[MAC_BYTES];
        for (int i = 0; i < MAC_BYTES; i++) {
            result[i] = (byte) Integer.parseInt(cleaned.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    private void send(byte[] packet, String broadcast, int port) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            InetAddress address = InetAddress.getByName(broadcast);
            socket.send(new DatagramPacket(packet, packet.length, address, port));
        } catch (Exception e) {
            log.warn("Failed to send WoL packet to {}:{} - {}", broadcast, port, e.getMessage());
        }
    }
}
