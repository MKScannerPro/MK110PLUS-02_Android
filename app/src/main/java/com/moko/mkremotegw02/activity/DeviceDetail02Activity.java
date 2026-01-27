package com.moko.mkremotegw02.activity;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;

import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.lib.mqtt.MQTTSupport;
import com.moko.lib.mqtt.entity.MsgConfigResult;
import com.moko.lib.mqtt.entity.MsgNotify;
import com.moko.lib.mqtt.entity.MsgReadResult;
import com.moko.lib.mqtt.event.DeviceModifyNameEvent;
import com.moko.lib.mqtt.event.DeviceOnlineEvent;
import com.moko.lib.mqtt.event.MQTTMessageArrivedEvent;
import com.moko.lib.scannerui.utils.ToastUtils;
import com.moko.mkremotegw02.AppConstants;
import com.moko.mkremotegw02.R;
import com.moko.mkremotegw02.activity.beacon.BXPBCRActivity;
import com.moko.mkremotegw02.activity.beacon.BXPBDActivity;
import com.moko.mkremotegw02.activity.beacon.BXPButtonInfoActivity;
import com.moko.mkremotegw02.activity.beacon.BXPCActivity;
import com.moko.mkremotegw02.activity.beacon.BXPDActivity;
import com.moko.mkremotegw02.activity.beacon.BXPSActivity;
import com.moko.mkremotegw02.activity.beacon.BXPTActivity;
import com.moko.mkremotegw02.activity.beacon.BleOtherInfo02Activity;
import com.moko.mkremotegw02.activity.beacon.MKPIRActivity;
import com.moko.mkremotegw02.activity.beacon.MKTOFActivity;
import com.moko.mkremotegw02.adapter.ScanDevice02Adapter;
import com.moko.mkremotegw02.base.BaseActivity;
import com.moko.mkremotegw02.databinding.ActivityDetail02Binding;
import com.moko.mkremotegw02.db.DBTools02;
import com.moko.mkremotegw02.entity.MQTTConfig;
import com.moko.mkremotegw02.entity.MokoDevice;
import com.moko.mkremotegw02.utils.SPUtiles;
import com.moko.support.remotegw02.MQTTConstants;
import com.moko.support.remotegw02.entity.BeaconInfo;
import com.moko.support.remotegw02.entity.BleConnectedList;
import com.moko.support.remotegw02.entity.OtherDeviceInfo;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import androidx.recyclerview.widget.LinearLayoutManager;

public class DeviceDetail02Activity extends BaseActivity<ActivityDetail02Binding> {
    public static final String TAG = DeviceDetail02Activity.class.getSimpleName();
    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;
    private boolean mScanSwitch;
    private ScanDevice02Adapter mAdapter;
    private ArrayList<String> mScanDevices;
    private Handler mHandler;
    private BeaconInfo mConnectedBeaconInfo;

    @Override
    protected void onCreate() {
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mAppTopic = TextUtils.isEmpty(appMqttConfig.topicPublish) ? mMokoDevice.topicSubscribe : appMqttConfig.topicPublish;
        mHandler = new Handler(Looper.getMainLooper());

        mBind.tvDeviceName.setText(mMokoDevice.name);
        mScanDevices = new ArrayList<>();
        mAdapter = new ScanDevice02Adapter();
        mAdapter.openLoadAnimation();
        mAdapter.replaceData(mScanDevices);
        mBind.rvDevices.setLayoutManager(new LinearLayoutManager(this));
        mBind.rvDevices.setAdapter(mAdapter);

        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        showLoadingProgressDialog();
        getScanConfig();
    }

    @Override
    protected ActivityDetail02Binding getViewBinding() {
        return ActivityDetail02Binding.inflate(getLayoutInflater());
    }

