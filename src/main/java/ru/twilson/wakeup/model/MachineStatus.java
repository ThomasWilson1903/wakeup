package ru.twilson.wakeup.model;

public record MachineStatus(
        String name,
        String mac,
        String ip,
        int port,
        Status status
) {
    public static MachineStatus of(Machine machine, Status status) {
        return new MachineStatus(
                machine.name(),
                machine.mac(),
                machine.ip(),
                machine.port(),
                status
        );
    }
}
