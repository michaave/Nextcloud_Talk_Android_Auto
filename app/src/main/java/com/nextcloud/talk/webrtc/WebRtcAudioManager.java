/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-FileCopyrightText: 2014 The WebRTC Project Authors
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Original code:
 *
 * Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license
 * that can be found in the LICENSE file in the root of the source
 * tree. An additional intellectual property rights grant can be found
 * in the file PATENTS.  All contributing project authors may
 * be found in the AUTHORS file in the root of the source tree.
 */
package com.nextcloud.talk.webrtc;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.util.Log;

import com.nextcloud.talk.call.TalkCallInterop;
import com.nextcloud.talk.events.ProximitySensorEvent;
import com.nextcloud.talk.utils.ContextExtensionsKt;
import com.nextcloud.talk.utils.ReceiverFlag;
import com.nextcloud.talk.utils.power.PowerManagerUtils;

import org.greenrobot.eventbus.EventBus;
import org.webrtc.ThreadUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class WebRtcAudioManager {
    private static final String TAG = WebRtcAudioManager.class.getSimpleName();
    private final Context context;
    private final WebRtcBluetoothManager bluetoothManager;
    private final boolean useProximitySensor;
    private final AudioManager audioManager;
    private AudioManagerListener audioManagerListener;
    private AudioManagerState amState;
    private int savedAudioMode = AudioManager.MODE_INVALID;
    private boolean savedIsSpeakerPhoneOn = false;
    private boolean savedIsMicrophoneMute = false;
    private boolean hasWiredHeadset = false;
    private boolean telecomManagedAudioSession = false;
    private boolean wiredHeadsetReceiverRegistered = false;
    private boolean telecomAudioStateReceiverRegistered = false;

    private AudioDevice userSelectedAudioDevice;
    private AudioDevice currentAudioDevice;
    private AudioDevice defaultAudioDevice;

    private ProximitySensor proximitySensor = null;

    private Set<AudioDevice> audioDevices = new HashSet<>();

    private Set<AudioDevice> internalAudioDevices = new HashSet<>();

    private final BroadcastReceiver wiredHeadsetReceiver;
    private final BroadcastReceiver telecomAudioStateReceiver;
    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;
    private AudioFocusRequest audioFocusRequest;
    private final AudioFocusState audioFocusState = new AudioFocusState();

    private final PowerManagerUtils powerManagerUtils;

    private WebRtcAudioManager(Context context, boolean useProximitySensor) {
        Log.d(TAG, "ctor");
        ThreadUtils.checkIsOnMainThread();
        this.context = context;
        audioManager = ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE));
        bluetoothManager = WebRtcBluetoothManager.create(context, this);
        wiredHeadsetReceiver = new WiredHeadsetReceiver();
        telecomAudioStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (TalkCallInterop.ACTION_TELECOM_AUDIO_STATE_CHANGED.equals(intent.getAction())) {
                    updateAudioDeviceState();
                }
            }
        };
        amState = AudioManagerState.UNINITIALIZED;

        powerManagerUtils = new PowerManagerUtils();
        powerManagerUtils.updatePhoneState(PowerManagerUtils.PhoneState.WITH_PROXIMITY_SENSOR_LOCK);

        this.useProximitySensor = useProximitySensor;
        updateAudioDeviceState();

        // Create and initialize the proximity sensor.
        // Tablet devices (e.g. Nexus 7) does not support proximity sensors.
        // Note that, the sensor will not be active until start() has been called.
        proximitySensor = ProximitySensor.create(context, new Runnable() {
            // This method will be called each time a state change is detected.
            // Example: user holds his hand over the device (closer than ~5 cm),
            // or removes his hand from the device.
            public void run() {
                onProximitySensorChangedState();
            }
        });
    }

    /**
     * Construction.
     */
    public static WebRtcAudioManager create(Context context, boolean useProximitySensor) {
       return new WebRtcAudioManager(context, useProximitySensor);
    }

    public void startBluetoothManager() {
        if (isTelecomAudioManaged()) {
            Log.d(TAG, "Telecom owns audio routing; not starting the legacy Bluetooth SCO manager");
            return;
        }
        // Initialize and start Bluetooth if a BT device is available or initiate
        // detection of new (enabled) BT devices.
        bluetoothManager.start();
    }

    /**
     * This method is called when the proximity sensor reports a state change, e.g. from "NEAR to FAR" or from "FAR to
     * NEAR".
     */
    private void onProximitySensorChangedState() {
        if (!useProximitySensor || isTelecomAudioManaged()) {
            return;
        }

        if (userSelectedAudioDevice == AudioDevice.SPEAKER_PHONE
            && audioDevices.contains(AudioDevice.EARPIECE)
            && audioDevices.contains(AudioDevice.SPEAKER_PHONE)) {

            if (proximitySensor.sensorReportsNearState()) {
                setAudioDeviceInternal(AudioDevice.EARPIECE);
                Log.d(TAG, "switched to EARPIECE because userSelectedAudioDevice was SPEAKER_PHONE and proximity=near");

                EventBus.getDefault().post(new ProximitySensorEvent(ProximitySensorEvent.ProximitySensorEventType.SENSOR_NEAR));

            } else {
                setAudioDeviceInternal(WebRtcAudioManager.AudioDevice.SPEAKER_PHONE);
                Log.d(TAG, "switched to SPEAKER_PHONE because userSelectedAudioDevice was SPEAKER_PHONE and proximity=far");

                EventBus.getDefault().post(new ProximitySensorEvent(ProximitySensorEvent.ProximitySensorEventType.SENSOR_FAR));
            }
        }
    }

    @SuppressLint("WrongConstant")
    public void start(AudioManagerListener audioManagerListener) {
        Log.d(TAG, "start");
        ThreadUtils.checkIsOnMainThread();
        if (amState == AudioManagerState.RUNNING) {
            Log.e(TAG, "AudioManager is already active");
            return;
        }
        // TODO(henrika): perhaps call new method called preInitAudio() here if UNINITIALIZED.

        Log.d(TAG, "AudioManager starts...");
        this.audioManagerListener = audioManagerListener;
        amState = AudioManagerState.RUNNING;
        telecomManagedAudioSession = TalkCallInterop.isTelecomAudioManaged();

        // Store current audio state so we can restore it when stop() is called.
        savedAudioMode = audioManager.getMode();
        savedIsSpeakerPhoneOn = audioManager.isSpeakerphoneOn();
        savedIsMicrophoneMute = audioManager.isMicrophoneMute();
        hasWiredHeadset = hasWiredHeadset();

        audioFocusChangeListener = this::onAudioFocusChange;

        // Request audio focus for a long-running call (delivered on the main thread).
        audioFocusRequest = buildCallAudioFocusRequest(audioFocusChangeListener);
        int result = audioManager.requestAudioFocus(audioFocusRequest);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.d(TAG, "Audio focus request granted for VOICE_CALL streams");
        } else {
            Log.e(TAG, "Audio focus request failed");
        }

        // Start by setting MODE_IN_COMMUNICATION as default audio mode. It is
        // required to be in this mode when playout and/or recording starts for
        // best possible VoIP performance. Telecom owns the endpoint, not this mode.
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

        // Legacy calls use AudioManager mute state. Telecom-managed calls mirror
        // mute through the WebRTC track instead, so don't override Telecom here.
        if (!telecomManagedAudioSession) {
            setMicrophoneMute(false);
        }

        // Set initial device states.
        userSelectedAudioDevice = AudioDevice.NONE;
        currentAudioDevice = AudioDevice.NONE;
        defaultAudioDevice = AudioDevice.NONE;
        audioDevices.clear();
        internalAudioDevices.clear();

        registerReceiver(
            telecomAudioStateReceiver,
            new IntentFilter(TalkCallInterop.ACTION_TELECOM_AUDIO_STATE_CHANGED)
        );
        telecomAudioStateReceiverRegistered = true;

        if (!telecomManagedAudioSession) {
            startBluetoothManager();
            proximitySensor.start();
            registerReceiver(wiredHeadsetReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
            wiredHeadsetReceiverRegistered = true;
        }

        // Do initial selection of audio device. In a Telecom-managed call this
        // consumes Telecom's endpoint flows; otherwise it uses Talk's legacy route logic.
        updateAudioDeviceState();
        Log.d(TAG, "AudioManager started");
    }

    /**
     * Handles audio focus changes (called on the main thread). Re-asserts the communication mode and audio route
     * when focus returns after a transient loss, see {@link AudioFocusState}.
     */
    void onAudioFocusChange(int focusChange) {
        if (audioFocusState.handle(focusChange) && amState == AudioManagerState.RUNNING) {
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            updateAudioDeviceState();
        }
        Log.d(TAG, "onAudioFocusChange: " + focusChange);
    }

    static AudioFocusRequest buildCallAudioFocusRequest(AudioManager.OnAudioFocusChangeListener listener) {
        return new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            .setAcceptsDelayedFocusGain(true)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(listener)
            .build();
    }

    /**
     * Tracks audio focus losses during a call.
     *
     * A transient focus holder such as the telephony stack also switches the global audio mode and restores its own
     * saved mode on release, clobbering MODE_IN_COMMUNICATION. "handle" reports whether the communication mode must
     * be re-asserted for a focus change, so the call does not continue without hardware echo cancellation and proper
     * VoIP routing after an interruption.
     */
    static class AudioFocusState {
        private boolean transientLoss = false;

        boolean handle(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    transientLoss = true;
                    return false;
                case AudioManager.AUDIOFOCUS_GAIN:
                    boolean restore = transientLoss;
                    transientLoss = false;
                    return restore;
                default:
                    transientLoss = false;
                    return false;
            }
        }
    }

    @SuppressLint("WrongConstant")
    public void stop() {
        Log.d(TAG, "stop");
        ThreadUtils.checkIsOnMainThread();
        if (amState != AudioManagerState.RUNNING) {
            Log.e(TAG, "Trying to stop AudioManager in incorrect state: " + amState);
            return;
        }
        amState = AudioManagerState.UNINITIALIZED;

        if (wiredHeadsetReceiverRegistered) {
            unregisterReceiver(wiredHeadsetReceiver);
            wiredHeadsetReceiverRegistered = false;
        }
        if (telecomAudioStateReceiverRegistered) {
            unregisterReceiver(telecomAudioStateReceiver);
            telecomAudioStateReceiverRegistered = false;
        }

        if(bluetoothManager.started()) {
            bluetoothManager.stop();
        }

        // Don't alter endpoint or global microphone state for a Telecom-managed
        // session. Telecom restores the route after the call leaves its scope.
        if (!telecomManagedAudioSession) {
            setSpeakerphoneOn(savedIsSpeakerPhoneOn);
            setMicrophoneMute(savedIsMicrophoneMute);
        }
        audioManager.setMode(savedAudioMode);

        // Abandon audio focus. Gives the previous focus owner, if any, focus.
        if (audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
            audioFocusRequest = null;
        }
        audioFocusChangeListener = null;
        Log.d(TAG, "Abandoned audio focus for VOICE_CALL streams");

        if (proximitySensor != null) {
            proximitySensor.stop();
            proximitySensor = null;
        }

        powerManagerUtils.updatePhoneState(PowerManagerUtils.PhoneState.IDLE);

        audioManagerListener = null;
        telecomManagedAudioSession = false;
        Log.d(TAG, "AudioManager stopped");
    }

    ;

    /**
     * Changes selection of the currently active audio device.
     */
    private void setAudioDeviceInternal(AudioDevice audioDevice) {
        Log.d(TAG, "setAudioDeviceInternal(device=" + audioDevice + ")");

        if (isTelecomAudioManaged()) {
            // Telecom endpoint changes arrive asynchronously through currentCallEndpoint.
            return;
        }

        if (audioDevices.contains(audioDevice)) {
            switch (audioDevice) {
                case SPEAKER_PHONE:
                    setSpeakerphoneOn(true);
                    break;
                case EARPIECE:
                case WIRED_HEADSET:
                case BLUETOOTH:
                    setSpeakerphoneOn(false);
                    break;
                default:
                    Log.e(TAG, "Invalid audio device selection");
                    break;
            }
            currentAudioDevice = audioDevice;
        }
    }

    /**
     * Sets the default audio device to use if selection algo has no other option
     */
    public void setDefaultAudioDevice(AudioDevice device) {
        ThreadUtils.checkIsOnMainThread();
        defaultAudioDevice = device;
        if (isTelecomAudioManaged()) {
            return;
        }
        if (!audioDevices.contains(device)) {
            Log.e(TAG, "Can not select default " + device + " from available " + audioDevices);
        }
        updateAudioDeviceState();
    }

    /**
     * Changes selection of the currently active audio device.
     */
    public void selectAudioDevice(AudioDevice device) {
        ThreadUtils.checkIsOnMainThread();
        if (isTelecomAudioManaged()) {
            String route = toTelecomAudioRoute(device);
            if (route != null) {
                userSelectedAudioDevice = device;
                TalkCallInterop.requestTelecomAudioRoute(context, route);
            }
            return;
        }
        if (!audioDevices.contains(device)) {
            Log.e(TAG, "Can not select " + device + " from available " + audioDevices);
        }
        userSelectedAudioDevice = device;
        updateAudioDeviceState();
    }

    /**
     * Returns current set of available/selectable audio devices.
     */
    public Set<AudioDevice> getAudioDevices() {
        ThreadUtils.checkIsOnMainThread();
        return Collections.unmodifiableSet(new HashSet<AudioDevice>(audioDevices));
    }

    /**
     * Returns the currently selected audio device.
     */
    public AudioDevice getCurrentAudioDevice() {
        ThreadUtils.checkIsOnMainThread();
        return currentAudioDevice;
    }

    /**
     * Helper method for receiver registration.
     */
    private void registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        ContextExtensionsKt.registerBroadcastReceiver(context, receiver, filter, ReceiverFlag.NotExported);
    }

    /**
     * Helper method for unregistration of an existing receiver.
     */
    private void unregisterReceiver(BroadcastReceiver receiver) {
        context.unregisterReceiver(receiver);
    }

    /**
     * Sets the speaker phone mode.
     */
    private void setSpeakerphoneOn(boolean on) {
        if (isTelecomAudioManaged()) {
            Log.d(TAG, "Telecom owns speaker routing; ignoring setSpeakerphoneOn(" + on + ")");
            return;
        }
        boolean wasOn = audioManager.isSpeakerphoneOn();
        if (wasOn == on) {
            return;
        }
        audioManager.setSpeakerphoneOn(on);
    }

    /**
     * Sets the microphone mute state.
     */
    private void setMicrophoneMute(boolean on) {
        if (isTelecomAudioManaged()) {
            return;
        }
        boolean wasMuted = audioManager.isMicrophoneMute();
        if (wasMuted == on) {
            return;
        }
        audioManager.setMicrophoneMute(on);
    }

    /**
     * Gets the current earpiece state.
     */
    private boolean hasEarpiece() {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }

    /**
     * Checks whether a wired headset is connected or not. This is not a valid indication that audio playback is
     * actually over the wired headset as audio routing depends on other conditions. We only use it as an early
     * indicator (during initialization) of an attached wired headset.
     */
    @Deprecated
    private boolean hasWiredHeadset() {
        @SuppressLint("WrongConstant") final AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL);
        for (AudioDeviceInfo device : devices) {
            final int type = device.getType();
            if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                Log.d(TAG, "hasWiredHeadset: found wired headset");
                return true;
            } else if (type == AudioDeviceInfo.TYPE_USB_DEVICE) {
                Log.d(TAG, "hasWiredHeadset: found USB audio device");
                return true;
            }
        }
        return false;
    }

    public final void updateAudioDeviceState() {
        ThreadUtils.checkIsOnMainThread();

        if (TalkCallInterop.isTelecomAudioManaged()) {
            telecomManagedAudioSession = true;
        }
        if (telecomManagedAudioSession) {
            updateTelecomAudioDeviceState();
            return;
        }

        Log.d(TAG, "--- updateAudioDeviceState: "
            + "wired headset=" + hasWiredHeadset + ", "
            + "BT state=" + bluetoothManager.getState());
        Log.d(TAG, "Device status: "
            + "internally available=" + internalAudioDevices + ", "
            + "externally available=" + audioDevices + ", "
            + "default=" + defaultAudioDevice + ", "
            + "current=" + currentAudioDevice + ", "
            + "user selected=" + userSelectedAudioDevice);

        if (bluetoothManager.getState() == WebRtcBluetoothManager.State.HEADSET_AVAILABLE
            || bluetoothManager.getState() == WebRtcBluetoothManager.State.HEADSET_UNAVAILABLE
            || bluetoothManager.getState() == WebRtcBluetoothManager.State.SCO_DISCONNECTING) {
            bluetoothManager.updateDevice();
        }

        Set<AudioDevice> newInternalAudioDevices = new HashSet<>();

        if (bluetoothManager.getState() == WebRtcBluetoothManager.State.SCO_CONNECTED
            || bluetoothManager.getState() == WebRtcBluetoothManager.State.SCO_CONNECTING
            || bluetoothManager.getState() == WebRtcBluetoothManager.State.HEADSET_AVAILABLE) {
            newInternalAudioDevices.add(AudioDevice.BLUETOOTH);
        }

        if (bluetoothManager.getState() == WebRtcBluetoothManager.State.SCO_CONNECTED) {
            newInternalAudioDevices.add(AudioDevice.BLUETOOTH_SCO);
        }

        if (hasWiredHeadset) {
            // If a wired headset is connected, then it is the only possible option.
            newInternalAudioDevices.add(AudioDevice.WIRED_HEADSET);
        } else {
            newInternalAudioDevices.add(AudioDevice.SPEAKER_PHONE);
            if (hasEarpiece()) {
                newInternalAudioDevices.add(AudioDevice.EARPIECE);
            }
        }

        // Correct user selected audio devices if needed.
        if (userSelectedAudioDevice == AudioDevice.BLUETOOTH
            && bluetoothManager.getState() == WebRtcBluetoothManager.State.HEADSET_UNAVAILABLE) {
            userSelectedAudioDevice = AudioDevice.SPEAKER_PHONE;
        }
        if (userSelectedAudioDevice == AudioDevice.SPEAKER_PHONE && hasWiredHeadset) {
            userSelectedAudioDevice = AudioDevice.WIRED_HEADSET;
        }
        if (userSelectedAudioDevice == AudioDevice.WIRED_HEADSET && !hasWiredHeadset) {
            userSelectedAudioDevice = AudioDevice.SPEAKER_PHONE;
        }


        // Need to start Bluetooth if it is available and user either selected it explicitly or
        // user did not select any output device.
        boolean needBluetoothScoStart =
            bluetoothManager.getState() == WebRtcBluetoothManager.State.HEADSET_AVAILABLE
                && (userSelectedAudioDevice == AudioDevice.NONE
                || userSelectedAudioDevice == AudioDevice.BLUETOOTH);

        // Need to stop Bluetooth audio if user selected different device and
        // Bluetooth SCO connection is established or in the process.
        boolean needBluetoothScoStop =
            (bluetoothManager.getState() == WebRtcBluetoothManager.State.SCO_CONNECTED
                || bluetoothManager.getState() == WebRtcBluetoothManager.State.SCO_CONNECTING)
                && (userSelectedAudioDevice != AudioDevice.NONE
                && userSelectedAudioDevice != AudioDevice.BLUETOOTH);

        if (bluetoothManager.getState() == WebRtcBluetoothManager.State.HEADSET_AVAILABLE
            || bluetoothManager.getState() == WebRtcBluetoothManager.State.SCO_CONNECTING
            || bluetoothManager.getState() == WebRtcBluetoothManager.State.SCO_CONNECTED) {
            Log.d(TAG, "Need BT audio: start=" + needBluetoothScoStart + ", "
                + "stop=" + needBluetoothScoStop + ", "
                + "BT state=" + bluetoothManager.getState());
        }

        // Start or stop Bluetooth SCO connection given states set earlier.
        if (needBluetoothScoStop) {
            bluetoothManager.stopScoAudio();
            bluetoothManager.updateDevice();
        } else if (needBluetoothScoStart && !bluetoothManager.startScoAudio()) {
            // Remove BLUETOOTH and BLUETOOTH_SCO from list of available devices since SCO start has
            // reported no longer available or too many failed attempts.
            newInternalAudioDevices.remove(AudioDevice.BLUETOOTH);
            newInternalAudioDevices.remove(AudioDevice.BLUETOOTH_SCO);
        }

        boolean audioDeviceSetUpdated = !internalAudioDevices.equals(newInternalAudioDevices);
        internalAudioDevices = newInternalAudioDevices;
        // BLUETOOTH_SCO isn't allowed to be in the externally accessible list of devices
        audioDevices = new HashSet<>(internalAudioDevices);
        audioDevices.remove(AudioDevice.BLUETOOTH_SCO);


        // Update selected audio device.
        AudioDevice newCurrentAudioDevice;

        if ((bluetoothManager.getState() == WebRtcBluetoothManager.State.SCO_CONNECTED)
            && newInternalAudioDevices.contains(AudioDevice.BLUETOOTH_SCO))
        {
            // If Bluetooth SCO is connected and available to use, then it has been selected by user or
            // auto-selected and it should be used as output audio device.
            newCurrentAudioDevice = AudioDevice.BLUETOOTH;
        } else if (hasWiredHeadset) {
            // If a wired headset is connected, but Bluetooth SCO is not, then wired headset is used as
            // audio device.
            newCurrentAudioDevice = AudioDevice.WIRED_HEADSET;
        } else {
            // No wired headset and no Bluetooth SCO, hence the audio-device list can contain speaker
            // phone (on a tablet), or speaker phone and earpiece (on mobile phone).
            // |userSelectedAudioDevice| may contain either AudioDevice.SPEAKER_PHONE or AudioDevice.EARPIECE
            // depending on the user's selection. |defaultAudioDevice|, which is set in code depending on
            // call is audio only or video, to be used if user hasn't made an explicit selection
            if ((userSelectedAudioDevice == AudioDevice.NONE) && (defaultAudioDevice != AudioDevice.NONE))
                newCurrentAudioDevice = defaultAudioDevice;
            else
                newCurrentAudioDevice = userSelectedAudioDevice;
        }
        // Switch to new device but only if there has been any changes.
        if (newCurrentAudioDevice != currentAudioDevice || audioDeviceSetUpdated) {
            // Do the required device switch.
            setAudioDeviceInternal(newCurrentAudioDevice);
            Log.d(TAG, "New device status: "
                + "internally available=" + internalAudioDevices + ", "
                + "externally available=" + audioDevices + ", "
                + "current(new)=" + newCurrentAudioDevice);
            if (audioManagerListener != null) {
                // Notify a listening client that audio device has been changed.
                audioManagerListener.onAudioDeviceChanged(currentAudioDevice, audioDevices);
            }
        }
        Log.d(TAG, "--- updateAudioDeviceState done");
    }

    private void updateTelecomAudioDeviceState() {
        String currentRoute = TalkCallInterop.getTelecomCurrentAudioRoute();
        String[] availableRoutes = TalkCallInterop.getTelecomAvailableAudioRoutes();
        Set<AudioDevice> newAudioDevices = new HashSet<>();

        for (String route : availableRoutes) {
            AudioDevice device = fromTelecomAudioRoute(route);
            if (device != AudioDevice.NONE) {
                newAudioDevices.add(device);
            }
        }

        AudioDevice newCurrentAudioDevice = fromTelecomAudioRoute(currentRoute);
        if (newCurrentAudioDevice != AudioDevice.NONE) {
            newAudioDevices.add(newCurrentAudioDevice);
        }

        // beginTelecomAudioManagement() deliberately publishes an empty snapshot
        // before Telecom's endpoint flows emit. Keep the last visible state until
        // the real snapshot arrives instead of flashing the picker to an empty list.
        if (newAudioDevices.isEmpty() && newCurrentAudioDevice == AudioDevice.NONE) {
            Log.d(TAG, "Waiting for initial Telecom audio endpoint snapshot");
            return;
        }

        boolean audioDeviceSetUpdated = !audioDevices.equals(newAudioDevices);
        boolean currentAudioDeviceUpdated =
            newCurrentAudioDevice != AudioDevice.NONE && newCurrentAudioDevice != currentAudioDevice;

        internalAudioDevices = new HashSet<>(newAudioDevices);
        audioDevices = new HashSet<>(newAudioDevices);
        if (newCurrentAudioDevice != AudioDevice.NONE) {
            currentAudioDevice = newCurrentAudioDevice;
        }

        Log.d(TAG, "Telecom audio state: available=" + audioDevices + ", current=" + currentAudioDevice);
        if ((audioDeviceSetUpdated || currentAudioDeviceUpdated) && audioManagerListener != null) {
            audioManagerListener.onAudioDeviceChanged(currentAudioDevice, audioDevices);
        }
    }

    private boolean isTelecomAudioManaged() {
        return telecomManagedAudioSession || TalkCallInterop.isTelecomAudioManaged();
    }

    private static String toTelecomAudioRoute(AudioDevice device) {
        if (device == null) {
            return null;
        }
        switch (device) {
            case EARPIECE:
                return TalkCallInterop.AUDIO_ROUTE_EARPIECE;
            case BLUETOOTH:
            case BLUETOOTH_SCO:
                return TalkCallInterop.AUDIO_ROUTE_BLUETOOTH;
            case WIRED_HEADSET:
                return TalkCallInterop.AUDIO_ROUTE_WIRED_HEADSET;
            case SPEAKER_PHONE:
                return TalkCallInterop.AUDIO_ROUTE_SPEAKER;
            default:
                return null;
        }
    }

    private static AudioDevice fromTelecomAudioRoute(String route) {
        if (route == null) {
            return AudioDevice.NONE;
        }
        switch (route) {
            case TalkCallInterop.AUDIO_ROUTE_EARPIECE:
                return AudioDevice.EARPIECE;
            case TalkCallInterop.AUDIO_ROUTE_BLUETOOTH:
            case TalkCallInterop.AUDIO_ROUTE_EXTERNAL:
                // The current Talk picker has one external wireless output row.
                // Treat Telecom's streaming endpoint as that row until the UI
                // grows named endpoint support.
                return AudioDevice.BLUETOOTH;
            case TalkCallInterop.AUDIO_ROUTE_WIRED_HEADSET:
                return AudioDevice.WIRED_HEADSET;
            case TalkCallInterop.AUDIO_ROUTE_SPEAKER:
                return AudioDevice.SPEAKER_PHONE;
            default:
                return AudioDevice.NONE;
        }
    }

    /**
     * AudioDevice is the names of possible audio devices that we currently support.
     */
    public enum AudioDevice {
        SPEAKER_PHONE, WIRED_HEADSET, EARPIECE, BLUETOOTH, NONE,
        BLUETOOTH_SCO // BLUETOOTH_SCO is only valid internal to this class
    }

    /**
     * AudioManager state.
     */
    public enum AudioManagerState {
        UNINITIALIZED,
        PREINITIALIZED,
        RUNNING,
    }

    /**
     * Selected audio device change event.
     */
    public static interface AudioManagerListener {
        // Callback fired once audio device is changed or list of available audio devices changed.
        void onAudioDeviceChanged(
            AudioDevice selectedAudioDevice, Set<AudioDevice> availableAudioDevices);
    }

    /* Receiver which handles changes in wired headset availability. */
    private class WiredHeadsetReceiver extends BroadcastReceiver {
        private static final int STATE_UNPLUGGED = 0;
        private static final int STATE_PLUGGED = 1;
        private static final int HAS_NO_MIC = 0;

        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra("state", STATE_UNPLUGGED);
            // int microphone = intent.getIntExtra("microphone", HAS_NO_MIC);
            // String name = intent.getStringExtra("name");
            hasWiredHeadset = (state == STATE_PLUGGED);
            updateAudioDeviceState();
        }
    }
}
