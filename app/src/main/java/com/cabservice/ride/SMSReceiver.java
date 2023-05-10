package com.cabservice.ride;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;

public class SMSReceiver extends BroadcastReceiver {

    private OTPReceiveListener otpListener;
    public void setOTPListener(OTPReceiveListener otpListener) {
        this.otpListener = otpListener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (SmsRetriever.SMS_RETRIEVED_ACTION.equals(intent.getAction())) {
            Bundle extras = intent.getExtras();
            Status status = (Status) extras.get(SmsRetriever.EXTRA_STATUS);
            switch (status.getStatusCode()) {
                case CommonStatusCodes.SUCCESS:
                    String message = (String) extras.get(SmsRetriever.EXTRA_SMS_MESSAGE);
                    if (otpListener != null) {
                        otpListener.onOTPReceived(message);
                    }
                    break;
                case CommonStatusCodes.API_NOT_CONNECTED:
                    if (otpListener != null) {
                        otpListener.onOTPReceivedError("API NOT CONNECTED");
                    }
                    break;
                case CommonStatusCodes.NETWORK_ERROR:
                    if (otpListener != null) {
                        otpListener.onOTPReceivedError("NETWORK ERROR");
                    }
                    break;
                case CommonStatusCodes.ERROR:
                    if (otpListener != null) {
                        otpListener.onOTPReceivedError("SOME THING WENT WRONG");
                    }
                    break;
            }
        }
    }

    public interface OTPReceiveListener {

        void onOTPReceived(String otp);
        void onOTPReceivedError(String error);
    }
}