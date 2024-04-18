package base;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import sim.engine.SimState;
import sim.engine.Steppable;
import sim.field.continuous.Continuous2D;
import sim.field.network.Network;
import sim.util.Bag;
import sim.util.Double2D;

//[initParam]: tag for parameters that determine initially
//assigned values for a variable.
public class Participants extends SimState
{
  //geographical location yard.
  public Continuous2D yard = new Continuous2D(1.0,100,100);
  public Double MIN_LATITUDE;
  public Double MAX_LATITUDE;
  public Double MIN_LONGITUDE;
  public Double MAX_LONGITUDE;
  public Double MAX_DISTANCE;

  public double TEMPERING_CUT_DOWN = 0.99;
  public double TEMPERING_INITIAL_RANDOM_MULTIPLIER = 10.0;
  public boolean tempering = false;
  public boolean isTempering() { return tempering; }
  public void setTempering(boolean val) { tempering = val; }

  public int numParticipants = 0;


  double randomMultiplier = 0.1;
  public Network buddies = new Network(false);


  Bag populationSuppliers = new Bag(); // whole population.
  Vector<SupplierAgent> suppliers = new Vector<SupplierAgent>(); //in the simulation.
  Bag pupulationParticipants = new Bag(); // whole population.


  public DataSeriesSet dataSeriesSet = new DataSeriesSet();

  public Hashtable<String, SupplyRelationship> supplyRelationships = new Hashtable<String, SupplyRelationship>();

  public Vector<Participant> participantSet = new  Vector<Participant>();

  private Hashtable<String, Coalition> coalitionSet = new Hashtable<String, Coalition>();

  private static Vector<String> outputFolderNames = new Vector<String>();

  private static Double maxSavingsAllJobs = null;

  public static Long numJobs = null;

  //To indicate that a coalition should be added to the schedule.
  public static int ACTION_SCHEDULE_COALITION = 1;

  private static final Logger logger = LogManager.getLogger(Participants.class);

  private Long lastSimStep;

  public Participants(long seed){
    super(seed);
  }

  public void start(){
    super.start();

    SimConfig.print();
    System.out.println("--->job:" + job());
    // add the tempering agent
    if (tempering)
    {
      randomMultiplier = TEMPERING_INITIAL_RANDOM_MULTIPLIER;
      schedule.scheduleRepeating(schedule.EPOCH, 1, new Steppable()
      {
        public void step(SimState state) { if (tempering) randomMultiplier *= TEMPERING_CUT_DOWN; }
      });
    }

    //totalVol.clear();
    dataSeriesSet.clear();
    supplyRelationships.clear();
    coalitionSet.clear();
    //outputFolderNames.clear();

    Coalition.NUM_COALITIONS = 0;
    //load population of suppliers.
    logger.debug("Loading population of suppliers.");
    populationSuppliers.clear();
    loadPopulationSuppliers();
    //printPopulationSuppliers();

    //load population of participants.
    logger.debug("Loading population of participants.");
    pupulationParticipants.clear();
    loadPopulationParticipants();
    assignOrientationParticipants();

    assignBaseQPParticipants();

    printPopulationParticipants();
    logger.debug("MIN_LATITUDE:" + MIN_LATITUDE);
    logger.debug("MAX_LATITUDE:" + MAX_LATITUDE);
    logger.debug("MIN_LONGITUDE:" + MIN_LONGITUDE);
    logger.debug("MAX_LONGITUDE:" + MAX_LONGITUDE);

    logger.debug("MAX_DISTANCE:" + MAX_DISTANCE);

    // clear the yard
    yard.clear();
    logger.debug("yard.getWidth:" + yard.getWidth());
    logger.debug("yard.getHeight:" + yard.getHeight());

    // clear the suppliers.
    suppliers.clear();

    //assignSuppliers();

    // clear the buddies
    buddies.clear();
    participantSet.clear();



    //Create participants and add to the simulation.
    for (Iterator iterator = pupulationParticipants.iterator(); iterator.hasNext();) {
      ParticipantSource b = (ParticipantSource) iterator.next();
      Double2D locationb = new Double2D(
            ParticipantsUtil.scale(b.getLongitude(),
              1.0, 99.0, MIN_LONGITUDE, MAX_LONGITUDE),
            99.0 - ParticipantsUtil.scale(b.getLatitude(),
              1.0, 99.0, MIN_LATITUDE, MAX_LATITUDE)
      );

      Participant participant = new Participant();
      participant.PU = ParticipantsUtil.getPU(null, random.nextDouble(), SimConfig.MIN_PU0, SimConfig.MAX_PU0);
      //System.out.println("participant.PU:" + participant.PU);
      participant.participantInfo = b;
      participant.initEstimateVol(random.nextDouble());


      initFinance(participant);
      assignSupplier(participant);
      yard.setObjectLocation(participant, locationb);
      buddies.addNode(participant);
      participantSet.add(participant);
      schedule.scheduleRepeating(participant);
      //System.out.println("");

		}

    //Create suppliers and add to the simulation.
    for (Iterator iterator = populationSuppliers.iterator(); iterator.hasNext();) {
      Supplier s = (Supplier) iterator.next();
      SupplierAgent supplierAgent = new SupplierAgent();
      supplierAgent.supplierInfo = s;
      suppliers.add(supplierAgent);
      schedule.scheduleRepeating(supplierAgent);
    }

    // define closeness for cooperation.
    Bag participants_ = buddies.getAllNodes();
    for(int i = 0; i < participants_.size(); i++){
      Object participantA_ = participants_.get(i);
      Participant participantA = (Participant)participantA_;
      // who does he like?

      for(int j = 0; j < participants_.size(); j++){
        if(i != j){
          Object participantB_ = participants_.get(j);
          Participant participantB = (Participant)participantB_;

          Double PU_D = 1.0 - (participantA.PU + participantB.PU)/2.0;

          Double NormGeo_D =  ParticipantsUtil.euclideanDistance(
                              participantA.participantInfo.getLongitude(),
                              participantA.participantInfo.getLatitude(),
                              participantB.participantInfo.getLongitude(),
                              participantB.participantInfo.getLatitude() )/MAX_DISTANCE;

          Double Empathy_D = Math.abs(participantA.participantInfo.getBusinessOriented() -
                                      participantB.participantInfo.getBusinessOriented());


          //Double[] A = new Double[]{PU_D, NormGeo_D, Empathy_D};
          Double D = PU_D + NormGeo_D + Empathy_D;
          //System.out.println("D:" + D);
          if(D < SimConfig.THRESHOLD_D_COOPERATION){
            buddies.addEdge(participantA_, participantB_, 0.1);
          }
        }
      }
    }


  }

