package com.codevam.vecindad;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VecindadApplication {
    public static void main(String[] args) {
        SpringApplication.run(VecindadApplication.class, args);
    }
}
