package com.openclassrooms.tourguide.mapper;

import com.openclassrooms.tourguide.dto.NearbyAttractionDto;
import com.openclassrooms.tourguide.model.AttractionDistanceFromUser;
import com.openclassrooms.tourguide.user.User;
import gpsUtil.location.VisitedLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import rewardCentral.RewardCentral;

import java.util.List;

@Component
public class MapperNearbyAttractionDto {

    private static final Logger log = LoggerFactory.getLogger(MapperNearbyAttractionDto.class);
    private final RewardCentral rewardsCentral;

    public MapperNearbyAttractionDto(RewardCentral rewardsCentral) {
        this.rewardsCentral = rewardsCentral;
    }

    public List<NearbyAttractionDto> mapAttractionsToNearAttractionsDto(List<AttractionDistanceFromUser> attractionDistances, VisitedLocation visitedLocation, User user) {
        log.info("Mapping {} attractions to NearbyAttractionDto", attractionDistances.size());
        return attractionDistances.stream().
                map(attractionDistance -> new NearbyAttractionDto(
                        attractionDistance.getAttraction().attractionName, // Attraction name
                        attractionDistance.getAttraction().latitude, // Attraction latitude
                        attractionDistance.getAttraction().longitude, // Attraction longitude
                        visitedLocation.location.latitude, // User latitude
                        visitedLocation.location.longitude, // User longitude
                        attractionDistance.getDistance(), // Distance
                        rewardsCentral.getAttractionRewardPoints(attractionDistance.getAttraction().attractionId, user.getUserId()) // Reward points
                )).
                toList();
    }
}
