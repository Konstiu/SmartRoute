package com.smartroute.smartroute1.datagenerator;

import com.smartroute.smartroute1.entity.ViennaPoint;
import com.smartroute.smartroute1.repository.ViennaPointRepository;
import com.smartroute.smartroute1.util.Coordinate;
import com.smartroute.smartroute1.entity.enums.Sanitary;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;

@Profile("generateData")
@Component
public class ViennaPointDataGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String FOUNTAINFILENAME ="TRINKBRUNNENOGD.csv";
    private static final String TOILETFILENAME ="WCANLAGE2OGD.csv";
    private final ViennaPointRepository repository;

    public ViennaPointDataGenerator(ViennaPointRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void importPointsOfInterest() {
        ClassPathResource fountains = new ClassPathResource(FOUNTAINFILENAME);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(fountains.getInputStream()))) {
            LOGGER.info("Importing from" + FOUNTAINFILENAME);
            String line = br.readLine(); //Skip header;

            while ((line =br.readLine()) != null) {
                System.out.println(line);
                String[] parts = line.split(",");
                String objectId = parts[1];
                String point = parts[2];

                Coordinate coordinate = parsePoint(point);

                ViennaPoint poi = new ViennaPoint();

                poi.setId(objectId);
                poi.setCoordinate(coordinate);
                poi.setType(Sanitary.Fountain);

                repository.save(poi);
                LOGGER.debug("Saving fountain with id: " + objectId);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        ClassPathResource toilets = new ClassPathResource(TOILETFILENAME);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(toilets.getInputStream()))) {
            LOGGER.info("Importing from" + TOILETFILENAME);
            String line = br.readLine(); //Skip header;

            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                String objectId = parts[3];
                String point = parts[2];

                Coordinate coordinate = parsePoint(point);

                ViennaPoint poi = new ViennaPoint();

                poi.setId(objectId);
                poi.setCoordinate(coordinate);
                poi.setType(Sanitary.Toilet);

                repository.save(poi);
                LOGGER.debug("Saving toilet with id: " + objectId);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private Coordinate parsePoint(String shape) {
        // FORMAT: POINT (lon lat)
        String inner = shape.substring(shape.indexOf("(") + 1, shape.indexOf(")"));
        String[] xy = inner.trim().split(" ");

        double lon = Double.parseDouble(xy[0]);
        double lat = Double.parseDouble(xy[1]);

        return new Coordinate(lat, lon);
    }
}
