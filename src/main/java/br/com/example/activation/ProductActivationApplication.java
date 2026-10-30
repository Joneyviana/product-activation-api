package br.com.example.activation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ProductActivationApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductActivationApplication.class, args);
    }
}
