package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.model.AttractionDistanceFromUser;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;

import tripPricer.Provider;
import tripPricer.TripPricer;

/**
 * TourGuideService provides methods to manage users, track their locations,
 * calculate rewards, and retrieve trip deals.
 */
@Service
public class TourGuideService {
	private Logger logger = LoggerFactory.getLogger(TourGuideService.class);
	private final GpsUtil gpsUtil;
	private final RewardsService rewardsService;
	private final TripPricer tripPricer = new TripPricer();
	public final Tracker tracker;
	boolean testMode = true;
	private final ExecutorService executor;

	public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
		this.gpsUtil = gpsUtil;
		this.rewardsService = rewardsService;
		int nThreads = Runtime.getRuntime().availableProcessors();
		this.executor = Executors.newFixedThreadPool(nThreads * 4);
		Locale.setDefault(Locale.US);

		if (testMode) {
			logger.info("TestMode enabled");
			logger.debug("Initializing users");
			initializeInternalUsers();
			logger.debug("Finished initializing users");
		}
		tracker = new Tracker(this);
		addShutDownHook();
	}

	/**
	 * Get the list of user rewards for a given user.
	 *
	 * @param user the user for whom to get rewards
	 * @return a list of UserReward objects containing the user's rewards
	 */
	public List<UserReward> getUserRewards(User user) {
		return user.getUserRewards();
	}

	/**
	 * Get the current location of a user.
	 *
	 * @param user the user for whom to get the location
	 * @return the VisitedLocation object containing the user's current location
	 */
	public VisitedLocation getUserLocation(User user) {
		try	{
            return (user.getVisitedLocations().size() > 0) ? user.getLastVisitedLocation()
                    : trackUserLocationAsync(user).get();
		} catch(InterruptedException | ExecutionException e) {
			logger.error("Error getting user location for user: {}", user.getUserName(), e);
			return null;
		}
	}

	/**
	 * Get a user by their username.
	 *
	 * @param userName the name of the user
	 * @return the User object if found, null otherwise
	 */
	public User getUser(String userName) {
		return internalUserMap.get(userName);
	}

	/**
	 * Get all users in the system.
	 *
	 * @return a list of User objects representing all users
	 */
	public List<User> getAllUsers() {
		return internalUserMap.values().stream().collect(Collectors.toList());
	}

	/**
	 * Add a user to the internal user map if they do not already exist.
	 *
	 * @param user the user to add
	 */
	public void addUser(User user) {
		if (!internalUserMap.containsKey(user.getUserName())) {
			internalUserMap.put(user.getUserName(), user);
		}
	}

	/* * Get trip deals for a user based on their preferences and cumulative reward points.
	 *
	 * @param user the user for whom to get trip deals
	 * @return a list of providers offering trip deals
	 */
	public List<Provider> getTripDeals(User user) {
		int cumulatativeRewardPoints = user.getUserRewards().stream().mapToInt(i -> i.getRewardPoints()).sum();
		List<Provider> providers = tripPricer.getPrice(tripPricerApiKey, user.getUserId(),
				user.getUserPreferences().getNumberOfAdults(), user.getUserPreferences().getNumberOfChildren(),
				user.getUserPreferences().getTripDuration(), cumulatativeRewardPoints);
		user.setTripDeals(providers);
		return providers;
	}

	/**
	 * Track the user's location asynchronously and calculate rewards.
	 *
	 * @param user the user whose location is to be tracked
	 * @return a CompletableFuture containing the VisitedLocation object
	 */
	public CompletableFuture<VisitedLocation> trackUserLocationAsync(User user) {
				return CompletableFuture
				.supplyAsync(() -> {
					VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
					user.addToVisitedLocations(visitedLocation);
					rewardsService.calculateRewards(user).join();
					return visitedLocation;
				}, executor)
				.exceptionally((it) -> {
					logger.info("Error tracking user location for user: {}", user.getUserName(), it);
					return null;
				});
	}

	/**
	 * Get nearby attractions for a user based on their visited location.
	 *
	 * @param visitedLocation the VisitedLocation object containing the user's current location
	 * @return a list of Attraction objects representing nearby attractions
	 */
	public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
		if (visitedLocation == null) {
			logger.error("Visited location is null. Cannot get nearby attractions.");
			return Collections.emptyList();
		}

		List<AttractionDistanceFromUser> attractionDistancesFromUser = rewardsService.getAttractionDistancesFromUser(visitedLocation);

		return attractionDistancesFromUser.stream()
				.map(AttractionDistanceFromUser::getAttraction)
				.collect(Collectors.toList());
	}

	private void addShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				tracker.stopTracking();
			}
		});
	}

	/**********************************************************************************
	 * 
	 * Methods Below: For Internal Testing
	 * 
	 **********************************************************************************/
	private static final String tripPricerApiKey = "test-server-api-key";
	// Database connection will be used for external users, but for testing purposes
	// internal users are provided and stored in memory
	private final Map<String, User> internalUserMap = new HashMap<>();

	private void initializeInternalUsers() {
		IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
			String userName = "internalUser" + i;
			String phone = "000";
			String email = userName + "@tourGuide.com";
			User user = new User(UUID.randomUUID(), userName, phone, email);
			generateUserLocationHistory(user);

			internalUserMap.put(userName, user);
		});
		logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
	}

	private void generateUserLocationHistory(User user) {
		IntStream.range(0, 3).forEach(i -> {
			user.addToVisitedLocations(new VisitedLocation(user.getUserId(),
					new Location(generateRandomLatitude(), generateRandomLongitude()), getRandomTime()));
		});
	}

	private double generateRandomLongitude() {
		double leftLimit = -180;
		double rightLimit = 180;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private double generateRandomLatitude() {
		double leftLimit = -85.05112878;
		double rightLimit = 85.05112878;
		return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
	}

	private Date getRandomTime() {
		LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
		return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
	}
}
