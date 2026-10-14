package ru.twilson.wakeup.service;

import ru.twilson.wakeup.config.WakeupProperties;
import ru.twilson.wakeup.model.Machine;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MachineService {

    private final WakeupProperties properties;

    public MachineService(WakeupProperties properties) {
        this.properties = properties;
    }

    public List<Machine> findAll() {
        return properties.machines();
    }

    public Optional<Machine> findByName(String name) {
        return properties.machines().stream()
                .filter(m -> m.name().equals(name))
                .findFirst();
    }
}
