package com.moko.mkremotegw02.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.moko.mkremotegw02.R;
import com.moko.support.remotegw03.entity.DeviceInfo;

public class DeviceInfo02Adapter extends BaseQuickAdapter<DeviceInfo, BaseViewHolder> {
    public DeviceInfo02Adapter() {
        super(R.layout.item_devices);
    }

    @Override
    protected void convert(BaseViewHolder helper, DeviceInfo item) {
        helper.setText(R.id.tv_device_name, item.name);
        helper.setText(R.id.tv_device_rssi, String.valueOf(item.rssi));
    }
}
