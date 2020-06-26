/**
 *  Copyright 2015 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
	definition (name: "Etherdyne Switch", namespace: "WillNyeAG", author: "William AG-i3pd", ocfDeviceType: "oic.d.switch", runLocally: true, minHubCoreVersion: '000.017.0012', executeCommandsLocally: false) {
		capability "Actuator"
		capability "Switch"
        
        attribute "power", "number"
        attribute "current", "number"
        attribute "voltage", "number"
        attribute "pwmValue", "number"
        
        command "hubConfigurationGet"
        command "configurationGet"
		command "restart"
        command "pwmSet", ["number"]

        fingerprint mfr:"0000", prod:"0004", model:"0002", deviceJoinName: "Etherdyne Switch" //Etherdyne Switch General Z Wave
	}

	// simulator metadata
	simulator {
	}

	preferences {
	}

	// tile definitions
	tiles(scale: 2) {
        standardTile("switch", "device.switch", width: 6, height: 2) {
        state "off", label: "off", icon: "st.switches.switch.off", backgroundColor: "#ffffff", action: "switch.on"
        state "on", label: "on", icon: "st.switches.switch.on", backgroundColor: "#00a0dc", action: "configurationGet"
    	}
//PWM
        controlTile("pwm", "device.pwm", "slider", width: 3, height: 1, inactiveLabel: false, range:"(0..10000)") {
   			 state "pwmValue", action:"pwmSet"
		}
        valueTile("pwmValue", "device.pwmValue", height: 1, width: 3) {
			state "pwmValue", label:'PWM: ${currentValue}', defaultState: true
		}
//Energy Levels
        
		//voltage tile
        valueTile("voltage", "device.voltage", width: 2, height: 2) {
        	state "voltage", label:'${currentValue} mVolts', unit: mV, defaultState: true //,backgroundColor: "#f7d31b" //yellow
        }
        // power tile (read only)
        valueTile("power", "device.power", decoration: "flat", width: 2, height: 2) {
            state "power", label:'${currentValue} mWatts', defaultState: true//, backgroundColor: "#23badb" //blue
        }
        // amperage tile (read only)
        valueTile("current", "device.current", decoration: "flat", width: 2, height: 2) {
            state "current", label:'${currentValue} mAmps', defaultState: true//, backgroundColor: "#1bb530" //green
        }
//Restart
        standardTile("restart", "device.restart", width: 6, height: 2) {
        	state "val", action:"restart", icon: "st.primary.refresh"
        }
		

		main "switch"
		details(["switch", "restart", "pwm", "pwmValue", "voltage","power", "current"])
	}
}

def installed() {
	// Device-Watch simply pings if no device events received for 32min(checkInterval)
	sendEvent(name: "checkInterval", value: 2 * 15 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])
    
    hubConfigGet()
    state.lastUpdate = new Date().time
    
    
    
}

def updated(){
	response(refresh())
}

def parse(String description) {
	def result = null
	if (description.startsWith("Err 106")) {
		result = createEvent(descriptionText: description, isStateChange: true)
	} else if (description != "updated") {
		def cmd = zwave.parse(description, [0x20: 1, 0x25: 1, 0x70: 1, 0x98: 1])
		if (cmd) {
			result = zwaveEvent(cmd)
			log.debug("'$description' parsed to $result")
		} else {
			log.debug("Couldn't zwave.parse '$description'")
		}
	}
	result
}


def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
	createEvent(name: "switch", value: cmd.value ? "on" : "off")
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
	createEvent(name: "switch", value: cmd.value ? "on" : "off")
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
	createEvent(name: "switch", value: cmd.value ? "on" : "off")
}

def zwaveEvent(physicalgraph.zwave.commands.hailv1.Hail cmd) {
	createEvent(name: "hail", value: "hail", descriptionText: "Switch button was pressed", displayed: false)
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def encapsulatedCommand = cmd.encapsulatedCommand([0x20: 1, 0x25: 1])
	if (encapsulatedCommand) {
		zwaveEvent(encapsulatedCommand)
	}
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {
	//log.debug "$cmd"
    //SWITCH CASES
    	switch (cmd.parameterNumber) {
        case 1:
        	pause(5000)
            state.lastUpdate = new Date().time
            hubConfigGet()
        	return createEvent(name: "power", value: cmd.scaledConfigurationValue)
		case 2:
        	return createEvent(name: "current", value: cmd.scaledConfigurationValue)
        case 3:
        	return createEvent(name: "voltage", value: cmd.scaledConfigurationValue)
		case 5:
        	return createEvent(name: "pwmValue", value: cmd.scaledConfigurationValue)
        default:
        	log.debug "Parameter Number: $cmd.parameterNumber"
            null
        }
}

def pause(ms) {
	def passed = 0
    def now = new Date().time
    while (passed < ms) {
    	passed = new Date().time - now
    }
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	// Handles all Z-Wave commands we aren't interested in
	log.debug "Unhandled: $cmd"
    null
}

def on() {
	commands([
		zwave.basicV1.basicSet(value: 0xFF),
		zwave.basicV1.basicGet()
	])
}

def off() {
	commands([
		zwave.basicV1.basicSet(value: 0x00),
		zwave.basicV1.basicGet()
	])
}


def configurationGet() {
	log.debug "fetching..."
	commands([
    	zwave.configurationV2.configurationGet(parameterNumber: 1),
        zwave.configurationV2.configurationGet(parameterNumber: 2), 
        zwave.configurationV2.configurationGet(parameterNumber: 3),
        zwave.configurationV2.configurationGet(parameterNumber: 5)
       ])

}

def hubConfigGet() {
	sendHubCommand(zwave.configurationV2.configurationGet(parameterNumber: 1).format())
    sendHubCommand(zwave.configurationV2.configurationGet(parameterNumber: 2).format())
    sendHubCommand(zwave.configurationV2.configurationGet(parameterNumber: 3).format())
    snedHubCommand(zwave.configurationV2.configurationGet(parameterNumber: 5).format())
}


def restart () {
	def diff = new Date().time - state.lastUpdate
    if (diff > 20 * 1000) {
    	hubConfigGet()
    } else {
    	log.error "It's only been $diff ms since last update, wait 20 sec"
    }
  }

def pwmSet(value) {
	log.debug "setting pwm level to $value"
    commands([
    	zwave.configurationV2.configurationSet(parameterNumber: 5, size: 2, scaledConfigurationValue: value)
    ])
    
}
/**
  * PING is used by Device-Watch in attempt to reach the Device
**/
def ping() {
	refresh()
}

def poll() {
	refresh()
}

def refresh() {
	command(zwave.basicV1.basicGet())
}

private command(physicalgraph.zwave.Command cmd) {
	if((zwaveInfo.zw == null && state.sec != 0) || zwaveInfo?.zw?.contains("s")){
    	zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
        } else {
        	cmd.format()
        }   
}

private commands(commands, delay = 200) {
	delayBetween(commands.collect { command(it) }, delay)
}

