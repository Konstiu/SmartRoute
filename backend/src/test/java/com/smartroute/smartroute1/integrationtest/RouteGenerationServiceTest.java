package com.smartroute.smartroute1.integrationtest;

import com.smartroute.smartroute1.endpoint.dto.geojson.*;
import com.smartroute.smartroute1.repository.ActivityRepository;
import com.smartroute.smartroute1.service.OpenRouteServiceService;
import com.smartroute.smartroute1.service.RouteGenerationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@SpringBootTest
class RouteGenerationServiceTest {

    @Autowired
    private RouteGenerationService routeGenerationService;

    @MockitoBean
    private ActivityRepository activityRepository;

    @MockitoBean
    private OpenRouteServiceService openRouteServiceService;

    @Test
    void generateRoute_withStartInVienna_shouldGenerateRoute() throws Exception {
        Random random = new Random();
        int length = 2000 + random.nextInt(0, 2000);


        when(openRouteServiceService.requestElevation(any())).thenReturn(List.of(
                new GeoJsonPosition(16.338227, 48.188802, 181.0),
                new GeoJsonPosition(16.344972, 48.188804, 183.0),
                new GeoJsonPosition(16.378697, 48.188791, 202.0),
                new GeoJsonPosition(16.392186, 48.19327, 183.0),
                new GeoJsonPosition(16.398931, 48.193269, 175.0),
                new GeoJsonPosition(16.378698, 48.202234, 169.0),
                new GeoJsonPosition(16.385445, 48.202233, 176.0),
                new GeoJsonPosition(16.392192, 48.202232, 178.0),
                new GeoJsonPosition(16.358452, 48.220173, 176.0),
                new GeoJsonPosition(16.365201, 48.220174, 178.0),
                new GeoJsonPosition(16.385448, 48.220157, 170.0),
                new GeoJsonPosition(16.385458, 48.2336, 163.0),
                new GeoJsonPosition(16.392209, 48.233599, 167.0),
                new GeoJsonPosition(16.39896, 48.233598, 154.0),
                new GeoJsonPosition(16.405711, 48.233596, 154.0))
        );

        GeoJsonDto dto = new GeoJsonDto();
        GeoJsonFeature feature = new GeoJsonFeature();
        dto.setFeatures(List.of(feature));
        GeoJsonProperties props = new GeoJsonProperties();
        feature.setProperties(props);
        GeoJsonGeometryLineString ls = new GeoJsonGeometryLineString();
        feature.setGeometry(ls);

        dto.setType("FeatureCollection");
        dto.setBbox(List.of(16.371952, 48.211206, 153.27, 16.412702, 48.233733, 191.0));

        feature.setBbox(List.of(16.371952, 48.211206, 153.27, 16.412702, 48.233733, 191.0));
        feature.setType("Feature");

        props.setAscent(179.8);
        props.setDescent(179.8);
        props.setDistance(9590.0);

        ls.setType("LineString");
        List<GeoJsonPosition> coordinates = new ArrayList<>(List.of(new GeoJsonPosition(16.371952, 48.211212, 188.0),
                new GeoJsonPosition(16.372825, 48.211916, 191.0),
                new GeoJsonPosition(16.373935, 48.211673, 188.0),
                new GeoJsonPosition(16.375274, 48.21172, 186.0),
                new GeoJsonPosition(16.3768, 48.211809, 172.0),
                new GeoJsonPosition(16.378346, 48.21148, 185.0),
                new GeoJsonPosition(16.378918, 48.211685, 168.0),
                new GeoJsonPosition(16.379317, 48.212492, 164.0),
                new GeoJsonPosition(16.37996, 48.212636, 170.0),
                new GeoJsonPosition(16.380865, 48.212869, 169.0),
                new GeoJsonPosition(16.382675, 48.21367, 175.0),
                new GeoJsonPosition(16.383265, 48.213917, 175.0),
                new GeoJsonPosition(16.384549, 48.214581, 174.0),
                new GeoJsonPosition(16.387699, 48.216263, 168.0),
                new GeoJsonPosition(16.390331, 48.21765, 167.0),
                new GeoJsonPosition(16.39125, 48.217879, 165.5),
                new GeoJsonPosition(16.392032, 48.218446, 157.0),
                new GeoJsonPosition(16.392629, 48.218841, 156.0),
                new GeoJsonPosition(16.393446, 48.219335, 160.0),
                new GeoJsonPosition(16.394288, 48.21979, 164.0),
                new GeoJsonPosition(16.39903, 48.222341, 165.0),
                new GeoJsonPosition(16.401619, 48.224091, 168.0),
                new GeoJsonPosition(16.402918, 48.224916, 164.0),
                new GeoJsonPosition(16.404694, 48.225736, 160.0),
                new GeoJsonPosition(16.411134, 48.229182, 154.0),
                new GeoJsonPosition(16.412391, 48.229839, 154.0),
                new GeoJsonPosition(16.412063, 48.230427, 160.0),
                new GeoJsonPosition(16.410057, 48.231693, 155.0),
                new GeoJsonPosition(16.409204, 48.231956, 154.0),
                new GeoJsonPosition(16.409204, 48.231956, 154.0),
                new GeoJsonPosition(16.410057, 48.231693, 155.0),
                new GeoJsonPosition(16.412063, 48.230427, 160.0),
                new GeoJsonPosition(16.412391, 48.229839, 154.0),
                new GeoJsonPosition(16.411134, 48.229182, 154.0),
                new GeoJsonPosition(16.404694, 48.225736, 160.0),
                new GeoJsonPosition(16.402918, 48.224916, 164.0),
                new GeoJsonPosition(16.401619, 48.224091, 168.0),
                new GeoJsonPosition(16.39903, 48.222341, 165.0),
                new GeoJsonPosition(16.394288, 48.21979, 164.0),
                new GeoJsonPosition(16.393446, 48.219335, 160.0),
                new GeoJsonPosition(16.392629, 48.218841, 156.0),
                new GeoJsonPosition(16.392032, 48.218446, 157.0),
                new GeoJsonPosition(16.39125, 48.217879, 165.5),
                new GeoJsonPosition(16.390331, 48.21765, 167.0),
                new GeoJsonPosition(16.387699, 48.216263, 168.0),
                new GeoJsonPosition(16.384549, 48.214581, 174.0),
                new GeoJsonPosition(16.383265, 48.213917, 175.0),
                new GeoJsonPosition(16.382675, 48.21367, 175.0),
                new GeoJsonPosition(16.380865, 48.212869, 169.0),
                new GeoJsonPosition(16.37996, 48.212636, 170.0),
                new GeoJsonPosition(16.379317, 48.212492, 164.0),
                new GeoJsonPosition(16.378918, 48.211685, 168.0),
                new GeoJsonPosition(16.378346, 48.21148, 185.0),
                new GeoJsonPosition(16.3768, 48.211809, 172.0),
                new GeoJsonPosition(16.375274, 48.21172, 186.0),
                new GeoJsonPosition(16.373935, 48.211673, 188.0),
                new GeoJsonPosition(16.372825, 48.211916, 191.0),
                new GeoJsonPosition(16.371952, 48.211212, 188.)));
        ls.setCoordinates(coordinates);
        when(openRouteServiceService.requestRoute(any(), anyBoolean())).thenReturn(dto);

        GeoJsonPosition position = new GeoJsonPosition(48.21129045708595, 16.37195107544338, null);
        GeoJsonDto route = routeGenerationService.generateRoundTrip(position, length);


        assertAll(
                () -> assertNotNull(route),
                () -> assertEquals(1, route.getFeatures().size()),
                () -> assertNotNull(route.getFeatures().getFirst())
        );
        GeoJsonFeature f = route.getFeatures().getFirst();
        assertAll(
                () -> assertTrue(f.getProperties().getDistance() <= length),
                () -> assertEquals(f.getProperties().getAscent(), f.getProperties().getDescent()),
                () -> assertTrue(f.getGeometry().getCoordinates().size() > 3),
                () -> assertEquals(f.getGeometry().getCoordinates().getFirst().getLatitude(),
                        f.getGeometry().getCoordinates().getLast().getLatitude(), 0),
                () -> assertEquals(f.getGeometry().getCoordinates().getFirst().getLongitude(),
                        f.getGeometry().getCoordinates().getLast().getLongitude(), 0)
        );
    }
}
