package org.eclipse.kura.example.IoTGateway;

import java.awt.Checkbox;
import java.io.IOException;
import java.text.DecimalFormat;

import org.eclipse.kura.gpio.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SensorMeter
{
	private static final Logger s_logger = LoggerFactory.getLogger(SensorMeter.class);

	//check image in the resources for info on pin enumeration
	//private int PIN_TRIGGER = 5;//pin 29
	//private int PIN_ECHO = 6;//pin 31

	private int PIN_TRIGGER = 12;//pin 32
	private int PIN_ECHO = 13;//pin 33
	
	private GPIOService _gpioService = null;
	private KuraGPIOPin outputPin_T;
	private KuraGPIOPin outputPin_E;	

	public SensorMeter(){
	}

	public void activatePins()
	{
		info("Activate pins...");
		
		getPins();
	}

	public void setGPIOService(GPIOService gpioService)
	{
		this._gpioService = gpioService;
	}

	public String getDistance() {
		try {
			if (outputPin_T != null && outputPin_E != null){
				int a = 0;
				
				info("Measuring distance...");
				
				outputPin_T.setValue(true);
				Thread.sleep((long) 0.01);// Delay for 10 microseconds
				outputPin_T.setValue(false);
				
				while(!outputPin_E.getValue() && a++<1000) {} //Wait until the ECHO pin gets HIGH
				long startTime= System.nanoTime(); // Store the surrent time to calculate ECHO pin HIGH time.
				while(outputPin_E.getValue()){} //Wait until the ECHO pin gets LOW
				long endTime= System.nanoTime(); // Store the echo pin HIGH end time to calculate ECHO pin HIGH time.
				
				info("Distance1: "+((((endTime-startTime)/1e3)/2) / 29.1)+" cm");
				//info("Distance2: "+(int)((endTime-startTime)/5882.35294118) +" mm"); //distance in mm
				//info("Distance3: "+(endTime-startTime)/(200*29.1)+" mm"); //distance in mm
				//DecimalFormat formatter = new DecimalFormat("##.00");
				//info("Distance4: "+formatter.format(((endTime-startTime)*17150))+" cm");
				
				return (a<1000?((((endTime-startTime)/1e3)/2) / 29.1):-1.0)+"";
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "0";
	}
	
	/*
	 * 
	 * get the pin for LED operations (OUTPUT, ON|OFF)
	 * 
	 */
	private void getPins()
	{
		KuraGPIOPin pin_T = null;
		KuraGPIOPin pin_E = null;

		
		if (this._gpioService != null){
			pin_T = this._gpioService.getPinByTerminal(this.PIN_TRIGGER, KuraGPIODirection.OUTPUT, KuraGPIOMode.OUTPUT_PUSH_PULL, KuraGPIOTrigger.NONE);
			pin_E = this._gpioService.getPinByTerminal(this.PIN_ECHO, KuraGPIODirection.INPUT, KuraGPIOMode.INPUT_PULL_DOWN, KuraGPIOTrigger.NONE);
			if (pin_T != null && pin_E != null) {
				try {
					// it is mandatory to open the pin !!!!
					info("Open the LED pin...");
					
					
					if(!pin_T.isOpen())pin_T.open();
					
					if(!pin_E.isOpen())pin_E.open();
					
					
					outputPin_T = pin_T;
					outputPin_E = pin_E;
				} catch (KuraGPIODeviceException e) {
					e.printStackTrace();
				} catch (KuraUnavailableDeviceException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/*
	 * utility methods for logging
	 * 
	 */
	private static void info(String msg) {
		s_logger.info(msg);
	}
}
