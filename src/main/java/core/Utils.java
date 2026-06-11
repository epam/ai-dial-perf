package core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    public static final Logger logger = LoggerFactory.getLogger(Utils.class);

    public static int parseDuration(String value) {
        int totalSeconds = 0;
        Pattern pattern = Pattern.compile("(\\d+)([smh]?)");
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) {
            int num = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);
            if (unit.equals("h")) {
                totalSeconds += num * 3600;
            } else if (unit.equals("m")) {
                totalSeconds += num * 60;
            } else {
                totalSeconds += num;
            }
        }
        if (totalSeconds == 0) {
            System.err.println("Invalid duration format: " + value + ", using default 600s");
            return 600;
        }
        return totalSeconds;
    }

    public static int resolveDuration(String propertyName, int defaultValue) {
        String sysDuration = System.getProperty(propertyName);
        if (sysDuration != null && !sysDuration.isEmpty()) {
            return parseDuration(sysDuration.trim());
        }
        return defaultValue;
    }
}
