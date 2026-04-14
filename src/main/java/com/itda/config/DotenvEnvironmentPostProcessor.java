package com.itda.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String DOTENV_PROPERTY_SOURCE_NAME = "dotenv";
    private static final String DOTENV_FILE_NAME = ".env";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path dotenvPath = Path.of(System.getProperty("user.dir"), DOTENV_FILE_NAME);
        if (!Files.exists(dotenvPath)) {
            return;
        }

        Map<String, Object> dotenvValues = loadDotenvFile(dotenvPath);
        if (dotenvValues.isEmpty()) {
            return;
        }

        PropertySource<?> propertySource = new MapPropertySource(DOTENV_PROPERTY_SOURCE_NAME, dotenvValues);
        environment.getPropertySources().addFirst(propertySource);
    }

    private Map<String, Object> loadDotenvFile(Path dotenvPath) {
        Map<String, Object> values = new LinkedHashMap<>();

        try (BufferedReader reader = Files.newBufferedReader(dotenvPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();

                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                    continue;
                }

                int equalsIndex = trimmedLine.indexOf('=');
                if (equalsIndex <= 0) {
                    continue;
                }

                String key = trimmedLine.substring(0, equalsIndex).trim();
                String value = trimmedLine.substring(equalsIndex + 1).trim();

                if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }

                if (!key.isEmpty()) {
                    values.put(key, value);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load .env file from " + dotenvPath, ex);
        }

        return values;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
