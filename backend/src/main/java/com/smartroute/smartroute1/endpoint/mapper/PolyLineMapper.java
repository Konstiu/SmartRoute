package com.smartroute.smartroute1.endpoint.mapper;

import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonGeometryLineString;
import com.smartroute.smartroute1.endpoint.dto.geojson.GeoJsonPosition;
import org.mapstruct.Mapper;

@Mapper
public interface PolyLineMapper {

    default String geoJsonGeometryLineStringToPolyline(GeoJsonGeometryLineString lineString) {
        StringBuilder sb = new StringBuilder();
        double lastLat = 0;
        double lastLong = 0;
        for (GeoJsonPosition pos : lineString.getCoordinates()) {
            convertCoordinate(pos.getLatitude() - lastLat, sb);
            convertCoordinate(pos.getLongitude() - lastLong, sb);
            lastLat = pos.getLatitude();
            lastLong = pos.getLongitude();
        }
        return sb.toString();
    }

    private void convertCoordinate(double coordinate, StringBuilder sb) {
        int coord = (int) Math.round(coordinate * 1e6);
        coord <<= 1;
        if (coordinate < 0) {
            coord = ~coord;
        }
        while (coord >= 0x20) {
            sb.append((char) (((coord & 0b11111) | 0x20) + 63));
            coord >>= 5;
        }
        sb.append((char) (coord + 63));
    }
}