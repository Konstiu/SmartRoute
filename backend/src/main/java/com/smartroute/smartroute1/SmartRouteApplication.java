package com.smartroute.smartroute1;

import com.smartroute.smartroute1.datagenerator.RunClassificationDataGenerator;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

@SpringBootApplication
public class SmartRouteApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartRouteApplication.class, args);
    }

    @Profile("datagen_train")
    @Bean
    CommandLineRunner generateRunDataset(
            RunClassificationDataGenerator generator) {

        return args -> {
            generator.createCsv();
            System.out.println("RunDataset.csv generated");
        };
    }
}
