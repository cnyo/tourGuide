package com.openclassrooms.tourguide.dto;

public class NearbyAttractionDto {
    private String name;
    private Double latitude;
    private Double longitude;
    private Double latitudeUser;
    private Double longitudeUser;
    private Double distance;
    private int rewardPoints;

    public NearbyAttractionDto(String name, Double latitude, Double longitude, Double latitudeUser, Double longitudeUser, Double distance, int rewardPoints) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.latitudeUser = latitudeUser;
        this.longitudeUser = longitudeUser;
        this.distance = distance;
        this.rewardPoints = rewardPoints;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getLatitudeUser() {
        return latitudeUser;
    }

    public void setLatitudeUser(Double latitudeUser) {
        this.latitudeUser = latitudeUser;
    }

    public Double getLongitudeUser() {
        return longitudeUser;
    }

    public void setLongitudeUser(Double longitudeUser) {
        this.longitudeUser = longitudeUser;
    }

    public Double getDistance() {
        return distance;
    }

    public void setDistance(Double distance) {
        this.distance = distance;
    }

    public int getRewardPoints() {
        return rewardPoints;
    }

    public void setRewardPoints(int rewardPoints) {
        this.rewardPoints = rewardPoints;
    }
}
