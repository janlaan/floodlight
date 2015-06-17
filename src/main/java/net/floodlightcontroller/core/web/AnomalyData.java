package net.floodlightcontroller.core.web;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import net.floodlightcontroller.core.web.AnomaliesResource.AnomaliesJsonSerializerWrapper;
import javafx.util.Pair;

import com.almworks.sqlite4java.SQLiteConnection;
import com.almworks.sqlite4java.SQLiteException;
import com.almworks.sqlite4java.SQLiteStatement;

public class AnomalyData {
	HashMap<String, Double> indexCount;
HashMap<String, Double> indexCountHour;
HashMap<Pair<String,String>, Double> dataHour;

public AnomalyData() {
	this.indexCount = new HashMap<String, Double>();
	this.indexCountHour = new HashMap<String, Double>();
}

public Map<String, AnomaliesJsonSerializerWrapper> run() {
    //  System.out.println("Starting anomaly detection");
      
      HashMap<Pair<String,String>, Double> data;

      Map<String, AnomaliesJsonSerializerWrapper> anomaliesForJson = new HashMap<String, AnomaliesJsonSerializerWrapper>();
      try {
	      SQLiteConnection db = new SQLiteConnection(new File("/Users/jan/Documents/floodlight-1.1/restlog.sqlite"));
	      db.open(true);
	      
	      SQLiteStatement st = db.prepare("SELECT ip, agent, api, timestamp FROM restlog WHERE timestamp > ?");

		data = getData(st);

		db.dispose();
      
		 if(data.size() > 0)
		 {
			//Todo: Decide when something is an anomaly. Purely based on percentages only works when there is a high volume of consistent API calls.
			//Definately flag new API calls
			//For existing api calls, only flag them if there is enough data, and a large difference in percentages. (15%? Determine by testing)
			//How to validate/test this??? Generate lots of test data. (semi-random times), then change behaviour to "malicious".
		// System.out.println("\nTable for last 30 days:\n-----------");
		 
		 HashMap<Pair<String, String>, Double> chances = new HashMap<Pair<String, String>, Double>();
	      for (Entry<Pair<String, String>, Double> entry : data.entrySet()) {
	    	    Pair<String,String> key = entry.getKey();
	    	    Double count = entry.getValue();
	    	    String index = key.getKey();
	    	    
	    	    String api = key.getValue();
	    	    double totalIndex = this.indexCount.get(index);
	    	//    System.out.println(count);
	    	    double pct = Math.round((double) count / totalIndex * 1000) / 10.0; //percentage with one decimal
	    	    chances.put(key, count/totalIndex);
	    	//    System.out.println(api + " : " + pct + "%");
	    	    
	    	}
	     // System.out.println("\nTable for last hour:\n------------"); 
	      for (Entry<Pair<String, String>, Double> entry : this.dataHour.entrySet()) {
	    	    Pair<String,String> key = entry.getKey();
	    	    Double count = entry.getValue();
	    	    String index = key.getKey();
	    	    
	    	    String api = key.getValue();
	    	    double totalIndex = this.indexCountHour.get(index);
	    	    double pct = Math.round((double) count / totalIndex * 1000) / 10.0; //percentage with one decimal
	    	   // System.out.println(api + " : " + pct + "%");
	    	    Double historicalChance = chances.get(key);
	    	    
	    	    //Check if this application appears in our test set. If not, the app is first observed, and new, and it will be accepted.
	    	    //Note that when an application is malicious from the start, the entire detection will fail. (That's an unfortunate characteristic of anomaly based detection)
	    	    int indexExistsPreviously = data.keySet().stream().filter(p -> p.getKey().equals(index)).collect(Collectors.toList()).size();
	    	   
	    	    /* chance diff of 0.15: Raise an alert */
	    	    if(indexExistsPreviously == 0) {
	    	    	//System.err.println("Skipping new application" + index);
	    	    }
	    	    else if(historicalChance == null) {
	    	    	//System.out.println("Alert: New API call from existing application " + api + "---" + indexExistsPreviously + "---" + index);

		    	    anomaliesForJson.put(index + api, new AnomaliesJsonSerializerWrapper(index, api,
		    	    		"0 [new]",
	    	    			Double.toString(Math.round(count/totalIndex * 100) / 100.0)));
	    	    }
	    	    else if(historicalChance != null && Math.abs(historicalChance - (count / totalIndex)) > 0.15) {
	    	    	//System.out.println("Alert: more than 0.15 chance difference for " + api + " (" + (count / totalIndex) + " instead of "+ historicalChance + ")");
	    	    	anomaliesForJson.put(index + api, new AnomaliesJsonSerializerWrapper(index, api, 
	    	    			Double.toString(Math.round(historicalChance * 100) / 100.0),
	    	    			Double.toString(Math.round(count/totalIndex * 100) / 100.0)));
	    	    }
	    	}
	      
	      
		 }
		 else {
		//	 System.out.println("No historical data, skipping.");
		 }

      } catch(SQLiteException e) {
    	  System.out.println("Error setting up database connection: " + e.getMessage());
      }
      
      return anomaliesForJson;
   }
   
   private HashMap<Pair<String,String>, Double> getData(SQLiteStatement st) {
	   HashMap<Pair<String,String>, Double> data = new HashMap<Pair<String,String>, Double>();
	   this.dataHour = new HashMap<Pair<String,String>, Double>();

	   try {
		   long currentTime = new Date().getTime();
		   st.bind(1, currentTime - (86400L * 1000 * 30)); //Get last 34 days, this is our testset. Anything more than 34 days ago will have weight 0 anyway.
		   long lastHour = currentTime - (3600L * 1000); // 1 hour in milliseconds
		   while(st.step()) {

		      String index = st.columnString(0) + ":" + st.columnString(1);
	    	  
	    	  //Store last hour separately, for comparison, this is our anomaly
	    	  if(st.columnLong(3) > lastHour) {
	    		  //nb: Weight is always 1 here, as all data is from the current day.
			      dataHour.merge(new Pair<String, String>(index, st.columnString(2)), 1.0, (a, b) -> a + b);
		    	  this.indexCountHour.merge(index, 1.0, (a, b) -> a+b);

	    	  }
	    	  else {
			      long ageInDays = (long) Math.floor((currentTime - st.columnLong(3)) / (86400.0 * 1000));
			   //   System.out.println(ageInDays);
			      double weight = Math.max(1 - (ageInDays * 0.03), 0.0); //Weight decreases 3% per day, but it can never be negative.
			      
			      data.merge(new Pair<String, String>(index, st.columnString(2)), weight, (a, b) -> a + b);
		    	  this.indexCount.merge(index, weight, (a, b) -> a+b);
	    	  }

		   }
		} catch(SQLiteException e) {
		   System.out.println("SQLite Exception: " + e.getMessage());
		} finally {
		   st.dispose();
		}
	   
	   return data;
   }
}
