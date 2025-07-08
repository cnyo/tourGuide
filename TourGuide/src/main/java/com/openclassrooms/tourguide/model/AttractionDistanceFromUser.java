package com.openclassrooms.tourguide.model;

import gpsUtil.location.Attraction;

import java.util.Comparator;

/**
 * AttractionDistanceFromUser represents an attraction along with the distance from the user.
 * It is used to store and compare distances of attractions from a user's current location.
 */
public class AttractionDistanceFromUser {
    private Attraction attraction;
    private double distance;

    public AttractionDistanceFromUser(Attraction attraction, double distance) {
        this.attraction = attraction;
        this.distance = distance;
    }

    public Attraction getAttraction() {
        return attraction;
    }

    public void setAttraction(Attraction attraction) {
        this.attraction = attraction;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public static Comparator<AttractionDistanceFromUser> comparingByDistance() {
        return Comparator.comparingDouble(AttractionDistanceFromUser::getDistance);
    }
}

