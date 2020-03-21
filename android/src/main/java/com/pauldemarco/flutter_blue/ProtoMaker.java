// Copyright 2017, Paul DeMarco.
// All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.pauldemarco.flutter_blue;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.os.Build;
import android.os.Parcel;
import android.os.ParcelUuid;
import android.util.Log;
import android.util.SparseArray;

import com.google.protobuf.ByteString;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static android.bluetooth.BluetoothGattService.*;

/**
 * Created by paul on 8/31/17.
 */

public class ProtoMaker {

    private static final UUID CCCD_UUID = UUID.fromString("000002902-0000-1000-8000-00805f9b34fb");

    static Protos.ScanResult from(BluetoothDevice device, byte[] advertisementData, int rssi) {
        Protos.ScanResult.Builder p = Protos.ScanResult.newBuilder();
        p.setDevice(from(device));
        if(advertisementData != null && advertisementData.length > 0)
            p.setAdvertisementData(AdvertisementParser.parse(advertisementData));
        p.setRssi(rssi);
        return p.build();
    }

    @TargetApi(21)
    static Protos.ScanResult from(BluetoothDevice device, ScanResult scanResult) {
        Protos.ScanResult.Builder p = Protos.ScanResult.newBuilder();
        p.setDevice(from(device));
        Protos.AdvertisementData.Builder a = Protos.AdvertisementData.newBuilder();
        ScanRecord scanRecord = scanResult.getScanRecord();
        if(Build.VERSION.SDK_INT >= 26) {
            a.setConnectable(scanResult.isConnectable());
        } else {
            if(scanRecord != null) {
                int flags = scanRecord.getAdvertiseFlags();
                a.setConnectable((flags & 0x2) > 0);
            }
        }
        if(scanRecord != null) {
            String deviceName = scanRecord.getDeviceName();
            if(deviceName != null) {
                a.setLocalName(deviceName);
            }
            int txPower = scanRecord.getTxPowerLevel();
            if(txPower != Integer.MIN_VALUE) {
                a.setTxPowerLevel(Protos.Int32Value.newBuilder().setValue(txPower));
            }
            // Manufacturer Specific Data
            SparseArray<byte[]> msd = scanRecord.getManufacturerSpecificData();
            for (int i = 0; i < msd.size(); i++) {
                int key = msd.keyAt(i);
                byte[] value = msd.valueAt(i);
                a.putManufacturerData(key, ByteString.copyFrom(value));
            }
            // Service Data
            Map<ParcelUuid, byte[]> serviceData = scanRecord.getServiceData();
            for (Map.Entry<ParcelUuid, byte[]> entry : serviceData.entrySet()) {
                ParcelUuid key = entry.getKey();
                byte[] value = entry.getValue();
                a.putServiceData(key.getUuid().toString(), ByteString.copyFrom(value));
            }
            // Service UUIDs
            List<ParcelUuid> serviceUuids = scanRecord.getServiceUuids();
            if(serviceUuids != null) {
                for (ParcelUuid s : serviceUuids) {
                    a.addServiceUuids(s.getUuid().toString());
                }
            }
        }
        p.setRssi(scanResult.getRssi());
        p.setAdvertisementData(a.build());
        return p.build();
    }

    static Protos.BluetoothDevice from(BluetoothDevice device) {
        Protos.BluetoothDevice.Builder p = Protos.BluetoothDevice.newBuilder();
        p.setRemoteId(device.getAddress());
        String name = device.getName();
        if(name != null) {
            p.setName(name);
        }
        switch(device.getType()){
            case BluetoothDevice.DEVICE_TYPE_LE:
                p.setType(Protos.BluetoothDevice.Type.LE);
                break;
            case BluetoothDevice.DEVICE_TYPE_CLASSIC:
                p.setType(Protos.BluetoothDevice.Type.CLASSIC);
                break;
            case BluetoothDevice.DEVICE_TYPE_DUAL:
                p.setType(Protos.BluetoothDevice.Type.DUAL);
                break;
            default:
                p.setType(Protos.BluetoothDevice.Type.UNKNOWN);
                break;
        }
        return p.build();
    }

    static Protos.BluetoothService from(BluetoothDevice device, BluetoothGattService service, BluetoothGatt gatt) {
        Protos.BluetoothService.Builder p = Protos.BluetoothService.newBuilder();
        p.setRemoteId(device.getAddress());
        p.setUuid(service.getUuid().toString());
        p.setIsPrimary(service.getType() == SERVICE_TYPE_PRIMARY);
        for(BluetoothGattCharacteristic c : service.getCharacteristics()) {
            p.addCharacteristics(from(device, c, gatt));
        }
        for(BluetoothGattService s : service.getIncludedServices()) {
            p.addIncludedServices(from(device, s, gatt));
        }
        return p.build();
    }

    static Protos.BluetoothService from(BluetoothGattServer server, BluetoothGattService service) {
        Protos.BluetoothService.Builder p = Protos.BluetoothService.newBuilder();
        p.setUuid(service.getUuid().toString());
        p.setIsPrimary(service.getType() == SERVICE_TYPE_PRIMARY);
        for(BluetoothGattCharacteristic c : service.getCharacteristics()) {
            p.addCharacteristics(from(service, c));
        }
        for(BluetoothGattService s : service.getIncludedServices()) {
            p.addIncludedServices(from(server, s));
        }
        return p.build();
    }

