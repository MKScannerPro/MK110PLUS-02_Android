package com.moko.mkremotegw02.activity.beacon;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.lib.mqtt.MQTTSupport;
import com.moko.lib.mqtt.entity.MsgNotify;
import com.moko.lib.mqtt.event.DeviceOnlineEvent;
import com.moko.lib.mqtt.event.MQTTMessageArrivedEvent;
import com.moko.lib.scannerui.dialog.AlertMessageDialog;
import com.moko.lib.scannerui.dialog.BottomDialog;
import com.moko.lib.scannerui.utils.ToastUtils;
import com.moko.mkremotegw02.AppConstants;
import com.moko.mkremotegw02.R;
import com.moko.mkremotegw02.activity.DeviceDetail02Activity;
import com.moko.mkremotegw02.base.BaseActivity;
import com.moko.mkremotegw02.databinding.ActivityBxpBDInfo02Binding;
import com.moko.mkremotegw02.entity.MQTTConfig;
import com.moko.mkremotegw02.entity.MokoDevice;
import com.moko.mkremotegw02.utils.SPUtiles;
import com.moko.support.remotegw02.MQTTConstants;
import com.moko.support.remotegw02.entity.BeaconInfo;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public class BXPBDActivity extends BaseActivity<ActivityBxpBDInfo02Binding> {
    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;
    private BeaconInfo mBeaconInfo;
    private Handler mHandler;
    private String[] mAdvMode = {"Battery percentage", "Battery voltage"};

    @Override
    protected void onCreate() {
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mAppTopic = TextUtils.isEmpty(appMqttConfig.topicPublish) ? mMokoDevice.topicSubscribe : appMqttConfig.topicPublish;
        mHandler = new Handler(Looper.getMainLooper());

        mBeaconInfo = (BeaconInfo) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_BEACON_INFO);
        mBind.tvDeviceName.setText(mMokoDevice.name);
        mBind.tvProductModel.setText(mBeaconInfo.product_model);
        mBind.tvManufacturer.setText(mBeaconInfo.company_name);
        mBind.tvDeviceFirmwareVersion.setText(mBeaconInfo.firmware_version);
        mBind.tvDeviceHardwareVersion.setText(mBeaconInfo.hardware_version);
        mBind.tvDeviceSoftwareVersion.setText(mBeaconInfo.software_version);
        mBind.tvDeviceMac.setText(mBeaconInfo.mac);
        mBind.tvBatteryVoltage.setText(String.format("%dmV", mBeaconInfo.battery_v));
        mBind.tvSinglePressCount.setText(String.valueOf(mBeaconInfo.single_alarm_num));
        mBind.tvDoublePressCount.setText(String.valueOf(mBeaconInfo.double_alarm_num));
        mBind.tvLongPressCount.setText(String.valueOf(mBeaconInfo.long_alarm_num));
        String alarmStatusStr = "";
        mBeaconInfo.alarm_status = mBeaconInfo.alarm_status & 0x0F;
        if (mBeaconInfo.alarm_status == 0) {
            alarmStatusStr = "Not triggered";
        } else {
            StringBuilder modeStr = new StringBuilder();
            if ((mBeaconInfo.alarm_status & 0x01) == 0x01)
                modeStr.append("1&");
            if ((mBeaconInfo.alarm_status & 0x02) == 0x02)
                modeStr.append("2&");
            if ((mBeaconInfo.alarm_status & 0x04) == 0x04)
                modeStr.append("3&");
            if ((mBeaconInfo.alarm_status & 0x08) == 0x08)
                modeStr.append("4&");
            String mode = modeStr.substring(0, modeStr.length() - 1);
            alarmStatusStr = String.format("Mode %s triggered", mode);
        }
        mBind.tvAlarmStatus.setText(alarmStatusStr);
        mBind.tvRemoteReminder.setOnClickListener(v -> gotoRemoteReminder());
        mBind.tvAccData.setOnClickListener(v -> gotoAccData());
        mBind.tvAdvParams.setOnClickListener(v -> gotoAdvParams());
        mBind.tvPowerOff.setOnClickListener(v -> showPowerOffDialog());
        if (Integer.parseInt(mBeaconInfo.firmware_version.substring(1, 2)) > 1) {
            mBind.llBatteryAdvMode.setVisibility(View.VISIBLE);
            mBind.tvBattery.setText("Battery voltage/level");
            mBind.tvBatteryVoltage.setText(String.format("%dmv/%d%%", mBeaconInfo.battery_v, mBeaconInfo.battery_level));
            getBatteryMode();
        } else {
            mBind.llBatteryAdvMode.setVisibility(View.GONE);
        }
    }

    private void getBatteryMode() {
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Setup failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_B_D_BATTERY_MODE_READ;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBeaconInfo.mac);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void gotoRemoteReminder() {
        if (isWindowLocked()) return;
        Intent intent = new Intent(this, BXPBDRemoteReminderActivity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        intent.putExtra(AppConstants.EXTRA_KEY_MAC, mBeaconInfo.mac);
        startActivity(intent);
    }

    private void gotoAccData() {
        if (isWindowLocked()) return;
        Intent intent = new Intent(this, BXPAccActivity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        intent.putExtra(AppConstants.EXTRA_KEY_MAC, mBeaconInfo.mac);
        startActivity(intent);
    }

    private void gotoAdvParams() {
        if (isWindowLocked()) return;
        Intent intent = new Intent(this, BXPBAdvParamsActivity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        intent.putExtra(AppConstants.EXTRA_KEY_BEACON_INFO, mBeaconInfo);
        startActivity(intent);
    }

    private void showPowerOffDialog() {
        AlertMessageDialog dialog = new AlertMessageDialog();
        dialog.setMessage("Please confirm again whether to power off BLE device");
        dialog.setOnAlertConfirmListener(() -> {
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(this, "Set up failed");
            }, 30 * 1000);
            showLoadingProgressDialog();
            int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_B_D_POWER_OFF;
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("mac", mBeaconInfo.mac);
            String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
            try {
                MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
            } catch (MqttException e) {
                e.printStackTrace();
            }
        });
        dialog.show(getSupportFragmentManager());
    }

    @Override
    protected ActivityBxpBDInfo02Binding getViewBinding() {
        return ActivityBxpBDInfo02Binding.inflate(getLayoutInflater());
    }

    @Subscribe(threadMode = ThreadMode.POSTING, priority = 200)
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
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_B_D_BATTERY_MODE_READ) {
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            int mode = result.data.get("batt_adv_mode").getAsInt();
            mBind.tvAdvMode.setTag(mode);
            mBind.tvAdvMode.setText(mAdvMode[mode]);
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_B_D_STATUS) {
            EventBus.getDefault().cancelEventDelivery(event);
            runOnUiThread(() -> {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
                Type type = new TypeToken<MsgNotify<BeaconInfo>>() {
                }.getType();
                MsgNotify<BeaconInfo> result = new Gson().fromJson(message, type);
                if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                    return;
                BeaconInfo bxpButtonInfo = result.data;
                if (bxpButtonInfo.result_code != 0) {
                    ToastUtils.showToast(this, "Setup failed");
                    return;
                }
                ToastUtils.showToast(this, "Setup succeed!");
                mBind.tvBatteryVoltage.setText(String.format("%dmV", bxpButtonInfo.battery_v));
                mBind.tvSinglePressCount.setText(String.valueOf(bxpButtonInfo.single_alarm_num));
                mBind.tvDoublePressCount.setText(String.valueOf(bxpButtonInfo.double_alarm_num));
                mBind.tvLongPressCount.setText(String.valueOf(bxpButtonInfo.long_alarm_num));
                String alarmStatusStr = "";
                bxpButtonInfo.alarm_status = bxpButtonInfo.alarm_status & 0x0F;
                if (bxpButtonInfo.alarm_status == 0) {
                    alarmStatusStr = "Not triggered";
                } else {
                    StringBuilder modeStr = new StringBuilder();
                    if ((bxpButtonInfo.alarm_status & 0x01) == 0x01)
                        modeStr.append("1&");
                    if ((bxpButtonInfo.alarm_status & 0x02) == 0x02)
                        modeStr.append("2&");
                    if ((bxpButtonInfo.alarm_status & 0x04) == 0x04)
                        modeStr.append("3&");
                    if ((bxpButtonInfo.alarm_status & 0x08) == 0x08)
                        modeStr.append("4&");
                    String mode = modeStr.substring(0, modeStr.length() - 1);
                    alarmStatusStr = String.format("Mode %s triggered", mode);
                }
                mBind.tvAlarmStatus.setText(alarmStatusStr);
                if (Integer.parseInt(mBeaconInfo.firmware_version.substring(1, 2)) > 1) {
                    mBind.tvBattery.setText("Battery voltage/level");
                    mBind.tvBatteryVoltage.setText(String.format("%dmv/%d%%", bxpButtonInfo.battery_v, bxpButtonInfo.battery_level));
                }
            });
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_B_D_CLEAR_PRESS_EVENT) {
            runOnUiThread(() -> {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
                Type type = new TypeToken<MsgNotify<BeaconInfo>>() {
                }.getType();
                MsgNotify<BeaconInfo> result = new Gson().fromJson(message, type);
                if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                    return;
                BeaconInfo bxpButtonInfo = result.data;
                if (bxpButtonInfo.result_code != 0) {
                    ToastUtils.showToast(this, "Setup failed");
                    return;
                }
                ToastUtils.showToast(this, "Setup succeed!");
                mHandler.postDelayed(() -> {
                    dismissLoadingProgressDialog();
                    ToastUtils.showToast(this, "Setup failed");
                }, 30 * 1000);
                showLoadingProgressDialog();
                getBXPButtonStatus();
            });
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_B_D_DISMISS_ALARM) {
            runOnUiThread(() -> {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
                Type type = new TypeToken<MsgNotify<BeaconInfo>>() {
                }.getType();
                MsgNotify<BeaconInfo> result = new Gson().fromJson(message, type);
                if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                    return;
                BeaconInfo bxpButtonInfo = result.data;
                if (bxpButtonInfo.result_code != 0) {
                    ToastUtils.showToast(this, "Setup failed");
                    return;
                }
                ToastUtils.showToast(this, "Setup succeed!");
                mHandler.postDelayed(() -> {
                    dismissLoadingProgressDialog();
                    ToastUtils.showToast(this, "Setup failed");
                }, 30 * 1000);
                showLoadingProgressDialog();
                getBXPButtonStatus();
            });
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_B_D_POWER_OFF) {
            runOnUiThread(() -> {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
                Type type = new TypeToken<MsgNotify<BeaconInfo>>() {
                }.getType();
                MsgNotify<BeaconInfo> result = new Gson().fromJson(message, type);
                if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                    return;
                BeaconInfo bxpButtonInfo = result.data;
                if (bxpButtonInfo.result_code != 0) {
                    ToastUtils.showToast(this, "Setup failed");
                    return;
                }
                ToastUtils.showToast(this, "Setup succeed!");
            });
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_B_D_BATTERY_MODE_WRITE) {
            runOnUiThread(() -> {
                Type type = new TypeToken<MsgNotify<JsonObject>>() {
                }.getType();
                MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
                if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
                int code = result.data.get("result_code").getAsInt();
                ToastUtils.showToast(this, code == 0 ? "Setup succeed！" : "setup failed");
            });
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_DISCONNECT
                || msg_id == MQTTConstants.CONFIG_MSG_ID_BLE_DISCONNECT) {
            runOnUiThread(() -> {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
                Type type = new TypeToken<MsgNotify<JsonObject>>() {
                }.getType();
                MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
                if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                    return;
                ToastUtils.showToast(this, "Bluetooth disconnect");
                finish();
            });
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceOnlineEvent(DeviceOnlineEvent event) {
        String mac = event.getMac();
        if (!mMokoDevice.mac.equals(mac))
            return;
        boolean online = event.isOnline();
        if (!online) {
            ToastUtils.showToast(this, "device is off-line");
            finish();
        }
    }

    public void onDFU(View view) {
        if (isWindowLocked()) return;
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        Intent intent = new Intent(this, BeaconDFUv2Activity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        intent.putExtra(AppConstants.EXTRA_KEY_MAC, mBeaconInfo.mac);
        startBeaconDFU.launch(intent);
    }

    private final ActivityResultLauncher<Intent> startBeaconDFU = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK && null != result.getData()) {
            ToastUtils.showToast(this, "Bluetooth disconnect");
            finish();
        }
    });

    public void onReadBXPButtonStatus(View view) {
        if (isWindowLocked()) return;
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Setup failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        getBXPButtonStatus();
    }

    public void onClearPressEvent(View view) {
        if (isWindowLocked()) return;
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Setup failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        int type = Integer.parseInt((String) view.getTag());
        clearPressEvent(type);
    }


    public void onDismissAlarmStatus(View view) {
        if (isWindowLocked()) return;
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Setup failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        dismissAlarmStatus();
    }

    public void onDisconnect(View view) {
        if (isWindowLocked()) return;
        AlertMessageDialog dialog = new AlertMessageDialog();
        dialog.setMessage("Please confirm again whether to disconnect the gateway from BLE devices?");
        dialog.setOnAlertConfirmListener(() -> {
            if (!MQTTSupport.getInstance().isConnected()) {
                ToastUtils.showToast(this, R.string.network_error);
                return;
            }
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(BXPBDActivity.this, "Setup failed");
            }, 30 * 1000);
            showLoadingProgressDialog();
            disconnectDevice();
        });
        dialog.show(getSupportFragmentManager());
    }

    public void onBatteryAdvMode(View view) {
        if (isWindowLocked()) return;
        int selected = (int) view.getTag();
        BottomDialog dialog = new BottomDialog();
        dialog.setDatas(new ArrayList<>(Arrays.asList(mAdvMode)), selected);
        dialog.setListener(value -> {
            view.setTag(value);
            mBind.tvAdvMode.setText(mAdvMode[value]);
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(this, "Setup failed");
            }, 30 * 1000);
            showLoadingProgressDialog();
            setAdvMode(value);
        });
        dialog.show(getSupportFragmentManager());
    }

    private void setAdvMode(int value) {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_B_D_BATTERY_MODE_WRITE;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBeaconInfo.mac);
        jsonObject.addProperty("batt_adv_mode", value);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void clearPressEvent(int type) {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_B_D_CLEAR_PRESS_EVENT;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBeaconInfo.mac);
        jsonObject.addProperty("type", type);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void disconnectDevice() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_DISCONNECT;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBeaconInfo.mac);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void getBXPButtonStatus() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_B_D_STATUS;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBeaconInfo.mac);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void dismissAlarmStatus() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_BUTTON_DISMISS_ALARM;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mBeaconInfo.mac);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onBackPressed() {
        if (isWindowLocked()) return;
        backToDetail();
    }

    public void onBack(View view) {
        if (isWindowLocked()) return;
        backToDetail();
    }

    private void backToDetail() {
        Intent intent = new Intent(this, DeviceDetail02Activity.class);
        startActivity(intent);
    }
}
