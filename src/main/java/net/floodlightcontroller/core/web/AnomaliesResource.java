package net.floodlightcontroller.core.web;

import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;


public class AnomaliesResource extends ServerResource {
	
    public static class AnomaliesJsonSerializerWrapper {
        private final String application;
        private final String api; 
        private final String originalChance;
        private final String newChance;
        public AnomaliesJsonSerializerWrapper(String application, String api, String originalChance, String newChance) {
            this.application = application;
            this.api = api;
            this.originalChance = originalChance;
            this.newChance = newChance;
        }
        
        public String getApplication() {
            return application;
        }
        public String getApi() {
            return api;
        }
        public String getOriginalChance() {
            return originalChance;
        }
        public String getNewChance() {
            return newChance;
        }



    }

    @Get("json")
    public Set<AnomaliesJsonSerializerWrapper> retrieve(){ 
    	
    	AnomalyData ad = new AnomalyData();
    	Map<String, AnomaliesJsonSerializerWrapper> anomalies = ad.run();
    	System.err.println(anomalies.size());
        //IOFSwitchService switchService = 
        //    (IOFSwitchService) getContext().getAttributes().
       //         get(IOFSwitchService.class.getCanonicalName());
        Set<AnomaliesJsonSerializerWrapper> anomalySets = new HashSet<AnomaliesJsonSerializerWrapper>();
        
        for(Entry<String, AnomaliesJsonSerializerWrapper> e : anomalies.entrySet()) {
        	anomalySets.add(e.getValue());
        }
        //for(IOFSwitch sw: switchService.getAllSwitchMap().values()) {

        //}
        return anomalySets;
    }
}
