package com.cabzen.dto;

public final class GeoDtos {

    private GeoDtos() {
    }

    /** @param name short label for inputs, @param address full Nominatim address */
    public record Place(String name, String address, double lat, double lng) {
    }

    public record Address(String name) {
    }
}
