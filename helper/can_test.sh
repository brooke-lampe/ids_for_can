# Send test message
cansend vcan0 123#DEADBEEF
cansend vcan0 123#00C0FFEE

# View CAN statistics
ip -details -statistics link show vcan0
