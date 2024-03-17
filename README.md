# DSPFlow

# DSPFlow

DSPFlow is a data processing framework designed for converting complex datasets into meaningful insights through a series of processing steps.

## Prerequisites

Before running the program, ensure Maven 3.6.3 is installed as versions after 3.8 might not be compatible with this project. Follow these steps to install Maven 3.6.3:

1. Download Maven 3.6.3:

    ```bash
    wget https://archive.apache.org/dist/maven/maven-3/3.6.3/binaries/apache-maven-3.6.3-bin.tar.gz
    ```

2. Extract the archive to `/opt`:

    ```bash
    sudo tar -xvzf apache-maven-3.6.3-bin.tar.gz -C /opt
    ```

3. Add Maven to your environment variables:

    ```bash
    export M2_HOME=/opt/apache-maven-3.6.3
    export MAVEN_HOME=/opt/apache-maven-3.6.3
    export PATH=$PATH:$M2_HOME/bin
    ```

4. Verify the Maven installation:

    ```bash
    mvn --version
    ```

### Configuring IntelliJ IDEA to Use Maven 3.6.3

- Open `File > Settings` (or `IntelliJ IDEA > Preferences` on macOS).
- Navigate to `Build, Execution, Deployment > Build Tools > Maven`.
- Ensure the `Maven home directory` points to your installed Maven directory (`/opt/apache-maven-3.6.3`).
- Click `OK` or `Apply` to apply the changes.

### Troubleshooting Maven Environment

If you encounter any issues with Maven configuration or dependencies are not being correctly downloaded, contact the project administrator for a backup of the `.m2/repository` directory, which contains all the necessary dependencies. To use the backup:

1. Extract the backup `.m2/repository` directory.
2. Copy it into your local `.m2` directory, usually located at `~/.m2/` on UNIX-like systems or `C:\Users\<YourUsername>\.m2\` on Windows.
3. Merge the directory if it already exists to ensure no existing dependencies are lost.

### Adding `pflowlib.jar` to Libraries

If `pflowlib.jar` needs to be included in the project:

- Open `File > Project Structure` in IntelliJ IDEA.
- Go to `Libraries` and click the `+` sign to add a new library.
- Navigate to the location of `pflowlib.jar`, select it, and add it to the project.

## Ensure Correct Run Configuration

If you face issues with Run Configuration:

- Check `Project Structure > Modules` and ensure the `Sources` and `Resources` tags are set correctly for your project directories.
- Under `Paths`, confirm the `Output path` and `Test output path` are directed to the intended directories, usually `target/classes` and `target/test-classes`.



## 0. Data prosessing
### Processing steps
Household data -> Person data-> Activity data -> Trip data -> Trajectory dat -> Aggregated data
 
### Dataset for processing
Please download the dataset from S3://pseudo-pflow/processing<br>
The dasetset was created from various statistical data.<br>
Each processing step references files in the dataset.<br>
 
## 1. Create person data from household data developed by Kajiwara
Each person is assigned a role(workder, student, no-worker).
* Entry point: pseudo.pre.PersonGenerator
* Input
  * pre_labor_rate.csv: Output of pseudo.pre.CensusKakou2
  * pre_holiday_rate.csv: Data from Survey on time use and leisure activities
  * pre_enrollment_rate.csv: Data from School Basic Survey
  * househould data  developed by Kajiwara
* Output
  * Person data in CSV format
  
## 2. Create activity data for commuter
* Entry point: pseudo.gen.Commuter
* Input
  * base_station.csv: Location of stations
  * city_boundary.csv: City boundary data
  * city_census_od.csv: Output of pseudo.pre.CensusKakou1
  * city_hospital data:  Location of hospitals
  * mesh_ecensus: Mesh-based economic census data
  * city_tatemono: Tatamono data from Zenrin
  * tky2008_trip_01-10_labor_male_prob.csv: Markov chain parameters calculated from PT data
  * tky2008_trip_01-10_labor_female_prob.csv: Markov chain parameters calculated from PT data
  * labor_params.csv: MNL parmaters calculate for location choice from PT data
* Output
  * Activity data in CSV format
  
## 3. Create activity data for student
* Entry point: pseudo.gen.Student
* Input
  * base_station.csv: Location of stations
  * city_boundary.csv: City boundary data
  * city_hospital data:  Location of hospitals
  * city_pre_school: Location of preschools
  * city_school.csv: Location of schools
  * mesh_ecensus: Mesh-based economic census data
  * city_tatemono: Tatamono data from Zenrin
  * tky2008_trip_11-11_student1_prob.csv: Markov chain parameters calculated from PT data
  * tky2008_trip_12-13_student2_prob.csv: Markov chain parameters calculated from PT data
  * nolabor_params.csv: MNL parmaters calculate for location choice from PT data
  * primary_*.csv:　Map of households and school destinations
  * secondary_*.csv:　Map of households and school destinations
* Output
  * Activity data in CSV format
  
## 4. Create activity data for no-worker
* Entry point: pseudo.gen.NonCommuter
* Input
  * base_station.csv: Location of stations
  * city_boundary.csv: City boundary data
  * city_hospital data:  Location of hospitals
  * mesh_ecensus: Mesh-based economic census data
  * city_tatemono: Tatamono data from Zenrin
  * tky2008_trip_14-15_nolabor_male_prob.csv: Markov chain parameters calculated from PT data
  * tky2008_trip_14-15_nolabor_female_prob.csv: Markov chain parameters calculated from PT data
  * tky2008_trip_14-15_nolabor_male_senior_prob.csv: Markov chain parameters calculated from PT data
  * tky2008_trip_14-15_nolabor_female_senior_prob.csv: Markov chain parameters calculated from PT data
  * nolabor_params.csv: MNL parmaters calculate for location choice from PT data
* Output
  * Activity data in CSV format

## 5. Create trip data from activity data
* Entry point: pseudo.gen.TripGenerator
* Input
  * city_boundary.csv: City boundary data
  * base_station.csv: Location of stations
  * act_transport.csv: Traffic sharing rate from National PT survey
  * activity: Result of No. 3, 4, 5
* Output
  * Trip data in CSV format
 
## 6. Create trajectory data from trip data
* Entry point: pseudo.gen.TrajectoryGenerator
* Input
  * drm_*.tsv: Road network data
  * railnetwork.tsv: Railway network data
  * act_transport.csv: Traffic sharing rate from National PT survey
  * trip: Result of No. 5
* Output
  * Trajectory data in CSV format
 
## 7. Create zip file of trajectory for each city
* Entry point: pseudo.gen.TripJoinner
* Input
  * trajectory : Result of No. 6
* Output
  * ZIP-compressed trajectory data for each city

## 8. Create mesh population data from trajectory data
* Entry point: pseudo.aggr.MeshVolumeCalculator2
* Input
  * trajectory : Result of No. 6
* Output
  * 500x500m grid population every 10 minutes

## 9. Create link volume data from trajectory data
* Entry point: pseudo.aggr.LinkVolumeCalculator
* Input
  * trajectory : Result of No. 6
* Output
  * The number of people passing by each link every hou






 




