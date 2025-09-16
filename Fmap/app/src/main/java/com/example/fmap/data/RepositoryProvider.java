package com.example.fmap.data;

public final class RepositoryProvider {
    private static PlacesRepository places;

    public static synchronized void init(PlacesRepository repo) {
        places = repo;
    }

    public static synchronized PlacesRepository getPlaces() {
        if (places == null) throw new IllegalStateException("Repository not initialized");
        return places;
    }
}
