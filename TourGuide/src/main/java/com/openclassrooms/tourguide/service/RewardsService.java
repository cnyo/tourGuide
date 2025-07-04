package com.openclassrooms.tourguide.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import com.openclassrooms.tourguide.model.AttractionDistanceFromUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import rewardCentral.RewardCentral;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

@Service
public class RewardsService {
    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;
    private static final Integer NEAR_ATTRACTION_LIMIT = 5;
	private static final Logger logger = LoggerFactory.getLogger(RewardsService.class);

	// proximity in miles
    private int defaultProximityBuffer = 10;
	private int proximityBuffer = defaultProximityBuffer;
	private int attractionProximityRange = 200;
	private final GpsUtil gpsUtil;
	private final RewardCentral rewardsCentral;
	private final List<Attraction> attractions;
	private final ExecutorService executor;

	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
		this.attractions = gpsUtil.getAttractions();
		this.executor = Executors.newFixedThreadPool(46);
	}
	
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}
	
	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}

	public CompletableFuture<Void> calculateRewards(User user) {
		CopyOnWriteArrayList<VisitedLocation> userLocations = new CopyOnWriteArrayList<>(user.getVisitedLocations());
		List<String> attractionRewarded = getAttractionNamesFromUserRewards(user);
		List<CompletableFuture<Void>> futures = new ArrayList<>();

		for (VisitedLocation visitedLocation : userLocations) {
			for (Attraction attraction : attractions) {
				boolean isRewarded = attractionRewarded.contains(attraction.attractionName);
				if (!isRewarded && nearAttraction(visitedLocation, attraction)) {
					CompletableFuture<Void> future = CompletableFuture
							.supplyAsync(() -> getRewardPoints(attraction, user), executor)
							.thenAcceptAsync(rewardPoints -> {
								user.addUserReward(new UserReward(visitedLocation, attraction, rewardPoints));
							});
					futures.add(future);
				}
			}
		}

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}

	public List<String> getAttractionNamesFromUserRewards(User user) {
		logger.debug("mapping user rewards to attraction names");
		List<UserReward> userRewards = user.getUserRewards();

		return userRewards.stream().
				map(userReward -> userReward.attraction.attractionName)
				.collect(Collectors.toList());
	}
	
	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return getDistance(attraction, location) > attractionProximityRange ? false : true;
	}
	
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) > proximityBuffer ? false : true;
	}
	
	private int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}
	
	public double getDistance(Location loc1, Location loc2) {
        double lat1 = Math.toRadians(loc1.latitude);
        double lon1 = Math.toRadians(loc1.longitude);
        double lat2 = Math.toRadians(loc2.latitude);
        double lon2 = Math.toRadians(loc2.longitude);

        double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
                               + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

        double nauticalMiles = 60 * Math.toDegrees(angle);
        double statuteMiles = STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
        return statuteMiles;
	}

	public List<AttractionDistanceFromUser> getAttractionDistancesFromUser(VisitedLocation visitedLocation) {
		if (visitedLocation == null || visitedLocation.location == null) {
			logger.error("VisitedLocation or its location is null, cannot calculate distances.");
			return new ArrayList<>();
		}

		logger.info("Calculating distances for visited location: {}", visitedLocation.location);
		return gpsUtil.getAttractions().stream().
				map(attraction -> new AttractionDistanceFromUser(
						attraction,
						getDistance(attraction, visitedLocation.location)
				)).
				sorted(AttractionDistanceFromUser.comparingByDistance()).
				limit(NEAR_ATTRACTION_LIMIT).// Limit to the closest 5 attractions
				toList();
	}
}
