package com.cloudcomputing.samza.nycabs;

import com.google.common.io.Resources;
import org.apache.samza.context.Context;
import org.apache.samza.storage.kv.Entry;
import org.apache.samza.storage.kv.KeyValueIterator;
import org.apache.samza.storage.kv.KeyValueStore;
import org.apache.samza.system.IncomingMessageEnvelope;
import org.apache.samza.system.OutgoingMessageEnvelope;
import org.apache.samza.task.InitableTask;
import org.apache.samza.task.MessageCollector;
import org.apache.samza.task.StreamTask;
import org.apache.samza.task.TaskCoordinator;

import org.codehaus.jackson.map.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Arrays;


/**
 * Consumes the stream of events.
 * Outputs a stream which handles static file and one stream
 * and gives a stream of advertisement matches.
 */
public class AdMatchTask implements StreamTask, InitableTask {

    //direction
    private static final String DIRECTION = "direction";

    //common
    private static final String LATITUDE = "latitude";
    private static final String LONGITUDE = "longitude";
    private static final String BLOCKID = "blockId";

    //user
    private static final String GENDER = "gender";
    private static final String AGE = "age";
    private static final String TRAVEL_COUNT = "travel_count";
    private static final String DEVICE = "device";

    //device
    private static final String IPHONE_FIVE = "iPhone 5";
    private static final String IPHONE_SEVEN = "iPhone 7";
    private static final String IPHONE_XS = "iPhone XS";

    private static final int IPHONE_FIVE_VALUE = 1;
    private static final int IPHONE_SEVEN_VALUE = 2;
    private static final int IPHONE_XS_VALUE = 3;

    //business
    private static final String STOREID = "storeId";
    private static final String NAME = "name";
    private static final String REVIEWCOUNT = "review_count";
    private static final String CATEGORIES = "categories";
    private static final String RATING = "rating";
    private static final String PRICE = "price";

    //5 minute
    private int FIVE_MINTUTE_MS = 300000;

    //type
    private static final String TYPE = "type";

    private static final String RIDER_STATUS = "RIDER_STATUS";
    private static final String RIDER_INTEREST = "RIDER_INTEREST";
    private static final String RIDE_REQUEST = "RIDE_REQUEST";

    private static final String USERID = "userId";
    private static final String CLIENTID = "clientId";

    //set up the string of information RIDER_STATUS
    private static final String MOOD = "mood";
    private static final String BLOODSUGAR = "blood_sugar";
    private static final String STRESS = "stress";
    private static final String ACTIVITY = "active";

    //set up the string of information RIDER_INTEREST
    private static final String INTEREST = "interest";
    private static final String DURATION = "duration";

    //tag
    private static final String TAG = "tag";
    private static final String LOWCALORIES = "lowCalories";
    private static final String ENERGYPROVIDERS = "energyProviders";
    private static final String WILLINGTOUR = "willingTour";
    private static final String STRESSRELEASE = "stressRelease";
    private static final String HAPPYCHOICE = "happyChoice";
    private static final String OTHERS = "others";

    /*
       Define per task state here. (kv stores etc)
       READ Samza API part in Writeup to understand how to start
    */

    private KeyValueStore<Integer, Map<String, Object>> userInfo;

    private KeyValueStore<String, Map<String, Object>> yelpInfo;

    private Set<String> lowCalories;

    private Set<String> energyProviders;

    private Set<String> willingTour;

    private Set<String> stressRelease;

    private Set<String> happyChoice;

    private void initSets() {
        lowCalories = new HashSet<>(Arrays.asList("seafood", "vegetarian", "vegan", "sushi"));
        energyProviders = new HashSet<>(Arrays.asList("bakeries", "ramen", "donuts", "burgers",
                "bagels", "pizza", "sandwiches", "icecream",
                "desserts", "bbq", "dimsum", "steak"));
        willingTour = new HashSet<>(Arrays.asList("parks", "museums", "newamerican", "landmarks"));
        stressRelease = new HashSet<>(Arrays.asList("coffee", "bars", "wine_bars", "cocktailbars", "lounges"));
        happyChoice = new HashSet<>(Arrays.asList("italian", "thai", "cuban", "japanese", "mideastern",
                "cajun", "tapas", "breakfast_brunch", "korean", "mediterranean",
                "vietnamese", "indpak", "southern", "latin", "greek", "mexican",
                "asianfusion", "spanish", "chinese"));
    }

