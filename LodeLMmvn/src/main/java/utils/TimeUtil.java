package utils;

import java.time.LocalDateTime;  
import java.time.Duration; 
import java.time.format.DateTimeFormatter; 

public class TimeUtil { 
    public static DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    public static String getTime() {  
        LocalDateTime now = LocalDateTime.now();
        String time = now.format(dtf);
        return time;
    }  

    public static int getElapsedTime(LocalDateTime now, LocalDateTime later) {
        Duration duration = Duration.between(now, later);
        return (int) duration.getSeconds();
    }

    public static boolean validTimeDifference(String aTimeStr) {
        LocalDateTime bTime = LocalDateTime.now();
        LocalDateTime aTime = LocalDateTime.parse(aTimeStr);
        return (getElapsedTime(aTime, bTime) < 2);
    }
}  