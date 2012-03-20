package net.floodlightcontroller.perfmon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.openflow.protocol.OFMessage;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.perfmon.CircularTimeBucketSet;

/**
 * An IPktInProcessingTimeService implementation that does nothing.
 * This is used mainly for performance testing or if you don't
 * want to use the IPktInProcessingTimeService features.
 * @author alexreimers
 *
 */
public class NullPktInProcessingTime 
    implements IFloodlightModule, IPktInProcessingTimeService {

    private CircularTimeBucketSet emptyBucket;
    
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IPktInProcessingTimeService.class);
        return l;
    }
    
    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
        IFloodlightService> m = 
            new HashMap<Class<? extends IFloodlightService>,
                        IFloodlightService>();
        // We are the class that implements the service
        m.put(IPktInProcessingTimeService.class, this);
        return m;
    }
    
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        // We don't have any dependencies
        return null;
    }
    
    @Override
    public void init(FloodlightModuleContext context)
                             throws FloodlightModuleException {
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        // no-op
    }

    @Override
    public CircularTimeBucketSet getCtbs() {
        return emptyBucket;
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void bootstrap(Set<IOFMessageListener> listeners) {

    }

    @Override
    public void recordStartTimeComp(IOFMessageListener listener) {

    }

    @Override
    public void recordEndTimeComp(IOFMessageListener listener) {

    }

    @Override
    public void recordStartTimePktIn() {

    }

    @Override
    public void recordEndTimePktIn(IOFSwitch sw, OFMessage m,
                                   FloodlightContext cntx) {
        
    }
}