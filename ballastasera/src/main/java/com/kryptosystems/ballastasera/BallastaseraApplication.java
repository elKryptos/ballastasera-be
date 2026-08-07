package com.kryptosystems.ballastasera;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class BallastaseraApplication {

    public static void main(String[] args) {
        SpringApplication.run(BallastaseraApplication.class, args);
    }

}
