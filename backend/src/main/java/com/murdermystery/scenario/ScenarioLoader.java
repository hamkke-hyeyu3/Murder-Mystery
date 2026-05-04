package com.murdermystery.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ScenarioLoader {

    private static final Logger log = LoggerFactory.getLogger(ScenarioLoader.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScenarioCrossFieldValidator crossFieldValidator = new ScenarioCrossFieldValidator();
    private final JsonSchema jsonSchema;
    private final String locationPattern;

    public ScenarioLoader(String locationPattern) throws IOException {
        this.locationPattern = locationPattern;
        try (InputStream schemaStream = getClass().getClassLoader()
                .getResourceAsStream("scenario-schema.json")) {
            if (schemaStream == null) {
                throw new IOException("scenario-schema.json not found on classpath");
            }
            this.jsonSchema = JsonSchemaFactory
                .getInstance(SpecVersion.VersionFlag.V7)
                .getSchema(schemaStream);
        }
    }

    public List<Scenario> loadAll() {
        List<Scenario> valid = new ArrayList<>();
        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver().getResources(locationPattern);
        } catch (IOException e) {
            log.warn("Failed to scan scenarios at {}: {}", locationPattern, e.getMessage());
            return valid;
        }
        for (Resource resource : resources) {
            String path = resource.getFilename();
            try (InputStream in = resource.getInputStream()) {
                JsonNode node = objectMapper.readTree(in);
                Set<ValidationMessage> errors = jsonSchema.validate(node);
                if (!errors.isEmpty()) {
                    log.warn("Scenario {} rejected: schema violation — {}",
                        path, errors.iterator().next().getMessage());
                    continue;
                }
                Scenario scenario = objectMapper.treeToValue(node, Scenario.class);
                var crossError = crossFieldValidator.validate(scenario);
                if (crossError.isPresent()) {
                    log.warn("Scenario {} rejected: {}", path, crossError.get());
                    continue;
                }
                valid.add(scenario);
            } catch (IOException e) {
                log.warn("Scenario {} rejected: parse error — {}", path, e.getMessage());
            }
        }
        return valid;
    }
}
