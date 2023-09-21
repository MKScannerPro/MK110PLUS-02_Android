package com.moko.support.remotegw02.callback;

import com.moko.support.remotegw02.entity.DeviceInfo;

public interface MokoScanDeviceCallback {
    void onStartScan();

    void onScanDevice(DeviceInfo device);

    void onStopScan();
}
