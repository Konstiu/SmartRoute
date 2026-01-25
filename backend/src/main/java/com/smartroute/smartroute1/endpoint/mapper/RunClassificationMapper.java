package com.smartroute.smartroute1.endpoint.mapper;

import com.smartroute.smartroute1.endpoint.dto.RunClassificationDecisionDto;
import com.smartroute.smartroute1.entity.RunClassificationDecision;
import org.mapstruct.Mapper;

@Mapper
public interface RunClassificationMapper {
    default RunClassificationDecision dtoToEntity(RunClassificationDecisionDto runClassification) {
        if (runClassification == null) {
            return null;
        }

        RunClassificationDecision entity = new RunClassificationDecision();
        entity.setProbabilities(runClassification.getProbabilities());
        entity.setRunType(runClassification.getRunType());

        return entity;
    }

    default RunClassificationDecisionDto entityToDto(RunClassificationDecision runClassification) {
        if (runClassification == null) {
            return null;
        }

        RunClassificationDecisionDto dto = new RunClassificationDecisionDto();
        dto.setProbabilities(runClassification.getProbabilities());
        dto.setRunType(runClassification.getRunType());

        return dto;
    }
}
