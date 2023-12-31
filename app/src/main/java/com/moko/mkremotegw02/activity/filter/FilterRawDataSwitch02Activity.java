package com.moko.mkremotegw02.activity.filter;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.mkremotegw02.AppConstants;
import com.moko.mkremotegw02.R;
import com.moko.mkremotegw02.base.BaseActivity;
import com.moko.mkremotegw02.databinding.ActivityFilterRawDataSwitch02Binding;
import com.moko.mkremotegw02.entity.MQTTConfig;
import com.moko.mkremotegw02.entity.MokoDevice;
import com.moko.mkremotegw02.utils.SPUtiles;
import com.moko.mkremotegw02.utils.ToastUtils;
import com.moko.support.remotegw02.MQTTConstants;
import com.moko.support.remotegw02.MQTTSupport;
import com.moko.support.remotegw02.entity.MsgConfigResult;
import com.moko.support.remotegw02.entity.MsgReadResult;
import com.moko.support.remotegw02.event.DeviceOnlineEvent;
import com.moko.support.remotegw02.event.MQTTMessageArrivedEvent;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;

public class FilterRawDataSwitch02Activity extends BaseActivity<ActivityFilterRawDataSwitch02Binding> {
    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;
    public Handler mHandler;
    private boolean isBXPDeviceInfoOpen;
    private boolean isBXPAccOpen;
    private boolean isBXPTHOpen;

    @Override
    protected void onCreate() {
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mAppTopic = TextUtils.isEmpty(appMqttConfig.topicPublish) ? mMokoDevice.topicSubscribe : appMqttConfig.topicPublish;
        mHandler = new Handler(Looper.getMainLooper());
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        showLoadingProgressDialog();
        getFilterRawDataSwitch();
    }

