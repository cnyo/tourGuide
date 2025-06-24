package com.openclassrooms.tourguide.service;

import java.util.ArrayList;
import java.util.Collections;
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

	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
	}
	
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}
	
	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}

	public void calculateRewards(User user) {
		logger.info("Calculating rewards for user: {}", user.getUserName());
		// Mise à jour dans le run du tracker
		CopyOnWriteArrayList<VisitedLocation> userLocations = new CopyOnWriteArrayList<>(user.getVisitedLocations());
		List<Attraction> attractions = gpsUtil.getAttractions();
		List<String> visitedAttractionNameRewards = getAttractionNamesFromUserRewards(user);
		List<UserReward> rewardsToAdd = Collections.synchronizedList(new ArrayList<>());

		// Use a thread pool to parallelize the reward calculation
		int processors = Runtime.getRuntime().availableProcessors();
		ExecutorService executor = Executors.newFixedThreadPool(processors);

        for (VisitedLocation visitedLocation : userLocations) {
            for (Attraction attraction : attractions) {
				boolean hasAttractionRewards = visitedAttractionNameRewards.contains(attraction.attractionName);
                if (!hasAttractionRewards && nearAttraction(visitedLocation, attraction)) {
					// Submit a task to the executor for reward calculation
					executor.submit(() -> {
						int rewardPoints = getRewardPoints(attraction, user);
						rewardsToAdd.add(new UserReward(visitedLocation, attraction, rewardPoints));
					});
                }
            }
        }

		// Wait for all tasks to complete
		executor.shutdown();

		// Optional: Use awaitTermination to ensure all tasks are completed before proceeding
		try {
			// Wait for the executor to terminate, with a timeout to avoid indefinite blocking
			if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
				logger.warn("Executor did not terminate in the specified time.");
			}
		} catch (InterruptedException e) {
			logger.error("Executor was interrupted while waiting for tasks to complete.", e);
			Thread.currentThread().interrupt(); // Restore interrupted status
		}

		// Add all rewards to the user
		logger.info("Adding {} rewards to user: {}", rewardsToAdd.size(), user.getUserName());
		rewardsToAdd.forEach(user::addUserReward);

		logger.info("UserRewards size: {}", user.getUserRewards().size());
	}

	public List<String> getAttractionNamesFromUserRewards(User user) {
		logger.info("mapping user rewards to attraction names");
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
