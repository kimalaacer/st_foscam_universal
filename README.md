SmartThings Foscam Camera Support
================

SmartThings support for Foscam Camera with simple motion detection.  

*Note:  HD support pieces left, but not active (do not have HD camera, someday).*

## Operation

  - Can/Should use a *poll* application service as Smarthings service is ~10min or when it feels like it.

## Notes

  - Added debounce parameter for false motion
    - Example: Light changes. 
    - Alarm on Foscam is for 60 seconds therefore debounce increases time before motion is active by minute/debounce.