  //Assets are set equivalent to the income received by a year's production.
  public static void initFinance(Participant b) {
	  Double prdProd = ParticipantsUtil.approxPrdBasedonMalt(b.participantInfo.getBaseQPYear())*100.0;
      b.setAssets(SimConfig.AVG_PRICE_LITRE_BEER_UK*prdProd);
      b.setLiabilities(0.0);
  }



  public void finish(){
    super.finish();
    lastSimStep = schedule.getSteps();
    logger.warn("--Finishing simulation.");
    if(SimConfig.withResultGeneration()){
      createOutputs();
    }
  }

  public void generateBoxPlotSavingsForAllJobs(){
    String dataFileName = createMergedData(	SimConfig.FILE_NAME_SAVINGS_NO_TIME_ORDER_DATA,
    		  										SimConfig.FILE_NAME_ALL_SAVINGS_DATA,
    		  										SimConfig.DESCRIPTION);
    Rscripts rscripts = new Rscripts();

    //String rScriptFileName = SimConfig.BASE_FOLDER + SimConfig.FILE_NAME_ALL_SAVINGS_RSCRIPT;
    String folderName = getOrCreateFolderForSimulation();

    String rScriptFileName = getCompletedName(folderName +
    SimConfig.FILE_NAME_ALL_SAVINGS_RSCRIPT,
                    "",
                    "");

    String figureFileName = getCompletedName(folderName +
    SimConfig.FILE_NAME_ALL_SAVINGS_FIGURE,
                    "",
                    "");

    rscripts.generateRBoxPlotSavingsForAllJobs(dataFileName,
    null,
    rScriptFileName,
    figureFileName);

    ParticipantsUtil.excecuteRScript(rScriptFileName, null);

  }

  public String createDataRatioBuyingInCoalition(){
    //aqui voy
    //consider https://www.r-graph-gallery.com/2d-density-plot-with-ggplot2.html#hist
    Vector<String> lines = new Vector<String>();

    lines.add( "Period\tLatitude\tLongitude\tDensity");

    Double d = SimConfig.DELTA_FOR_MAPS_IN_R;
    Double lat = MIN_LATITUDE;
    //from Long in MIN_LONGITUDE to MAX_LONGITUDE by 0.2
    while(lat <= MAX_LATITUDE){
      Double longit = MIN_LONGITUDE;
      while(longit <= MAX_LONGITUDE){

        Double t_final = Double.valueOf(lastSimStep);
        Double t = Double.valueOf(0);
        while(t < t_final){
            Double density = getRatioOfParticipantsBuyingInCoalition(
                             lat, lat + d,
                             longit, longit + d,
                             t);
            if(density != null){
              Double midLat = (lat + lat + d)/2;
              Double midLong = (longit + longit + d)/2;
              String line = t + "\t" + midLat + "\t" + midLong + "\t" + density;
              lines.add(line);
            }

            t = t + Double.valueOf(1);
        }
        longit += d;
      }
      lat += d;
    }


    String folderName = getOrCreateFolderForJob();

    String fileName = folderName +
                      SimConfig.FILE_NAME_RATIO_BUYING_IN_COALITION_DATA;

    ParticipantsUtil.writeFile(lines, fileName);

    return fileName;

  }

  Double getRatioOfParticipantsBuyingInCoalition(Double latMin,
                                            Double latMax,
                                            Double longitMin,
                                            Double longitMax,
                                            Double t){
    int countParticipants = 0;
    int countBuyingFromCoalition = 0;

    for (Iterator iterator = participantSet.iterator(); iterator.hasNext();) {
      Participant b = (Participant) iterator.next();

      if(b.participantInfo.getLatitude() >= latMin &&
         b.participantInfo.getLatitude() < latMax &&
         b.participantInfo.getLongitude() >= longitMin &&
         b.participantInfo.getLongitude() < longitMax ) {

           countParticipants++;

           OperationRecord or = b.getOperationRecord(t);

           if(or != null && or.getCoalitionId() != null && or.getCoalitionSupplier() != null){
             countBuyingFromCoalition++;
           }

         }

      }

      return  (countParticipants == 0 ? null :
               Double.valueOf(countBuyingFromCoalition)/
               Double.valueOf(countParticipants));
  }

  public void createHeatMapsRatioBuyingInCoalition(){
      String fileNameData = createDataRatioBuyingInCoalition();
      String folderName = getOrCreateFolderForJob();
      String rFileName = folderName +
                         SimConfig.FILE_NAME_RATIO_BUYING_IN_COALITION_RSCRIPT;
      Rscripts rScripts = new Rscripts();
      rScripts.generateRDensityBuyingFromCoalition( fileNameData,
                                                    MIN_LONGITUDE,
                                                    MAX_LONGITUDE,
                                                    MIN_LATITUDE,
                                                    MAX_LATITUDE,
                                                    rFileName,
                                                    folderName);

      ParticipantsUtil.excecuteRScript(rFileName, null);
  }


