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

/**
 * MapperNearbyAttractionDto is responsible for mapping AttractionDistanceFromUser
 * objects to NearbyAttractionDto objects, which contain details about nearby attractions.
 */
@Component
public class MapperNearbyAttractionDto {

    private static final Logger log = LoggerFactory.getLogger(MapperNearbyAttractionDto.class);
    private final RewardCentral rewardsCentral;

    public MapperNearbyAttractionDto(RewardCentral rewardsCentral) {
        this.rewardsCentral = rewardsCentral;
    }

    /**
     * Maps a list of AttractionDistanceFromUser to a list of NearbyAttractionDto.
     *
     * @param attractionDistances the list of AttractionDistanceFromUser to map
     * @param visitedLocation the VisitedLocation of the user
     * @param user the User for whom the attractions are being mapped
     * @return a list of NearbyAttractionDto containing details about nearby attractions
     */
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
