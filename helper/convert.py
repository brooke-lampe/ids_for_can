from __future__ import print_function
import re

#file = open('Attack_free_dataset.txt', 'r')
#convert_file = open('Attack_free_dataset.log', 'w+')

#file = open('DoS_attack_dataset.txt', 'r')
#convert_file = open('DoS_attack_dataset.log', 'w+')

#file = open('Fuzzy_attack_dataset.txt', 'r')
#convert_file = open('Fuzzy_attack_dataset.log', 'w+')

file = open('Impersonation_attack_dataset.txt', 'r')
convert_file = open('Impersonation_attack_dataset.log', 'w+')

line = file.readline()
while line != '':
    #print(line, end='')

    lines = re.split(r"\s{2,}", line)

    timestamp_label = lines[0]
    timestamp_data = lines[1]

    id_info = lines[2].split(" ")
    id_label = id_info[0]
    id_data = id_info[1][1:]

    unknown = lines[3]

    dlc_info = lines[4].split(" ")
    dlc_label = dlc_info[0]
    dlc_data = dlc_info[1]

    data = lines[5].replace(" ", "")

    #for ln in lines:
    #    print(ln)

    #convert_line = "(" + timestamp_data + ")" + " vcan0 " + id_data + "#" + data + "\n"
    convert_line = "(" + timestamp_data + ")" + " vcan0 " + id_data + "#" + data
    print(convert_line, end='')

    convert_file.write(convert_line)
    line = file.readline()

file.close()
