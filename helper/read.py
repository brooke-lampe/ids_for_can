import can

can_interface = 'vcan0'
bus = can.interface.Bus(can_interface, bustype='socketcan')
message = bus.recv(10.0)  # Timeout in seconds.
print(message)

if message is None:
    print('Timeout occurred, no message.')
