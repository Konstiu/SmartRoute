package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.RunClassificationDto;
import com.smartroute.smartroute1.entity.enums.WorkoutType;

public interface RunClassificationService {
    /**
     * A non trained model as a labelling basis to determine what type of run the user ran.
     *
     * @param dto the dto including all relevant information for classification. See RunClassificationDto for detailed
     *            content explanation.
     * @return the type of run the run has been labelled as.
     */
    WorkoutType classifyRun(RunClassificationDto dto);
}
