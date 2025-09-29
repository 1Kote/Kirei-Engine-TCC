package br.com.tcc.kireiengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Aplicação principal do Kirei Engine usando Spring Boot
 */
@SpringBootApplication
@EnableScheduling
public class KireiEngineApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(KireiEngineApplication.class, args);
    }
}