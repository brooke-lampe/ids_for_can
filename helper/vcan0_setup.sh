sudo modprobe vcan

# Create a vcan network interface with a specific name
sudo ip link add dev vcan0 type vcan
sudo ip link set vcan0 up

# Send test message
cansend vcan0 123#DEADBEEF

# View CAN statistics
ip -details -statistics link show vcan0
