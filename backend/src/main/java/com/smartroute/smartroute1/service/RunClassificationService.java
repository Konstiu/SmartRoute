package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.RunClassificationDecisionDto;
import com.smartroute.smartroute1.entity.enums.RunType;

/**
 * Service for automatically classifying a run.
 */
public interface RunClassificationService {
    /**
     * Classifies the given run and saves the result.
     *
     * @param activityId the id of the activity to classify.
     * @return the run classification and a map with the probability of each run type or null if the activity is not a run.
     */
    RunClassificationDecisionDto classifyRun(Long activityId);

    /**
     * Apply a users manual correction to a run prediction and update the correction map.
     *
     * @param activityId id of the run to update
     * @return the new result dto
     */
    void correctRun(Long activityId, RunType runType);
}
