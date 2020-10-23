package com.opalkelly.frontpanelcounters;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import com.opalkelly.frontpanel.*;
import com.opalkelly.exception.FrontPanelException;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    private static final String ACTION_USB_PERMISSION = "com.opalkelly.frontpanelcounters.usb_permission";
    private static final int VENDOR_ID_OPALKELLY = 0x151f;

    /**
     * The Indices of the pages fragments.
     */
    private static final int MAIN_PAGE_INDEX = 0;
    private static final int COUNTERS_PAGE_INDEX = 1;

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        System.loadLibrary("okjFrontPanel");

        // Create the adapter that will return a fragment for each of the two
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        mDeviceAvailable = false;

        mActionUSBPermissionReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                    UsbManager usbManager = (UsbManager)context.getSystemService(Context.USB_SERVICE);
                    UsbDeviceConnection conn = usbManager.openDevice(device);
                    if (conn == null) {
                        Toast.makeText(context, "Opening the device failed", Toast.LENGTH_LONG)
                                .show();
                        return;
                    }

                    mDeviceAvailable = true;

                    int fd = conn.getFileDescriptor();
                    Log.d("FrontPanel", String.format(
                            "Permission granted for accessing %s (fd=%d)",
                            device.getDeviceName(),
                            fd
                    ));

                    mFrontPanel = new okCFrontPanel(fd);

                    Log.d("FrontPanel", String.format(
                            "Device serial number: %s; device ID: %s",
                            mFrontPanel.GetSerialNumber(),
                            mFrontPanel.GetDeviceID()
                    ));

                    if (mMainFragment != null) {
                        mMainFragment.onDeviceAvailable();
                    }
                } else {
                    mDeviceAvailable = false;

                    Log.d("FrontPanel", "Permission denied for device " + device);

                    if (mMainFragment != null) {
                        mMainFragment.onDeviceUnavailable();
                    }
                }
            }
        };
        registerReceiver(mActionUSBPermissionReceiver, new IntentFilter(ACTION_USB_PERMISSION));

        mActionUSBDeviceAttachedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                if (device.getVendorId() != VENDOR_ID_OPALKELLY)
                    return;

                Log.d("FrontPanel", "Device attached: " + device);
                Toast.makeText(context, "FrontPanel device attached.", Toast.LENGTH_LONG)
                        .show();

                UsbManager usbManager = (UsbManager)context.getSystemService(Context.USB_SERVICE);
                usbManager.requestPermission(device, makeUSBPermissionPendingIntent(context));
            }
        };
        registerReceiver(mActionUSBDeviceAttachedReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED));

        mActionUSBDeviceDetachedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device.getVendorId() != VENDOR_ID_OPALKELLY)
                    return;

                mDeviceAvailable = false;

                Log.d("FrontPanel", "Device detached: " + device);
                Toast.makeText(context, "FrontPanel device detached.", Toast.LENGTH_LONG)
                        .show();

                doReset();
            }
        };
        registerReceiver(mActionUSBDeviceDetachedReceiver, new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED));

        doRefresh();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mActionUSBPermissionReceiver);
        unregisterReceiver(mActionUSBDeviceAttachedReceiver);
        unregisterReceiver(mActionUSBDeviceDetachedReceiver);

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (mViewPager.getCurrentItem() == COUNTERS_PAGE_INDEX) {
            mViewPager.setCurrentItem(MAIN_PAGE_INDEX, true);
        } else {
            super.onBackPressed();
        }
    }

    public void onException(Throwable e)
    {
        mMainFragment.mTextError.setVisibility(View.VISIBLE);
        mMainFragment.mTextError.setText("Error: " + e.getMessage());
        Log.e("FrontPanel", "Exception: ", e);
        mViewPager.setCurrentItem(MAIN_PAGE_INDEX, true);
    }

    private void doReset() {
        Log.d("FrontPanel", "Resetting.");

        mFrontPanel = null;

        mViewPager.setCurrentItem(MAIN_PAGE_INDEX, true);

        if (mMainFragment != null) {
            mMainFragment.onDeviceUnavailable();
        }
    }

    private void doRefresh() {
        Log.d("FrontPanel", "Refreshing.");

        doReset();

        final PendingIntent usbPermIntent = makeUSBPermissionPendingIntent(this);

        UsbManager usbManager = (UsbManager)getSystemService(Context.USB_SERVICE);

        HashMap<String, UsbDevice> stringDeviceMap = usbManager.getDeviceList();
        Collection<UsbDevice> usbDevices = stringDeviceMap.values();
        Iterator<UsbDevice> usbDeviceIter = usbDevices.iterator();
        while (usbDeviceIter.hasNext()) {
            UsbDevice device = usbDeviceIter.next();
            if (device.getVendorId() == VENDOR_ID_OPALKELLY)
                usbManager.requestPermission(device, usbPermIntent);
        }
    }

    private PendingIntent makeUSBPermissionPendingIntent(Context context) {
        return PendingIntent.getBroadcast(
                context,
                0,
                new Intent(ACTION_USB_PERMISSION),
                0
        );
    }

    private BroadcastReceiver mActionUSBPermissionReceiver;
    private BroadcastReceiver mActionUSBDeviceAttachedReceiver;
    private BroadcastReceiver mActionUSBDeviceDetachedReceiver;

    private boolean mDeviceAvailable;

    private okCFrontPanel mFrontPanel;

    private MainFragment mMainFragment;

    /**
     * A fragment containing the main page.
     */
    public static class MainFragment extends Fragment {
        public MainFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            final MainActivity activity = (MainActivity) getActivity();

            View rootView = inflater.inflate(R.layout.fragment_main, container, false);

            mTextStatus = rootView.findViewById(R.id.labelStatus);
            mTextDeviceSerial = rootView.findViewById(R.id.textDeviceSerial);
            mTextDeviceID = rootView.findViewById(R.id.textDeviceID);

            mTextError = rootView.findViewById(R.id.textError);

            mButtonStartSample = rootView.findViewById(R.id.buttonStartSample);
            mButtonStartSample.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    mButtonStartSample.setEnabled(false);

                    AsyncTask.execute(new Runnable() {
                        @Override
                        public void run() {
                            Throwable throwable = null;
                            try {
                                UploadFirmware();
                            } catch (Throwable e) {
                                throwable = e;
                            }

                            final Throwable finalThrowable = throwable;
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (finalThrowable != null) {
                                        activity.onException(finalThrowable);
                                    } else {
                                        activity.mViewPager.setCurrentItem(COUNTERS_PAGE_INDEX, true);
                                    }

                                    mButtonStartSample.setEnabled(true);
                                }
                            });
                        }
                    });
                }
            });

            mProgressBar = rootView.findViewById(R.id.progressBar);

            activity.mMainFragment = this;

            if (activity.mDeviceAvailable) {
                onDeviceAvailable();
            } else {
                onDeviceUnavailable();
            }

            return rootView;
        }

        @Override
        public void onDestroyView() {
            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                activity.mMainFragment = null;
            }

            super.onDestroyView();
        }

        public void onDeviceAvailable() {
            mTextStatus.setText("Device connected");

            MainActivity activity = (MainActivity) getActivity();
            if (activity != null) {
                activity.mMainFragment = this;

                mTextDeviceSerial.setText(activity.mFrontPanel.GetSerialNumber());
                mTextDeviceID.setText(activity.mFrontPanel.GetDeviceID());

                mButtonStartSample.setEnabled(true);
            }
        }

        public void onDeviceUnavailable() {
            mTextStatus.setText("Please connect a FrontPanel device");
            mTextDeviceSerial.setText("N/A");
            mTextDeviceID.setText("N/A");

            mTextError.setVisibility(View.INVISIBLE);

            mButtonStartSample.setEnabled(false);
        }

        private void UploadFirmware() throws FrontPanelException {
            final MainActivity activity = (MainActivity) getActivity();

            okTDeviceInfo deviceInfo = new okTDeviceInfo();
            okCFrontPanel.ErrorCode errorCode = activity.mFrontPanel.GetDeviceInfo(deviceInfo);
            if (okCFrontPanel.ErrorCode.NoError != errorCode) {
                throw new FrontPanelException(String.format(
                        "Failed to get device info: %s",
                        errorCode
                ));
            }

            String filename = String.format("counters-%s.bit", deviceInfo.getProductName().toLowerCase());

            Log.d("FrontPanel", "Successfully loaded firmware from" + filename);

            byte[] data;
            InputStream stream;
            try {
                stream = activity.getAssets().open(filename);
            } catch (IOException ioe) {
                throw new FrontPanelException(
                        "This device model is not supported by this application, sorry.",
                        ioe
                );
            }

            try {
                data = new byte[stream.available()];
                stream.read(data);
            } catch (IOException ioe) {
                throw new FrontPanelException(
                        "Failed to read firmware",
                        ioe
                );
            }

            final int maximum = data.length;
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onUploadFirmwareStart(maximum);
                }
            });

            errorCode = activity.mFrontPanel.ConfigureFPGAFromMemory(data,
                    new ProgressCallback() {
                        @Override
                        public void OnProgress(final int maximum, final int current) {
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mProgressBar.setProgress(current);
                                    if (current == maximum) {
                                        activity.mMainFragment.onUploadFirmwareFinish();
                                    }
                                }
                            });
                        }
                    }
            );
            if (okCFrontPanel.ErrorCode.NoError != errorCode) {
                throw new FrontPanelException(String.format(
                        "Failed to configure FPGA from memory: %s",
                        errorCode
                ));
            }
        }

        public void onUploadFirmwareStart(int progressBarMax) {
            mProgressBar.setProgress(0);
            mProgressBar.setMax(progressBarMax);
            mProgressBar.setVisibility(View.VISIBLE);
        }

        public void onUploadFirmwareFinish() {
            mProgressBar.setVisibility(View.INVISIBLE);
        }

        private TextView mTextStatus;
        private TextView mTextDeviceSerial;
        private TextView mTextDeviceID;
        private TextView mTextError;

        private Button mButtonStartSample;

        private ProgressBar mProgressBar;
    }

    /**
     * A fragment containing the counters page.
     */
    public static class CountersFragment extends Fragment {
        public CountersFragment() {
        }

        private class PushButtonTouchListener implements View.OnTouchListener {
            public PushButtonTouchListener(int endPointAddress, long bit) {
                mEndPointAddress = endPointAddress;
                mBit = bit;
            }

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                try {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        setAndUpdateWireInValue(mEndPointAddress, 0xffffffff, mBit);
                    } else if (event.getAction() == MotionEvent.ACTION_UP ||
                            event.getAction() == MotionEvent.ACTION_CANCEL) {
                        setAndUpdateWireInValue(mEndPointAddress, 0x00000000, mBit);
                    }
                } catch (Throwable e) {
                    ((MainActivity) getActivity()).onException(e);
                }

                return false;
            }

            private int mEndPointAddress;
            private long mBit;
        }

        private class TriggerButtonClickListener implements View.OnClickListener {
            public TriggerButtonClickListener(int endPointAddress, int bit) {
                mEndPointAddress = endPointAddress;
                mBit = bit;
            }

            @Override
            public void onClick(View v) {
                try {
                    activateTriggerIn(mEndPointAddress, mBit);
                } catch (Throwable e) {
                    ((MainActivity) getActivity()).onException(e);
                }
            }

            private int mEndPointAddress;
            private int mBit;
        }

        private class ToggleCheckChangeListener implements CompoundButton.OnCheckedChangeListener {
            public ToggleCheckChangeListener(int endPointAddress, int bit) {
                mEndPointAddress = endPointAddress;
                mBit = bit;
            }

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                try {
                    if (isChecked) {
                        setAndUpdateWireInValue(mEndPointAddress, 0xffffffff, mBit);
                    } else {
                        setAndUpdateWireInValue(mEndPointAddress, 0x00000000, mBit);
                    }
                } catch (Throwable e) {
                    ((MainActivity) getActivity()).onException(e);
                }
            }

            private int mEndPointAddress;
            private int mBit;
        }

        @Override
        public void onCreate (Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            mTimer = null;

            mWireOut0x20 = 0;
            mWireOut0x21 = 0;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_counters, container, false);

            rootView.findViewById(R.id.buttonCounter1Reset).
                    setOnTouchListener(new PushButtonTouchListener(0x00, 0));
            rootView.findViewById(R.id.buttonCounter1Disable).
                    setOnTouchListener(new PushButtonTouchListener(0x00, 1));

            mTextViewCounter1HighNibble = rootView.findViewById(R.id.textViewCounter1HighNibble);
            mTextViewCounter1LowNibble = rootView.findViewById(R.id.textViewCounter1LowNibble);

            mImageViewLedBits = new ImageView[8];
            mImageViewLedBits[0] = rootView.findViewById(R.id.imageViewLedBit0);
            mImageViewLedBits[1] = rootView.findViewById(R.id.imageViewLedBit1);
            mImageViewLedBits[2] = rootView.findViewById(R.id.imageViewLedBit2);
            mImageViewLedBits[3] = rootView.findViewById(R.id.imageViewLedBit3);
            mImageViewLedBits[4] = rootView.findViewById(R.id.imageViewLedBit4);
            mImageViewLedBits[5] = rootView.findViewById(R.id.imageViewLedBit5);
            mImageViewLedBits[6] = rootView.findViewById(R.id.imageViewLedBit6);
            mImageViewLedBits[7] = rootView.findViewById(R.id.imageViewLedBit7);

            rootView.findViewById(R.id.buttonCounter2Reset).
                    setOnClickListener(new TriggerButtonClickListener(0x40, 0));
            ((CheckBox)rootView.findViewById(R.id.checkboxCounter2Autocount)).
                    setOnCheckedChangeListener(new ToggleCheckChangeListener(0x00, 2));
            rootView.findViewById(R.id.buttonCounter2Up).
                    setOnClickListener(new TriggerButtonClickListener(0x40, 1));
            rootView.findViewById(R.id.buttonCounter2Down).
                    setOnClickListener(new TriggerButtonClickListener(0x40, 2));

            mTextViewCounter2HighNibble = rootView.findViewById(R.id.textViewCounter2HighNibble);
            mTextViewCounter2LowNibble = rootView.findViewById(R.id.textViewCounter2LowNibble);

            return rootView;
        }

        @Override
        public void setUserVisibleHint(boolean isVisibleToUser) {
            super.setUserVisibleHint(isVisibleToUser);
            updateTimerState();
            Log.d("FrontPanel", String.format("CountersFragment: setUserVisibleHint(%b)", isVisibleToUser));
        }

        @Override
        public void onPause() {
            super.onPause();
            updateTimerState();
        }

        @Override
        public void onResume() {
            super.onResume();
            updateTimerState();
        }

        private void updateTimerState() {
            boolean enableTimer = getUserVisibleHint() && isResumed();
            if (enableTimer && mTimer == null) {
                Log.d("FrontPanel", "Start timer");
                mTimer = new Timer();
                mTimer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        final MainActivity activity = (MainActivity) getActivity();

                        if (activity.mFrontPanel == null) {
                            return;
                        }

                        Throwable throwable = null;
                        try {
                            updateWireOuts();
                        } catch (Throwable e) {
                            throwable = e;
                        }

                        final long wireOut0x20 =
                                (throwable == null) ? activity.mFrontPanel.GetWireOutValue(0x20) : 0;
                        final long wireOut0x21 =
                                (throwable == null) ? activity.mFrontPanel.GetWireOutValue(0x21) : 0;

                        final Throwable finalThrowable = throwable;
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (finalThrowable != null) {
                                    activity.onException(finalThrowable);
                                } else {
                                    if (wireOut0x20 != mWireOut0x20) {
                                        updateNibble(mTextViewCounter1HighNibble, wireOut0x20, mWireOut0x20, 4);
                                        updateNibble(mTextViewCounter1LowNibble, wireOut0x20, mWireOut0x20, 0);

                                        for (int i = 0; i < 8; i++) {
                                            long deviceBit = ((wireOut0x20 >> i) & 0x01);
                                            long guiBit = ((mWireOut0x20 >> i) & 0x01);
                                            if (deviceBit != guiBit) {
                                                if (deviceBit == 0) {
                                                    mImageViewLedBits[i].setImageResource(R.drawable.led_off);
                                                } else {
                                                    mImageViewLedBits[i].setImageResource(R.drawable.led_on);
                                                }
                                            }
                                        }

                                        mWireOut0x20 = wireOut0x20;
                                    }

                                    if (wireOut0x21 != mWireOut0x21) {
                                        updateNibble(mTextViewCounter2HighNibble, wireOut0x21, mWireOut0x21, 4);
                                        updateNibble(mTextViewCounter2LowNibble, wireOut0x21, mWireOut0x21, 0);

                                        mWireOut0x21 = wireOut0x21;
                                    }
                                }
                            }
                        });
                    }
                }, 0, 100);
            } else if (!enableTimer && mTimer != null) {
                Log.d("FrontPanel", "Stop timer");
                mTimer.cancel();
                mTimer = null;
            }
        }

        public void updateNibble(TextView nibbleControl, long deviceWireOut, long guiWireOut, long shift) {
            long deviceHighNibble = (deviceWireOut >> shift) & 0x0f;
            long guiHighNibble = (guiWireOut >> shift) & 0x0f;
            if (deviceHighNibble != guiHighNibble) {
                nibbleControl.setText(String.format("%X", deviceHighNibble));
            }
        }

        public void setAndUpdateWireInValue(int endPointAddress, long value, long bit) throws FrontPanelException {
            MainActivity activity = (MainActivity) getActivity();
            okCFrontPanel.ErrorCode errorCode =
                    activity.mFrontPanel.SetWireInValue(endPointAddress, value, 1 << bit);

            if (okCFrontPanel.ErrorCode.NoError == errorCode) {
                errorCode = activity.mFrontPanel.UpdateWireIns();
            }

            if (okCFrontPanel.ErrorCode.NoError != errorCode) {
                throw new FrontPanelException(String.format(
                        "Failed to set wire in value: %s",
                        errorCode
                ));
            }
        }

        public void updateWireOuts() throws FrontPanelException {
            MainActivity activity = (MainActivity) getActivity();
            okCFrontPanel.ErrorCode errorCode = activity.mFrontPanel.UpdateWireOuts();

            if (okCFrontPanel.ErrorCode.NoError != errorCode) {
                throw new FrontPanelException(String.format(
                        "Failed to update wire outs: %s",
                        errorCode
                ));
            }
        }

        public void activateTriggerIn(int endPointAddress, int bit) throws FrontPanelException {
            MainActivity activity = (MainActivity) getActivity();
            okCFrontPanel.ErrorCode errorCode =
                    activity.mFrontPanel.ActivateTriggerIn(endPointAddress, bit);

            if (okCFrontPanel.ErrorCode.NoError != errorCode) {
                throw new FrontPanelException(String.format(
                        "Failed to activate trigger in: %s",
                        errorCode
                ));
            }
        }

        private Timer mTimer;

        private long mWireOut0x20;
        private long mWireOut0x21;

        private TextView mTextViewCounter1HighNibble;
        private TextView mTextViewCounter1LowNibble;
        private ImageView mImageViewLedBits[];

        private TextView mTextViewCounter2HighNibble;
        private TextView mTextViewCounter2LowNibble;
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            if (position == MAIN_PAGE_INDEX)
                return new MainFragment();

            return new CountersFragment();
        }

        @Override
        public int getCount() {
            // Show 2 total pages.
            return 2;
        }
    }
}
