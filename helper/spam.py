import time
import can
import can.interfaces.vector

bustype = 'socketcan'
channel = 'vcan0'

# IGNORE THIS MESSAGE
# Could not import vxlapi: module 'ctypes' has no attribute 'windll'

# USE PYTHON3
# BE SURE CAN HAS BEEN SET UP (can_setup.sh)

def producer(id):
    """:param id: Spam the bus with messages including the data id."""
    bus = can.interface.Bus(channel=channel, bustype=bustype)
    for i in range(10):
        msg = can.Message(arbitration_id=0xc0ffee, data=[id, i, 0, 1, 3, 1, 4, 1], is_extended_id=False)
        bus.send(msg)

    time.sleep(10)

producer(10)
producer(20)
producer(30)