  public String createDataSavingsPerArea(){
    //aqui voy
    //consider https://www.r-graph-gallery.com/2d-density-plot-with-ggplot2.html#hist
    Vector<String> lines = new Vector<String>();

    lines.add( "Period\tYear\tLatitude\tLongitude\tParticipant\tSavings\tTrial");

    Double d = SimConfig.DELTA_FOR_MAPS_IN_R;
    Double lat = MIN_LATITUDE;
    Double maxSavings = null;
    //from Long in MIN_LONGITUDE to MAX_LONGITUDE by 0.2
    while(lat <= MAX_LATITUDE){
      Double longit = MIN_LONGITUDE;
      while(longit <= MAX_LONGITUDE){

        Double t_final = Double.valueOf(lastSimStep);
        Double t = Double.valueOf(0);
        while(t < t_final){//xx

           for (Iterator iterator = participantSet.iterator(); iterator.hasNext();) {
             Participant b = (Participant) iterator.next();

             if(b.participantInfo.getLatitude() >= lat &&
                b.participantInfo.getLatitude() < lat + d &&
                b.participantInfo.getLongitude() >= longit &&
                b.participantInfo.getLongitude() < longit + d ) {



                  OperationRecord or = b.getOperationRecord(t);
//
                  if(or != null && or.getCoalitionId() != null ){
                    Double midLat = (lat + lat + d)/2;
                    Double midLong = (longit + longit + d)/2;
                    String line = t + "\t" +
                                  ParticipantsUtil.getNumYearsBefore(t.longValue()) + "\t" +
                                  midLat + "\t" +
                                  midLong + "\t" +
                                  b.participantInfo.getUid()  + "\t" +
                                  or.getSavings() + "\t" +
                                  job();

                    if(maxSavings == null || or.getSavings() > maxSavings){
                      maxSavings = or.getSavings();
                    }

                    lines.add(line);
                  }

                }

             }


            t = t + Double.valueOf(1);
        }
        longit += d;
      }
      lat += d;
    }

    if(maxSavings != null){
      if(Participants.maxSavingsAllJobs == null ||
         maxSavings > Participants.maxSavingsAllJobs){
        Participants.maxSavingsAllJobs = maxSavings;
      }
    }

    String folderName = getOrCreateFolderForJob();

    String fileName = folderName +
                      SimConfig.FILE_NAME_SAVINGS_PER_AREA_DATA;

    ParticipantsUtil.writeFile(lines, fileName);

    return fileName;

  }




  public void createHeatMapsSavingsPerArea(){
      String fileNameData = createDataSavingsPerArea();
      String folderName = getOrCreateFolderForJob();
      String rFileName = folderName +
                         SimConfig.FILE_NAME_SAVINGS_PER_AREA_RSCRIPT;
      Rscripts rScripts = new Rscripts();
      //
      rScripts.generateRHeatMapSavingsPerArea(fileNameData,
                                              MIN_LONGITUDE,
                                              MAX_LONGITUDE,
                                              MIN_LATITUDE,
                                              MAX_LATITUDE,
                                              rFileName,
                                              folderName);
      ParticipantsUtil.excecuteRScript(rFileName, null);
  }

  public String createMergedSavingsDataPerArea(){
    Vector<String> allLines = new Vector<String>();

    for(int i = 0 ; i < outputFolderNames.size(); i++){

      String folder = outputFolderNames.elementAt(i);
      Vector<String> lines = ParticipantsUtil.getDataFromFile(folder +
      SimConfig.FILE_NAME_SAVINGS_PER_AREA_DATA);

      for(int j = 0; j < lines.size(); j++){

        boolean skipLine = (i > 0 && j == 0);
        if(!skipLine){
          String line =  lines.elementAt(j);
          allLines.add(line);
        }
      }

    }
//

    String folderName = getOrCreateFolderForSimulation();

    String fileName = getCompletedName(folderName +
                SimConfig.FILE_NAME_ALL_SAVINGS_PER_AREA_DATA,
                SimConfig.DESCRIPTION,
                "");

    ParticipantsUtil.writeFile(allLines, fileName);

    return fileName;
  }

  public void createHeatMapSavingsPerAreaAllJobs(){
    //
    String dataFileName = createMergedSavingsDataPerArea();
    Rscripts rScripts = new Rscripts();
    Long final_year = ParticipantsUtil.getNumYearsBefore(lastSimStep);
    String folderName = getOrCreateFolderForSimulation();

    String rFileName = getCompletedName(folderName +
                       SimConfig.FILE_NAME_ALL_SAVINGS_PER_AREA_RSCRIPT,
                                       SimConfig.DESCRIPTION,
                                       "");

   String figureFileName = getCompletedName(folderName +
                           SimConfig.FILE_NAME_ALL_SAVINGS_PER_AREA_FIGURE,
                                           SimConfig.DESCRIPTION,
                                           "");

    //To store values needed to later normalise.
    String tmpOputFileName = folderName + SimConfig.FILE_NAME_ALL_SAVINGS_PER_AREA_TMP_OUTPUT;

    rScripts.generateRHeatMapSavingsForAllJobs( dataFileName,
                                                null,
                                                MIN_LONGITUDE,
                                                MAX_LONGITUDE,
                                                MIN_LATITUDE,
                                                MAX_LATITUDE,
                                                final_year,
                                                rFileName,
                                                figureFileName,
                                                tmpOputFileName);
     ParticipantsUtil.excecuteRScript(rFileName, null);
  }



  public void saveLimitValues(){
    Vector<String> lines = new Vector<String>();
    lines.add("maxSavings="+maxSavingsAllJobs);
    lines.add("MIN_LONGITUDE=" + MIN_LONGITUDE);
    lines.add("MAX_LONGITUDE=" + MAX_LONGITUDE);
    lines.add("MIN_LATITUDE=" + MIN_LATITUDE);
    lines.add("MAX_LATITUDE=" + MAX_LATITUDE);
    lines.add("final_year=" + ParticipantsUtil.getNumYearsBefore(lastSimStep));
    String folderName = getOrCreateFolderForSimulation();
    String fileName = folderName + SimConfig.FILE_NAME_LIMIT_VALUES_AFTER_SIM;
    ParticipantsUtil.writeFile(lines, fileName);
  }

  public void createOutputs(){
    createSeriesSavingsInCoalition();
    createSeriesFinance();
    createSeriesVolume();
    createDataSavingsNoTimeOrder();
    createSeriesTrust();
    createHeatMapsRatioBuyingInCoalition();
    createHeatMapsSavingsPerArea();
    createDataSucessCoalitionsByRegion();
    if(Participants.numJobs == job() + Long.valueOf(1)){
      System.out.println("---final job reached");
      generateBoxPlotSavingsForAllJobs();
      generateBoxPlotFinanceForAllJobs();
      createSeriesVolumeForAllJobs();
      generateBoxPlotSucessCoalitionsByRegionForAllJobs();
      createHeatMapSavingsPerAreaAllJobs();
      saveLimitValues();
      saveSimulationParamValues();
    }
    //createMergedSavingsData();
  }

