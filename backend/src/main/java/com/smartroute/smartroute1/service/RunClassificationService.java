package com.smartroute.smartroute1.service;

import com.smartroute.smartroute1.endpoint.dto.RunClassificationDto;
import com.smartroute.smartroute1.endpoint.dto.RunClassificationResultDto;
import com.smartroute.smartroute1.entity.enums.WorkoutType;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface RunClassificationService {
    /**
     * A non trained model as a labelling basis to determine what type of run the user ran.
     *
     * @param dto the dto including all relevant information for classification. See RunClassificationDto for detailed
     *            content explanation.
     * @return a result dto containing the classification and the initial dto
     */
    RunClassificationResultDto classifyRun(RunClassificationDto dto);


    /**
     * Takes information about runs from a csv files, parses them to a dto and classifies them.
     *
     * @param csvPath       the path to the file
     * @param outputCsvPath the path the new file will be stored
     * @return the dto list with classification
     * @throws IOException if the input file could not be found
     */
    Path classifyCsv(Path csvPath, Path outputCsvPath) throws IOException;
}
