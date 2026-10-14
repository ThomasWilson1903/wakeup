package ru.twilson.wakeup.controller;

import ru.twilson.wakeup.model.Machine;
import ru.twilson.wakeup.model.Status;
import ru.twilson.wakeup.service.MachineService;
import ru.twilson.wakeup.service.ReachabilityService;
import ru.twilson.wakeup.service.ShutdownException;
import ru.twilson.wakeup.service.ShutdownService;
import ru.twilson.wakeup.service.WakeOnLanService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WakeController.class)
class WakeControllerTest {

    private static final Machine MACHINE =
            new Machine("twilson-server", "0a:e0:af:af:2d:d5", "100.92.222.28", 22, "192.168.88.255", "twilson");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MachineService machineService;

    @MockitoBean
    private WakeOnLanService wakeOnLanService;

    @MockitoBean
    private ReachabilityService reachabilityService;

    @MockitoBean
    private ShutdownService shutdownService;

    @Test
    void shutdownOnlineMachineReturnsAccepted() throws Exception {
        when(machineService.findByName("twilson-server")).thenReturn(Optional.of(MACHINE));
        when(reachabilityService.isOnline(MACHINE)).thenReturn(true);

        mockMvc.perform(post("/api/machines/twilson-server/shutdown"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value(Status.STOPPING.name()));

        verify(shutdownService).shutdown(MACHINE);
    }

    @Test
    void shutdownOfflineMachineReturnsOk() throws Exception {
        when(machineService.findByName("twilson-server")).thenReturn(Optional.of(MACHINE));
        when(reachabilityService.isOnline(MACHINE)).thenReturn(false);

        mockMvc.perform(post("/api/machines/twilson-server/shutdown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(Status.OFFLINE.name()));

        verify(shutdownService, never()).shutdown(MACHINE);
    }

    @Test
    void shutdownUnknownMachineReturnsNotFound() throws Exception {
        when(machineService.findByName("nope")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/machines/nope/shutdown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shutdownFailureReturnsBadGateway() throws Exception {
        when(machineService.findByName("twilson-server")).thenReturn(Optional.of(MACHINE));
        when(reachabilityService.isOnline(MACHINE)).thenReturn(true);
        doThrow(new ShutdownException("ssh failed")).when(shutdownService).shutdown(MACHINE);

        mockMvc.perform(post("/api/machines/twilson-server/shutdown"))
                .andExpect(status().isBadGateway());
    }
}