  public void createSeriesTrust(){

    Vector<String> coalitionIds = new Vector<>();
    Enumeration<String> keys = coalitionSet.keys();
    Long t_final = lastSimStep;
    Vector<String> lines = new Vector<String>();
    //ids of coalitions and regions. If size of coalition becomes zero
    //the region cannot be stablished. Last retion taken.
    Hashtable <String, String> regions = new Hashtable <String, String>();

    //iterate coalitions

    lines.add("Coalition\tPeriod\tTrust\tSize\tRegion");

    while( keys.hasMoreElements() ){
      String key = keys.nextElement();
      Coalition c = coalitionSet.get(key);
      //Double[] centroid = calculateCentroidCoalition(c);
      String region = c.getRegion();
      //if(centroid != null){
      if(region != null){//xxr
        regions.put(c.getUid(), region);
      }

      if(c.hasTrustValues()){
        Hashtable<Double, Double> trustHistory = c.getTrustHistory();
        Hashtable<Double, Double> sizeHistory = c.getSizeHistory();
        Enumeration<Double> trustkeys = trustHistory.keys();

        while( trustkeys.hasMoreElements() ){
          Double tTrust = trustkeys.nextElement();
          Double trust = trustHistory.get(tTrust);
          Double size = sizeHistory.get(tTrust);
          String line = c.getUid() + "\t" + tTrust + "\t" + trust + "\t"
          + size + "\t" + regions.get(c.getUid());
          lines.add(line);
        }
      }
    }

    String folderName = getOrCreateFolderForJob();

    String fileName =   folderName +
                        SimConfig.FILE_NAME_TRUST_AND_SIZE_DATA;

    ParticipantsUtil.writeFile(lines, fileName);

    String rFileName = folderName +
                       SimConfig.FILE_NAME_TRUST_AND_SIZE_RSCRIPT;

    Rscripts rScripts = new Rscripts();

    rScripts.generateRTrust( fileName,
                             rFileName,
                             folderName );

    ParticipantsUtil.excecuteRScript(rFileName, null);
  }


  public void createDataSucessCoalitionsByRegion(){
    Vector<String> lines = new Vector<String>();

    lines.add("Trial\tRegion\tSuccessful\tUnSuccessful");

    Vector<String> regions = new Vector<String>();
    Hashtable<String, Integer> count = new Hashtable<String, Integer>();

      Enumeration<String> keys = coalitionSet.keys();

      while( keys.hasMoreElements() ){
        String key = keys.nextElement();
        Coalition c = coalitionSet.get(key);
        //Double[] centroid = calculateCentroidCoalition(c);
        String region = c.getRegion();
        //if(centroid != null){
        if(region != null){
          //String region = getRegion(centroid[0], centroid[1]);
          if(!regions.contains(region)){
            regions.add(region);
          }
          String sucess = c.wasSuccessful() ? "Successful" : "UnSuccessful";
          String keyCount = region + "-" + sucess;
          Integer cnt = count.get(keyCount);
          if(cnt == null){
            cnt = Integer.valueOf(1);
          }else{
            cnt += Integer.valueOf(1);
          }
          count.put(keyCount, cnt);
        }
      }

    for(int i = 0 ; i < regions.size(); i++){
      String region = regions.elementAt(i);
      String keySuccess = region + "-" + "Successful";
      String keyNotSucess = region + "-" + "UnSuccessful";

      String line = job() + "\t" +
      region + "\t" +
      (count.get(keySuccess) != null ?  count.get(keySuccess) : 0) + "\t" +
      (count.get(keyNotSucess) != null ?  count.get(keyNotSucess) : 0);

      lines.add(line);
    }

    String folderName = getOrCreateFolderForJob();
    String fileName =   folderName +
                        SimConfig.FILE_NAME_SUCCESSFUL_AND_REGION_DATA;

    ParticipantsUtil.writeFile(lines, fileName);
  }

  public String createMergedSucessCoalitionsByRegionData(){
    Vector<String> allLines = new Vector<String>();

    for(int i = 0 ; i < outputFolderNames.size(); i++){

      String folder = outputFolderNames.elementAt(i);
      Vector<String> lines = ParticipantsUtil.getDataFromFile(folder +
      SimConfig.FILE_NAME_SUCCESSFUL_AND_REGION_DATA);

      for(int j = 0; j < lines.size(); j++){

        boolean skipLine = (i > 0 && j == 0);
        if(!skipLine){
          String line =  lines.elementAt(j);
          allLines.add(line);
        }
      }

    }

    String folderName = getOrCreateFolderForSimulation();

    String fileName = getCompletedName(folderName +
                SimConfig.FILE_NAME_ALL_SUCCESSFUL_AND_REGION_DATA,
                SimConfig.DESCRIPTION,
                "");

    ParticipantsUtil.writeFile(allLines, fileName);
    return fileName;
  }

  public void generateBoxPlotSucessCoalitionsByRegionForAllJobs(){
    String folderName = getOrCreateFolderForSimulation();
    String fileName = createMergedSucessCoalitionsByRegionData();

    String rFileName = getCompletedName( folderName +
                                         SimConfig.FILE_NAME_ALL_SUCCESSFUL_AND_REGION_RSCRIPT,
                                         SimConfig.DESCRIPTION,
                                         "");

    String figureFileName = getCompletedName(folderName +
                                           SimConfig.FILE_NAME_ALL_SUCCESSFUL_AND_REGION_FIGURE,
                                           SimConfig.DESCRIPTION,
                                           "");

    Rscripts rscripts = new Rscripts();
    rscripts.generateRBoxPlotSucessCoalitionsByRegionForAllJobs(fileName,
    rFileName,
    figureFileName);

    ParticipantsUtil.excecuteRScript(rFileName, null);
  }

