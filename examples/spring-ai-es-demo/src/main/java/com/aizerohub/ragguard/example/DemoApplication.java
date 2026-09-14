package com.aizerohub.ragguard.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring AI + Elasticsearch demo application. Start an Elasticsearch
 * instance, provide OPENAI_API_KEY, then:
 *
 * <pre>
 * # ingest the knowledge base (one-time)
 * ./mvnw -pl examples/spring-ai-es-demo spring-boot:run -Dspring-boot.run.profiles=ingest
 *
 * # start the app / run the evaluation tests
 * ./mvnw -pl examples/spring-ai-es-demo test
 * </pre>
 */
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
