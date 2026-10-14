package ru.twilson.wakeup.model;

public record Machine(
        String name,
        String mac,
        String ip,
        int port,
        String broadcast
) {
}