    @Override
    protected ActivityFilterRawDataSwitch02Binding getViewBinding() {
        return ActivityFilterRawDataSwitch02Binding.inflate(getLayoutInflater());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTMessageArrivedEvent(MQTTMessageArrivedEvent event) {
        // 更新所有设备的网络状态
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
        if (msg_id == MQTTConstants.READ_MSG_ID_FILTER_RAW_DATA_SWITCH) {
            Type type = new TypeToken<MsgReadResult<JsonObject>>() {
            }.getType();
            MsgReadResult<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            mBind.tvFilterByIbeacon.setText(result.data.get("ibeacon").getAsInt() == 1 ? "ON" : "OFF");
            mBind.tvFilterByUid.setText(result.data.get("eddystone_uid").getAsInt() == 1 ? "ON" : "OFF");
            mBind.tvFilterByUrl.setText(result.data.get("eddystone_url").getAsInt() == 1 ? "ON" : "OFF");
            mBind.tvFilterByTlm.setText(result.data.get("eddystone_tlm").getAsInt() == 1 ? "ON" : "OFF");
            isBXPDeviceInfoOpen = result.data.get("bxp_devinfo").getAsInt() == 1;
            isBXPAccOpen = result.data.get("bxp_acc").getAsInt() == 1;
            isBXPTHOpen = result.data.get("bxp_th").getAsInt() == 1;
            mBind.ivFilterByBxpInfo.setImageResource(isBXPDeviceInfoOpen ? R.drawable.ic_cb_open : R.drawable.ic_cb_close);
            mBind.ivFilterByBxpAcc.setImageResource(isBXPAccOpen ? R.drawable.ic_cb_open : R.drawable.ic_cb_close);
            mBind.ivFilterByBxpTh.setImageResource(isBXPTHOpen ? R.drawable.ic_cb_open : R.drawable.ic_cb_close);
            mBind.tvFilterByBxpButton.setText(result.data.get("bxp_button").getAsInt() == 1 ? "ON" : "OFF");
            mBind.tvFilterByBxpTag.setText(result.data.get("bxp_tag").getAsInt() == 1 ? "ON" : "OFF");
            mBind.tvFilterByPir.setText(result.data.get("pir").getAsInt() == 1 ? "ON" : "OFF");
            mBind.tvFilterByOther.setText(result.data.get("other").getAsInt() == 1 ? "ON" : "OFF");
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_FILTER_BXP_DEVICE_INFO
                || msg_id == MQTTConstants.CONFIG_MSG_ID_FILTER_BXP_ACC
                || msg_id == MQTTConstants.CONFIG_MSG_ID_FILTER_BXP_TH) {
            Type type = new TypeToken<MsgConfigResult>() {
            }.getType();
            MsgConfigResult result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            if (result.result_code == 0) {
                getFilterRawDataSwitch();
                ToastUtils.showToast(this, "Set up succeed");
            } else {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
                ToastUtils.showToast(this, "Set up failed");
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceOnlineEvent(DeviceOnlineEvent event) {
        super.offline(event, mMokoDevice.mac);
    }

    private void getFilterRawDataSwitch() {
        int msgId = MQTTConstants.READ_MSG_ID_FILTER_RAW_DATA_SWITCH;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void onBack(View view) {
        finish();
    }

    public void onFilterByBXPDeviceInfo(View view) {
        if (isWindowLocked())
            return;
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        setBXPDevice();
    }

    public void onFilterByBXPAcc(View view) {
        if (isWindowLocked())
            return;
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        setBXPAcc();
    }

    public void onFilterByBXPTH(View view) {
        if (isWindowLocked())
            return;
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        setBXPTH();
    }

    private void setBXPDevice() {
        isBXPDeviceInfoOpen = !isBXPDeviceInfoOpen;
        int msgId = MQTTConstants.CONFIG_MSG_ID_FILTER_BXP_DEVICE_INFO;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("switch_value", isBXPDeviceInfoOpen ? 1 : 0);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setBXPAcc() {
        isBXPAccOpen = !isBXPAccOpen;
        int msgId = MQTTConstants.CONFIG_MSG_ID_FILTER_BXP_ACC;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("switch_value", isBXPAccOpen ? 1 : 0);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setBXPTH() {
        isBXPTHOpen = !isBXPTHOpen;
        int msgId = MQTTConstants.CONFIG_MSG_ID_FILTER_BXP_TH;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("switch_value", isBXPTHOpen ? 1 : 0);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void onFilterByIBeacon(View view) {
        if (isWindowLocked())
            return;
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        Intent i = new Intent(this, FilterIBeacon02Activity.class);
        i.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startFilterDetail.launch(i);
    }

    public void onFilterByUid(View view) {
        if (isWindowLocked())
            return;
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        Intent i = new Intent(this, FilterUID02Activity.class);
        i.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startFilterDetail.launch(i);
    }

    public void onFilterByUrl(View view) {
        if (isWindowLocked())
            return;
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        Intent i = new Intent(this, FilterUrl02Activity.class);
        i.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startFilterDetail.launch(i);
    }

    public void onFilterByTlm(View view) {
        if (isWindowLocked())
            return;
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        Intent i = new Intent(this, FilterTLM02Activity.class);
        i.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startFilterDetail.launch(i);
    }

    public void onFilterByBXPButton(View view) {
        if (isWindowLocked())
            return;
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        Intent i = new Intent(this, FilterBXPButton02Activity.class);
        i.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startFilterDetail.launch(i);
    }

    public void onFilterByBXPTag(View view) {
        if (isWindowLocked())
            return;
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        Intent i = new Intent(this, FilterBXPTag02Activity.class);
        i.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startFilterDetail.launch(i);
    }

    public void onFilterByPIRPresence(View view) {
        if (isWindowLocked())
            return;
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        Intent i = new Intent(this, FilterPIR02Activity.class);
        i.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startFilterDetail.launch(i);
    }


    public void onFilterByOther(View view) {
        if (isWindowLocked())
            return;
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        Intent i = new Intent(this, FilterOther02Activity.class);
        i.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startFilterDetail.launch(i);
    }

    private final ActivityResultLauncher<Intent> startFilterDetail = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        showLoadingProgressDialog();
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        getFilterRawDataSwitch();
    });
}
