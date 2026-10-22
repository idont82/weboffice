package weboffice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import weboffice.view.ViewProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(ViewProperties.class)
public class WebOfficeApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebOfficeApplication.class, args);
    }
}
