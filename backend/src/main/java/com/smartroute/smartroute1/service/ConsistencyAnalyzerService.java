package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.ConsistencyScoreResultDto;
import com.smartroute.smartroute1.entity.ApplicationUser;

import java.time.Instant;


public interface ConsistencyAnalyzerService {
    /**
     * The Consistency Analyzer evaluates how closely an athlete’s executed training aligns
     * with the prescribed training intent over time. Its primary objective is to quantify
     * training adherence and detect deviations such as undertraining, overtraining, irreg-
     * ular session frequency, or mismatched training intensity. The resulting Consistency
     * Score is used by the Training Plan Generator to adapt future loads and maintain
     * an optimal progression rhythm.
     *
     * @param user                    The user to get the score from
     * @param start                   The start date of the interval
     * @param end                     The end date of the interval
     * @param plannedTrainingSessions The number of training sessions the user planned to do in a week
     * @return A score dto consisting of the frequency consistency score, regularity consistency score and final score
     */
    ConsistencyScoreResultDto computeScore(ApplicationUser user, Instant start, Instant end, int plannedTrainingSessions);
}
