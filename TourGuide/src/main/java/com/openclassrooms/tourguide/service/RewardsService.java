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

/**
 * RewardsService is responsible for calculating rewards for users based on their
 * visited locations and the attractions they have not been rewarded for yet.
 * It also provides methods to calculate distances from user locations to
 * attractions and manage proximity settings.
 */
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
	private final ExecutorService executor;

	public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
		this.gpsUtil = gpsUtil;
		this.rewardsCentral = rewardCentral;
		int nThreads = Runtime.getRuntime().availableProcessors();
		this.executor = Executors.newFixedThreadPool(nThreads * 4);
	}

	/**
	 * Sets the proximity buffer for calculating rewards.
	 *
	 * @param proximityBuffer the new proximity buffer in miles
	 */
	public void setProximityBuffer(int proximityBuffer) {
		this.proximityBuffer = proximityBuffer;
	}

	/**
	 * Gets the current proximity buffer.
	 *
	 * @return the current proximity buffer in miles
	 */
	public void setDefaultProximityBuffer() {
		proximityBuffer = defaultProximityBuffer;
	}

	/**
	 * Calculates rewards for a user based on their visited locations and the
	 * attractions they have not been rewarded for yet.
	 *
	 * @param user the user for whom to calculate rewards
	 * @return a CompletableFuture that completes when all rewards have been calculated
	 */
	public CompletableFuture<Void> calculateRewardsAsync(User user) {
		CopyOnWriteArrayList<VisitedLocation> userLocations = new CopyOnWriteArrayList<>(user.getVisitedLocations());
		List<Attraction> attractions = gpsUtil.getAttractions();
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
							})
							.exceptionally((it) -> {
								logger.error("Error while calculating rewards", it);
								return null;
							});;
					futures.add(future);
				}
			}
		}

		return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
	}

	/**
	 * Retrieves the names of attractions from the user's rewards.
	 *
	 * @param user the user whose rewards are to be mapped to attraction names
	 * @return a list of attraction names associated with the user's rewards
	 */
	public List<String> getAttractionNamesFromUserRewards(User user) {
		List<UserReward> userRewards = user.getUserRewards();

		return userRewards.stream().
				map(userReward -> userReward.attraction.attractionName)
				.collect(Collectors.toList());
	}

	/**
	 * Checks if a given attraction is within the proximity range of a user's
	 * visited location.
	 *
	 * @param attraction the attraction to check
	 * @param location   the user's current location
	 * @return true if the attraction is within proximity, false otherwise
	 */
	public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
		return getDistance(attraction, location) > attractionProximityRange ? false : true;
	}

	/**
	 * Calculates the distance between an attraction and a user's visited location.
	 *
	 * @param attraction the attraction to calculate the distance to
	 * @param visitedLocation   the user's visited location
	 * @return the distance in miles between the attraction and the user's location
	 */
	private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
		return getDistance(attraction, visitedLocation.location) > proximityBuffer ? false : true;
	}

	/**
	 * Calculates and returns the number of reward points for a given user
	 * based on the specified attraction.
	 *
	 * @param attraction the attraction for which to calculate reward points
	 * @param user the user for whom to calculate reward points
	 * @return the number of reward points awarded to the user for this attraction
	 */
	private int getRewardPoints(Attraction attraction, User user) {
		return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
	}

	/**
	 * Calculates the distance between two locations using the Haversine formula.
	 *
	 * @param loc1 the first location
	 * @param loc2 the second location
	 * @return the distance in statute miles between the two locations
	 */
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

	/**
	 * Retrieves a list of attractions sorted by their distance from the user's
	 * visited location.
	 *
	 * @param visitedLocation the location where the user has been
	 * @return a list of AttractionDistanceFromUser objects, sorted by distance
	 */
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
				))
				.sorted(AttractionDistanceFromUser.comparingByDistance())
				.limit(NEAR_ATTRACTION_LIMIT) // Limit to the closest 5 attractions
				.toList();
	}
}
