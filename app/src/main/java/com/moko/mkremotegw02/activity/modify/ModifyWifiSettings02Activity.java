package com.moko.mkremotegw02.activity.modify;

import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
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
import com.moko.lib.mqtt.event.DeviceOnlineEvent;
import com.moko.lib.mqtt.event.MQTTMessageArrivedEvent;
import com.moko.lib.scannerui.dialog.BottomDialog;
import com.moko.lib.scannerui.utils.ToastUtils;
import com.moko.mkremotegw02.AppConstants;
import com.moko.mkremotegw02.R;
import com.moko.mkremotegw02.base.BaseActivity;
import com.moko.mkremotegw02.databinding.ActivityModifyWifiSettings02Binding;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ModifyWifiSettings02Activity extends BaseActivity<ActivityModifyWifiSettings02Binding> {
    private final String FILTER_ASCII = "[ -~]*";
    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;
    public Handler mHandler;
    private final String[] mSecurityValues = {"Personal", "Enterprise"};
    private int mSecuritySelected;
    private final String[] mEAPTypeValues = {"PEAP-MSCHAPV2", "TTLS-MSCHAPV2", "TLS"};
    private int mEAPTypeSelected;
    private Pattern pattern;
    private boolean wifiDhcpEnable;
    private String wifiIp;
    private String wifiMask;
    private String wifiGateway;
    private String wifiDns;

    @Override
    protected void onCreate() {
        mBind.cbVerifyServer.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mSecuritySelected != 0 && mEAPTypeSelected != 2)
                mBind.clCa.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });
        String IP_REGEX = "((25[0-5]|2[0-4]\\d|((1\\d{2})|([1-9]?\\d)))\\.){3}(25[0-5]|2[0-4]\\d|((1\\d{2})|([1-9]?\\d)))*";
        pattern = Pattern.compile(IP_REGEX);
        InputFilter filter = (source, start, end, dest, dstart, dend) -> {
            if (!(source + "").matches(FILTER_ASCII)) {
                return "";
            }
            return null;
        };
        mBind.etUsername.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32), filter});
        mBind.etPassword.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), filter});
        mBind.etEapPassword.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), filter});
        mBind.etSsid.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32), filter});
        mBind.etDomainId.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), filter});
        mBind.etCaFileUrl.setFilters(new InputFilter[]{new InputFilter.LengthFilter(256), filter});
        mBind.etCertFileUrl.setFilters(new InputFilter[]{new InputFilter.LengthFilter(256), filter});
        mBind.etKeyFileUrl.setFilters(new InputFilter[]{new InputFilter.LengthFilter(256), filter});

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
        getWifiSettings();
        mBind.layoutIp.imgDhcp.setOnClickListener(v -> {
            wifiDhcpEnable = !wifiDhcpEnable;
            setDhcpEnable(wifiDhcpEnable);
        });
    }

    @Override
    protected ActivityModifyWifiSettings02Binding getViewBinding() {
        return ActivityModifyWifiSettings02Binding.inflate(getLayoutInflater());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTMessageArrivedEvent(MQTTMessageArrivedEvent event) {
        final String message = event.getMessage();
        if (TextUtils.isEmpty(message)) return;
        int msg_id;
        try {
            JsonObject object = new Gson().fromJson(message, JsonObject.class);
            JsonElement element = object.get("msg_id");
            msg_id = element.getAsInt();
        } catch (Exception e) {
            return;
        }

        if (msg_id == MQTTConstants.READ_MSG_ID_WIFI_SETTINGS) {
            Type type = new TypeToken<MsgReadResult<JsonObject>>() {
            }.getType();
            MsgReadResult<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            mSecuritySelected = result.data.get("security_type").getAsInt();
            mBind.etSsid.setText(result.data.get("ssid").getAsString());
            mBind.etPassword.setText(result.data.get("passwd").getAsString());
            mBind.etDomainId.setText(result.data.get("eap_id").getAsString());
            mBind.tvSecurity.setText(mSecurityValues[mSecuritySelected]);
            mBind.clEapType.setVisibility(mSecuritySelected != 0 ? View.VISIBLE : View.GONE);
            mBind.clPassword.setVisibility(mSecuritySelected != 0 ? View.GONE : View.VISIBLE);
            mEAPTypeSelected = result.data.get("eap_type").getAsInt();
            mBind.tvEapType.setText(mEAPTypeValues[mEAPTypeSelected]);
            if (mSecuritySelected != 0) {
                mBind.clUsername.setVisibility(mEAPTypeSelected == 2 ? View.GONE : View.VISIBLE);
                mBind.etUsername.setText(result.data.get("eap_username").getAsString());
                mBind.clEapPassword.setVisibility(mEAPTypeSelected == 2 ? View.GONE : View.VISIBLE);
                mBind.etEapPassword.setText(result.data.get("eap_passwd").getAsString());
                mBind.cbVerifyServer.setVisibility(mEAPTypeSelected == 2 ? View.INVISIBLE : View.VISIBLE);
                mBind.cbVerifyServer.setChecked(result.data.get("eap_verify_server").getAsInt() == 1);
                if (mEAPTypeSelected != 2) {
                    mBind.clCa.setVisibility(mBind.cbVerifyServer.isChecked() ? View.VISIBLE : View.GONE);
                } else {
                    mBind.clCa.setVisibility(View.VISIBLE);
                }
                mBind.clDomainId.setVisibility(mEAPTypeSelected == 2 ? View.VISIBLE : View.GONE);
                mBind.clCert.setVisibility(mEAPTypeSelected == 2 ? View.VISIBLE : View.GONE);
                mBind.clKey.setVisibility(mEAPTypeSelected == 2 ? View.VISIBLE : View.GONE);
            }
            getNetworkSettings();
        }
        if (msg_id == MQTTConstants.READ_MSG_ID_NETWORK_SETTINGS) {
            Type type = new TypeToken<MsgReadResult<JsonObject>>() {
            }.getType();
            MsgReadResult<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            wifiDhcpEnable = result.data.get("dhcp_en").getAsInt() == 1;
            wifiIp = result.data.get("ip").getAsString();
            wifiMask = result.data.get("netmask").getAsString();
            wifiGateway = result.data.get("gw").getAsString();
            wifiDns = result.data.get("dns").getAsString();
            setDhcpEnable(wifiDhcpEnable);
            setIpInfo();
        }
        if (msg_id == MQTTConstants.READ_MSG_ID_DEVICE_STATUS) {
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            int status = result.data.get("status").getAsInt();
            if (status == 1) {
                ToastUtils.showToast(this, "Device is OTA, please wait");
                return;
            }
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(this, "Set up failed");
            }, 30 * 1000);
            showLoadingProgressDialog();
            setWifiSettings();
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_WIFI_SETTINGS) {
            Type type = new TypeToken<MsgConfigResult<?>>() {
            }.getType();
            MsgConfigResult<?> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            if (result.result_code != 0) return;
            setIpConfig();
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_NETWORK_SETTINGS) {
            Type type = new TypeToken<MsgConfigResult<?>>() {
            }.getType();
            MsgConfigResult<?> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            if (result.result_code == 0) {
                if (mSecuritySelected == 0) {
                    ToastUtils.showToast(this, "Set up succeed");
                    return;
                }
                String caFileUrl = mBind.etCaFileUrl.getText().toString();
                String certFileUrl = mBind.etCertFileUrl.getText().toString();
                String keyFileUrl = mBind.etKeyFileUrl.getText().toString();
                // 若EAP类型不是TLS且CA证书为空，不发送证书更新指令
                if (mEAPTypeSelected != 2
                        && TextUtils.isEmpty(caFileUrl))
                    return;
                // 若EAP类型是TLS且所有证书都为空，不发送证书更新指令
                if (mEAPTypeSelected == 2
                        && TextUtils.isEmpty(caFileUrl)
                        && TextUtils.isEmpty(certFileUrl)
                        && TextUtils.isEmpty(keyFileUrl))
                    return;
                XLog.i("升级Wifi证书");
                mHandler.postDelayed(() -> {
                    dismissLoadingProgressDialog();
                    finish();
                }, 60 * 1000);
                showLoadingProgressDialog();
                setWifiCertFile();
            } else {
                ToastUtils.showToast(this, "Set up failed");
            }
        }
        if (msg_id == MQTTConstants.NOTIFY_MSG_ID_WIFI_CERT_RESULT) {
            Type type = new TypeToken<MsgNotify<JsonObject>>() {
            }.getType();
            MsgNotify<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            int resultCode = result.data.get("result_code").getAsInt();
            if (resultCode == 1) {
                ToastUtils.showToast(this, R.string.update_success);
            } else {
                ToastUtils.showToast(this, R.string.update_failed);
            }
        }
    }

    private void setIpConfig() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_NETWORK_SETTINGS;
        JsonObject jsonObject = new JsonObject();
        int enable = wifiDhcpEnable ? 1 : 0;
        jsonObject.addProperty("dhcp_en", enable);
        jsonObject.addProperty("ip", mBind.layoutIp.etIp.getText().toString());
        jsonObject.addProperty("netmask", mBind.layoutIp.etMask.getText().toString());
        jsonObject.addProperty("gw", mBind.layoutIp.etGateway.getText().toString());
        jsonObject.addProperty("dns", mBind.layoutIp.etDns.getText().toString());
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            XLog.e(e);
        }
    }

    private void getNetworkSettings() {
        int msgId = MQTTConstants.READ_MSG_ID_NETWORK_SETTINGS;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            XLog.e(e);
        }
    }

    private void setDhcpEnable(boolean enable) {
        mBind.layoutIp.imgDhcp.setImageResource(enable ? R.drawable.ic_checkbox_open : R.drawable.ic_checkbox_close);
        mBind.layoutIp.clIp.setVisibility(enable ? View.GONE : View.VISIBLE);
    }

    private void setIpInfo() {
        mBind.layoutIp.etIp.setText(wifiIp);
        mBind.layoutIp.etMask.setText(wifiMask);
        mBind.layoutIp.etGateway.setText(wifiGateway);
        mBind.layoutIp.etDns.setText(wifiDns);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceOnlineEvent(DeviceOnlineEvent event) {
        super.offline(event, mMokoDevice.mac);
    }

    public void onBack(View view) {
        finish();
    }

    private void setWifiCertFile() {
        String caFileUrl = mBind.etCaFileUrl.getText().toString();
        String certFileUrl = mBind.etCertFileUrl.getText().toString();
        String keyFileUrl = mBind.etKeyFileUrl.getText().toString();
        int msgId = MQTTConstants.CONFIG_MSG_ID_WIFI_CERT_FILE;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("ca_url", caFileUrl);
        jsonObject.addProperty("client_cert_url", certFileUrl);
        jsonObject.addProperty("client_key_url", keyFileUrl);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            XLog.e(e);
        }
    }

    private void setWifiSettings() {
        String ssid = mBind.etSsid.getText().toString();
        String username = mBind.etUsername.getText().toString();
        String password = mBind.etPassword.getText().toString();
        String eapPassword = mBind.etEapPassword.getText().toString();
        String domainId = mBind.etDomainId.getText().toString();
        int msgId = MQTTConstants.CONFIG_MSG_ID_WIFI_SETTINGS;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("security_type", mSecuritySelected);
        jsonObject.addProperty("ssid", ssid);
        jsonObject.addProperty("passwd", mSecuritySelected == 0 ? password : "");
        jsonObject.addProperty("eap_type", mEAPTypeSelected);
        jsonObject.addProperty("eap_id", mEAPTypeSelected == 2 ? domainId : "");
        jsonObject.addProperty("eap_username", mSecuritySelected != 0 ? username : "");
        jsonObject.addProperty("eap_passwd", mSecuritySelected != 0 ? eapPassword : "");
        jsonObject.addProperty("eap_verify_server", mBind.cbVerifyServer.isChecked() ? 1 : 0);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            XLog.e(e);
        }
    }

    private void getWifiSettings() {
        int msgId = MQTTConstants.READ_MSG_ID_WIFI_SETTINGS;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            XLog.e(e);
        }
    }

    public void onSelectSecurity(View view) {
        if (isWindowLocked()) return;
        BottomDialog dialog = new BottomDialog();
        dialog.setDatas(new ArrayList<>(Arrays.asList(mSecurityValues)), mSecuritySelected);
        dialog.setListener(value -> {
            mSecuritySelected = value;
            mBind.tvSecurity.setText(mSecurityValues[value]);
            mBind.clEapType.setVisibility(mSecuritySelected != 0 ? View.VISIBLE : View.GONE);
            mBind.clPassword.setVisibility(mSecuritySelected != 0 ? View.GONE : View.VISIBLE);
            if (mSecuritySelected == 0) {
                mBind.clCa.setVisibility(View.GONE);
                mBind.clUsername.setVisibility(View.GONE);
                mBind.clEapPassword.setVisibility(View.GONE);
                mBind.cbVerifyServer.setVisibility(View.GONE);
                mBind.clDomainId.setVisibility(View.GONE);
                mBind.clCert.setVisibility(View.GONE);
                mBind.clKey.setVisibility(View.GONE);
            } else {
                if (mEAPTypeSelected != 2) {
                    mBind.clCa.setVisibility(mBind.cbVerifyServer.isChecked() ? View.VISIBLE : View.GONE);
                } else {
                    mBind.clCa.setVisibility(View.VISIBLE);
                }
                mBind.clUsername.setVisibility(mEAPTypeSelected == 2 ? View.GONE : View.VISIBLE);
                mBind.clEapPassword.setVisibility(mEAPTypeSelected == 2 ? View.GONE : View.VISIBLE);
                mBind.cbVerifyServer.setVisibility(mEAPTypeSelected == 2 ? View.INVISIBLE : View.VISIBLE);
                mBind.clDomainId.setVisibility(mEAPTypeSelected == 2 ? View.VISIBLE : View.GONE);
                mBind.clCert.setVisibility(mEAPTypeSelected == 2 ? View.VISIBLE : View.GONE);
                mBind.clKey.setVisibility(mEAPTypeSelected == 2 ? View.VISIBLE : View.GONE);
            }
        });
        dialog.show(getSupportFragmentManager());
    }

    public void onSelectEAPType(View view) {
        if (isWindowLocked()) return;
        BottomDialog dialog = new BottomDialog();
        dialog.setDatas(new ArrayList<>(Arrays.asList(mEAPTypeValues)), mEAPTypeSelected);
        dialog.setListener(value -> {
            mEAPTypeSelected = value;
            mBind.tvEapType.setText(mEAPTypeValues[value]);
            mBind.clUsername.setVisibility(mEAPTypeSelected == 2 ? View.GONE : View.VISIBLE);
            mBind.clEapPassword.setVisibility(mEAPTypeSelected == 2 ? View.GONE : View.VISIBLE);
            mBind.cbVerifyServer.setVisibility(mEAPTypeSelected == 2 ? View.INVISIBLE : View.VISIBLE);
            mBind.clDomainId.setVisibility(mEAPTypeSelected == 2 ? View.VISIBLE : View.GONE);
            if (mEAPTypeSelected != 2) {
                mBind.clCa.setVisibility(mBind.cbVerifyServer.isChecked() ? View.VISIBLE : View.GONE);
            } else {
                mBind.clCa.setVisibility(View.VISIBLE);
            }
            mBind.clCert.setVisibility(mEAPTypeSelected == 2 ? View.VISIBLE : View.GONE);
            mBind.clKey.setVisibility(mEAPTypeSelected == 2 ? View.VISIBLE : View.GONE);
        });
        dialog.show(getSupportFragmentManager());
    }

    public void onSave(View view) {
        if (isWindowLocked()) return;
        if (!isParaError()) {
            saveParams();
        } else {
            ToastUtils.showToast(this, "Para Error");
        }
    }

    private boolean isParaError() {
        String ssid = mBind.etSsid.getText().toString();
        if (TextUtils.isEmpty(ssid)) return true;
        if (!wifiDhcpEnable) {
            //检查ip地址是否合法
            String ip = mBind.layoutIp.etIp.getText().toString();
            String mask = mBind.layoutIp.etMask.getText().toString();
            String gateway = mBind.layoutIp.etGateway.getText().toString();
            String dns = mBind.layoutIp.etDns.getText().toString();
            Matcher matcherIp = pattern.matcher(ip);
            Matcher matcherMask = pattern.matcher(mask);
            Matcher matcherGateway = pattern.matcher(gateway);
            Matcher matcherDns = pattern.matcher(dns);
            if (!matcherIp.matches()
                    || !matcherMask.matches()
                    || !matcherGateway.matches()
                    || !matcherDns.matches())
                return true;
            wifiIp = ip;
            wifiMask = mask;
            wifiGateway = gateway;
            wifiDns = dns;
        }
        return false;
    }

    private void saveParams() {
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        XLog.i("查询设备当前状态");
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 50 * 1000);
        showLoadingProgressDialog();
        getDeviceStatus();
    }

    private void getDeviceStatus() {
        int msgId = MQTTConstants.READ_MSG_ID_DEVICE_STATUS;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            XLog.e(e);
        }
    }
}
