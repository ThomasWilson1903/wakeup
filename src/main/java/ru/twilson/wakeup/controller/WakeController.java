package ru.twilson.wakeup.controller;

import ru.twilson.wakeup.model.Machine;
import ru.twilson.wakeup.model.MachineStatus;
import ru.twilson.wakeup.model.Status;
import ru.twilson.wakeup.service.MachineService;
import ru.twilson.wakeup.service.ReachabilityService;
import ru.twilson.wakeup.service.WakeOnLanService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/machines")
public class WakeController {

    private final MachineService machineService;
    private final WakeOnLanService wakeOnLanService;
    private final ReachabilityService reachabilityService;

    public WakeController(MachineService machineService,
                          WakeOnLanService wakeOnLanService,
                          ReachabilityService reachabilityService) {
        this.machineService = machineService;
        this.wakeOnLanService = wakeOnLanService;
        this.reachabilityService = reachabilityService;
    }

    @GetMapping
    public List<MachineStatus> list() {
        return machineService.findAll().stream()
                .map(this::withStatus)
                .toList();
    }

    @GetMapping("/{name}/status")
    public MachineStatus status(@PathVariable String name) {
        return withStatus(getMachine(name));
    }

    @PostMapping("/{name}/wake")
    public ResponseEntity<MachineStatus> wake(@PathVariable String name) {
        Machine machine = getMachine(name);
        if (reachabilityService.isOnline(machine)) {
            return ResponseEntity.ok(MachineStatus.of(machine, Status.ONLINE));
        }
        wakeOnLanService.sendWake(machine);
        return ResponseEntity.accepted().body(MachineStatus.of(machine, Status.WAKING));
    }

    private MachineStatus withStatus(Machine machine) {
        Status status = reachabilityService.isOnline(machine) ? Status.ONLINE : Status.OFFLINE;
        return MachineStatus.of(machine, status);
    }

    private Machine getMachine(String name) {
        return machineService.findByName(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Machine not found: " + name));
    }
}