    private void changeView() {
        mBind.ivScanSwitch.setImageResource(mScanSwitch ? R.drawable.ic_checkbox_open : R.drawable.ic_checkbox_close);
        mBind.tvScanDeviceTotal.setVisibility(mScanSwitch ? View.VISIBLE : View.GONE);
        mBind.tvScanDeviceTotal.setText(getString(R.string.scan_device_total, mScanDevices.size()));
        mBind.tvManageDevices.setVisibility(mScanSwitch ? View.VISIBLE : View.GONE);
        mBind.rvDevices.setVisibility(mScanSwitch ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        XLog.i(TAG + "-->onNewIntent...");
        setIntent(intent);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTMessageArrivedEvent(MQTTMessageArrivedEvent event) {
        // 更新所有设备的网络状态
        final String topic = event.getTopic();
        final String message = event.getMessage();
        if (TextUtils.isEmpty(message))
            return;
        int msg_id;
        try {
            JsonObject object = new Gson().fromJson(message, JsonObject.class);
            JsonElement element = object.get("msg_id");
            msg_id = element.getAsInt();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        if (msg_id == MQTTConstants.READ_MSG_ID_SCAN_CONFIG) {
            Type type = new TypeToken<MsgReadResult<JsonObject>>() {
            }.getType();
            MsgReadResult<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            mScanSwitch = result.data.get("scan_switch").getAsInt() == 1;
            changeView();
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_SCAN_RESULT) {
            Type type = new TypeToken<MsgNotify<List<JsonObject>>>() {
            }.getType();
            MsgNotify<List<JsonObject>> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            for (JsonObject jsonObject : result.data) {
                mScanDevices.add(0, jsonObject.toString());
            }
            mBind.tvScanDeviceTotal.setText(getString(R.string.scan_device_total, mScanDevices.size()));
            mAdapter.replaceData(mScanDevices);
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_SCAN_CONFIG) {
            Type type = new TypeToken<MsgConfigResult>() {
            }.getType();
            MsgConfigResult result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            if (result.result_code == 0) {
                ToastUtils.showToast(this, "Set up succeed");
            } else {
                ToastUtils.showToast(this, "Set up failed");
            }
        }
        if (msg_id == MQTTConstants.READ_MSG_ID_BLE_CONNECTED_LIST) {
            Type type = new TypeToken<MsgReadResult<BleConnectedList>>() {
            }.getType();
            MsgReadResult<BleConnectedList> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            if (result.data.ble_conn_list != null && !result.data.ble_conn_list.isEmpty()) {
                // 当前连接的设备有值
                BleConnectedList.BleDevice bleDevice = result.data.ble_conn_list.get(0);
                // 根据类型请求不同数据
                if (bleDevice.type > 0) {
                    mConnectedBeaconInfo = new BeaconInfo();
                    mConnectedBeaconInfo.type = bleDevice.type;
                    readConnectedBeaconInfo(bleDevice.mac, bleDevice.type);
                } else {
                    readConnectedOtherInfo(bleDevice.mac);
                }
            } else {
                Intent intent = new Intent(this, BleManager02Activity.class);
                if (mMokoDevice.deviceType == 0x11)
                    intent = new Intent(this, BleManager02V2Activity.class);
                intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
                startActivity(intent);
            }
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_B_D_INFO
                || msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_B_CR_INFO
                || msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_C_INFO
                || msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_D_INFO
                || msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_T_INFO
                || msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_S_INFO
                || msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_MK_PIR_INFO
                || msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_MK_TOF_INFO) {
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            Type type = new TypeToken<MsgNotify<BeaconInfo>>() {
            }.getType();
            MsgNotify<BeaconInfo> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            BeaconInfo beaconInfo = result.data;
            if (beaconInfo.result_code != 0) {
                ToastUtils.showToast(this, "Setup failed");
                return;
            }
            mConnectedBeaconInfo.mac = beaconInfo.mac;
            mConnectedBeaconInfo.product_model = beaconInfo.product_model;
            mConnectedBeaconInfo.company_name = beaconInfo.company_name;
            mConnectedBeaconInfo.hardware_version = beaconInfo.hardware_version;
            mConnectedBeaconInfo.software_version = beaconInfo.software_version;
            mConnectedBeaconInfo.firmware_version = beaconInfo.firmware_version;
            mConnectedBeaconInfo.sensor_status = beaconInfo.sensor_status;
            mConnectedBeaconInfo.axis_type = beaconInfo.axis_type;
            mConnectedBeaconInfo.th_type = beaconInfo.th_type;
            mConnectedBeaconInfo.light_type = beaconInfo.light_type;
            mConnectedBeaconInfo.pir_type = beaconInfo.pir_type;
            mConnectedBeaconInfo.tof_type = beaconInfo.tof_type;
            if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_B_CR_INFO) {
                readConnectedBeaconStatus(beaconInfo.mac, MQTTConstants.CONFIG_MSG_ID_BLE_BXP_B_CR_STATUS);
            } else if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_C_INFO) {
                readConnectedBeaconStatus(beaconInfo.mac, MQTTConstants.CONFIG_MSG_ID_BLE_BXP_C_STATUS);
            } else if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_D_INFO) {
                readConnectedBeaconStatus(beaconInfo.mac, MQTTConstants.CONFIG_MSG_ID_BLE_BXP_D_STATUS);
            } else if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_T_INFO) {
                readConnectedBeaconStatus(beaconInfo.mac, MQTTConstants.CONFIG_MSG_ID_BLE_BXP_T_STATUS);
            } else if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_S_INFO) {
                readConnectedBeaconStatus(beaconInfo.mac, MQTTConstants.CONFIG_MSG_ID_BLE_BXP_S_STATUS);
            } else if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_MK_PIR_INFO) {
                readConnectedBeaconStatus(beaconInfo.mac, MQTTConstants.CONFIG_MSG_ID_BLE_MK_PIR_STATUS);
            } else if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_MK_TOF_INFO) {
                readConnectedBeaconStatus(beaconInfo.mac, MQTTConstants.CONFIG_MSG_ID_BLE_MK_TOF_STATUS);
            } else {
                readConnectedBeaconStatus(beaconInfo.mac, MQTTConstants.CONFIG_MSG_ID_BLE_BXP_B_D_STATUS);
            }
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_B_D_STATUS
                || msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_B_CR_STATUS
                || msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_C_STATUS
                || msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_D_STATUS
                || msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_T_STATUS
                || msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_S_STATUS
                || msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_MK_PIR_STATUS
                || msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_MK_TOF_STATUS) {
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            Type type = new TypeToken<MsgNotify<BeaconInfo>>() {
            }.getType();
            MsgNotify<BeaconInfo> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            BeaconInfo beaconInfo = result.data;
            if (beaconInfo.result_code != 0) {
                ToastUtils.showToast(this, "Setup failed");
                return;
            }
            mConnectedBeaconInfo.battery_v = beaconInfo.battery_v;
            mConnectedBeaconInfo.battery_level = beaconInfo.battery_level;
            mConnectedBeaconInfo.single_alarm_num = beaconInfo.single_alarm_num;
            mConnectedBeaconInfo.double_alarm_num = beaconInfo.double_alarm_num;
            mConnectedBeaconInfo.long_alarm_num = beaconInfo.long_alarm_num;
            mConnectedBeaconInfo.alarm_status = beaconInfo.alarm_status;
            mConnectedBeaconInfo.run_time = beaconInfo.run_time;
            ToastUtils.showToast(this, "Setup succeed");
            Intent intent;
            if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_B_CR_STATUS) {
                intent = new Intent(this, BXPBCRActivity.class);
            } else if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_C_STATUS) {
                intent = new Intent(this, BXPCActivity.class);
            } else if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_D_STATUS) {
                intent = new Intent(this, BXPDActivity.class);
            } else if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_T_STATUS) {
                intent = new Intent(this, BXPTActivity.class);
            } else if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_S_STATUS) {
                intent = new Intent(this, BXPSActivity.class);
            } else if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_MK_PIR_STATUS) {
                intent = new Intent(this, MKPIRActivity.class);
            } else if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_MK_TOF_STATUS) {
                intent = new Intent(this, MKTOFActivity.class);
            } else {
                intent = new Intent(this, BXPButtonInfoActivity.class);
                if (mMokoDevice.deviceType == 0x11)
                    intent = new Intent(this, BXPBDActivity.class);
            }
            intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
            intent.putExtra(AppConstants.EXTRA_KEY_BEACON_INFO, mConnectedBeaconInfo);
            startActivity(intent);
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_OTHER_INFO) {
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            Type type = new TypeToken<MsgNotify<OtherDeviceInfo>>() {
            }.getType();
            MsgNotify<OtherDeviceInfo> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            OtherDeviceInfo otherDeviceInfo = result.data;
            if (otherDeviceInfo.result_code != 0) {
                ToastUtils.showToast(this, "Setup failed");
                return;
            }
            ToastUtils.showToast(this, "Setup succeed");
            Intent intent = new Intent(this, BleOtherInfo02Activity.class);
            intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
            intent.putExtra(AppConstants.EXTRA_KEY_OTHER_DEVICE_INFO, otherDeviceInfo);
            startActivity(intent);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceModifyNameEvent(DeviceModifyNameEvent event) {
        // 修改了设备名称
        MokoDevice device = DBTools02.getInstance(DeviceDetail02Activity.this).selectDevice(mMokoDevice.mac);
        mMokoDevice.name = device.name;
        mBind.tvDeviceName.setText(mMokoDevice.name);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceOnlineEvent(DeviceOnlineEvent event) {
        String mac = event.getMac();
        if (!mMokoDevice.mac.equals(mac)) return;
        boolean online = event.isOnline();
        if (!online) {
            ToastUtils.showToast(this, "device is off-line");
            finish();
        }
    }

    public void onBack(View view) {
        finish();
    }

    public void onDeviceSetting(View view) {
        if (isWindowLocked()) return;
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        Intent intent = new Intent(this, DeviceSetting02Activity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startActivity(intent);
    }

    public void onPowerMeteringSetting(View view) {
        if (isWindowLocked()) return;
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        Intent i = new Intent(this, PowerMetering02Activity.class);
        i.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startActivity(i);
    }

    public void onScannerOptionSetting(View view) {
        if (isWindowLocked()) return;
        // 获取扫描过滤
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        Intent i = new Intent(this, ScannerUploadOption02Activity.class);
        i.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startActivity(i);
    }

    public void onScanSwitch(View view) {
        if (isWindowLocked()) return;
        // 切换扫描开关
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        mScanSwitch = !mScanSwitch;
        mBind.ivScanSwitch.setImageResource(mScanSwitch ? R.drawable.ic_checkbox_open : R.drawable.ic_checkbox_close);
        mBind.tvManageDevices.setVisibility(mScanSwitch ? View.VISIBLE : View.GONE);
        mBind.tvScanDeviceTotal.setVisibility(mScanSwitch ? View.VISIBLE : View.GONE);
        mBind.tvScanDeviceTotal.setText(getString(R.string.scan_device_total, 0));
        mBind.rvDevices.setVisibility(mScanSwitch ? View.VISIBLE : View.GONE);
        mScanDevices.clear();
        mAdapter.replaceData(mScanDevices);
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        setScanConfig();
    }

    public void onManageBleDevices(View view) {
        if (isWindowLocked()) return;
        // 设置扫描间隔
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        getBleConnectedList();
    }

    private void getBleConnectedList() {
        int msgId = MQTTConstants.READ_MSG_ID_BLE_CONNECTED_LIST;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void getScanConfig() {
        int msgId = MQTTConstants.READ_MSG_ID_SCAN_CONFIG;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setScanConfig() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_SCAN_CONFIG;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("scan_switch", mScanSwitch ? 1 : 0);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void readConnectedOtherInfo(String mac) {
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Setup failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        getOtherInfo(mac);
    }

    private void getOtherInfo(String mac) {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_OTHER_INFO;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mac);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void readConnectedBeaconInfo(String mac, int type) {
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Setup failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        getConnectedBeaconInfo(mac, type);
    }

    private void getConnectedBeaconInfo(String mac, int type) {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_B_D_INFO;
        if (type == 2)
            msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_B_CR_INFO;
        if (type == 3)
            msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_C_INFO;
        if (type == 4)
            msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_D_INFO;
        if (type == 5)
            msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_T_INFO;
        if (type == 6)
            msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_S_INFO;
        if (type == 7)
            msgId = MQTTConstants.CONFIG_MSG_ID_BLE_MK_PIR_INFO;
        if (type == 8)
            msgId = MQTTConstants.CONFIG_MSG_ID_BLE_MK_TOF_INFO;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mac);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void readConnectedBeaconStatus(String mac, int msgId) {
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Setup failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        getConnectedBeaconStatus(mac, msgId);
    }

    private void getConnectedBeaconStatus(String mac, int msgId) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mac);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
