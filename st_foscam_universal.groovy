/* 
*  Original Author: skp19
*  https://github.com/skp19/st_foscam_universal
*/

metadata {
  definition (name: "Foscam Universal Device", namespace: "Foscam", author: "skp19") {
    capability "Polling"
    capability "Image Capture"

    attribute "alarmStatus", "string"
    attribute "hubactionMode", "string"

    command "alarmOn"
    command "alarmOff"
    command "toggleAlarm"
  }

    preferences {
    input("ip", "string", title:"Camera IP Address", description: "Camera IP Address", required: true, displayDuringSetup: true)
    input("port", "string", title:"Camera Port", description: "Camera Port", defaultValue: 80 , required: true, displayDuringSetup: true)
    input("username", "string", title:"Camera Username", description: "Camera Username", required: true, displayDuringSetup: true)
    input("password", "password", title:"Camera Password", description: "Camera Password", required: true, displayDuringSetup: true)
    input("hdcamera", "bool", title:"HD Foscam Camera?", description: "Type of Foscam Camera", required: true, displayDuringSetup: true)
    input("mirror", "bool", title:"Mirror?", description: "Camera Mirrored?")
    input("flip", "bool", title:"Flip?", description: "Camera Flipped?")
    section("Options") {
        input "notify", "bool", title: "Notification?"
    }
 }

  tiles {
    carouselTile("cameraDetails", "device.image", width: 3, height: 2) { }

    standardTile("camera", "device.alarmStatus", width: 1, height: 1, canChangeIcon: true, inactiveLabel: false, canChangeBackground: true) {
      state "off", label: "off", icon: "st.camera.dropcam-centered", backgroundColor: "#FFFFFF"
      state "on", label: "on", icon: "st.camera.dropcam-centered",  backgroundColor: "#53A7C0"
    }

    standardTile("take", "device.image", width: 1, height: 1, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false) {
      state "take", label: "Take", action: "Image Capture.take", icon: "st.camera.camera", backgroundColor: "#FFFFFF", nextState:"taking"
      state "taking", label:'Taking', action: "", icon: "st.camera.take-photo", backgroundColor: "#53a7c0"
      state "image", label: "Take", action: "Image Capture.take", icon: "st.camera.camera", backgroundColor: "#FFFFFF", nextState:"taking"
    }

    standardTile("alarmStatus", "device.alarmStatus", width: 1, height: 1, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false) {
      state "off", label: "off", action: "toggleAlarm", icon: "st.security.alarm.off", backgroundColor: "#FFFFFF"
      state "on", label: "on", action: "toggleAlarm", icon: "st.security.alarm.on",  backgroundColor: "#53A7C0"
    }

    standardTile("refresh", "device.alarmStatus", inactiveLabel: false, decoration: "flat") {
      state "refresh", action:"polling.poll", icon:"st.secondary.refresh"
    }
    
    standardTile("blank", "device.image", width: 1, height: 1, canChangeIcon: false,  canChangeBackground: false, decoration: "flat") {
      state "blank", label: "", action: "", icon: "", backgroundColor: "#FFFFFF"
    }

    main "camera"
    details(["cameraDetails", "take", "alarmStatus", "refresh"])
  }
}

def take() {
  log.debug("Taking Photo")
  sendEvent(name: "hubactionMode", value: "s3");
    if(hdcamera == "true") {
    hubGet("cmd=snapPicture2")
    }
    else {
      hubGet("/snapshot.cgi?")
    }
}

def toggleAlarm() {
  log.debug "Toggling Alarm"
  if(device.currentValue("alarmStatus") == "on") {
      alarmOff()
    }
  else {
      alarmOn()
  }
}

def alarmOn() {
  log.debug "Enabling Alarm"
    sendEvent(name: "alarmStatus", value: "on");
    if(hdcamera == "true") {
    hubGet("cmd=setMotionDetectConfig&isEnable=1")
    }
    else {
      hubGet("/set_alarm.cgi?motion_armed=1&")
    }
}

def alarmOff() {
  log.debug "Disabling Alarm"
    sendEvent(name: "alarmStatus", value: "off");
    if(hdcamera == "true") {
    hubGet("cmd=setMotionDetectConfig&isEnable=0")
    }
    else {
      hubGet("/set_alarm.cgi?motion_armed=0&")
    }
}

