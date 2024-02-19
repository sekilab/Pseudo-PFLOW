package utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtils {
	public static Date parse(SimpleDateFormat format, String strTimestamp) {
		try {
			return format.parse(strTimestamp);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return null;
	}
}
