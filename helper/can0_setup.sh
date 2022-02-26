sudo modprobe can
sudo modprobe can-raw
sudo ip link set can0 type can bitrate 500000
sudo ip link set up can0
