package com.moko.support.remotegw02.entity;

public class MsgConfigResult<T> {
    public int msg_id;
    public MsgDeviceInfo device_info;
    public int result_code;
    public String result_msg;
    public T data;
}