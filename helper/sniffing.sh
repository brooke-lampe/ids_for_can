# initial can setup
sudo modprobe can
sudo modprobe can-raw
sudo ip link set can0 type can bitrate 500000
sudo ip link set up can0

# set bitrate and turn on the network at the same time
sudo ip link set can0 up type can bitrate 500000

# set bitrate and restart
sudo ip link set can0 type can bitrate 125000 restart-ms 100

# set triple-sampling
ip link set can0 type can bitrate 125000 triple-sampling on

# set automatic restart if "bus off" occurs
# (bus off normally occurs due to errors)
sudo ip link set can0 down
sudo ip link set can0 type can restart-ms 100
sudo ip link set can0 up

# show the details of the can0 link
ip -details -statistics link show can0

# check all usb devices
lsusb

# check messages for when usb connects/disconnects
dmesg
dmesg | grep usb

# check that SocketCAN kernel modules have been loaded
lsmod | grep "can"
    vcan                   16384  0
    can_raw                20480  1
    can                    24576  1 can_raw
    can_dev                28672  1 gs_usb

# check if can0 network is present
ifconfig
ifconfig -a

# loopback mode
sudo ip link set can0 down    
sudo ip link set can0 type can bitrate 125000 loopback on
sudo ip link set can0 up
candump can0 & cansend can0 123#1234567890

# result
brooke@brooke-HP-Envy:~$ candump can0 & cansend can0 123#1234567890
[2] 7499
  can0  123   [5]  12 34 56 78 90
  can0  123   [5]  12 34 56 78 90
brooke@brooke-HP-Envy:~$   can0  123   [5]  12 34 56 78 90
  can0  123   [5]  12 34 56 78 90
  can0  123   [5]  12 34 56 78 90
  can0  123   [5]  12 34 56 78 90
  can0  123   [5]  12 34 56 78 90
  can0  123   [5]  12 34 56 78 90

# can0 details when restart-ms set
# packets appear to be received, just not proper CAN packets?
brooke@brooke-HP-Envy:~$ ip -details -statistics link show can0
10: can0: <NOARP,UP,LOWER_UP,ECHO> mtu 16 qdisc fq_codel state UP mode DEFAULT group default qlen 10
    link/can  promiscuity 1 
    can state BUS-OFF restart-ms 100 
	  bitrate 4800000 sample-point 0.700 
	  tq 20 prop-seg 3 phase-seg1 3 phase-seg2 3 sjw 1
	  gs_usb: tseg1 1..16 tseg2 1..8 sjw 1..4 brp 1..1024 brp-inc 1
	  clock 48000000
	  re-started bus-errors arbit-lost error-warn error-pass bus-off
	  0          0          0          174446     994537     3279725   numtxqueues 1 numrxqueues 1 gso_max_size 65536 gso_max_segs 65535 
    RX: bytes  packets  errors  dropped overrun mcast   
    39462000   4932750  0       259     0       0       
    TX: bytes  packets  errors  dropped carrier collsns 
    5          1        0       0       0       0

# bitrates tested
 125000
 500000
1000000

# maximum bitrate depends on hardware, so error on the side of smaller numbers

