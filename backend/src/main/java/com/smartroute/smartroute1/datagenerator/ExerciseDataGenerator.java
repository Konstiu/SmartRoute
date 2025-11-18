package com.smartroute.smartroute1.datagenerator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartroute.smartroute1.endpoint.dto.ExerciseDto;
import com.smartroute.smartroute1.entity.Exercise;
import com.smartroute.smartroute1.repository.ExerciseRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.List;

@Profile("generateData")
@Component
public class ExerciseDataGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String EXERCISEFILENAME = "exercises.json";
    private final ExerciseRepository repository;

    public ExerciseDataGenerator(ExerciseRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void importExercises() {
        if (!repository.findAll().isEmpty()) {
            LOGGER.info("Exercises already generated");
        } else {
            try {
                LOGGER.info("Importing exercises.json");
                ObjectMapper mapper = new ObjectMapper();
                InputStream is = getClass().getClassLoader().getResourceAsStream(EXERCISEFILENAME);

                if (is == null) {
                    LOGGER.error("Could not find exercises.json");
                    return;
                }

                List<ExerciseDto> dtos = mapper.readValue(
                        is,
                        new TypeReference<List<ExerciseDto>>() {
                        }
                );

                List<Exercise> entities = dtos.stream().map(this::mapToEntity).toList();

                repository.saveAll(entities);
                LOGGER.info("Successfully imported " + dtos.size() + " exercises");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private Exercise mapToEntity(ExerciseDto dto) {
        Exercise e = new Exercise();
        e.setId(dto.getExerciseId());
        e.setName(dto.getName());
        e.setGifUrl(dto.getGifUrl());
        e.setTargetMuscles(dto.getTargetMuscles());
        e.setBodyParts(dto.getBodyParts());
        e.setEquipments(dto.getEquipments());
        e.setSecondaryMuscles(dto.getSecondaryMuscles());
        e.setInstructions(dto.getInstructions());
        return e;
    }
}
