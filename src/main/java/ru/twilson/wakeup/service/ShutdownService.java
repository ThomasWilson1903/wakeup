package ru.twilson.wakeup.service;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.twilson.wakeup.config.WakeupProperties;
import ru.twilson.wakeup.model.Machine;

import java.util.concurrent.TimeUnit;

@Service
public class ShutdownService {

    private static final Logger log = LoggerFactory.getLogger(ShutdownService.class);

    private final WakeupProperties properties;

    public ShutdownService(WakeupProperties properties) {
        this.properties = properties;
    }

    public void shutdown(Machine machine) {
        String user = machine.sshUser();
        if (user == null || user.isBlank()) {
            throw new ShutdownException("SSH user is not configured for machine: " + machine.name());
        }

        String keyPath = properties.ssh().privateKey();
        if (keyPath == null || keyPath.isBlank()) {
            throw new ShutdownException("SSH private key is not configured (wakeup.ssh.private-key)");
        }

        String command = properties.ssh().shutdownCommand();
        int connectTimeout = properties.ssh().connectTimeoutMs();
        int commandTimeout = properties.ssh().commandTimeoutMs();

        SSHClient ssh = new SSHClient();
        try {
            ssh.addHostKeyVerifier(new PromiscuousVerifier());
            ssh.setConnectTimeout(connectTimeout);
            ssh.setTimeout(commandTimeout);
            ssh.connect(machine.ip(), machine.port());

            KeyProvider keyProvider = ssh.loadKeys(keyPath);
            ssh.authPublickey(user, keyProvider);

            boolean initiated;
            try (Session session = ssh.startSession()) {
                Session.Command cmd = session.exec(command);
                cmd.join(commandTimeout, TimeUnit.MILLISECONDS);
                Integer exitStatus = cmd.getExitStatus();
                if (exitStatus == null) {
                    initiated = true;
                } else if (exitStatus == 0) {
                    initiated = true;
                } else {
                    throw new ShutdownException("Shutdown command exited with status " + exitStatus);
                }
            } catch (ShutdownException e) {
                throw e;
            } catch (Exception e) {
                initiated = true;
            }

            if (initiated) {
                log.info("Shutdown initiated for '{}' ({}@{})", machine.name(), user, machine.ip());
            }
        } catch (ShutdownException e) {
            throw e;
        } catch (Exception e) {
            throw new ShutdownException("Failed to shut down '" + machine.name() + "': " + e.getMessage(), e);
        } finally {
            try {
                ssh.close();
            } catch (Exception ignored) {
            }
        }
    }
}
