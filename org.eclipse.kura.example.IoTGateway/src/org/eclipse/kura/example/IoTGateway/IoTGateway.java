package org.eclipse.kura.example.IoTGateway;

import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.kura.KuraConnectException;
import org.eclipse.kura.cloudconnection.listener.CloudConnectionListener;
import org.eclipse.kura.cloudconnection.listener.CloudDeliveryListener;
import org.eclipse.kura.cloudconnection.message.KuraMessage;
import org.eclipse.kura.cloudconnection.publisher.CloudPublisher;
import org.eclipse.kura.cloudconnection.subscriber.CloudSubscriber;
import org.eclipse.kura.cloudconnection.subscriber.listener.CloudSubscriberListener;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.data.DataService;
import org.eclipse.kura.data.listener.DataServiceListener;
import org.eclipse.kura.gpio.GPIOService;
import org.eclipse.kura.message.KuraPayload;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IoTGateway implements DataServiceListener, ConfigurableComponent, CloudConnectionListener, CloudDeliveryListener, CloudSubscriberListener {
	private static final Logger s_logger = LoggerFactory.getLogger(IoTGateway.class);

	private static final String APP_ID = "org.eclipse.kura.example.IoTGateway";
	
	private static int SIM = 0;
	private static int MQTT = 1;
	private static int GPIO = 2;
	
    private final ScheduledExecutorService worker;
    private ScheduledFuture<?> handle;
	/*
	 * Parameters configurable from UI
	 */
	private Map<String, Object> properties;
    private final Random random;
    //CLOUD
    private CloudPublisher cloudPublisher;
    private CloudSubscriber cloudSubscriber;
    // MQTT
	private DataService _dataService;
	// GPIO
	private GPIOService _gpioService = null;
	
	private boolean isThreadGenStarted = false;
	private boolean isTestMode = true;
	
	public IoTGateway() {
        super();
        this.random = new Random();
        this.worker = Executors.newSingleThreadScheduledExecutor();
	}

	protected void setDataService(DataService dataService) {
		//info("Bundle " + APP_ID + " setDataServiceCalled!");
		
		_dataService = dataService;

		_dataService.addDataServiceListener(this);
	}

	protected void unsetDataService(DataService dataService) {
		// to avoid multiple message effect
		_dataService.removeDataServiceListener(this);

		_dataService = null;
	}

	protected void setGPIOService(GPIOService gpioService) {
		info("Bundle " + APP_ID + " setGPIOServiceCalled!");

		_gpioService = gpioService;
	}

	protected void unsetGPIOService(GPIOService gpioService) {
		_gpioService = null;
	}

    public void setCloudPublisher(CloudPublisher cloudPublisher) {
        this.cloudPublisher = cloudPublisher;
        this.cloudPublisher.registerCloudConnectionListener(IoTGateway.this);
        this.cloudPublisher.registerCloudDeliveryListener(IoTGateway.this);
    }

    public void unsetCloudPublisher(CloudPublisher cloudPublisher) {
        if(this.cloudPublisher!=null) {
        	this.cloudPublisher.unregisterCloudConnectionListener(IoTGateway.this);
            this.cloudPublisher.unregisterCloudDeliveryListener(IoTGateway.this);
        }
        this.cloudPublisher = null;
    }

    public void setCloudSubscriber(CloudSubscriber cloudSubscriber) {
        this.cloudSubscriber= cloudSubscriber;
        this.cloudSubscriber.registerCloudConnectionListener(IoTGateway.this);
        this.cloudSubscriber.registerCloudSubscriberListener(IoTGateway.this);
    }

    public void unsetCloudSubscriber(CloudSubscriber cloudSubscriber) {
    	if(this.cloudSubscriber!=null) {
    		this.cloudSubscriber.unregisterCloudConnectionListener(IoTGateway.this);
    		this.cloudSubscriber.unregisterCloudSubscriberListener(IoTGateway.this);
    	}
        this.cloudPublisher = null;
    }

	/*
	 * 
	 * 						  ----------------------- SERVICE CONFIGURATION -----------------------
	 * 
	 */
    
	protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
		try {
	        info("Activating and starting with config...");

			// save the config
	        this.properties = properties;
			//do update behaviour 
			String TOPIC = (String) getProperty("msg.topic");
			subscribe(TOPIC);
			handleSourceModeChanged();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected void deactivate(ComponentContext componentContext) {
		debug("Bundle " + APP_ID + " has stopped!");

		// here the code that disactivate bundle processing
		String TOPIC = (String) getProperty("msg.topic");

		unSubscribe(TOPIC);
		
        this.worker.shutdown();
	}

	/*
	 * 
	 * handle change of configuration
	 * 
	 */
	public void updated(Map<String, Object> properties) {
		// save the new configuration
		Map<String, Object> oldProperties = this.properties;
		this.properties = properties;

		// verify if TOPIC has changed
		info("UPDATING PROP");
		if (hasPropertyChanged(oldProperties, "msg.topic")) {
			info("TOPIC changed!");
			handleMsgTopicChanged(oldProperties);
		}

		// changed mode.test
		if (hasPropertyChanged(oldProperties, "mode.test")) {
			// TEST MODE changed
			info("TEST MODE changed! ");
			isTestMode = (boolean) properties.get("mode.test");
		}
		
		// changed mode.simulation
		if (hasPropertyChanged(oldProperties, "mode.source")) {
			// SIMULATION MODE changed
			info("SOURCE MODE changed! ");
			//start or stop the thread
			handleSourceModeChanged();
		}
		// changed publish.rate
		if (hasPropertyChanged(oldProperties, "publish.rate")) {
			//PUBLISH RATE changed
			if(isThreadGenStarted) {
				info("PUBLISH RATE changed! ");
				if(getProperty("mode.source")!=null) {
					generate((int)getProperty("mode.source"));
				}else {
					info("Source mode not found... falback to simulated mode");
					generate(SIM);
				}
			}
		}
		dumpProperties();
	}

	private void handleSourceModeChanged() {
		int newMode = 0;
		if(getProperty("mode.source")!=null)
			newMode = (int) properties.get("mode.source");

		if (newMode==MQTT){
			//not in simulation mode
			if(isThreadGenStarted)if(this.handle!=null)this.handle.cancel(true);
			isThreadGenStarted = false;
		}else if(newMode==SIM || newMode==GPIO){
			isThreadGenStarted = true;
			generate(newMode);
		}else {
			info("Source mode changed but not found... falback to simulated mode");
			isThreadGenStarted = true;
			generate(SIM);
		}
	}
	
	private void handleMsgTopicChanged(Map<String, Object> properties) {
		// topic of callback changed
		String newMsgTopic = (String) getProperty("msg.topic");
		String oldMsgTopic = (String) properties.get("msg.topic");

		// unsubscribe from old topic
		unSubscribe(oldMsgTopic);

		// subscribe to new
		subscribe(newMsgTopic);
	}
	
	/*
	 * 
	 * 						  ----------------------- SERVICE LOGIC -----------------------
	 * 
	 */

	/*
	 * 
	 * 												DOWN SOURCE STREAM
	 *				aka the message incoming form the sensor (the connected cloud service or the GPIO or the simulation)
	 * 
	 */
	
	
	//SOURCE STREAM = 0
	//DATA SIMULTATED
	
	private void generate(int mode) {
        // cancel a current worker handle if one if active
	    if (this.handle != null) {
	        this.handle.cancel(true);
	    }
	    // schedule a new worker based on the properties of the service
	    int pubrate = 20;
	    if(getProperty("publish.rate")!=null)
	    	pubrate = (Integer) getProperty("publish.rate");
	    if(mode==SIM){
	    	 this.handle = this.worker.scheduleAtFixedRate(new Runnable() {
	    			
	 	        @Override
	 	        public void run() {
	 	            Thread.currentThread().setName(getClass().getSimpleName());
	 	            doPublish("gen");
	 	        }
	 	    }, 0, pubrate, TimeUnit.SECONDS);
	    	
	    	
	    }else if(mode==GPIO){
	    	SensorMeter hcsr = new SensorMeter();
	    	hcsr.setGPIOService(_gpioService);
	    	hcsr.activatePins();
	    	
	    	this.handle = this.worker.scheduleAtFixedRate(new Runnable() {
    			
	 	        @Override
	 	        public void run() {
	 	            Thread.currentThread().setName(getClass().getSimpleName());
	 	            doPublish(hcsr.getDistance());
	 	        }
	 	    }, 0, pubrate, TimeUnit.SECONDS);
	    }else {
			info("No generating mode available");
		}
	   
	}
	
	//SOURCE STREAM = 1
	//MQTT CLOUD CONNECTION FROM THE CHOSEN SUBSCRIBER
	
	@Override
	public void onMessageConfirmed(String arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onConnectionLost() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onMessageArrived(KuraMessage msg) {
		if(getProperty("mode.simulation")!=null && (int)getProperty("mode.simulation") == MQTT) {
			handleIncomingMessage(msg);
		}	
	}
	
	//SOURCE STREAM = 2
	//DATA FROM THE GPIO LIBRARY 
	
	
	
	
	
	
	
	//for all down stream source
	
	private void doPublish(String dataPayload) {
   	 // Allocate a new payload
    	KuraPayload payload = new KuraPayload();

    	// Timestamp the message
    	payload.setTimestamp(new Date());
    	payload.addMetric("errorCode", 0);
    	if(dataPayload.equals("gen")) {
    	   //create random data
	       // Add the temperature as a metric to the payload
	       payload.addMetric("type", "kura-gen");
	       payload.addMetric("value", ((Math.abs(this.random.nextDouble()) % 5)+1)*5+(Math.abs(this.random.nextDouble()) % 10));
    	}else {
    	   payload.addMetric("type", "kura-sensor");
    	   payload.addMetric("value", Double.parseDouble(dataPayload));
    	}
    	KuraMessage message = new KuraMessage(payload);	
    	handleIncomingMessage(message);
   }	
	
	/*
	 * 
	 * 												UPPER SOURCE STREAM
	 *				aka the message incoming form the application (the connected cloud service)
	 *
	 */
	
	@Override
	public void onConnectionEstablished() {
		info("Connection established");

		String TOPIC = (String) getProperty("msg.topic");

		subscribe(TOPIC);
	}

	@Override
	public void onConnectionLost(Throwable arg0) {
		info("Connection lost");

	}
	
	@Override
	public void onDisconnected() {

	}

	@Override
	public void onDisconnecting() {

	}
	
	private void subscribe(String TOPIC) {
		try {
			info("trying subscription");
			if ((_dataService != null) && (_dataService.isConnected())) {
				// subscribe to MQTT topic on local broker
				_dataService.subscribe(TOPIC, 1);

				info("subscription done to topic " + TOPIC);
			}
		} catch (Exception e) {
			error("failed to subscribe to topic: " + e);
		}
	}

	private void unSubscribe(String TOPIC) {
		try {
			if ((_dataService != null) && (_dataService.isConnected())) {
				_dataService.unsubscribe(TOPIC);

				info("unsubscribe done on " + TOPIC);
			}

		} catch (Exception e) {
			error("failed to unsubscribe to topic: " + e);
		}
	}
	
	@Override
	public void onMessageConfirmed(int arg0, String arg1) {

	}

	@Override
	public void onMessagePublished(int arg0, String arg1) {

	}
	
	@Override
	public void onMessageArrived(String topic, byte[] payload, int qos, boolean retained) {
		//message handling
		long tStart = System.currentTimeMillis();

		info(" Message arrived on topic " + topic);

		// is on the subscribed topic?
		String SUBSCRIBED_TOPIC = (String) (getProperty("msg.topic"));

		//
		// need to extend to support wildcard in TOPIC definition
		//
		if (areCompatibleTopics(topic, SUBSCRIBED_TOPIC)) {
			// topic OK, proceed!
			handleIncomingCommand(new String(payload));
		}
		// register time needed to process msg
		long tElapsed = (System.currentTimeMillis() - tStart);

		info("Elapsed time to process msg(msec) : " + tElapsed);
				
	}
	
	//introduced to support the definition of TOPIC with wildcard single level (+)
	//for example device/+/data
	//
	//check if msgTopic is compatible with definedTopic (using +) this way we can
	//subscribe to a SET of topics
	private boolean areCompatibleTopics(String msgTopic, String definedTopic) {
		String SEPARATOR = "/";

		boolean vRit = true;

		// split strings
		String[] parts1 = msgTopic.split(SEPARATOR);

		String[] parts2 = definedTopic.split(SEPARATOR);

		// now compare except for +

		for (int i = 0; i < parts1.length; i++) {
			// compare parts1[i] with parts2[i]
			if (!parts2[i].equals("+")) // otherwise (+) OK and skip
			{
				// compare
				if (!parts2[i].equals(parts1[i])) {
					vRit = false;
					break;
				}
			}
		}
		return vRit;
	}
	
	
	
	
	
	
	
	
	
	
	/*
	 * 
	 * 												INTERNAL HANDLING
	 * 
	 */
	
	private void handleIncomingMessage(KuraMessage msg){
		//TODO your logic here
		// log message only if enabled
		msgLog("payoload of type: "+msg.getPayload().getMetric("type")+" ACCEPTED at: "+msg.getPayload().getTimestamp().toString());
		// if no test mode... send the msg to Cloud
		if (!isTestMode) {
			if (this.cloudPublisher == null) {
				msgLog("No cloud publisher selected. Cannot publish!");
	            return;
	        }
			 // Publish the message
	        try {
	            this.cloudPublisher.publish(msg);
	            msgLog("Published message: "+ msg.toString());
	        } catch (Exception e) {
	        	msgLog("Cannot publish message: "+ msg.toString() +" - "+ e.toString());
	        }
		}
	}

	
	private void  handleIncomingCommand(String msg){
		//TODO your logic here
	}

	
	/*
	 * 
	 * 												LOG & UTLITY
	 * 
	 */
	private void msgLog(String msg) {
		if ((boolean) getProperty("msglog.enable") == true) {
			s_logger.info(msg);
		}
	}
	
	private static void info(String msg) {
		s_logger.info(msg);
	}

	private static void debug(String msg) {
		s_logger.debug(msg);
	}

	private static void error(String msg) {
		s_logger.error(msg);
	}
	
	private void dumpProperties() {
		if (this.properties != null && !this.properties.isEmpty()) {
			Iterator<Entry<String, Object>> it = this.properties.entrySet().iterator();

			info("Properties:.................");
			while (it.hasNext()) {
				Entry<String, Object> entry = it.next();
				info("Property - " + entry.getKey() + " = " + entry.getValue() + " of type " + entry.getValue().getClass().toString());
			}
		}
	}
	
	private Object getProperty(String label) {
		return this.properties.get(label);
	}

	private boolean hasPropertyChanged(Map<String, Object> newSet, String label) {
		// compare with oldSet
		Object oldProperty = getProperty(label);
		Object newProperty = newSet.get(label);

		if ((oldProperty != null) && (newProperty != null)) {
			if (!getProperty(label).equals(newSet.get(label)))
				return true; // changed
			else
				return false;
		} else
			return false;

	}
	
	
	
	
}
