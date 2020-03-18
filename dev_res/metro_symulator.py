#
# Simulate a Meteo station
# reads data from a file, select an interval (one day for example)
# and send data as JSON msgs to an MQTT broker
#
# can change the speed using sleepTime
#
#!/usr/bin/python
import csv 
import json
import sys
from datetime import datetime
import time
import paho.mqtt.client as mqtt
import RPi.GPIO as GPIO

# send a msg every 1 sec.
sleepTime = 1

# configuration for MQTT

# every attendee should have a different client ID (meteo1, meteo2...)
clientID = "a"
HOST = "127.0.0.1"
PORT = 1883
TIMEOUT = 10
# TOPIC name MUST be build around clientID (it is a convention for these workshops)
TOPIC_NAME = "telemetry/device/a/MQTT/BIRTH" 

# enables MQTT logging
DO_LOG = True

# handles MQTT logging
def on_log(client, userdata, level, buf):
    if DO_LOG == True:
        print("log: ",buf)

#
# Main 
#

# reading data file name from command line args
#fName = sys.argv[1]


print ("***********************")
print ("Starting....")
print ("")
print ("")

# connect to MQTT broker
# mqttClient = mqtt.Client(clientID, protocol=mqtt.MQTTv311)
# mqttClient.on_log = on_log
# mqttClient.username_pw_set(username="a",password="b")
# mqttClient.connect(HOST, PORT, TIMEOUT)


try:
    GPIO.setmode(GPIO.BOARD)

    PIN_TRIGGER = 29
    PIN_ECHO = 31

    id = 1

    GPIO.setup(PIN_TRIGGER, GPIO.OUT)
    GPIO.setup(PIN_ECHO, GPIO.IN)

    GPIO.output(PIN_TRIGGER, GPIO.LOW)

    print "Waiting for sensor to settle"

    time.sleep(2)

    print "Calculating distance"
    
    while True:

        GPIO.output(PIN_TRIGGER, GPIO.HIGH)

        time.sleep(0.00001)

        GPIO.output(PIN_TRIGGER, GPIO.LOW)

        while GPIO.input(PIN_ECHO)==0:
            pulse_start_time = time.time()
        while GPIO.input(PIN_ECHO)==1:
            pulse_end_time = time.time()

        pulse_duration = pulse_end_time - pulse_start_time
        distance = round(pulse_duration * 17150, 2)
        print "Distance:",distance,"cm"
        
        # Unix timestamp of the read from sensors
        #ts = datetime.timestamp(datetime.now())

        # build the JSON msg
        #msg = {}
        #msg['id'] = int(id)
        #msg['ts'] = int(0)
        #msg['dist'] = float(distance)

        #msgJson = json.dumps(msg)
        
        #print ('Sending:  UTC', datetime.utcfromtimestamp(int(0)).strftime('%Y-%m-%d %H:%M:%S'), ' msg:', msgJson)
        
        # send the msg to the MQTT broker
        #(result, mid) = mqttClient.publish(TOPIC_NAME, msgJson)

        #if result != 0:
            # some problems ? try enabling logging
        #    print (result, mid)

        time.sleep(0.1)

        id = id + 1
        

except Exception:
    print()
    print('\n')
    print('*** Error info: ', sys.exc_info()[0], sys.exc_info()[1])
    sys.exit(-1)

finally:
      GPIO.cleanup()

