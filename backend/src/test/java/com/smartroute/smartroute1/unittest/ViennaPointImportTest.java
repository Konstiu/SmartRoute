package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.entity.ViennaPoint;
import com.smartroute.smartroute1.repository.ViennaPointRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "generateData"})
@AutoConfigureMockMvc
public class ViennaPointImportTest {

    @Autowired
    private ViennaPointRepository repository;
    @Test
    public void testViennaPointImportReturnsNonEmptyRepository() {
        assertNotEquals(1,repository.count());
    }

    @Test
    public void testViennaPointImportImportsToilets() {
        List<ViennaPoint> viennaPoints = repository.findAll();

        ViennaPoint fountainPoint = viennaPoints.stream().filter(ViennaPoint::isToilet).findFirst().get();

        assertNotNull(fountainPoint);

    }

    @Test
    public void testViennaPointImportImportsFountains() {
        List<ViennaPoint> viennaPoints = repository.findAll();

        ViennaPoint fountainPoint = viennaPoints.stream().filter(ViennaPoint::isFountain).findFirst().get();

        assertNotNull(fountainPoint);
    }


}