  public String getOrCreateFolderForJob(){

    String folderName = getCompletedName(SimConfig.getJobOutputFolder(),
                                      "ma_output_" + SimConfig.DESCRIPTION,
                                      String.valueOf(job()));
    ParticipantsUtil.createFolder(folderName);

    if(!outputFolderNames.contains(folderName)){
       //outputFolderNames.put(Double.valueOf(job()), folderName);
       outputFolderNames.add(folderName);
       System.out.println("---Folder added:" + folderName + ", total: " + outputFolderNames.size());
    }

    return folderName;

  }

  public void saveSimulationParamValues(){
    String folderName = getOrCreateFolderForSimulation();
    String fileName = folderName + "configParams.txt";
    SimConfig.saveParamValues(fileName);

  }

  public String getOrCreateFolderForSimulation(){

    String folderName = getCompletedName(SimConfig.getSimulationOutputFolder(),
                                      "ma_output_" + SimConfig.DESCRIPTION, "");
    ParticipantsUtil.createFolder(folderName);

    return folderName;

  }

  public void createSeriesSavingsInCoalition(){

    Vector<Participant> wasInCoalition = new Vector<Participant>();

    Double t_final = Double.valueOf(lastSimStep);
    logger.warn("t_final:" + t_final);
    Double t = Double.valueOf(0);

    while(t < t_final){

      for (Iterator iterator = participantSet.iterator(); iterator.hasNext();) {
        Participant b = (Participant) iterator.next();

        OperationRecord or = b.getOperationRecord(t);

        if(or != null && or.getCoalitionId() != null){
          if(!wasInCoalition.contains(b)){
              wasInCoalition.add(b);

            //  logger.warn("createSeriesSavings:" + t + ", participant " + b.participantInfo.getUid() + " or.savings:" + or.getSavings());

          }

        }

      }
      t = t + Double.valueOf(1);
    }

    t = Double.valueOf(0);
    Vector<String> lines = new Vector<String>();
    String line = "Period";

    for (int j = 0; j < wasInCoalition.size(); j++) {
      Participant b = wasInCoalition.elementAt(j);
      line = line + "\t" + "b" + j;
    }
    lines.add(line);

    while(t < t_final){
      line = String.valueOf(t);

      for (int j = 0; j < wasInCoalition.size(); j++) {
        Participant b = wasInCoalition.elementAt(j);
        OperationRecord or = b.getOperationRecord(t);
        line = line + "\t" + (or != null ? or.getSavings() : 0.0 );
      }
      //logger.warn(line);
      lines.add(line);
      t = t + Double.valueOf(1);
    }
    //uidsWasInCoalition

    String folderName = getOrCreateFolderForJob();

    String fileName =  folderName +
                       SimConfig.FILE_NAME_SAVINGS_DATA;

    String rFileName = folderName +
                       SimConfig.FILE_NAME_SAVINGS_RSCRIPT;

    ParticipantsUtil.writeFile(lines, fileName);
    Rscripts rScripts = new Rscripts();
    rScripts.generateRSavings(wasInCoalition, fileName, rFileName, folderName);
    ParticipantsUtil.excecuteRScript(rFileName, null);
  }

  public void createSeriesFinance(){

	Vector<String> lines = new Vector<String>();
	lines.add("Participant\tPeriod\tFinance\tInCoalition\tYear\tMonth\tTrial");
	Double t_final = Double.valueOf(lastSimStep);

	Double t = Double.valueOf(0);

	while(t < t_final){

	  for (Iterator iterator = participantSet.iterator(); iterator.hasNext();) {
	    Participant b = (Participant) iterator.next();

	    FinanceRecord fr = b.getFinanceForPeriod(t);

	    String line = b.getUid() + "\t" +
	    			  t + "\t" +
	    			  fr.getFinance() + "\t" +
	    			  (fr.isInCoalition() ? "Yes" : "No") + "\t" +
	    			  ParticipantsUtil.getNumYearsBefore(t.longValue()) + "\t" +
	    			  ParticipantsUtil.getMonthFromSimStep(t.longValue()) + "\t" +
	    			  job();

	    lines.add(line);
	  }

	  t = t + Double.valueOf(1);
	}

	String folderName = getOrCreateFolderForJob();

    String fileName =   folderName +
                        SimConfig.FILE_NAME_FINANCE_DATA;

    ParticipantsUtil.writeFile(lines, fileName);

    String rFileName = folderName +
                       SimConfig.FILE_NAME_FINANCE_RSCRIPT;

    Rscripts rScripts = new Rscripts();

    rScripts.generateRFinance( 	fileName,
                             	rFileName,
                             	folderName );

    ParticipantsUtil.excecuteRScript(rFileName, null);

  }

  public void generateBoxPlotFinanceForAllJobs(){

	  String dataFileName = createMergedData(	SimConfig.FILE_NAME_FINANCE_DATA,
		  										SimConfig.FILE_NAME_ALL_FINANCE_DATA,
		  										SimConfig.DESCRIPTION);

    Rscripts rscripts = new Rscripts();

    String folderName = getOrCreateFolderForSimulation();

    String rScriptFileName = getCompletedName(folderName +
    SimConfig.FILE_NAME_ALL_FINANCE_RSCRIPT,
                    "",
                    "");

    String figureFileName = getCompletedName(folderName +
    SimConfig.FILE_NAME_ALL_FINANCE_FIGURE,
                    "",
                    "");

    rscripts.generateRFinanceForAllJobs(dataFileName,
									    rScriptFileName,
									    figureFileName);

    ParticipantsUtil.excecuteRScript(rScriptFileName, null);

  }

