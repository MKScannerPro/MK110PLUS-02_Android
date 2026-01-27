package com.moko.mkremotegw02.activity.filter;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.lib.mqtt.MQTTSupport;
import com.moko.lib.mqtt.entity.MsgConfigResult;
import com.moko.lib.mqtt.entity.MsgReadResult;
import com.moko.lib.mqtt.event.DeviceOnlineEvent;
import com.moko.lib.mqtt.event.MQTTMessageArrivedEvent;
import com.moko.lib.scannerui.dialog.AlertMessageDialog;
import com.moko.lib.scannerui.dialog.BottomDialog;
import com.moko.lib.scannerui.utils.ToastUtils;
import com.moko.mkremotegw02.AppConstants;
import com.moko.mkremotegw02.R;
import com.moko.mkremotegw02.base.BaseActivity;
import com.moko.mkremotegw02.databinding.ActivityFilterNano02Binding;
import com.moko.mkremotegw02.entity.MQTTConfig;
import com.moko.mkremotegw02.entity.MokoDevice;
import com.moko.mkremotegw02.utils.SPUtiles;
import com.moko.support.remotegw02.MQTTConstants;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class FilterNanoActivity extends BaseActivity<ActivityFilterNano02Binding> {
    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;
    public Handler mHandler;
    private List<String> filterIdList;
    private final String[] mTriggerType = {"Normal type", "Trigger type", "All"};

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
        getFilterNano();
    }

    @Override
    protected ActivityFilterNano02Binding getViewBinding() {
        return ActivityFilterNano02Binding.inflate(getLayoutInflater());
    }

    private void getFilterNano() {
        int msgId = MQTTConstants.READ_MSG_ID_FILTER_NANO;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            XLog.e(e);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTMessageArrivedEvent(MQTTMessageArrivedEvent event) {
        // 更新所有设备的网络状态
        final String message = event.getMessage();
        if (TextUtils.isEmpty(message)) return;
        int msg_id;
        try {
            JsonObject object = new Gson().fromJson(message, JsonObject.class);
            JsonElement element = object.get("msg_id");
            msg_id = element.getAsInt();
        } catch (Exception e) {
            XLog.e(e);
            return;
        }
        if (msg_id == MQTTConstants.READ_MSG_ID_FILTER_NANO) {
            Type type = new TypeToken<MsgReadResult<JsonObject>>() {
            }.getType();
            MsgReadResult<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            mBind.cbNano.setChecked(result.data.get("switch_value").getAsInt() == 1);
            int triggerType = result.data.get("adv_type").getAsInt();
            mBind.tvTriggerType.setTag(triggerType);
            mBind.tvTriggerType.setText(mTriggerType[triggerType]);
            JsonArray codeList = result.data.getAsJsonArray("mf_id");
            int number = codeList.size();
            filterIdList = new ArrayList<>();
            if (number != 0) {
                int index = 1;
                for (JsonElement jsonElement : codeList) {
                    filterIdList.add(jsonElement.getAsString());
                    String codeId = jsonElement.getAsString();
                    View v = LayoutInflater.from(FilterNanoActivity.this).inflate(R.layout.item_nano_filter02, mBind.llId, false);
                    TextView title = v.findViewById(R.id.tvTitle);
                    EditText etMFId = v.findViewById(R.id.etId);
                    title.setText(String.format(Locale.getDefault(), "ID %d", index));
                    etMFId.setText(codeId);
                    mBind.llId.addView(v);
                    index++;
                }
            }
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_FILTER_NANO) {
            Type type = new TypeToken<MsgConfigResult<?>>() {
            }.getType();
            MsgConfigResult<?> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            if (result.result_code == 0) {
                ToastUtils.showToast(this, "Set up succeed");
            } else {
                ToastUtils.showToast(this, "Set up failed");
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceOnlineEvent(DeviceOnlineEvent event) {
        super.offline(event, mMokoDevice.mac);
    }

    public void onBack(View view) {
        finish();
    }

    public void onSave(View view) {
        if (isWindowLocked()) return;
        if (isValid()) {
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(this, "Set up failed");
            }, 30 * 1000);
            showLoadingProgressDialog();
            saveParams();
        }
    }

    public void onTriggerType(View view) {
        if (isWindowLocked()) return;
        int selected = (int) view.getTag();
        BottomDialog dialog = new BottomDialog();
        dialog.setDatas(new ArrayList<>(Arrays.asList(mTriggerType)), selected);
        dialog.setListener(value -> {
            view.setTag(value);
            mBind.tvTriggerType.setText(mTriggerType[value]);
        });
        dialog.show(getSupportFragmentManager());
    }

    public void onAdd(View view) {
        if (isWindowLocked()) return;
        int count = mBind.llId.getChildCount();
        if (count > 9) {
            ToastUtils.showToast(this, "You can set up to 10 filters!");
            return;
        }
        View v = LayoutInflater.from(this).inflate(R.layout.item_nano_filter02, mBind.llId, false);
        TextView title = v.findViewById(R.id.tvTitle);
        title.setText(String.format(Locale.getDefault(), "ID %d", count + 1));
        mBind.llId.addView(v);
    }

    public void onDel(View view) {
        if (isWindowLocked()) return;
        final int c = mBind.llId.getChildCount();
        if (c == 0) {
            ToastUtils.showToast(this, "There are currently no filters to delete");
            return;
        }
        AlertMessageDialog dialog = new AlertMessageDialog();
        dialog.setTitle("Warning");
        dialog.setMessage("Please confirm whether to delete it, if yes, the last option will be deleted!");
        dialog.setOnAlertConfirmListener(() -> {
            int count = mBind.llId.getChildCount();
            if (count > 0) {
                mBind.llId.removeViewAt(count - 1);
            }
        });
        dialog.show(getSupportFragmentManager());
    }

    private void saveParams() {
        int type = (int) mBind.tvTriggerType.getTag();
        int msgId = MQTTConstants.CONFIG_MSG_ID_FILTER_NANO;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("switch_value", mBind.cbNano.isChecked() ? 1 : 0);
        jsonObject.addProperty("adv_type", type);
        JsonArray codeList = new JsonArray();
        for (String code : filterIdList)
            codeList.add(code);
        jsonObject.add("mf_id", codeList);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            XLog.e(e);
        }
    }

    private boolean isValid() {
        final int c = mBind.llId.getChildCount();
        if (c > 0) {
            // 发送设置的过滤RawData
            int count = mBind.llId.getChildCount();
            if (count == 0) {
                ToastUtils.showToast(this, "Para Error");
                return false;
            }
            filterIdList.clear();
            for (int i = 0; i < count; i++) {
                View v = mBind.llId.getChildAt(i);
                EditText etId = v.findViewById(R.id.etId);
                final String code = etId.getText().toString();
                if (TextUtils.isEmpty(code)) {
                    ToastUtils.showToast(this, "Para Error");
                    return false;
                }
                int length = code.length();
                if (length != 4) {
                    ToastUtils.showToast(this, "Para Error");
                    return false;
                }
                filterIdList.add(code);
            }
        } else {
            filterIdList = new ArrayList<>();
        }
        return true;
    }
}
