package com.moko.mkremotegw02.activity.beacon;

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
import com.moko.lib.scannerui.dialog.BottomDialog;
import com.moko.lib.scannerui.utils.ToastUtils;
import com.moko.mkremotegw02.AppConstants;
import com.moko.mkremotegw02.base.BaseActivity;
import com.moko.mkremotegw02.databinding.ActivityBxpTRemoteReminder02Binding;
import com.moko.mkremotegw02.entity.MQTTConfig;
import com.moko.mkremotegw02.entity.MokoDevice;
import com.moko.mkremotegw02.utils.SPUtiles;
import com.moko.support.remotegw02.MQTTConstants;
import com.moko.support.remotegw02.entity.BeaconInfo;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;
import java.util.ArrayList;

public class BXPTRemoteReminderActivity extends BaseActivity<ActivityBxpTRemoteReminder02Binding> {
    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;
    private String mMac;
    private int mBeaconType;
    private Handler mHandler;

    private ArrayList<String> mColorArray;

    @Override
    protected void onCreate() {
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mAppTopic = TextUtils.isEmpty(appMqttConfig.topicPublish) ? mMokoDevice.topicSubscribe : appMqttConfig.topicPublish;
        mHandler = new Handler(Looper.getMainLooper());
        mMac = getIntent().getStringExtra(AppConstants.EXTRA_KEY_MAC);
        mBeaconType = getIntent().getIntExtra(AppConstants.EXTRA_KEY_BEACON_TYPE, 1);
        mColorArray = new ArrayList<>();
        mColorArray.add("Green");
        mColorArray.add("Blue");
        mColorArray.add("Red");
        mBind.tvLedReminderColor.setTag(0);
    }

    @Override
    protected ActivityBxpTRemoteReminder02Binding getViewBinding() {
        return ActivityBxpTRemoteReminder02Binding.inflate(getLayoutInflater());
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
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_DISCONNECT) {
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            finish();
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_T_LED
                || msg_id == MQTTConstants.NOTIFY_MSG_ID_BLE_BXP_S_LED) {
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            Type type = new TypeToken<MsgNotify<BeaconInfo>>() {
            }.getType();
            MsgNotify<BeaconInfo> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            ToastUtils.showToast(this, result.data.result_code == 0 ? "Setup succeed!" : "Setup failed");
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

    public void onLedNotifyRemind(View view) {
        if (isWindowLocked()) return;
        if (isLEDValid()) {
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(this, "Setup failed");
            }, 30 * 1000);
            showLoadingProgressDialog();
            setLEDNotifyRemind();
        } else {
            ToastUtils.showToast(this, "Para Error");
        }
    }

    public void onLedReminderColor(View view) {
        if (isWindowLocked()) return;
        int selected = (int) view.getTag();
        BottomDialog dialog = new BottomDialog();
        dialog.setDatas(mColorArray, selected);
        dialog.setListener(value -> {
            view.setTag(value);
            mBind.tvLedReminderColor.setText(mColorArray.get(value));
        });
        dialog.show(getSupportFragmentManager());
    }

    private void setLEDNotifyRemind() {
        String ledTimeStr = mBind.etBlinkingTime.getText().toString();
        String ledIntervalStr = mBind.etBlinkingInterval.getText().toString();
        int ledTime = Integer.parseInt(ledTimeStr) * 10;
        if (mBeaconType == 5)
            ledTime = Integer.parseInt(ledTimeStr);
        int ledInterval = Integer.parseInt(ledIntervalStr) * 100;
        String ledColor = mColorArray.get((int) mBind.tvLedReminderColor.getTag()).toLowerCase();
        int msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_T_LED;
        if (mBeaconType == 6)
            msgId = MQTTConstants.CONFIG_MSG_ID_BLE_BXP_S_LED;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("mac", mMac);
        jsonObject.addProperty("color", ledColor);
        jsonObject.addProperty("flash_time", ledTime);
        jsonObject.addProperty("flash_interval", ledInterval);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private boolean isLEDValid() {
        String ledTimeStr = mBind.etBlinkingTime.getText().toString();
        String ledIntervalStr = mBind.etBlinkingInterval.getText().toString();
        if (TextUtils.isEmpty(ledTimeStr) || TextUtils.isEmpty(ledIntervalStr)) {
            return false;
        }
        int ledTime = Integer.parseInt(ledTimeStr);
        if (ledTime < 1 || ledTime > 600)
            return false;
        int ledInterval = Integer.parseInt(ledIntervalStr);
        if (ledInterval < 1 || ledInterval > 100)
            return false;
        return true;
    }

    public void onBack(View view) {
        if (isWindowLocked()) return;
        finish();
    }
}
