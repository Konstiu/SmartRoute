package com.smartroute.smartroute1.endpoint.dto.geojson;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Data
@Getter
@Setter
@JsonDeserialize(using = GeoJsonPosition.GeoJsonPositionDeserializer.class)
public class GeoJsonPosition {
    private double latitude;
    private double longitude;
    private Double altitude;

    public GeoJsonPosition(double latitude, double longitude, Double altitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
    }

    @JsonValue
    public List<Double> toJson() {
        List<Double> coordinates = new ArrayList<>();
        coordinates.add(latitude);
        coordinates.add(longitude);
        if (altitude != null) {
            coordinates.add(altitude);
        }
        return coordinates;
    }

    static class GeoJsonPositionDeserializer extends JsonDeserializer<GeoJsonPosition> {
        @Override
        public GeoJsonPosition deserialize(JsonParser p, DeserializationContext ctxt)
                throws IOException, JsonProcessingException {
            if (p.currentToken() != JsonToken.START_ARRAY) {
                throw new JsonParseException(p, "GeoJsonPosition is encoded as an array (missing START_ARRAY token).");
            }
            p.nextToken();
            double latitude = p.getDoubleValue();
            p.nextToken();
            double longitude = p.getDoubleValue();
            Double altitude = null;
            if (p.nextToken() != JsonToken.END_ARRAY) {
                altitude = p.getDoubleValue();
                if (p.nextToken() != JsonToken.END_ARRAY) {
                    throw new JsonParseException(p, "GeoJsonPosition is encoded as an array with at most 3 elements (should be END_ARRAY but is " + p.getCurrentToken() + ").");
                }
            }
            return new GeoJsonPosition(latitude, longitude, altitude);
        }
    }
}