  public void createSeriesVolume(){

		Vector<String> lines = new Vector<String>();
		lines.add("Participant\tPeriod\tVolume\tInCoalition\tTrial");
		Double t_final = Double.valueOf(lastSimStep);

		Double t = Double.valueOf(0);

		while(t < t_final){

			for (Iterator iterator = participantSet.iterator(); iterator.hasNext();) {
			    Participant b = (Participant) iterator.next();

			    OperationRecord or = b.getOperationRecord(t);

			    if(or != null ){
			    	boolean inCoalition = or.getCoalitionId() != null &&
    			  			or.getCoalitionSupplier() != null;

			    	String line = b.getUid() + "\t" +
			    			  t + "\t" +
			    			  or.getQuantityBought() + "\t" +
			    			  (inCoalition ? "Yes" : "No") + "\t" +
			    			  job();

			    	lines.add(line);
			    }
			}

			t = t + Double.valueOf(1);
		}

		String folderName = getOrCreateFolderForJob();

	    String fileName =   folderName +
	                        SimConfig.FILE_NAME_VOLUME_DATA;

	    ParticipantsUtil.writeFile(lines, fileName);

	    String rFileName = folderName +
	                       SimConfig.FILE_NAME_VOLUME_RSCRIPT;

	    Rscripts rScripts = new Rscripts();

	    rScripts.generateRVolume( 	fileName,
	                             	rFileName,
	                             	folderName );

	    ParticipantsUtil.excecuteRScript(rFileName, null);

	    rFileName = folderName +
                SimConfig.FILE_NAME_PERCENT_VOLUME_RSCRIPT;

	    rScripts.generateRPcentVolume(	fileName, //same file as above.
							    		rFileName,
							    		folderName);

	    ParticipantsUtil.excecuteRScript(rFileName, null);
	  }

  public void createSeriesVolumeForAllJobs(){
	  String dataFileName = createMergedData(	SimConfig.FILE_NAME_VOLUME_DATA,
												SimConfig.FILE_NAME_ALL_VOLUME_DATA,
												SimConfig.DESCRIPTION);

	  Rscripts rscripts = new Rscripts();

	  String folderName = getOrCreateFolderForSimulation();

	  String rScriptFileName = getCompletedName(folderName +
			  SimConfig.FILE_NAME_ALL_VOLUME_RSCRIPT,
			  "",
			  "");

	  String figureFileName = getCompletedName(folderName +
	    SimConfig.FILE_NAME_ALL_VOLUME_FIGURE,
	                    "",
	                    "");

	  rscripts.generateRPcentVolumeForAllJobs(dataFileName,
										    rScriptFileName,
										    figureFileName);

	  ParticipantsUtil.excecuteRScript(rScriptFileName, null);
  }

  public void createDataSavingsNoTimeOrder(){

    Vector<String> lines = new Vector<>();
    lines.add("Period\tYear\tParticipant\tCoalition\tSavings\tTrial");

    for (Iterator iterator = participantSet.iterator(); iterator.hasNext();) {
      Participant b = (Participant) iterator.next();

      Hashtable<Double, OperationRecord> historyOperation = b.getHistoryOperation();
      Enumeration<Double> keys = historyOperation.keys();

      while( keys.hasMoreElements() ){
        Double t = keys.nextElement();
        OperationRecord or = historyOperation.get(t);
        if(or.getCoalitionId() != null){
          String line = or.getStepNum() + "\t" +
                        ParticipantsUtil.getNumYearsBefore(t.longValue()) + "\t" +
                        b.participantInfo.getUid() + "\t" +
                        or.getCoalitionId() + "\t" +
                        or.getSavings() + "\t" +
                        job();

          lines.add(line);
        }
      }

    }

    String folderName = getOrCreateFolderForJob();

    String fileName =   folderName +
                        SimConfig.FILE_NAME_SAVINGS_NO_TIME_ORDER_DATA;

    ParticipantsUtil.writeFile(lines, fileName);
  }


  public String createMergedData( String baseFileNameDataOrigin,
								  String fileNameDestination,
								  String description ){

    Vector<String> allLines = new Vector<String>();
    System.out.println("----createMergedSavingsData");

for(int i = 0 ; i < outputFolderNames.size(); i++){


String folder = outputFolderNames.elementAt(i);
      Vector<String> lines = ParticipantsUtil.getDataFromFile(folder +
    		  baseFileNameDataOrigin);

      //for(Iterator iterator2 = lines.iterator(); iterator2.hasNext();){
        for(int j = 0; j < lines.size(); j++){
        //String line =  (String)iterator2.next();
        boolean skipLine = (i > 0 && j == 0);
        if(!skipLine){
          String line =  lines.elementAt(j);
          allLines.add(line);
        }
      }
    }

    String folderName = getOrCreateFolderForSimulation();

    String fileName = getCompletedName(	folderName +
							    		fileNameDestination,
							    		description,
							            "");

    //String fileName =   SimConfig.BASE_FOLDER +
    //                    SimConfig.FILE_NAME_ALL_SAVINGS_DATA;
    ParticipantsUtil.writeFile(allLines, fileName);

    return fileName;
  }



  public static String getCompletedName(String base, String desc, String job){
    String fileName = base.replaceFirst("\\[desc\\]", desc);
    fileName = fileName.replaceFirst("\\[job\\]", job);
    //fileName = fileName.replaceFirst("\\[period\\]", period);
    return fileName;
  }



