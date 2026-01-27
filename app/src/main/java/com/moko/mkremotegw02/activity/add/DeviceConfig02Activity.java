package com.moko.mkremotegw02.activity.add;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;

import com.elvishew.xlog.XLog;
import com.github.lzyzsd.circleprogress.DonutProgress;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.ble.lib.MokoConstants;
import com.moko.ble.lib.event.ConnectStatusEvent;
import com.moko.ble.lib.event.OrderTaskResponseEvent;
import com.moko.ble.lib.task.OrderTaskResponse;
import com.moko.lib.mqtt.MQTTSupport;
import com.moko.lib.mqtt.entity.MsgNotify;
import com.moko.lib.mqtt.event.MQTTMessageArrivedEvent;
import com.moko.lib.scannerui.dialog.AlertMessageDialog;
import com.moko.lib.scannerui.dialog.CustomDialog;
import com.moko.lib.scannerui.utils.ToastUtils;
import com.moko.mkremotegw02.AppConstants;
import com.moko.mkremotegw02.R;
import com.moko.mkremotegw02.activity.MeteringSettings02Activity;
import com.moko.mkremotegw02.activity.ModifyName02Activity;
import com.moko.mkremotegw02.activity.RemoteMainWithMetering02Activity;
import com.moko.mkremotegw02.base.BaseActivity;
import com.moko.mkremotegw02.databinding.ActivityDeviceConfig02Binding;
import com.moko.mkremotegw02.db.DBTools02;
import com.moko.mkremotegw02.entity.MQTTConfig;
import com.moko.mkremotegw02.entity.MokoDevice;
import com.moko.mkremotegw02.utils.SPUtiles;
import com.moko.support.remotegw02.MQTTConstants;
import com.moko.support.remotegw02.MokoSupport;
import com.moko.support.remotegw02.OrderTaskAssembler;
import com.moko.support.remotegw02.entity.OrderCHAR;
import com.moko.support.remotegw02.entity.ParamsKeyEnum;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public class DeviceConfig02Activity extends BaseActivity<ActivityDeviceConfig02Binding> {
    public static String TAG = ModifyName02Activity.class.getSimpleName();
    private MQTTConfig mAppMqttConfig;
    private MQTTConfig mDeviceMqttConfig;
    private Handler mHandler;
    private int mSelectedDeviceType;
    private boolean mIsFirstConfig;
    private boolean mIsMQTTConfigFinished;
    private boolean mIsWIFIConfigFinished;
    private CustomDialog mqttConnDialog;
    private DonutProgress donutProgress;
    private boolean isSettingSuccess;
    private boolean isDeviceConnectSuccess;

    @Override
    protected void onCreate() {
        mSelectedDeviceType = getIntent().getIntExtra(AppConstants.EXTRA_KEY_SELECTED_DEVICE_TYPE, 0x10);
        mIsFirstConfig = getIntent().getBooleanExtra(AppConstants.EXTRA_KEY_FIRST_CONFIG, false);
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        mAppMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mHandler = new Handler(Looper.getMainLooper());
        mBind.tvScannerFilter.setVisibility(mSelectedDeviceType == 0x10 ? View.VISIBLE : View.GONE);
        mBind.tvAdvIbeacon.setVisibility(mSelectedDeviceType == 0x10 ? View.VISIBLE : View.GONE);
        mBind.tvScanAndUpload.setVisibility(mSelectedDeviceType == 0x10 ? View.GONE : View.VISIBLE);
        mBind.tvAdvSettings.setVisibility(mSelectedDeviceType == 0x10 ? View.GONE : View.VISIBLE);
    }

    @Override
    protected ActivityDeviceConfig02Binding getViewBinding() {
        return ActivityDeviceConfig02Binding.inflate(getLayoutInflater());
    }

    @Subscribe(threadMode = ThreadMode.POSTING, priority = 50)
    public void onConnectStatusEvent(ConnectStatusEvent event) {
        String action = event.getAction();
        EventBus.getDefault().cancelEventDelivery(event);
        if (isSettingSuccess) return;
        if (MokoConstants.ACTION_DISCONNECTED.equals(action)) {
            runOnUiThread(() -> {
                Intent intent = new Intent();
                setResult(RESULT_OK, intent);
                finish();
            });
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onOrderTaskResponseEvent(OrderTaskResponseEvent event) {
        final String action = event.getAction();
        if (MokoConstants.ACTION_ORDER_FINISH.equals(action)) {
            dismissLoadingProgressDialog();
        }
        if (MokoConstants.ACTION_ORDER_RESULT.equals(action)) {
            OrderTaskResponse response = event.getResponse();
            OrderCHAR orderCHAR = (OrderCHAR) response.orderCHAR;
            int responseType = response.responseType;
            byte[] value = response.responseValue;
            if (orderCHAR == OrderCHAR.CHAR_PARAMS) {
                if (value.length >= 4) {
                    int header = value[0] & 0xFF;// 0xED
                    int flag = value[1] & 0xFF;// read or write
                    int cmd = value[2] & 0xFF;
                    if (header == 0xED) {
                        ParamsKeyEnum configKeyEnum = ParamsKeyEnum.fromParamKey(cmd);
                        if (configKeyEnum == null) {
                            return;
                        }
                        int length = value[3] & 0xFF;
                        if (flag == 0x01) {
                            // write
                            int result = value[4] & 0xFF;
                            if (configKeyEnum == ParamsKeyEnum.KEY_EXIT_CONFIG_MODE) {
                                if (result != 1) {
                                    ToastUtils.showToast(this, "Setup failed！");
                                } else {
                                    if (!mIsFirstConfig) {
                                        if (mIsMQTTConfigFinished)
                                            subscribeTopic();
                                        Intent modifyIntent = new Intent(DeviceConfig02Activity.this, RemoteMainWithMetering02Activity.class);
                                        modifyIntent.putExtra(AppConstants.EXTRA_KEY_FROM_ACTIVITY, TAG);
                                        if (mDeviceMqttConfig != null) {
                                            MokoDevice mokoDevice = DBTools02.getInstance(DeviceConfig02Activity.this).selectDeviceByMac(mDeviceMqttConfig.staMac);
                                            String mqttConfigStr = new Gson().toJson(mDeviceMqttConfig, MQTTConfig.class);
                                            if (mokoDevice == null) {
                                                mokoDevice = new MokoDevice();
                                                mokoDevice.name = mDeviceMqttConfig.deviceName;
                                                mokoDevice.mac = mDeviceMqttConfig.staMac;
                                                mokoDevice.mqttInfo = mqttConfigStr;
                                                mokoDevice.topicSubscribe = mDeviceMqttConfig.topicSubscribe;
                                                mokoDevice.topicPublish = mDeviceMqttConfig.topicPublish;
                                                mokoDevice.lwtEnable = mDeviceMqttConfig.lwtEnable ? 1 : 0;
                                                mokoDevice.lwtTopic = mDeviceMqttConfig.lwtTopic;
                                                mokoDevice.deviceType = mSelectedDeviceType;
                                                DBTools02.getInstance(DeviceConfig02Activity.this).insertDevice(mokoDevice);
                                            } else {
                                                mokoDevice.name = mDeviceMqttConfig.deviceName;
                                                mokoDevice.mac = mDeviceMqttConfig.staMac;
                                                mokoDevice.mqttInfo = mqttConfigStr;
                                                mokoDevice.topicSubscribe = mDeviceMqttConfig.topicSubscribe;
                                                mokoDevice.topicPublish = mDeviceMqttConfig.topicPublish;
                                                mokoDevice.lwtEnable = mDeviceMqttConfig.lwtEnable ? 1 : 0;
                                                mokoDevice.lwtTopic = mDeviceMqttConfig.lwtTopic;
                                                mokoDevice.deviceType = mSelectedDeviceType;
                                                DBTools02.getInstance(DeviceConfig02Activity.this).updateDevice(mokoDevice);
                                            }
                                            modifyIntent.putExtra(AppConstants.EXTRA_KEY_MAC, mokoDevice.mac);
                                        }
                                        MokoSupport.getInstance().disConnectBle();
                                        startActivity(modifyIntent);
                                        return;
                                    }
                                    isSettingSuccess = true;
                                    showConnMqttDialog();
                                    subscribeTopic();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTMessageArrivedEvent(MQTTMessageArrivedEvent event) {
        final String topic = event.getTopic();
        final String message = event.getMessage();
        if (TextUtils.isEmpty(topic) || isDeviceConnectSuccess) {
            return;
        }
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
        if (msg_id != MQTTConstants.NOTIFY_MSG_ID_NETWORKING_STATUS) return;
        Type type = new TypeToken<MsgNotify<Object>>() {
        }.getType();
        MsgNotify<Object> msgNotify = new Gson().fromJson(message, type);
        final String mac = msgNotify.device_info.mac;
        if (mDeviceMqttConfig == null || !mDeviceMqttConfig.staMac.equals(mac)) {
            return;
        }
        if (donutProgress == null) return;
        if (!isDeviceConnectSuccess) {
            isDeviceConnectSuccess = true;
            donutProgress.setProgress(100);
            donutProgress.setText(100 + "%");
            // 关闭进度条弹框，保存数据，跳转修改设备名称页面
            mBind.tvName.postDelayed(() -> {
                dismissConnMqttDialog();
                MokoDevice MokoDevice = DBTools02.getInstance(getApplicationContext()).selectDeviceByMac(mDeviceMqttConfig.staMac);
                String mqttConfigStr = new Gson().toJson(mDeviceMqttConfig, MQTTConfig.class);
                if (MokoDevice == null) {
                    MokoDevice = new MokoDevice();
                    MokoDevice.name = mDeviceMqttConfig.deviceName;
                    MokoDevice.mac = mDeviceMqttConfig.staMac;
                    MokoDevice.mqttInfo = mqttConfigStr;
                    MokoDevice.topicSubscribe = mDeviceMqttConfig.topicSubscribe;
                    MokoDevice.topicPublish = mDeviceMqttConfig.topicPublish;
                    MokoDevice.lwtEnable = mDeviceMqttConfig.lwtEnable ? 1 : 0;
                    MokoDevice.lwtTopic = mDeviceMqttConfig.lwtTopic;
                    MokoDevice.deviceType = mSelectedDeviceType;
                    DBTools02.getInstance(getApplicationContext()).insertDevice(MokoDevice);
                } else {
                    MokoDevice.name = mDeviceMqttConfig.deviceName;
                    MokoDevice.mac = mDeviceMqttConfig.staMac;
                    MokoDevice.mqttInfo = mqttConfigStr;
                    MokoDevice.topicSubscribe = mDeviceMqttConfig.topicSubscribe;
                    MokoDevice.topicPublish = mDeviceMqttConfig.topicPublish;
                    MokoDevice.lwtEnable = mDeviceMqttConfig.lwtEnable ? 1 : 0;
                    MokoDevice.lwtTopic = mDeviceMqttConfig.lwtTopic;
                    MokoDevice.deviceType = mSelectedDeviceType;
                    DBTools02.getInstance(getApplicationContext()).updateDevice(MokoDevice);
                }
                Intent modifyIntent = new Intent(this, ModifyName02Activity.class);
                modifyIntent.putExtra(AppConstants.EXTRA_KEY_DEVICE, MokoDevice);
                startActivity(modifyIntent);
            }, 1000);
        }
    }

    public void onBack(View view) {
        if (isWindowLocked()) return;
        back();
    }

    @Override
    public void onBackPressed() {
        if (isWindowLocked()) return;
        back();
    }

    private void back() {
        MokoSupport.getInstance().disConnectBle();
    }

    public void onAdvertiseIBeacon(View view){
        if (isWindowLocked()) return;
        startActivity(new Intent(this, AdvertiseIBeacon02Activity.class));
    }

    public void onAdvSettings(View view) {
        if (isWindowLocked()) return;
        Intent intent = new Intent(this, AdvSettingsActivity.class);
        startActivity(intent);
    }

    public void onMeteringSettings(View view){
        if (isWindowLocked()) return;
        startActivity(new Intent(this, MeteringSettings02Activity.class));
    }

    public void onWifiSettings(View view) {
        if (isWindowLocked()) return;
        Intent intent = new Intent(this, WifiSettings02Activity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_BEACON_TYPE, mSelectedDeviceType);
        intent.putExtra(AppConstants.EXTRA_KEY_FIRST_CONFIG, mIsFirstConfig);
        startWIFISettings.launch(intent);
    }

    public void onMqttSettings(View view) {
        if (isWindowLocked()) return;
        Intent intent = new Intent(this, MqttSettings02Activity.class);
        startMQTTSettings.launch(intent);
    }

    public void onNtpSettings(View view) {
        if (isWindowLocked()) return;
        Intent intent = new Intent(this, NtpSettings02Activity.class);
        startActivity(intent);
    }

    public void onScannerFilter(View view) {
        if (isWindowLocked()) return;
        Intent intent = new Intent(this, ScannerFilter02Activity.class);
        startActivity(intent);
    }



    public void onScanAndUpload(View view) {
        if (isWindowLocked()) return;
        Intent intent = new Intent(this, ScanAndUploadActivity.class);
        startActivity(intent);
    }

    public void onDeviceInfo(View view) {
        if (isWindowLocked()) return;
        Intent intent = new Intent(this, DeviceInformation02Activity.class);
        intent.putExtra(AppConstants.EXTRA_KEY_SELECTED_DEVICE_TYPE, mSelectedDeviceType);
        startActivity(intent);
    }

    public void onConnect(View view) {
        if (isWindowLocked()) return;
        if (!mIsFirstConfig) {
            AlertMessageDialog dialog = new AlertMessageDialog();
            dialog.setMessage("New settings are applying to device, device is connecting to network and MQTT");
            dialog.setConfirm("OK");
            dialog.setCancelGone();
            dialog.setOnAlertConfirmListener(() -> {
                showLoadingProgressDialog();
                MokoSupport.getInstance().sendOrder(OrderTaskAssembler.exitConfigMode());
            });
            dialog.show(getSupportFragmentManager());
            return;
        }
        if (!mIsWIFIConfigFinished || !mIsMQTTConfigFinished) {
            ToastUtils.showToast(this, "Please configure WIFI and MQTT settings first!");
            return;
        }
        showLoadingProgressDialog();
        MokoSupport.getInstance().sendOrder(OrderTaskAssembler.exitConfigMode());
    }

    private final ActivityResultLauncher<Intent> startWIFISettings = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK)
            mIsWIFIConfigFinished = true;
    });
    private final ActivityResultLauncher<Intent> startMQTTSettings = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK) {
            mIsMQTTConfigFinished = true;
            mDeviceMqttConfig = (MQTTConfig) result.getData().getSerializableExtra(AppConstants.EXTRA_KEY_MQTT_CONFIG_DEVICE);
        }
    });
    private int progress;

    private void showConnMqttDialog() {
        isDeviceConnectSuccess = false;
        View view = LayoutInflater.from(this).inflate(R.layout.mqtt_conn_content, null);
        donutProgress = view.findViewById(R.id.dp_progress);
        mqttConnDialog = new CustomDialog.Builder(this)
                .setContentView(view)
                .create();
        mqttConnDialog.setCancelable(false);
        mqttConnDialog.show();
        new Thread(() -> {
            progress = 0;
            while (progress <= 100 && !isDeviceConnectSuccess) {
                runOnUiThread(() -> {
                    donutProgress.setProgress(progress);
                    donutProgress.setText(progress + "%");
                });
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                progress++;
            }
        }).start();
        mHandler.postDelayed(() -> {
            if (!isDeviceConnectSuccess) {
                isDeviceConnectSuccess = true;
                isSettingSuccess = false;
                dismissConnMqttDialog();
                ToastUtils.showToast(DeviceConfig02Activity.this, getString(R.string.mqtt_connecting_timeout));
                finish();
            }
        }, 90 * 1000);
    }

    private void dismissConnMqttDialog() {
        if (mqttConnDialog != null && !isFinishing() && mqttConnDialog.isShowing()) {
            isDeviceConnectSuccess = true;
            isSettingSuccess = false;
            mqttConnDialog.dismiss();
            mHandler.removeMessages(0);
        }
    }

    private void subscribeTopic() {
        // 订阅
        try {
            if (TextUtils.isEmpty(mAppMqttConfig.topicSubscribe)) {
                MQTTSupport.getInstance().subscribe(mDeviceMqttConfig.topicPublish, mAppMqttConfig.qos);
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
        // 订阅遗愿主题
        try {
            if (mDeviceMqttConfig.lwtEnable
                    && !TextUtils.isEmpty(mDeviceMqttConfig.lwtTopic)
                    && !mDeviceMqttConfig.lwtTopic.equals(mDeviceMqttConfig.topicPublish)) {
                MQTTSupport.getInstance().subscribe(mDeviceMqttConfig.lwtTopic, mAppMqttConfig.qos);
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
