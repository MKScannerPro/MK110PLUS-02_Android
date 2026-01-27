package com.moko.mkremotegw02.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.moko.mkremotegw02.R;
import com.moko.mkremotegw02.entity.WifiInfo;

public class NearbyWifiAdapter extends BaseQuickAdapter<WifiInfo, BaseViewHolder> {
    public NearbyWifiAdapter() {
        super(R.layout.item_wifi_ssid02);
    }

    @Override
    protected void convert(BaseViewHolder helper, WifiInfo item) {
        helper.setText(R.id.tv_ssid, item.ssid);
        helper.setText(R.id.tv_bssid, item.bssid);
        helper.setText(R.id.tv_rssi, String.valueOf(item.rssi));
    }
}
