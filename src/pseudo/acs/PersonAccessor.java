package pseudo.acs;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Random;
import java.util.TimeZone;

import jp.ac.ut.csis.pflow.geom2.ILonLat;
import jp.ac.ut.csis.pflow.geom2.LonLat;
import pseudo.res.Activity;
import pseudo.res.EGender;
import pseudo.res.ELabor;
import pseudo.res.EPurpose;
import pseudo.res.ETransport;
import pseudo.res.GLonLat;
import pseudo.res.HouseHold;
import pseudo.res.Person;
import pseudo.res.SPoint;
import pseudo.res.Trip;

public class PersonAccessor{
	
	private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";
	private static final long DAY_OF_DATE = 1601478000L;  //2020-10-01 00:00:00

	
	public static List<HouseHold> load(String filename, int mfactor) {
		return load(filename, ELabor.values(), mfactor);
	}
	
	public static List<HouseHold> load(String filename, ELabor[] labors, int mfactor) {
		Set<ELabor> setLabors = new HashSet<>();
		List<HouseHold> data = new ArrayList<>();
		setLabors.addAll(Arrays.asList(labors));
		
		int counter = 0;
		try (BufferedReader br = new BufferedReader(new FileReader(filename));){
            String line = br.readLine();
            HouseHold household = null;
            while ((line = br.readLine()) != null) {	
            	String[] items = line.split(",");
            	String householdId = String.valueOf(items[0]);
            	if (household == null || !householdId.equals(household.getId())) {
            		int family = Integer.valueOf(items[1]);
            		String gyousei = items[2];
            		double lon = Double.valueOf(items[3]);
            		double lat = Double.valueOf(items[4]);
                	
                	household = new HouseHold(
                			householdId, family, gyousei, new GLonLat(lon,lat,gyousei));
            		data.add(household);
            	}
            	
            	long personId = Long.valueOf(items[5]);
            	int age = Integer.valueOf(items[6]);
            	EGender gender = EGender.getType(Integer.valueOf(items[7]));
            	ELabor labor = ELabor.getType(Integer.valueOf(items[8]));
            	
            	if (setLabors.contains(labor)) {
            		if (counter++ % mfactor == 0) {
            			new Person(household, personId, age, gender, labor);
            		}
            	}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
		return data;
	}
	
	public static void write(String filename, List<HouseHold> data) {
		try(BufferedWriter bw = new BufferedWriter(new FileWriter(filename));){
			for (HouseHold house : data) {
				ILonLat home = house.getHome();
				for (Person p : house.getListPersons()) {
					bw.write(
							String.format("%s,%d,%s,%f,%f,%d,%d,%d,%d", 
									house.getId(),
									house.getFamilyType(), 
									house.getGcode(), 
									home.getLon(),
									home.getLat(),
									p.getId(),
									p.getAge(),
									p.getGender().getId(),
									p.getLabor().getId())							
							);
					bw.newLine();		
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
		
	public static void writeActivities(String filename, List<HouseHold> data) {
		try(BufferedWriter bw = new BufferedWriter(new FileWriter(filename));){
			for (HouseHold house : data) {
				for (Person p : house.getListPersons()) {
					List<Activity> activities = p.getActivities();
					for (Activity act : activities) {
						bw.write(
							String.format("%d,%d,%d,%d,%d,%d,%d,%f,%f,%s", 
									p.getId(),
									p.getAge(),
									p.getGender().getId(),
									p.getLabor().getId(),
									act.getStartTime(),
									act.getDuration(),
									act.getPurpose().getId(),
									act.getX(),
									act.getY(),
									act.getGcode()
									));
						bw.newLine();	
					}					
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static List<Person> loadActivity(String filename, int scale, Double carRatio, Double bikeRatio) {
		List<Person> res = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new FileReader(filename));){
            String line = null;
            Person person = null;
            int preId = 0;
            int counter = 0;
            while ((line = br.readLine()) != null) {	
            	String[] items = line.split(",");	
            	int nextId = Integer.valueOf(items[0]);
            	int age = Integer.valueOf(items[1]);
            	EGender gender = EGender.getType(Integer.valueOf(items[2]));
            	ELabor labor = ELabor.getType(Integer.valueOf(items[3]));
            	
            	if (nextId != preId) {
            		person = new Person(null, nextId, age, gender, labor);
					Random random = new Random();
					Boolean carOwnership = (age >= 20) && (random.nextDouble() < carRatio);
					person.setCarowner(carOwnership);
					Boolean bikeOwnership = random.nextDouble() < bikeRatio;
					person.setBikeowner(bikeOwnership);
            		if (counter++ % scale == 0) {
            			res.add(person);
            		}
            	}

            	long startTime = Long.valueOf(items[4]);
            	long duration = Long.valueOf(items[5]);
            	EPurpose purpose = EPurpose.getType(Integer.valueOf(items[6]));
            	double lon = Double.valueOf(items[7]);
            	double lat = Double.valueOf(items[8]);
            	String gcode = String.valueOf(items[9]);

            	Activity activity = new Activity(
            			new GLonLat(lon, lat, gcode), startTime, duration, purpose);
            	person.addAcitivity(activity);
            	
            	preId = nextId;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
		return res;
	}
	
	public static void writeTrips(String filename, List<Person> data) {
		try(BufferedWriter bw = new BufferedWriter(new FileWriter(filename));){
			for (Person p : data) {
				List<Trip> trips = p.listTrips();
				for (int i = 0; i < trips.size(); i++) {
					Trip trip = trips.get(i);
					ILonLat o = trip.getOrigin();
					ILonLat d = trip.getDestination();
					bw.write(
						String.format("%d,%d,%f,%f,%f,%f,%d,%d,%d", 
								p.getId(),
								trip.getDepTime(),
								o.getLon(), o.getLat(),
								d.getLon(), d.getLat(),
								trip.getTransport().getId(),
								trip.getPurpose().getId(),
								p.getLabor().getId()
							));
					bw.newLine();	
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	public static List<Person> loadTrips(String filename) {
		List<Person> res = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new FileReader(filename));){
            String line = null;
            Person person = null;
            int preId = 0;
            while ((line = br.readLine()) != null) {	
            	String[] items = line.split(",");	
            	int nextId = Integer.valueOf(items[0]);
            	
            	long depTime = Long.valueOf(items[1]);
            	double lon1 = Double.valueOf(items[2]);
            	double lat1 = Double.valueOf(items[3]);
            	double lon2 = Double.valueOf(items[4]);
            	double lat2 = Double.valueOf(items[5]);
            	
            	ETransport mode = ETransport.getType(Integer.valueOf(items[6]));
            	EPurpose purpose = EPurpose.getType(Integer.valueOf(items[7]));
            	ELabor labor = ELabor.getType(Integer.valueOf(items[8]));
            	
            	if (nextId != preId) {
            		person = new Person(null, nextId, 0, EGender.MALE, labor);
            		res.add(person);
            	}
            	
            	Trip trip = new Trip(mode, purpose, depTime, new LonLat(lon1,lat1), new LonLat(lon2,lat2));
            	person.addTrip(trip);
            	
            	preId = nextId;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
		return res;
	}
	
	
	public static List<Person> loadTrajectory(String filename) {
		List<Person> res = new ArrayList<>();
		try (BufferedReader br = new BufferedReader(new FileReader(filename));){
            String line = null;
            Person person = null;
            int preId = 0;
            while ((line = br.readLine()) != null) {	
            	String[] items = line.split(",");	
            	int nextId = Integer.valueOf(items[0]);
            	
            	if (nextId != preId) {
            		person = new Person(null, nextId, 0, EGender.MALE, ELabor.UNDEFINED);
            		res.add(person);
            	}
            	
            	long time = Long.valueOf(items[1]);
            	
            	double lon = Double.valueOf(items[3]);
            	double lat = Double.valueOf(items[4]);
            	ETransport mode = ETransport.getType(Integer.valueOf(items[5]));
            	EPurpose purpose = EPurpose.getType(Integer.valueOf(items[6]));
            	String link = String.valueOf(items[7]);
     
            	SPoint point = new SPoint(lon, lat, new Date(time), mode, purpose);
            	point.setLink(link);
            	person.addTrajectory(point);
            	
            	preId = nextId;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
		return res;
	}

	public static int writeTrajectory(String filename, List<Person> persons) {
		SimpleDateFormat sfd = new SimpleDateFormat(DATE_FORMAT);
		// TimeZone tzn = TimeZone.getTimeZone("Europe/London");
		// TimeZone tzn = TimeZone.getTimeZone("Asia/Tokyo");
		// sfd.setTimeZone(tzn);

		try(BufferedWriter bw = new BufferedWriter(new FileWriter(filename));){
			for (Person person : persons) {
				List<SPoint> points = person.getTrajectory();
				if (points != null) {
					for (int i = 0; i < points.size(); i++) {
						SPoint p = points.get(i);
						String link = p.getLink();
						long unixtime = p.getTimeStamp().getTime();
						String date = sfd.format(unixtime);

						bw.write(
								String.format("%d,%d,%s,%f,%f,%d,%d,%s,%s",
									person.getId(),
									// time.getTime(),
									unixtime,
									// sfd.format(p.getTimeStamp()),
									date,
									p.getLon(),
									p.getLat(),
									p.getTransport().getId(),
									p.getPurpose().getId(),
									person.getLabor(),
									link != null ? link : ""
									));
						bw.newLine();
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return 0;
	}
}
