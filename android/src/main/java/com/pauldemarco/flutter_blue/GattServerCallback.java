package com.pauldemarco.flutter_blue;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;

class GattServerCallback extends BluetoothGattServerCallback {

    private BluetoothGattServer server;

    void setServer(BluetoothGattServer server) {
        this.server = server;
    }

    @Override
    public void onCharacteristicReadRequest(BluetoothDevice device, int requestId,
                                            int offset, BluetoothGattCharacteristic characteristic) {
        server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());
    }
}