    // Get store tag
    private String getTag(String cate) {
        String tag = "";
        if (happyChoice.contains(cate)) {
            tag = "happyChoice";
        } else if (stressRelease.contains(cate)) {
            tag = "stressRelease";
        } else if (willingTour.contains(cate)) {
            tag = "willingTour";
        } else if (energyProviders.contains(cate)) {
            tag = "energyProviders";
        } else if (lowCalories.contains(cate)) {
            tag = "lowCalories";
        } else {
            tag = "others";
        }
        return tag;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(Context context) throws Exception {
        // Initialize kv store

        userInfo = (KeyValueStore<Integer, Map<String, Object>>) context.getTaskContext().getStore("user-info");
        yelpInfo = (KeyValueStore<String, Map<String, Object>>) context.getTaskContext().getStore("yelp-info");

        //Initialize store tags set
        initSets();

        //Initialize static data and save them in kv store
        initialize("UserInfoData.json", "NYCstore.json");
    }

    /**
     * This function will read the static data from resources folder
     * and save data in KV store.
     * <p>
     * This is just an example, feel free to change them.
     */
    public void initialize(String userInfoFile, String businessFile) {
        List<String> userInfoRawString = AdMatchConfig.readFile(userInfoFile);
        System.out.println("Reading user info file from " + Resources.getResource(userInfoFile).toString());
        System.out.println("UserInfo raw string size: " + userInfoRawString.size());
        for (String rawString : userInfoRawString) {
            Map<String, Object> mapResult;
            ObjectMapper mapper = new ObjectMapper();
            try {
                mapResult = mapper.readValue(rawString, HashMap.class);
                int userId = (Integer) mapResult.get("userId");


                //update user tags
                updateTags(mapResult);

                userInfo.put(userId, mapResult);
            } catch (Exception e) {
                System.out.println("Failed at parse user info :" + rawString);
            }
        }

        List<String> businessRawString = AdMatchConfig.readFile(businessFile);

        System.out.println("Reading store info file from " + Resources.getResource(businessFile).toString());
        System.out.println("Store raw string size: " + businessRawString.size());

        for (String rawString : businessRawString) {
            Map<String, Object> mapResult;
            ObjectMapper mapper = new ObjectMapper();
            try {
                mapResult = mapper.readValue(rawString, HashMap.class);
                String storeId = (String) mapResult.get("storeId");
                String cate = (String) mapResult.get("categories");
                String tag = getTag(cate);
                mapResult.put("tag", tag);
                yelpInfo.put(storeId, mapResult);

            } catch (Exception e) {
                System.out.println("Failed at parse store info :" + rawString);
            }
        }


    }

    @Override
    @SuppressWarnings("unchecked")
    public void process(IncomingMessageEnvelope envelope, MessageCollector collector, TaskCoordinator coordinator) {
        /*
        All the messsages are partitioned by blockId, which means the messages
        sharing the same blockId will arrive at the same task, similar to the
        approach that MapReduce sends all the key value pairs with the same key
        into the same reducer.
        */
        String incomingStream = envelope.getSystemStreamPartition().getStream();

        if (incomingStream.equals(AdMatchConfig.EVENT_STREAM.getStream())) {
            // Handle Event messages
            processEvent((Map<String, Object>) envelope.getMessage(), collector);

        } else {
            throw new IllegalStateException("Unexpected input stream: " + envelope.getSystemStreamPartition());
        }
    }

    //handele event
    private void processEvent(Map<String, Object> eventMap, MessageCollector collector) {
        //get type
        String type = eventMap.get(TYPE) + "";
        if (type == null) {
            System.out.println("type is null");
            return;
        }
        if (type.equals(RIDE_REQUEST)) {
            processRideRequest(eventMap, collector);
        }

        if (type.equals(RIDER_STATUS)) {
            updateStatus(eventMap);
        }

        if (type.equals(RIDER_INTEREST)) {
            updateInterest(eventMap);
        }

    }

    //process the ride request
    private void processRideRequest(Map<String, Object> eventMap, MessageCollector collector) {
        //update the location
        updateLocationUser(eventMap);

        //get client id
        int clientId = (int) eventMap.get(CLIENTID);
        Map<String, Object> curUser = userInfo.get(clientId);

        //get tags
        String userTagString = curUser.get(TAG) + "";

        String[] userTagsList = userTagString.split(",");

        //get a hashset
        HashSet<String> userCurTags = new HashSet<>(Arrays.asList(userTagsList));

        //get all store
        KeyValueIterator<String, Map<String, Object>> storeIter = yelpInfo.all();


        double scoreMax = Integer.MIN_VALUE;
        String storeId = null;

        //check all the store
        while (storeIter.hasNext()) {
            Entry<String, Map<String, Object>> store = storeIter.next();
            String businessTag = store.getValue().get(TAG) + "";

            if (userCurTags.contains(businessTag)) {

                //get score
                double curScore = calBusinessScore(curUser, store.getValue());

                //update score
                if (curScore > scoreMax) {
                    scoreMax = curScore;
                    storeId = store.getKey();
                }
            }
        }
        if (storeId != null) {

            //send the message
            HashMap<String, Object> result = new HashMap<>();
            result.put(USERID, clientId);
            result.put(STOREID, storeId);
            result.put(NAME, yelpInfo.get(storeId).get(NAME));

            //send the result
            collector.send(new OutgoingMessageEnvelope(AdMatchConfig.AD_STREAM, result));
        }

    }


    private double calBusinessScore(Map<String, Object> user, Map<String, Object> business) {

        //get the factor of business
        String businessStoreId = business.get(STOREID) + "";
        String businessName = business.get(NAME) + "";
        int businessReviewCount = (int) business.get(REVIEWCOUNT);
        String businessCategories = business.get(CATEGORIES) + "";
        double businessRating = (double) business.get(RATING);
        String businessPrice = business.get(PRICE) + "";
        double businessLatitude = (double) business.get(LATITUDE);
        double businessLongitude = (double) business.get(LONGITUDE);
        int businessBlockId = (int) business.get(BLOCKID);

        int priceValue = businessPrice.length();


        //get factor of user
        //'donuts' / 'burgers' / 'steak' / ...
        String userInterest = user.get(INTEREST) + "";

        //get the divice
        String userDivice = user.get(DEVICE) + "";

        double userLatitude = (double) user.get(LATITUDE);
        double userLongitude = (double) user.get(LONGITUDE);

        int userAge = (int) user.get(AGE);
        int userTravleCount = (int) user.get(TRAVEL_COUNT);


        int deviceValue;
        if (userDivice.equals(IPHONE_FIVE)) {
            deviceValue = IPHONE_FIVE_VALUE;
        } else if (userDivice.equals(IPHONE_SEVEN)) {
            deviceValue = IPHONE_SEVEN_VALUE;
        } else {
            deviceValue = IPHONE_XS_VALUE;
        }

        //calculate the distance
        double distanceValue = distance(userLatitude, userLongitude, businessLatitude, businessLongitude);

        //get initial score
        double score = businessRating * businessReviewCount;

        //if the category of the store is the same as the user's interest,
        // add 10 to the initial match score.
        if (businessCategories.equals(userInterest)) {
            score = score + 10;
        }

        //match device
        //if iphoneXS 3, and price is 4, it do not reduce
        if (deviceValue == IPHONE_XS_VALUE && priceValue == 4) {
            score = score;
        } else {
            score = score * (1 - Math.abs(priceValue - deviceValue) * 0.1);
        }

        //If the number of a user's trips is higher than 50 times
        // or the user is exactly 20 years old,
        // you do not have to change the match score for businesses within 10 miles(<= 10 miles).

        if (userAge == 20 || userTravleCount > 50) {
            // For businesses that are far away from the user (> 10 miles),
            // you will reduce the match score to 10%. In other words, score = score * 0.1.
            if (distanceValue > 10) {
                score = score * 0.1;
            }
            // For all other users ( > 20 years old and travel count <= 50),
            // the threshold for the distance is 5 miles.
        } else {
            // If the business is more than 5 miles away from the user,
            // you will reduce the match score to 10%.
            if (distanceValue > 5) {
                score = score * 0.1;
            }
        }


        //userdirection
        int userDirection = (int) user.get(DIRECTION);
        //direction
        int direction = calDirection(businessLatitude, businessLongitude, userLatitude, userLongitude);

        if (userDirection == direction) {
            score = 1.5 * score;
        }
        return score;
    }

    //update the location
    private void updateLocationUser(Map<String, Object> locationtMap) {
        //get the direction
        int clientDirection = (int) locationtMap.get(DIRECTION);

        double clientLatitude = (double) locationtMap.get(LATITUDE);
        double clientLongitude = (double) locationtMap.get(LONGITUDE);
        int clientId = (int) locationtMap.get(CLIENTID);

        Map<String, Object> curUser = userInfo.get(clientId);
        //update the status
        if (curUser != null) {
            //update
            curUser.put(LATITUDE, clientLatitude);
            curUser.put(LONGITUDE, clientLongitude);
            curUser.put(DIRECTION, clientDirection);
        }
    }

    //update status
    //You will update a user's mood, blood_sugar, stress,
    // active information in the user's profile every time you encounter a RIDER_STATUS event.
    private void updateStatus(Map<String, Object> statustMap) {

        //all possible non-negative integers
        int userId = (int) statustMap.get(USERID);
        int mood = (int) statustMap.get(MOOD);
        int bloodSugar = (int) statustMap.get(BLOODSUGAR);
        int stress = (int) statustMap.get(STRESS);
        int active = (int) statustMap.get(ACTIVITY);

        Map<String, Object> curUser = userInfo.get(userId);

        //update the status
        if (curUser != null) {
            curUser.put(MOOD, mood);
            curUser.put(BLOODSUGAR, bloodSugar);
            curUser.put(STRESS, stress);
            curUser.put(ACTIVITY, active);

            //update tags
            updateTags(curUser);
        }

    }


    //update interest of user
    private void updateInterest(Map<String, Object> interestMap) {
        //all possible non-negative integers
        int userId = (int) interestMap.get(USERID);

        //'donuts' / 'burgers' / 'steak' / ...
        String interest = interestMap.get(INTEREST) + "";

        //the time user browses websites that display his interest in ms
        int duration = (int) interestMap.get(DURATION);

        Map<String, Object> curUser = userInfo.get(userId);
        if (curUser != null) {

            //if larger than 5mins not include update
            if (duration > FIVE_MINTUTE_MS) {
                //update
                curUser.put(INTEREST, interest);

            }
        }


    }


    //up date the tags of user base on status
    private void updateTags(Map<String, Object> user) {
        int mood = (int) user.get(MOOD);
        int bloodSugar = (int) user.get(BLOODSUGAR);
        int stress = (int) user.get(STRESS);
        int active = (int) user.get(ACTIVITY);

        //add tag string builders
        StringBuilder sb = new StringBuilder();

        //lowCalories
        //blood_sugar > 4 && mood > 6 && active_level == 3
        if (bloodSugar > 4 && mood > 6 && active == 3) {
            sb.append(LOWCALORIES);
            sb.append(",");
        }

        //energyProviders
        //blood_sugar < 2 || mood < 4
        if (bloodSugar < 2 || mood < 4) {
            sb.append(ENERGYPROVIDERS);
            sb.append(",");
        }

        //willingTour
        //active == 3
        if (active == 3) {
            sb.append(WILLINGTOUR);
            sb.append(",");
        }

        //stressRelease
        //stress > 5 || active == 1 || mood < 4
        if (stress > 5 || active == 1 || mood < 4) {
            sb.append(STRESSRELEASE);
            sb.append(",");
        }

        //happyChoice
        //mood > 6
        if (bloodSugar > 4 && mood > 6 && active == 3) {
            sb.append(HAPPYCHOICE);
            sb.append(",");
        }

        //other
        //no tag
        if (sb == null || sb.length() == 0) {
            sb.append(OTHERS);
            sb.append(",");
        }

        //delete the , at last
//        sb.deleteCharAt(sb.length() - 1);

        //update the tag
        user.put(TAG, sb.toString());

    }

    //calculate the distance
    private double distance(double lat1, double lon1, double lat2, double lon2) {
        if ((lat1 == lat2) && (lon1 == lon2)) {
            return 0;
        } else {
            double theta = lon1 - lon2;
            double dist = Math.sin(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2))
                    + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                    * Math.cos(Math.toRadians(theta));
            dist = Math.acos(dist);
            dist = Math.toDegrees(dist);
            dist = dist * 60 * 1.1515;
            return (dist);
        }
    }

    //calculate direction
    private int calDirection(double businessLat, double businessLong, double clientLat, double clientLong) {

        // des_lat - cur_lat > 0 && des_lon - cur_lon > 0
        if (businessLat - clientLat > 0 && businessLong - clientLong > 0) {
            return 0;
            //des_lat - cur_lat < 0 && des_lon - cur_lon > 0
        } else if (businessLat - clientLat < 0 && businessLong - clientLong > 0) {
            return 1;
            //des_lat - cur_lat < 0 && des_lon - cur_lon < 0
        } else if (businessLat - clientLat < 0 && businessLong - clientLong < 0) {
            return 2;
        } else {
            return 3;
        }
    }
}