def poll() {

  sendEvent(name: "hubactionMode", value: "local");
    //Poll Motion Alarm Status and IR LED Mode
    if(hdcamera == "true") {
    delayBetween([hubGet("cmd=getMotionDetectConfig")])
    }
    else {
      hubGet("/get_params.cgi?")
    }
}

private getLogin() {
  if(hdcamera == "true") {
      return "usr=${username}&pwd=${password}&"
    }
    else {
      return "user=${username}&pwd=${password}"
    }
}

private hubGet(def apiCommand) {
  //Setting Network Device Id
    def iphex = convertIPtoHex(ip)
    def porthex = convertPortToHex(port)
    device.deviceNetworkId = "$iphex:$porthex"
    log.debug "Device Network Id set to ${iphex}:${porthex}"

  log.debug("Executing hubaction on " + getHostAddress())
    def uri = ""
    if(hdcamera == "true") {
      uri = "/cgi-bin/CGIProxy.fcgi?" + getLogin() + apiCommand
  }
    else {
      uri = apiCommand + getLogin()
    }
    log.debug uri
    def hubAction = new physicalgraph.device.HubAction(
      method: "GET",
        path: uri,
        headers: [HOST:getHostAddress()]
    )
    if(device.currentValue("hubactionMode") == "s3") {
        hubAction.options = [outputMsgToS3:true]
        sendEvent(name: "hubactionMode", value: "local");
    }
  hubAction
}

//Parse events into attributes
def parse(String description) {
  log.debug "Parsing '${description}'"
    
    def map = [:]
    def retResult = []
    def descMap = parseDescriptionAsMap(description)
        
    //Image
  if (descMap["bucket"] && descMap["key"]) {
    putImageInS3(descMap)
  }

  //Status Polling
    else if (descMap["headers"] && descMap["body"]) {
        def body = new String(descMap["body"].decodeBase64())
        if(hdcamera == "true") {
            def langs = new XmlSlurper().parseText(body)

            def motionAlarm = "$langs.isEnable"
            def ledMode = "$langs.mode"

            //Get Motion Alarm Status
            if(motionAlarm == "0") {
                log.info("Polled: Alarm Off")
                sendEvent(name: "alarmStatus", value: "off");
            }
            else if(motionAlarm == "1") {
                log.info("Polled: Alarm On")
                sendEvent(name: "alarmStatus", value: "on");
            }

            //Get IR LED Mode
            if(ledMode == "0") {
                log.info("Polled: LED Mode Auto")
                sendEvent(name: "ledStatus", value: "auto")
            }
            else if(ledMode == "1") {
                log.info("Polled: LED Mode Manual")
                sendEvent(name: "ledStatus", value: "manual")
            }
      }
        else {
          if(body.find("alarm_motion_armed=0")) {
        log.info("Polled: Alarm Off")
                sendEvent(name: "alarmStatus", value: "off")
            }
          else if(body.find("alarm_motion_armed=1")) {
        log.info("Polled: Alarm On")
                sendEvent(name: "alarmStatus", value: "on")
            }
            //The API does not provide a way to poll for LED status on 8xxx series at the moment
        }
  }
}

def parseDescriptionAsMap(description) {
  description.split(",").inject([:]) { map, param ->
    def nameAndValue = param.split(":")
    map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
  }
}

def putImageInS3(map) {

  def s3ObjectContent

  try {
    def imageBytes = getS3Object(map.bucket, map.key + ".jpg")

    if(imageBytes)
    {
      s3ObjectContent = imageBytes.getObjectContent()
      def bytes = new ByteArrayInputStream(s3ObjectContent.bytes)
      storeImage(getPictureName(), bytes)
    }
  }
  catch(Exception e) {
    log.error e
  }
  finally {
    //Explicitly close the stream
    if (s3ObjectContent) { s3ObjectContent.close() }
  }
}

private getPictureName() {
  def pictureUuid = java.util.UUID.randomUUID().toString().replaceAll('-', '')
  "image" + "_$pictureUuid" + ".jpg"
}

private getHostAddress() {
  return "${ip}:${port}"
}

private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
    return hex

}

private String convertPortToHex(port) {
  String hexport = port.toString().format( '%04x', port.toInteger() )
    return hexport
}

def sendMessage(msg) {
  if (notify) {
    sendPush msg
  }
}