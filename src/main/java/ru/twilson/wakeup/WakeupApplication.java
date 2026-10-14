package ru.twilson.wakeup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WakeupApplication {

    public static void main(String[] args) {
        SpringApplication.run(WakeupApplication.class, args);
    }
}
