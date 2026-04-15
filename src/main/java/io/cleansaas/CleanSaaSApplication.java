package io.cleansaas;

import io.cleansaas.config.CamundaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(CamundaProperties.class)
public class CleanSaaSApplication {

    public static void main(String[] args) {
        SpringApplication.run(CleanSaaSApplication.class, args);
    }
}
