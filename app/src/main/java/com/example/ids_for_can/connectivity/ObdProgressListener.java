package com.example.ids_for_can.connectivity;

public interface ObdProgressListener {

    void stateUpdate(final ObdCommandJob job);

}