    static Protos.BluetoothCharacteristic from(BluetoothGattService s, BluetoothGattCharacteristic c) {
        Protos.BluetoothCharacteristic.Builder b = Protos.BluetoothCharacteristic.newBuilder();
        // TODO ? b.setRemoteId(s.getRemoteId());
        b.setUuid(c.getUuid().toString());
        b.setProperties(from(c.getProperties()));
        if (c.getValue() != null) {
            b.setValue(ByteString.copyFrom(c.getValue()));
        }
        for (BluetoothGattDescriptor d : c.getDescriptors()) {
            b.addDescriptors(from(d));
        }
        if(c.getService().getType() == SERVICE_TYPE_PRIMARY) {
            b.setServiceUuid(c.getService().getUuid().toString());
        } else {
            if(s.getUuid().equals(c.getService().getUuid())) {
                b.setServiceUuid(s.getUuid().toString());
                b.setSecondaryServiceUuid(s.getUuid().toString());
            }
        }
        return b.build();
    }

    static Protos.BluetoothCharacteristic from(BluetoothDevice device, BluetoothGattCharacteristic characteristic, BluetoothGatt gatt) {
        Protos.BluetoothCharacteristic.Builder p = Protos.BluetoothCharacteristic.newBuilder();
        p.setRemoteId(device.getAddress());
        p.setUuid(characteristic.getUuid().toString());
        p.setProperties(from(characteristic.getProperties()));
        if(characteristic.getValue() != null)
            p.setValue(ByteString.copyFrom(characteristic.getValue()));
        for(BluetoothGattDescriptor d : characteristic.getDescriptors()) {
            p.addDescriptors(from(device, d));
        }
        if(characteristic.getService().getType() == SERVICE_TYPE_PRIMARY) {
            p.setServiceUuid(characteristic.getService().getUuid().toString());
        } else {
            // Reverse search to find service
            for(BluetoothGattService s : gatt.getServices()) {
                for(BluetoothGattService ss : s.getIncludedServices()) {
                    if(ss.getUuid().equals(characteristic.getService().getUuid())){
                        p.setServiceUuid(s.getUuid().toString());
                        p.setSecondaryServiceUuid(ss.getUuid().toString());
                        break;
                    }
                }
            }
        }
        return p.build();
    }

    static Protos.BluetoothDescriptor from(BluetoothDevice device, BluetoothGattDescriptor descriptor) {
        Protos.BluetoothDescriptor.Builder p = Protos.BluetoothDescriptor.newBuilder();
        p.setRemoteId(device.getAddress());
        p.setUuid(descriptor.getUuid().toString());
        p.setCharacteristicUuid(descriptor.getCharacteristic().getUuid().toString());
        p.setServiceUuid(descriptor.getCharacteristic().getService().getUuid().toString());
        if(descriptor.getValue() != null)
            p.setValue(ByteString.copyFrom(descriptor.getValue()));
        return p.build();
    }

    static Protos.CharacteristicProperties from(int properties) {
        return Protos.CharacteristicProperties.newBuilder()
                .setBroadcast((properties & 1) != 0)
                .setRead((properties & 2) != 0)
                .setWriteWithoutResponse((properties & 4) != 0)
                .setWrite((properties & 8) != 0)
                .setNotify((properties & 16) != 0)
                .setIndicate((properties & 32) != 0)
                .setAuthenticatedSignedWrites((properties & 64) != 0)
                .setExtendedProperties((properties & 128) != 0)
                .setNotifyEncryptionRequired((properties & 256) != 0)
                .setIndicateEncryptionRequired((properties & 512) != 0)
                .build();
    }

    static Protos.DeviceStateResponse from(BluetoothDevice device, int state) {
        Protos.DeviceStateResponse.Builder p = Protos.DeviceStateResponse.newBuilder();
        switch(state) {
            case BluetoothProfile.STATE_DISCONNECTING:
                p.setState(Protos.DeviceStateResponse.BluetoothDeviceState.DISCONNECTING);
                break;
            case BluetoothProfile.STATE_CONNECTED:
                p.setState(Protos.DeviceStateResponse.BluetoothDeviceState.CONNECTED);
                break;
            case BluetoothProfile.STATE_CONNECTING:
                p.setState(Protos.DeviceStateResponse.BluetoothDeviceState.CONNECTING);
                break;
            case BluetoothProfile.STATE_DISCONNECTED:
                p.setState(Protos.DeviceStateResponse.BluetoothDeviceState.DISCONNECTED);
                break;
            default:
                break;
        }
        p.setRemoteId(device.getAddress());
        return p.build();
    }

    private static Protos.BluetoothDescriptor from(BluetoothGattDescriptor d) {
        Protos.BluetoothDescriptor.Builder p = Protos.BluetoothDescriptor.newBuilder();
        // TODO important ? p.setRemoteId(device.getAddress());
        p.setUuid(d.getUuid().toString());
        p.setCharacteristicUuid(d.getCharacteristic().getUuid().toString());
        p.setServiceUuid(d.getCharacteristic().getService().getUuid().toString());
        if(d.getValue() != null) {
            p.setValue(ByteString.copyFrom(d.getValue()));
        }
        return p.build();
    }

    public static BluetoothGattCharacteristic to(Protos.BluetoothCharacteristic c) {
        BluetoothGattCharacteristic res = new BluetoothGattCharacteristic(
                UUID.fromString(c.getUuid()),
                c.getDescriptorsCount(),
                c.getProperties().getSerializedSize()
        );
        res.setValue(c.getValue().toByteArray());
        return res;
    }

    public static BluetoothGattService to(Protos.BluetoothService s) {
        BluetoothGattService res = new BluetoothGattService(
                UUID.fromString(s.getUuid()),
                s.getIsPrimary() ? SERVICE_TYPE_PRIMARY : SERVICE_TYPE_SECONDARY
        );
        for (Protos.BluetoothService ps : s.getIncludedServicesList()) {
            res.addService(to(ps));
        }
        for (Protos.BluetoothCharacteristic pc: s.getCharacteristicsList()) {
            res.addCharacteristic(to(pc));
        }
        return res;
    }
}