package com.smartroute.smartroute1.unittest;

import com.smartroute.smartroute1.endpoint.dto.AddStopsDto;
import com.smartroute.smartroute1.endpoint.dto.StopPointDto;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonDto;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonFeature;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonGeometryLineString;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonPosition;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonProperties;
import com.smartroute.smartroute1.exception.StopTooFarFromRouteException;
import com.smartroute.smartroute1.exception.ValidationException;
import com.smartroute.smartroute1.service.OpenRouteServiceService;
import com.smartroute.smartroute1.service.impl.AddStopsServiceImpl;
import com.smartroute.smartroute1.service.validators.AddStopsValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddStopsServiceTest {

    @Mock
    private AddStopsValidator validator;

    @Mock
    private OpenRouteServiceService orsService;

    private AddStopsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AddStopsServiceImpl(validator, orsService);
    }

    // -----------------------
    // addWaypoints(...) tests
    // -----------------------

    @Test
    void addWaypoints_nullDto_throwsValidationException() {
        assertAll("addWaypoints(null) throws ValidationException",
                () -> assertThrows(ValidationException.class, () -> service.addWaypoints(null))
        );
    }

    @Test
    void addWaypoints_nullNewPoints_returnsOriginalDto() throws ValidationException {
        doNothing().when(validator).validateRouteLength(anyList());

        AddStopsDto dto = new AddStopsDto(sampleRoute(), null);

        GeoJsonDto out = service.addWaypoints(dto);
        List<GeoJsonPosition> coords = out.getFeatures().getFirst().getGeometry().getCoordinates();

        assertAll("null newPoints -> original route",
                () -> assertNotNull(out),
                () -> assertNotNull(out.getFeatures()),
                () -> assertFalse(out.getFeatures().isEmpty()),
                () -> assertNotNull(coords),
                () -> assertEquals(dto.getOriginalRoute().size(), coords.size(),
                        "should match original route size when no new points provided")
        );
    }

    @Test
    void addWaypoints_emptyNewPoints_returnsOriginalDto() throws ValidationException {
        doNothing().when(validator).validateRouteLength(anyList());

        AddStopsDto dto = new AddStopsDto(sampleRoute(), List.of());

        GeoJsonDto out = service.addWaypoints(dto);
        List<GeoJsonPosition> coords = out.getFeatures().getFirst().getGeometry().getCoordinates();

        assertAll("empty newPoints -> original route",
                () -> assertNotNull(out),
                () -> assertNotNull(coords),
                () -> assertEquals(dto.getOriginalRoute().size(), coords.size())
        );
    }

    @Test
    void addWaypoints_newPointTooFarFromRoute_throwsStopTooFarFromRouteException() throws ValidationException {
        doNothing().when(validator).validateRouteLength(anyList());
        doNothing().when(validator).validateCoordinates(anyDouble(), anyDouble());

        AddStopsDto dto = new AddStopsDto(
                sampleRoute(),
                List.of(new StopPointDto(40.7128, -74.0060, null)) // NYC
        );

        assertAll("too far stop throws",
                () -> assertThrows(StopTooFarFromRouteException.class, () -> service.addWaypoints(dto))
        );

        assertAll("validator was consulted",
                () -> verify(validator, atLeastOnce()).validateRouteLength(anyList()),
                () -> verify(validator).validateCoordinates(anyDouble(), anyDouble()),
                () -> verify(validator, never()).validateSameEndpoints(anyList(), anyList())
        );
    }

    @Test
    void addWaypoints_insertsWaypoint_whenDetourProvidedViaSpy() throws Exception {
        AddStopsServiceImpl spy = Mockito.spy(service);

        doNothing().when(validator).validateRouteLength(anyList());
        doNothing().when(validator).validateCoordinates(anyDouble(), anyDouble());
        doNothing().when(validator).validateSameEndpoints(anyList(), anyList());

        List<StopPointDto> route = List.of(
                new StopPointDto(48.20820, 16.37380, null),
                new StopPointDto(48.20830, 16.37390, null),
                new StopPointDto(48.20840, 16.37400, null)
        );

        StopPointDto viaDto = new StopPointDto(48.20831, 16.37391, null); // very close to route (<< 1km)

        AddStopsDto dto = new AddStopsDto(route, List.of(viaDto));

        doReturn(List.of(
                new GeoJsonPosition(48.20830, 16.37390, null), // start anchor
                new GeoJsonPosition(viaDto.getLatitude(), viaDto.getLongitude(), null), // via
                new GeoJsonPosition(48.20840, 16.37400, null)  // end anchor
        )).when(spy).routeThroughPoint(any(), any(), any(), eq(false));

        GeoJsonDto out = spy.addWaypoints(dto);

        List<GeoJsonPosition> outCoords = out.getFeatures().getFirst().getGeometry().getCoordinates();

        assertAll("waypoint inserted + dto valid",
                () -> assertNotNull(out),
                () -> assertNotNull(out.getFeatures()),
                () -> assertFalse(out.getFeatures().isEmpty()),
                () -> assertNotNull(out.getFeatures().getFirst().getGeometry()),
                () -> assertNotNull(outCoords),
                () -> assertTrue(outCoords.size() >= 3, "should be at least original size (often bigger)"),
                () -> assertTrue(
                        outCoords.stream().anyMatch(p ->
                                Math.abs(p.getLatitude() - viaDto.getLatitude()) < 1e-8
                                        && Math.abs(p.getLongitude() - viaDto.getLongitude()) < 1e-8),
                        "output route should contain the via point (inserted waypoint)"
                ),
                () -> assertNotNull(out.getBbox()),
                () -> assertEquals(4, out.getBbox().size(), "bbox should be [minLon, minLat, maxLon, maxLat]"),
                () -> assertNotNull(out.getFeatures().getFirst().getProperties()),
                () -> assertTrue(out.getFeatures().getFirst().getProperties().getDistance() >= 0)
        );

        InOrder inOrder = inOrder(validator);
        assertAll("validator calls",
                () -> inOrder.verify(validator).validateRouteLength(anyList()),
                () -> inOrder.verify(validator, atLeastOnce()).validateCoordinates(anyDouble(), anyDouble())
        );
    }

    // --------------------
    // reshape tests
    // --------------------

    @Test
    void reshape_nullDto_throwsValidationException() {
        assertAll("reshape(null) throws ValidationException",
                () -> assertThrows(ValidationException.class, () -> service.reshape(null))
        );
    }

    @Test
    void reshape_nullNewPoints_returnsOriginalDto() throws ValidationException {
        AddStopsDto dto = new AddStopsDto(sampleRoute(), null);

        GeoJsonDto out = service.reshape(dto);
        List<GeoJsonPosition> coords = out.getFeatures().getFirst().getGeometry().getCoordinates();

        assertAll("reshape null newPoints -> original route",
                () -> assertNotNull(out),
                () -> assertNotNull(coords),
                () -> assertEquals(dto.getOriginalRoute().size(), coords.size())
        );
    }

    @Test
    void reshape_emptyNewPoints_returnsOriginalDto() throws ValidationException {
        AddStopsDto dto = new AddStopsDto(sampleRoute(), List.of());

        GeoJsonDto out = service.reshape(dto);
        List<GeoJsonPosition> coords = out.getFeatures().getFirst().getGeometry().getCoordinates();

        assertAll("reshape empty newPoints -> original route",
                () -> assertNotNull(out),
                () -> assertNotNull(coords),
                () -> assertEquals(dto.getOriginalRoute().size(), coords.size())
        );
    }

    @Test
    void reshape_baselineTooLong_fallsBackToAddWaypoints() throws Exception {
        AddStopsServiceImpl spy = Mockito.spy(service);

        GeoJsonDto stitched = geoJsonDtoWithCoords(List.of(
                new GeoJsonPosition(48.20820, 16.37380, null),
                new GeoJsonPosition(48.20830, 16.37390, null)
        ));
        doReturn(stitched).when(spy).addWaypoints(any(AddStopsDto.class));

        GeoJsonDto baselineTooLong = geoJsonDtoWithCoords(List.of(
                new GeoJsonPosition(0.0, 0.0, null),
                new GeoJsonPosition(50.0, 50.0, null)
        ));
        when(orsService.requestRoute(anyList(), eq(false))).thenReturn(baselineTooLong);

        AddStopsDto dto = new AddStopsDto(
                sampleRoute(),
                List.of(new StopPointDto(48.20831, 16.37391, null))
        );

        GeoJsonDto out = spy.reshape(dto);

        assertAll("fallback returns stitched route",
                () -> assertSame(stitched, out, "reshape should return addWaypoints(...) result when baseline too long")
        );

        assertAll("no roundTrip generation when falling back",
                () -> verify(orsService, never()).generateRoundTrip(anyList(), anyInt(), anyInt(), anyInt())
        );
    }

    // -----------------------
    // Test data / helpers
    // -----------------------

    private List<StopPointDto> sampleRoute() {
        return List.of(
                new StopPointDto(48.20820, 16.37380, null),
                new StopPointDto(48.20860, 16.37280, null),
                new StopPointDto(48.20790, 16.37210, null),
                new StopPointDto(48.20760, 16.37330, null),
                new StopPointDto(48.20820, 16.37380, null)
        );
    }

    private static GeoJsonDto geoJsonDtoWithCoords(List<GeoJsonPosition> coords) {
        GeoJsonGeometryLineString geom = new GeoJsonGeometryLineString();
        geom.setType("LineString");
        geom.setCoordinates(new ArrayList<>(coords));

        GeoJsonProperties props = new GeoJsonProperties();
        props.setDistance(0);

        GeoJsonFeature feature = new GeoJsonFeature();
        feature.setType("Feature");
        feature.setGeometry(geom);
        feature.setProperties(props);

        GeoJsonDto dto = new GeoJsonDto();
        dto.setType("FeatureCollection");
        dto.setFeatures(List.of(feature));
        return dto;
    }
}