  public void loadPopulationSuppliers(){

    Double referencePricePerTon = SimConfig.REFERENCE_PRICE_PER_TON;

    Double thresholdTier = ParticipantsUtil.getThresholdSecondTierSupplier();
    //High capacity
    Supplier s0 = new Supplier("0", "Supplier 0", "WF10 4LE");
    Vector<Double[]> s0Tiers = new Vector<Double[]>();
    s0Tiers.add(new Double[]{thresholdTier, referencePricePerTon});
    s0Tiers.add(new Double[]{2*thresholdTier, 0.985*referencePricePerTon});
    s0Tiers.add(new Double[]{4*thresholdTier, 0.98*referencePricePerTon});
    s0.setPriceTiers(s0Tiers);
    s0.setConsidersAlliances(false);
    assignContractDiscountFunction(s0); //[review contracts] To-Do: modify assignment of discount function if needed, to match with discounts for alliances?
    populationSuppliers.add(s0);

    ////Medium capacity
    Supplier s1 = new Supplier("1", "Supplier 1", "NR21 7AS");
    Vector<Double[]> s1Tiers = new Vector<Double[]>();
    s1Tiers.add(new Double[]{thresholdTier, referencePricePerTon});
    s1Tiers.add(new Double[]{2*thresholdTier, 0.986*referencePricePerTon});
    s1Tiers.add(new Double[]{3*thresholdTier, 0.981*referencePricePerTon});
    s1.setPriceTiers(s1Tiers);
    s1.setConsidersAlliances(true);
    assignContractDiscountFunction(s1);
    populationSuppliers.add(s1);

    Supplier s2 = new Supplier("2", "Supplier 2", "IP14 2AG");
    Vector<Double[]> s2Tiers = new Vector<Double[]>();
    s2Tiers.add(new Double[]{thresholdTier, referencePricePerTon});
    s2Tiers.add(new Double[]{2*thresholdTier, 0.9855*referencePricePerTon});
    s2Tiers.add(new Double[]{3*thresholdTier, 0.9805*referencePricePerTon});
    s2.setPriceTiers(s2Tiers);
    s2.setConsidersAlliances(false);
    assignContractDiscountFunction(s2);
    populationSuppliers.add(s2);

    ////Small capacity
    Supplier s3 = new Supplier("3", "Supplier 3", "IP32 7AD");
    Vector<Double[]> s3Tiers = new Vector<Double[]>();
    s3Tiers.add(new Double[]{thresholdTier, referencePricePerTon});
    s3Tiers.add(new Double[]{2*thresholdTier, 0.9855*referencePricePerTon});
    s3.setPriceTiers(s3Tiers);
    s3.setConsidersAlliances(true);
    assignContractDiscountFunction(s3);
    populationSuppliers.add(s3);

    Supplier s4 = new Supplier("4", "Supplier 4", "M24 1GB");
    Vector<Double[]> s4Tiers = new Vector<Double[]>();
    s4Tiers.add(new Double[]{thresholdTier, referencePricePerTon});
    s4Tiers.add(new Double[]{2*thresholdTier, 0.9842*referencePricePerTon});
    s4.setPriceTiers(s4Tiers);
    s4.setConsidersAlliances(true);
    assignContractDiscountFunction(s4);
    populationSuppliers.add(s4);

    Supplier s5 = new Supplier("5", "Supplier 5", "TD15 2UZ");
    Vector<Double[]> s5Tiers = new Vector<Double[]>();
    s5Tiers.add(new Double[]{thresholdTier, referencePricePerTon});
    s5Tiers.add(new Double[]{2*thresholdTier, 0.9857*referencePricePerTon});
    s5.setPriceTiers(s5Tiers);
    s5.setConsidersAlliances(false);
    assignContractDiscountFunction(s5);
    populationSuppliers.add(s5);

    Supplier s6 = new Supplier("6", "Supplier 6", "BA12 8NN");
    Vector<Double[]> s6Tiers = new Vector<Double[]>();
    s6Tiers.add(new Double[]{thresholdTier, referencePricePerTon});
    s6Tiers.add(new Double[]{2*thresholdTier, 0.9856*referencePricePerTon});
    s6.setPriceTiers(s6Tiers);
    s6.setConsidersAlliances(false);
    assignContractDiscountFunction(s6);
    populationSuppliers.add(s6);



  }

  //Assigns a contract discount function to a supplier.
  //Exponential discount by default.
  private void assignContractDiscountFunction(Supplier s) {
	  s.setContractDiscountFunction(
			  ParticipantsUtil.getVariationExpDiscountFunction(random.nextDouble(),
					  									  random.nextDouble()));
  }

  public void printPopulationSuppliers(){

    for(int i = 0; i < populationSuppliers.size(); i++){
      Supplier s = (Supplier)populationSuppliers.get(i);
      s.print();
    }
  }

  public void loadPopulationParticipants(){
    logger.info("Loading population of participants.");
    //records omitted

    numParticipants = pupulationParticipants.size();

    MIN_LATITUDE = Double.POSITIVE_INFINITY;
    MAX_LATITUDE = Double.NEGATIVE_INFINITY;
    MIN_LONGITUDE = Double.POSITIVE_INFINITY;
    MAX_LONGITUDE = Double.NEGATIVE_INFINITY;

    for (Iterator iterator = pupulationParticipants.iterator(); iterator.hasNext();) {
      ParticipantSource b = (ParticipantSource) iterator.next();

      if(b.getLatitude() < MIN_LATITUDE){
        MIN_LATITUDE = b.getLatitude();
      }

      if (b.getLongitude() < MIN_LONGITUDE){
        MIN_LONGITUDE = b.getLongitude();
      }

      if (b.getLatitude() > MAX_LATITUDE){
        MAX_LATITUDE = b.getLatitude();
      }

      if (b.getLongitude() > MAX_LONGITUDE){
        MAX_LONGITUDE = b.getLongitude();
      }

      MAX_DISTANCE = ParticipantsUtil.euclideanDistance(MIN_LONGITUDE, MIN_LATITUDE, MAX_LONGITUDE, MAX_LATITUDE);

    }
  }

 //Converts from latitude and longitude to yard dimensions.
 public Double2D getGeoYardLocation(ParticipantSource b){
    return null;
 }

  public void printPopulationParticipants(){

    for (Iterator iterator = pupulationParticipants.iterator(); iterator.hasNext();) {
      ParticipantSource b = (ParticipantSource) iterator.next();
      b.print();
    }
  }

  //business Orientation: HobbyOriented; BusinessOrienHobbyOriented + BusinessOriented = 100%
  //BusinessOriented = 100% − HobbyOriented
  public void assignOrientationParticipants(){
    for (Iterator iterator = pupulationParticipants.iterator(); iterator.hasNext();) {
      ParticipantSource b = (ParticipantSource) iterator.next();
      Double r = random.nextDouble();
      b.setBusinessOriented(r < 0.5 ? 0.5 : 1.0);
    }
  }



