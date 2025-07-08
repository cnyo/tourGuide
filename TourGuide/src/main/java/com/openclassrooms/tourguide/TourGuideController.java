package com.openclassrooms.tourguide;

import java.util.List;

import com.openclassrooms.tourguide.dto.NearbyAttractionDto;
import com.openclassrooms.tourguide.mapper.MapperNearbyAttractionDto;
import com.openclassrooms.tourguide.model.AttractionDistanceFromUser;
import com.openclassrooms.tourguide.service.RewardsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gpsUtil.location.VisitedLocation;

import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import tripPricer.Provider;

/**
 * TourGuideController provides endpoints for interacting with the Tour Guide
 * service, allowing users to retrieve their location, nearby attractions,
 * rewards, and trip deals.
 */
@RestController
public class TourGuideController {

	@Autowired
	TourGuideService tourGuideService;

    @Autowired
    RewardsService rewardsService;

    @Autowired
    MapperNearbyAttractionDto mapperNearbyAttractionDto;
	
    @RequestMapping("/")
    public String index() {
        return "Greetings from TourGuide!";
    }
    
    @RequestMapping("/getLocation") 
    public VisitedLocation getLocation(@RequestParam String userName) {
    	return tourGuideService.getUserLocation(getUser(userName));
    }

    /** * Get nearby attractions for a user.
     *
     * @param userName the name of the user
     * @return a list of NearbyAttractionDto containing details about nearby attractions
     */
    @RequestMapping("/getNearbyAttractions") 
    public List<NearbyAttractionDto> getNearbyAttractions(@RequestParam String userName) {
        User user = getUser(userName);
    	VisitedLocation visitedLocation = tourGuideService.getUserLocation(user);

        List<AttractionDistanceFromUser> attractionDistancesFromUser = rewardsService.getAttractionDistancesFromUser(visitedLocation);

        return mapperNearbyAttractionDto.mapAttractionsToNearAttractionsDto(attractionDistancesFromUser, visitedLocation, user);
    }
    
    @RequestMapping("/getRewards") 
    public List<UserReward> getRewards(@RequestParam String userName) {
    	return tourGuideService.getUserRewards(getUser(userName));
    }
       
    @RequestMapping("/getTripDeals")
    public List<Provider> getTripDeals(@RequestParam String userName) {
    	return tourGuideService.getTripDeals(getUser(userName));
    }
    
    private User getUser(String userName) {
    	return tourGuideService.getUser(userName);
    }
   

}