    public void assignBaseQPParticipants(){

    assignBaseQPYear("62", 110.0);

    assignBaseQPYear("56", 110.0);


    assignBaseQPYear("14", 12.0);
    assignBaseQPYear("89", 6.0);
    assignBaseQPYear("79", 130.0);
    assignBaseQPYear("50", 14.0);
    assignBaseQPYear("10",  2.0);


    assignPrdProdYear("56",	962000.0/100.0);
    assignPrdProdYear("6",	78000.0/100.0);
    assignPrdProdYear("58",	208000.0/100.0);
    assignPrdProdYear("89",	124800.0/100.0);
    assignPrdProdYear("12",	312000.0/100.0);
    assignPrdProdYear("62",	942676/100.0);

    assignPrdProdYear("8",	21274.0/100.0);
    assignPrdProdYear("14",	72800.0/100.0);
    assignPrdProdYear("65",	20800.0/100.0);
    assignPrdProdYear("91",	8320.0/100.0);
    assignPrdProdYear("50",	153920.0/100.0);


    Double q0 = 83.2;
    Double q1 = 470.37;
    Double q3 = 2600.0;
    Double q4 = 9620.0;

    for (Iterator iterator = pupulationParticipants.iterator(); iterator.hasNext();) {
      ParticipantSource b = (ParticipantSource) iterator.next();

      if(b.getBaseQPYear() == null){

        if(b.getPrdProdYear() == null){
          //System.out.println("Assigning ProdYear for " + b.getName());
          Double sampledProdYear_ = ParticipantsUtil.scale(random.nextDouble(), q0, q3, 0.0, 1.0);
          Double sampledProdYear = ParticipantsUtil.round(sampledProdYear_, SimConfig.NDEC_PROD_YEAR);
          b.setPrdProdYear(sampledProdYear);
          //System.out.println("sampledProdYear:" + sampledProdYear);
        }

        Double sampledApproxMaltYear = ParticipantsUtil.round(ParticipantsUtil.approxMaltBasedOnPrd(b.getPrdProdYear()), SimConfig.NDEC_QP);
        b.setBaseQPYear( sampledApproxMaltYear );
        //System.out.println("sampledApproxMaltYear" + sampledApproxMaltYear);
      }


    }


  }

  public void assignBaseQPYear(String uid, Double tons){
    ParticipantSource bs = getParticipantSorceByUid(uid);
    if(bs != null){
      bs.setBaseQPYear(tons);
    }
  }

  public void assignPrdProdYear(String uid, Double hectolitres){
    ParticipantSource bs = getParticipantSorceByUid(uid);
    if(bs != null){
      bs.setPrdProdYear(hectolitres);
    }
  }


  public void assignSupplier(Participant b){
      Vector<String> idsSuppliers = new Vector<String>();

      for (Iterator iterator = populationSuppliers.iterator(); iterator.hasNext();) {
        Supplier s = (Supplier) iterator.next();
        //System.out.print("----------->supplier:" + s.getName());
        if(s.hasCapacityToSupply(b.getInitEstQ())) {
          idsSuppliers.add(s.getUid());
          //System.out.println(" has capacity");
        }//else{
          //System.out.println(" has no capacity");
        //}
      }
      Supplier selected = null;

      do{
        int i = random.nextInt(idsSuppliers.size());
        String idSelected = idsSuppliers.get(i);
        selected = getSupplierByUid(idSelected);
      }while(selected == null);

      //System.out.println("Selected " + selected.getName() + " for " + b.participantInfo.getName());

      //based on size?
      String key = b.participantInfo.getUid() + "-" + selected.getUid();
      SupplyRelationship sr = new SupplyRelationship(true, selected);
      b.setSupplier(selected);

      //b.setSupplyRelationship(sr); //set relationship.
      supplyRelationships.put(key, sr); //add entry to dictionary of relatinships.



  }

  public Supplier getSupplierByUid(String uid){
    Supplier selectedSupplier = null;

    for (Iterator iterator = populationSuppliers.iterator(); iterator.hasNext();) {
      Supplier s = (Supplier) iterator.next();
      if(s.getUid().equals(uid)) {
        selectedSupplier = s;
        break;
      }
    }
    return selectedSupplier;
  }

  public ParticipantSource getParticipantSorceByUid(String uid){
    ParticipantSource selectedParticipantSource = null;

    for (Iterator iterator = pupulationParticipants.iterator(); iterator.hasNext();) {
      ParticipantSource b = (ParticipantSource) iterator.next();
      if(b.getUid().equals(uid) ){
        selectedParticipantSource = b;
        break;
      }
    }

    return selectedParticipantSource;
  }
  /**
  * Select member A, as in definition.
  */
  public Vector<Participant> selectMembersA(){
    return null;
  }

  /**
  * Select member B, as in definition.
  */
  public Vector<Participant> selectMembersB(){
    return null;
  }

  //Tryies to add a participant to a colition.
  public void askEntryToCoalition(Participant b, Coalition c, Long currentSimStep){

    Coalition c_ = null;

    if(c.getUid() != null &&
       coalitionSet.containsKey(c.getUid())){
      c_ = coalitionSet.get(c.getUid());
    }else{
      c_ = c;
    }

    int action = 0;

    if(c_.acceptMember(b, currentSimStep)){
      action = c_.addMember(b, currentSimStep, getSuppliers());
      coalitionSet.put(c_.getUid(), c_);

      if(action == Participants.ACTION_SCHEDULE_COALITION){
        scheduleCoalition(c_);
      }
    }else{
      logger.debug("Participant " + b.participantInfo.getUid() +
      " not added to coalition " +
      c.getUid());
    }


  }



  public void scheduleCoalition(Coalition c){
    //c.setActive(true);

    schedule.scheduleRepeating(c);

    logger.info("Coalition " + c.getUid() + " scheduled.");
  }

  public Double getCoalitionTrustValue(String coalitionUid, Double t){
    Coalition c = coalitionSet.get(coalitionUid);
    return (c != null ? c.getTrustValue(t) : null);
  }

  public boolean coalitionDissolved(String coalitionUid){
    Coalition c = coalitionSet.get(coalitionUid);
    return (c != null ? c.isDissolved() : true);
  }

  public Vector<SupplierAgent> getSuppliers(){
    return suppliers;
  }

  public Vector<Participant> getParticipants(){
    return participantSet;
  }

  public static String getParamValue(String paramName, String[] params){
    String paramValue = null;
    for(int i=0; i<params.length; i++){
      if(params[i].equals(paramName) && (i+1 < params.length) ){
        paramValue = params[i+1];
      }
    }
    return paramValue;
  }


  public static void setNumJobs(String nJobs){
    Participants.numJobs = Long.valueOf(nJobs);
  }

  public static void main(String[] args)
  {

    SimConfig.loadConfig(args);
    SimConfig.print();

    String numJobs = getParamValue("-repeat", args);
    Participants.setNumJobs(numJobs);

    doLoop(Participants.class, args);

    System.exit(0);
  }
}
