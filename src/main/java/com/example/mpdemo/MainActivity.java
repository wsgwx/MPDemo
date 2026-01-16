/*
 * Copyright (C) 2026 Rockchip Electronics Co., Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.mpdemo;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.SeekBar;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_MEDIA_PICK = 100;
    private static final int REQUEST_PERMISSION = 200;

    // Player 1
    private CustomVideoView player1VideoView;
    private SeekBar player1SeekBar;
    private MediaPlayer player1MediaPlayer;
    private Uri player1CurrentMediaUri;
    private AudioDeviceInfo player1SelectedDevice;
    private boolean isPlayer1Playing = false; // 是否Player1正在播放

    // Player 2
    private CustomVideoView player2VideoView;
    private SeekBar player2SeekBar;
    private MediaPlayer player2MediaPlayer;
    private Uri player2CurrentMediaUri;
    private AudioDeviceInfo player2SelectedDevice;
    private boolean isPlayer2Playing = false; // 是否Player2正在播放

    // Player 1 控制按钮
    private Button player1SelectButton;
    private Button player1PlayPauseButton;
    private Button player1LoopButton;
    private Button player1DeviceButton;

    // Player 2 控制按钮
    private Button player2SelectButton;
    private Button player2PlayPauseButton;
    private Button player2LoopButton;
    private Button player2DeviceButton;

    private MediaController player1MediaController;
    private MediaController player2MediaController;

    private AudioManager audioManager;
    private boolean isPlayer1Looping = true; // Player 1 循环播放状态
    private boolean isPlayer2Looping = true; // Player 2 循环播放状态
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateSeekBars;

    // 添加成员变量用于跟踪需要恢复的播放器
    private int player1ResumePosition = 0;
    private int player2ResumePosition = 0;
    private boolean player1NeedsResume = false;
    private boolean player2NeedsResume = false;
    // 添加成员变量用于跟踪播放器暂停时的位置
    private int player1PausedPosition = 0;
    private int player2PausedPosition = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupClickListeners();
        checkPermission();

        // 初始化AudioManager用于获取设备列表
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // 初始化进度条更新任务
        updateSeekBars = new Runnable() {
            @Override
            public void run() {
                updatePlayer1SeekBar();
                updatePlayer2SeekBar();

                // 继续下一帧更新
                handler.postDelayed(this, 1000); // 每秒更新一次
            }
        };
    }

    private void initViews() {
        // 初始化Player 1组件
        player1VideoView = findViewById(R.id.player1_video_view);
        player1SeekBar = findViewById(R.id.player1_seek_bar);
        player1MediaController = new MediaController(this);
        player1VideoView.setMediaController(player1MediaController);

        // 初始化Player 2组件
        player2VideoView = findViewById(R.id.player2_video_view);
        player2SeekBar = findViewById(R.id.player2_seek_bar);
        player2MediaController = new MediaController(this);
        player2VideoView.setMediaController(player2MediaController);

        // 初始化Player 1 控制按钮
        player1SelectButton = findViewById(R.id.player1_btn_select);
        player1PlayPauseButton = findViewById(R.id.player1_btn_play_pause);
        player1LoopButton = findViewById(R.id.player1_btn_loop);
        player1DeviceButton = findViewById(R.id.player1_btn_device);

        // 初始化Player 2 控制按钮
        player2SelectButton = findViewById(R.id.player2_btn_select);
        player2PlayPauseButton = findViewById(R.id.player2_btn_play_pause);
        player2LoopButton = findViewById(R.id.player2_btn_loop);
        player2DeviceButton = findViewById(R.id.player2_btn_device);

        // 设置Player 1进度条监听器
        player1SeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) { // 用户拖动进度条时才处理
                    if (player1MediaPlayer != null) {
                        // 计算实际播放位置
                        int duration = player1MediaPlayer.getDuration();
                        int newPosition = (int) (((float) progress / 100) * duration);
                        player1MediaPlayer.seekTo(newPosition);
                    } else if (player1VideoView != null && player1VideoView.isPlaying()) {
                        // 计算实际播放位置
                        int duration = player1VideoView.getDuration();
                        int newPosition = (int) (((float) progress / 100) * duration);
                        player1VideoView.seekTo(newPosition);
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // 用户开始拖动进度条时停止自动更新
                handler.removeCallbacks(updateSeekBars);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // 用户结束拖动进度条时恢复自动更新
                handler.post(updateSeekBars);
            }
        });

        // 设置Player 2进度条监听器
        player2SeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) { // 用户拖动进度条时才处理
                    if (player2MediaPlayer != null) {
                        // 计算实际播放位置
                        int duration = player2MediaPlayer.getDuration();
                        int newPosition = (int) (((float) progress / 100) * duration);
                        player2MediaPlayer.seekTo(newPosition);
                    } else if (player2VideoView != null && player2VideoView.isPlaying()) {
                        // 计算实际播放位置
                        int duration = player2VideoView.getDuration();
                        int newPosition = (int) (((float) progress / 100) * duration);
                        player2VideoView.seekTo(newPosition);
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // 用户开始拖动进度条时停止自动更新
                handler.removeCallbacks(updateSeekBars);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // 用户结束拖动进度条时恢复自动更新
                handler.post(updateSeekBars);
            }
        });

        // 安全地更新按钮状态
        if (player1LoopButton != null) {
            updatePlayer1LoopButtonState();
        }
        if (player2LoopButton != null) {
            updatePlayer2LoopButtonState();
        }
    }

    private void setupClickListeners() {
        // Player 1 按钮点击事件
        if (player1SelectButton != null) {
            player1SelectButton.setOnClickListener(v -> selectMediaForPlayer1());
        }
        if (player1PlayPauseButton != null) {
            player1PlayPauseButton.setOnClickListener(v -> togglePlayer1PlayPause());
        }
        if (player1LoopButton != null) {
            player1LoopButton.setOnClickListener(v -> togglePlayer1Looping());
        }
        if (player1DeviceButton != null) {
            player1DeviceButton.setOnClickListener(v -> showDeviceSelectionDialogForPlayer1());
        }

        // Player 2 按钮点击事件
        if (player2SelectButton != null) {
            player2SelectButton.setOnClickListener(v -> selectMediaForPlayer2());
        }
        if (player2PlayPauseButton != null) {
            player2PlayPauseButton.setOnClickListener(v -> togglePlayer2PlayPause());
        }
        if (player2LoopButton != null) {
            player2LoopButton.setOnClickListener(v -> togglePlayer2Looping());
        }
        if (player2DeviceButton != null) {
            player2DeviceButton.setOnClickListener(v -> showDeviceSelectionDialogForPlayer2());
        }
    }

    private void togglePlayer1Looping() {
        isPlayer1Looping = !isPlayer1Looping;
        updatePlayer1LoopButtonState();

        // 仅更新Player 1的循环状态，不影响输出设备
        if (player1MediaPlayer != null) {
            player1MediaPlayer.setLooping(isPlayer1Looping);
            // 不再重新应用音频设备，避免循环时频繁触发设备切换
        }
    }

    private void togglePlayer2Looping() {
        isPlayer2Looping = !isPlayer2Looping;
        updatePlayer2LoopButtonState();

        // 仅更新Player 2的循环状态，不影响输出设备
        if (player2MediaPlayer != null) {
            player2MediaPlayer.setLooping(isPlayer2Looping);
            // 不再重新应用音频设备，避免循环时频繁触发设备切换
        }
    }

    private void updatePlayer1LoopButtonState() {
        if (player1LoopButton != null) {
            if (isPlayer1Looping) {
                player1LoopButton.setText(R.string.loop_on);
                player1LoopButton.setBackgroundColor(Color.parseColor("#4CAF50")); // 绿色表示开启
            } else {
                player1LoopButton.setText(R.string.loop_off);
                player1LoopButton.setBackgroundColor(Color.GRAY); // 灰色表示关闭
            }
        }
    }

    private void updatePlayer2LoopButtonState() {
        if (player2LoopButton != null) {
            if (isPlayer2Looping) {
                player2LoopButton.setText(R.string.loop_on);
                player2LoopButton.setBackgroundColor(Color.parseColor("#4CAF50")); // 绿色表示开启
            } else {
                player2LoopButton.setText(R.string.loop_off);
                player2LoopButton.setBackgroundColor(Color.GRAY); // 灰色表示关闭
            }
        }
    }

    private void showDeviceSelectionDialogForPlayer1() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 获取可用的音频输出设备
            List<AudioDeviceInfo> devices = getAvailableOutputDevices();

            if (devices.isEmpty() && player1DeviceButton != null) {
                player1DeviceButton.setText(R.string.device_default);
                return;
            }

            // 创建对话框选项
            String[] deviceNames = new String[devices.size()];
            final AudioDeviceInfo[] deviceArray = devices.toArray(new AudioDeviceInfo[0]);

            for (int i = 0; i < devices.size(); i++) {
                deviceNames[i] = getDeviceName(deviceArray[i]);
            }

            // 创建并显示对话框
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("选择输出设备(Player 1)")
                   .setItems(deviceNames, new DialogInterface.OnClickListener() {
                       @Override
                       public void onClick(DialogInterface dialog, int which) {
                           player1SelectedDevice = deviceArray[which];
                           if (player1DeviceButton != null) {
                               player1DeviceButton.setText("设备: " + getDeviceName(player1SelectedDevice));
                           }

                           // 应用到Player 1
                           if (player1MediaPlayer != null) {
                               applyPreferredDevice(player1MediaPlayer, player1SelectedDevice);
                           } else if (player1VideoView != null) {
                               player1VideoView.setPreferredDevice(player1SelectedDevice);
                           }
                       }
                   });

            builder.create().show();
        }
    }

    private void showDeviceSelectionDialogForPlayer2() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 获取可用的音频输出设备
            List<AudioDeviceInfo> devices = getAvailableOutputDevices();

            if (devices.isEmpty() && player2DeviceButton != null) {
                player2DeviceButton.setText(R.string.device_default);
                return;
            }

            // 创建对话框选项
            String[] deviceNames = new String[devices.size()];
            final AudioDeviceInfo[] deviceArray = devices.toArray(new AudioDeviceInfo[0]);

            for (int i = 0; i < devices.size(); i++) {
                deviceNames[i] = getDeviceName(deviceArray[i]);
            }

            // 创建并显示对话框
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("选择输出设备(Player 2)")
                   .setItems(deviceNames, new DialogInterface.OnClickListener() {
                       @Override
                       public void onClick(DialogInterface dialog, int which) {
                           player2SelectedDevice = deviceArray[which];
                           if (player2DeviceButton != null) {
                               player2DeviceButton.setText("设备: " + getDeviceName(player2SelectedDevice));
                           }

                           // 应用到Player 2
                           if (player2MediaPlayer != null) {
                               applyPreferredDevice(player2MediaPlayer, player2SelectedDevice);
                           } else if (player2VideoView != null) {
                               player2VideoView.setPreferredDevice(player2SelectedDevice);
                           }
                       }
                   });

            builder.create().show();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private List<AudioDeviceInfo> getAvailableOutputDevices() {
        List<AudioDeviceInfo> availableDevices = new ArrayList<>();

        if (audioManager != null) {
            AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo device : devices) {
                if (device.isSink()) { // 输出设备
                    availableDevices.add(device);
                }
            }
        }

        return availableDevices;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private String getDeviceName(AudioDeviceInfo deviceInfo) {
        if (deviceInfo.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
            return getString(R.string.device_speaker);
        } else if (deviceInfo.getType() == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                   deviceInfo.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
            return getString(R.string.device_headphone);
        } else if (deviceInfo.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                   deviceInfo.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
            return getString(R.string.device_bluetooth);
        } else if (deviceInfo.getType() == AudioDeviceInfo.TYPE_HDMI) {
            return getString(R.string.device_hdmi);
        } else if (deviceInfo.getType() == AudioDeviceInfo.TYPE_USB_HEADSET ||
                   deviceInfo.getType() == AudioDeviceInfo.TYPE_USB_DEVICE) {
            return getString(R.string.device_usb);
        } else {
            return "其他设备";
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void applyPreferredDevice(MediaPlayer player, AudioDeviceInfo deviceInfo) {
        try {
            // 检查设备是否支持
            if (deviceInfo == null) {
                Log.d("MPDemo", "设备为空，无法设置");
                return;
            }

            Log.d("MPDemo", "尝试设置首选输出设备: " + getDeviceName(deviceInfo));

            // 设置首选设备
            boolean success = player.setPreferredDevice(deviceInfo);
            Log.d("MPDemo", "设备设置" + (success ? "成功" : "失败") + ": " + getDeviceName(deviceInfo));
        } catch (Exception e) {
            Log.e("MPDemo", "设置首选输出设备异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void checkPermission() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ?
            Manifest.permission.READ_MEDIA_VIDEO : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{permission}, REQUEST_PERMISSION);
        }
    }

    private void selectMediaForPlayer1() {
        // 保存Player2的当前播放状态
        int player2CurrentPos = 0;
        boolean player2WasPlaying = false;
        if (player2VideoView != null && player2VideoView.isPlaying()) {
            player2CurrentPos = player2VideoView.getCurrentPosition();
            player2WasPlaying = true;
        } else if (player2MediaPlayer != null && player2MediaPlayer.isPlaying()) {
            player2CurrentPos = player2MediaPlayer.getCurrentPosition();
            player2WasPlaying = true;
        }

        // 创建一个Intent来从sdcard选择媒体文件
        Intent intent = new Intent();
        // 明确指定支持的文件类型
        intent.setType("*/*"); // 支持所有文件类型
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"video/*", "audio/*", "application/octet-stream"});
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // 确保Intent可以处理
        if (intent.resolveActivity(getPackageManager()) != null) {
            // 保存播放状态到成员变量，以便在onResume中恢复
            if (player2WasPlaying) {
                player2ResumePosition = player2CurrentPos;
                player2NeedsResume = true;
            }
            startActivityForResult(intent, REQUEST_MEDIA_PICK + 1); // 使用不同的requestCode
        } else {
            // 如果没有合适的文件管理器，可以尝试使用系统媒体选择器
            Intent mediaIntent = new Intent(Intent.ACTION_PICK);
            mediaIntent.setType("video/*|audio/*");
            // 保存播放状态到成员变量，以便在onResume中恢复
            if (player2WasPlaying) {
                player2ResumePosition = player2CurrentPos;
                player2NeedsResume = true;
            }
            startActivityForResult(mediaIntent, REQUEST_MEDIA_PICK + 1); // 使用不同的requestCode
        }
    }

    private void selectMediaForPlayer2() {
        // 保存Player1的当前播放状态
        int player1CurrentPos = 0;
        boolean player1WasPlaying = false;
        if (player1VideoView != null && player1VideoView.isPlaying()) {
            player1CurrentPos = player1VideoView.getCurrentPosition();
            player1WasPlaying = true;
        } else if (player1MediaPlayer != null && player1MediaPlayer.isPlaying()) {
            player1CurrentPos = player1MediaPlayer.getCurrentPosition();
            player1WasPlaying = true;
        }

        // 创建一个Intent来从sdcard选择媒体文件
        Intent intent = new Intent();
        // 明确指定支持的文件类型
        intent.setType("*/*"); // 支持所有文件类型
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"video/*", "audio/*", "application/octet-stream"});
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // 确保Intent可以处理
        if (intent.resolveActivity(getPackageManager()) != null) {
            // 保存播放状态到成员变量，以便在onResume中恢复
            if (player1WasPlaying) {
                player1ResumePosition = player1CurrentPos;
                player1NeedsResume = true;
            }
            startActivityForResult(intent, REQUEST_MEDIA_PICK + 2); // 使用不同的requestCode
        } else {
            // 如果没有合适的文件管理器，可以尝试使用系统媒体选择器
            Intent mediaIntent = new Intent(Intent.ACTION_PICK);
            mediaIntent.setType("video/*|audio/*");
            // 保存播放状态到成员变量，以便在onResume中恢复
            if (player1WasPlaying) {
                player1ResumePosition = player1CurrentPos;
                player1NeedsResume = true;
            }
            startActivityForResult(mediaIntent, REQUEST_MEDIA_PICK + 2); // 使用不同的requestCode
        }
    }

    private void pausePlayer1() {
        if (player1MediaPlayer != null) {
            player1MediaPlayer.pause();
        } else if (player1VideoView != null) {
            player1VideoView.pause();
        }
    }

    private void pausePlayer2() {
        if (player2MediaPlayer != null) {
            player2MediaPlayer.pause();
        } else if (player2VideoView != null) {
            player2VideoView.pause();
        }
    }

    private void resumePlayer1FromPosition(int position) {
        if (player1MediaPlayer != null) {
            player1MediaPlayer.seekTo(position);
            player1MediaPlayer.start();
            isPlayer1Playing = true;
            if (player1PlayPauseButton != null) {
                player1PlayPauseButton.setText(R.string.pause);
            }
        } else if (player1VideoView != null) {
            player1VideoView.seekTo(position);
            player1VideoView.start();
            isPlayer1Playing = true;
            if (player1PlayPauseButton != null) {
                player1PlayPauseButton.setText(R.string.pause);
            }
        }
    }

    private void resumePlayer2FromPosition(int position) {
        if (player2MediaPlayer != null) {
            player2MediaPlayer.seekTo(position);
            player2MediaPlayer.start();
            isPlayer2Playing = true;
            if (player2PlayPauseButton != null) {
                player2PlayPauseButton.setText(R.string.pause);
            }
        } else if (player2VideoView != null) {
            player2VideoView.seekTo(position);
            player2VideoView.start();
            isPlayer2Playing = true;
            if (player2PlayPauseButton != null) {
                player2PlayPauseButton.setText(R.string.pause);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // 在处理新选择的文件之前，先保存两个播放器的状态
        final int player1CurrentPos = player1VideoView != null && player1VideoView.isPlaying()
            ? player1VideoView.getCurrentPosition()
            : (player1MediaPlayer != null && player1MediaPlayer.isPlaying()
                ? player1MediaPlayer.getCurrentPosition() : 0);
        final boolean player1WasPlaying = (player1VideoView != null && player1VideoView.isPlaying())
            || (player1MediaPlayer != null && player1MediaPlayer.isPlaying());

        final int player2CurrentPos = player2VideoView != null && player2VideoView.isPlaying()
            ? player2VideoView.getCurrentPosition()
            : (player2MediaPlayer != null && player2MediaPlayer.isPlaying()
                ? player2MediaPlayer.getCurrentPosition() : 0);
        final boolean player2WasPlaying = (player2VideoView != null && player2VideoView.isPlaying())
            || (player2MediaPlayer != null && player2MediaPlayer.isPlaying());

        if ((requestCode == REQUEST_MEDIA_PICK + 1) && resultCode == RESULT_OK) {
            if (data != null) {
                Uri selectedMediaUri = data.getData();
                if (selectedMediaUri != null) {
                    Log.d("MPDemo", "Player1 选择文件: " + selectedMediaUri.toString());
                    // 在Player 1中播放新选择的文件
                    playMediaInPlayer1(selectedMediaUri);

                    // 恢复Player 2的播放状态
                    if (player2WasPlaying) {
                        Log.d("MPDemo", "恢复Player2播放状态: 位置=" + player2CurrentPos);
                        handler.post(() -> {
                            if (player2MediaPlayer != null) {
                                player2MediaPlayer.seekTo(player2CurrentPos);
                                player2MediaPlayer.start();
                                isPlayer2Playing = true;
                            } else if (player2VideoView != null) {
                                player2VideoView.seekTo(player2CurrentPos);
                                player2VideoView.start();
                                isPlayer2Playing = true;
                            }
                            if (player2PlayPauseButton != null) {
                                player2PlayPauseButton.setText(R.string.pause);
                            }
                        });
                    }
                }
            }
        } else if ((requestCode == REQUEST_MEDIA_PICK + 2) && resultCode == RESULT_OK) {
            if (data != null) {
                Uri selectedMediaUri = data.getData();
                if (selectedMediaUri != null) {
                    Log.d("MPDemo", "Player2 选择文件: " + selectedMediaUri.toString());
                    // 在Player 2中播放新选择的文件
                    playMediaInPlayer2(selectedMediaUri);

                    // 恢复Player 1的播放状态
                    if (player1WasPlaying) {
                        Log.d("MPDemo", "恢复Player1播放状态: 位置=" + player1CurrentPos);
                        handler.post(() -> {
                            if (player1MediaPlayer != null) {
                                player1MediaPlayer.seekTo(player1CurrentPos);
                                player1MediaPlayer.start();
                                isPlayer1Playing = true;
                            } else if (player1VideoView != null) {
                                player1VideoView.seekTo(player1CurrentPos);
                                player1VideoView.start();
                                isPlayer1Playing = true;
                            }
                            if (player1PlayPauseButton != null) {
                                player1PlayPauseButton.setText(R.string.pause);
                            }
                        });
                    }
                }
            }
        }
    }

    private int getPlayer1CurrentPosition() {
        if (player1MediaPlayer != null) {
            return player1MediaPlayer.getCurrentPosition();
        } else if (player1VideoView != null) {
            return player1VideoView.getCurrentPosition();
        }
        return 0;
    }

    private int getPlayer2CurrentPosition() {
        if (player2MediaPlayer != null) {
            return player2MediaPlayer.getCurrentPosition();
        } else if (player2VideoView != null) {
            return player2VideoView.getCurrentPosition();
        }
        return 0;
    }

    private boolean isMediaPlayerPlaying(MediaPlayer mediaPlayer) {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.isPlaying();
            } catch (IllegalStateException e) {
                // MediaPlayer可能处于错误状态
                return false;
            }
        }
        return false;
    }

    private boolean isVideoViewPlaying(CustomVideoView videoView) {
        if (videoView != null) {
            // 由于VideoView没有isPlaying方法，我们通过播放状态变量来判断
            return true; // 实际上我们依赖isPlayerXPlaying标志
        }
        return false;
    }

    private String getFileExtension(Uri uri) {
        String fileName = uri.toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        return "";
    }

    private boolean isAudioFile(String extension) {
        String[] audioExtensions = {"mp3", "wav", "aac", "flac", "m4a", "ogg", "wma"};
        for (String ext : audioExtensions) {
            if (ext.equals(extension)) {
                return true;
            }
        }
        return false;
    }

    private void playMediaInPlayer1(Uri mediaUri) {
        // 获取文件扩展名来判断类型
        String fileExtension = getFileExtension(mediaUri);

        if (isAudioFile(fileExtension)) {
            // 停止并释放当前的视频播放器
            if (player1VideoView != null) {
                player1VideoView.stopPlayback();
            }
            // 停止并释放当前的音频播放器（如果存在）
            if (player1MediaPlayer != null) {
                player1MediaPlayer.stop();
                player1MediaPlayer.release();
                player1MediaPlayer = null;
            }

            // 播放音频文件在Player 1
            playAudioInPlayer1(mediaUri);
        } else {
            // 停止并释放当前的音频播放器
            if (player1MediaPlayer != null) {
                player1MediaPlayer.stop();
                player1MediaPlayer.release();
                player1MediaPlayer = null;
            }
            // 停止当前的视频播放
            if (player1VideoView != null) {
                player1VideoView.stopPlayback();
            }

            // 播放视频文件在Player 1
            playVideoInPlayer1(mediaUri);
        }
    }

    private void playMediaInPlayer2(Uri mediaUri) {
        // 获取文件扩展名来判断类型
        String fileExtension = getFileExtension(mediaUri);

        if (isAudioFile(fileExtension)) {
            // 停止并释放当前的视频播放器
            if (player2VideoView != null) {
                player2VideoView.stopPlayback();
            }
            // 停止并释放当前的音频播放器（如果存在）
            if (player2MediaPlayer != null) {
                player2MediaPlayer.stop();
                player2MediaPlayer.release();
                player2MediaPlayer = null;
            }

            // 播放音频文件在Player 2
            playAudioInPlayer2(mediaUri);
        } else {
            // 停止并释放当前的音频播放器
            if (player2MediaPlayer != null) {
                player2MediaPlayer.stop();
                player2MediaPlayer.release();
                player2MediaPlayer = null;
            }
            // 停止当前的视频播放
            if (player2VideoView != null) {
                player2VideoView.stopPlayback();
            }

            // 播放视频文件在Player 2
            playVideoInPlayer2(mediaUri);
        }
    }

    private void playVideoInPlayer1(Uri videoUri) {
        Log.d("MPDemo", "Player1 开始播放视频: " + videoUri.toString());
        player1CurrentMediaUri = videoUri;
        player1VideoView.setVisibility(View.VISIBLE); // 确保视频视图可见
        player1VideoView.setVideoURI(videoUri);

        // 设置循环播放
        player1VideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                Log.d("MPDemo", "Player1 视频播放完成，循环状态: " + isPlayer1Looping);
                if (isPlayer1Looping) {
                    // 应用首选设备，确保在循环播放时保持输出设备设置
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && player1SelectedDevice != null) {
                        try {
                            mp.setAudioAttributes(
                                new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                                    .build()
                            );

                            // 使用setPreferredDevice设置输出设备
                            boolean success = mp.setPreferredDevice(player1SelectedDevice);
                            Log.d("MPDemo", "Player1 循环播放时重新设置设备 " + (success ? "成功" : "失败") + ": " + getDeviceName(player1SelectedDevice));
                        } catch (Exception e) {
                            Log.e("MPDemo", "Player1 循环播放时设置音频属性失败: " + e.getMessage());
                        }
                    }

                    player1VideoView.start(); // 重新开始播放
                    Log.d("MPDemo", "Player1 循环播放已启动");
                } else {
                    if (player1PlayPauseButton != null) {
                        player1PlayPauseButton.setText(R.string.play);
                    }
                    handler.removeCallbacks(updateSeekBars); // 停止更新进度条
                    Log.d("MPDemo", "Player1 播放完成，未启用循环");
                }
            }
        });

        // 如果已经选择了输出设备，在视频准备完成后设置
        player1VideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                Log.d("MPDemo", "Player1 视频准备就绪");
                // 应用首选设备
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && player1SelectedDevice != null) {
                    try {
                        mp.setAudioAttributes(
                            new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                                .build()
                        );

                        // 使用setPreferredDevice设置输出设备
                        boolean success = mp.setPreferredDevice(player1SelectedDevice);
                        Log.d("MPDemo", "Player1 视频设备设置" + (success ? "成功" : "失败") + ": " + getDeviceName(player1SelectedDevice));
                    } catch (Exception e) {
                        Log.e("MPDemo", "设置Player1视频音频属性失败: " + e.getMessage());
                    }
                }

                player1VideoView.start();
                isPlayer1Playing = true;
                if (player1PlayPauseButton != null) {
                    player1PlayPauseButton.setText(R.string.pause);
                }

                // 开始更新进度条
                player1SeekBar.setMax(100);
                handler.removeCallbacks(updateSeekBars); // 先停止之前的更新任务
                handler.post(updateSeekBars); // 开始新的更新任务
                Log.d("MPDemo", "Player1 播放已启动");
            }
        });

        player1MediaController.setAnchorView(player1VideoView);
    }

    private void playVideoInPlayer2(Uri videoUri) {
        Log.d("MPDemo", "Player2 开始播放视频: " + videoUri.toString());
        player2CurrentMediaUri = videoUri;
        player2VideoView.setVisibility(View.VISIBLE); // 确保视频视图可见
        player2VideoView.setVideoURI(videoUri);

        // 设置循环播放
        player2VideoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                Log.d("MPDemo", "Player2 视频播放完成，循环状态: " + isPlayer2Looping);
                if (isPlayer2Looping) {
                    // 应用首选设备，确保在循环播放时保持输出设备设置
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && player2SelectedDevice != null) {
                        try {
                            mp.setAudioAttributes(
                                new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                                    .build()
                            );

                            // 使用setPreferredDevice设置输出设备
                            boolean success = mp.setPreferredDevice(player2SelectedDevice);
                            Log.d("MPDemo", "Player2 循环播放时重新设置设备 " + (success ? "成功" : "失败") + ": " + getDeviceName(player2SelectedDevice));
                        } catch (Exception e) {
                            Log.e("MPDemo", "Player2 循环播放时设置音频属性失败: " + e.getMessage());
                        }
                    }

                    player2VideoView.start(); // 重新开始播放
                    Log.d("MPDemo", "Player2 循环播放已启动");
                } else {
                    if (player2PlayPauseButton != null) {
                        player2PlayPauseButton.setText(R.string.play);
                    }
                    handler.removeCallbacks(updateSeekBars); // 停止更新进度条
                    Log.d("MPDemo", "Player2 播放完成，未启用循环");
                }
            }
        });

        // 如果已经选择了输出设备，在视频准备完成后设置
        player2VideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                Log.d("MPDemo", "Player2 视频准备就绪");
                // 应用首选设备
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && player2SelectedDevice != null) {
                    try {
                        mp.setAudioAttributes(
                            new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                                .build()
                        );

                        // 使用setPreferredDevice设置输出设备
                        boolean success = mp.setPreferredDevice(player2SelectedDevice);
                        Log.d("MPDemo", "Player2 视频设备设置" + (success ? "成功" : "失败") + ": " + getDeviceName(player2SelectedDevice));
                    } catch (Exception e) {
                        Log.e("MPDemo", "设置Player2视频音频属性失败: " + e.getMessage());
                    }
                }

                player2VideoView.start();
                isPlayer2Playing = true;
                if (player2PlayPauseButton != null) {
                    player2PlayPauseButton.setText(R.string.pause);
                }

                // 开始更新进度条
                player2SeekBar.setMax(100);
                handler.removeCallbacks(updateSeekBars); // 先停止之前的更新任务
                handler.post(updateSeekBars); // 开始新的更新任务
                Log.d("MPDemo", "Player2 播放已启动");
            }
        });

        player2MediaController.setAnchorView(player2VideoView);
    }

    private void playAudioInPlayer1(Uri audioUri) {
        Log.d("MPDemo", "Player1 开始播放音频: " + audioUri.toString());
        player1CurrentMediaUri = audioUri;
        isPlayer1Playing = true;

        // 隐藏Player 1的视频视图，因为正在播放音频
        if (player1VideoView != null) {
            player1VideoView.setVisibility(View.GONE);
        }

        // 释放之前的Player1的MediaPlayer实例
        if (player1MediaPlayer != null) {
            player1MediaPlayer.release();
            player1MediaPlayer = null;
        }

        player1MediaPlayer = new MediaPlayer();
        player1MediaPlayer.setAudioAttributes(
            new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        );
        player1MediaPlayer.setLooping(false); // 禁用内置循环，使用自定义循环逻辑

        try {
            player1MediaPlayer.setDataSource(this, audioUri);

            // 设置输出设备前准备
            player1MediaPlayer.prepareAsync(); // 使用异步准备避免阻塞UI线程

            // 如果已经选择了输出设备，使用MediaPlayer.setPreferredDevice设置
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && player1SelectedDevice != null) {
                applyPreferredDevice(player1MediaPlayer, player1SelectedDevice);
            }

            player1MediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    Log.d("MPDemo", "Player1 音频准备就绪");
                    mp.start();
                    isPlayer1Playing = true;
                    if (player1PlayPauseButton != null) {
                        player1PlayPauseButton.setText(R.string.pause);
                    }

                    // 开始更新进度条
                    player1SeekBar.setMax(100);
                    handler.removeCallbacks(updateSeekBars); // 先停止之前的更新任务
                    handler.post(updateSeekBars); // 开始新的更新任务
                    Log.d("MPDemo", "Player1 音频播放已启动");
                }
            });

            // 监听播放完成事件（用于实现自定义循环逻辑）
            player1MediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    Log.d("MPDemo", "Player1 音频播放完成，循环状态: " + isPlayer1Looping);
                    if (isPlayer1Looping) {
                        // 在自定义循环中重新应用首选设备
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && player1SelectedDevice != null) {
                            Log.d("MPDemo", "Player1 循环播放时重新应用首选设备");
                            applyPreferredDevice(mp, player1SelectedDevice);
                        }

                        // 手动循环播放
                        mp.seekTo(0); // 回到开头
                        mp.start();   // 重新开始播放
                        Log.d("MPDemo", "Player1 循环播放已启动");
                    } else {
                        isPlayer1Playing = false;
                        if (player1PlayPauseButton != null) {
                            player1PlayPauseButton.setText(R.string.play);
                        }
                        handler.removeCallbacks(updateSeekBars); // 停止更新进度条
                        Log.d("MPDemo", "Player1 音频播放完成，未启用循环");
                    }
                }
            });

            player1MediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Log.e("MPDemo", "Player1 播放错误: what=" + what + ", extra=" + extra);
                    handler.removeCallbacks(updateSeekBars); // 停止更新进度条
                    return false;
                }
            });
        } catch (IOException e) {
            Log.e("MPDemo", "Player1 设置音频数据源失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void playAudioInPlayer2(Uri audioUri) {
        Log.d("MPDemo", "Player2 开始播放音频: " + audioUri.toString());
        player2CurrentMediaUri = audioUri;
        isPlayer2Playing = true;

        // 隐藏Player 2的视频视图，因为正在播放音频
        if (player2VideoView != null) {
            player2VideoView.setVisibility(View.GONE);
        }

        // 释放之前的Player2的MediaPlayer实例
        if (player2MediaPlayer != null) {
            player2MediaPlayer.release();
            player2MediaPlayer = null;
        }

        player2MediaPlayer = new MediaPlayer();
        player2MediaPlayer.setAudioAttributes(
            new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        );
        player2MediaPlayer.setLooping(false); // 禁用内置循环，使用自定义循环逻辑

        try {
            player2MediaPlayer.setDataSource(this, audioUri);

            // 设置输出设备前准备
            player2MediaPlayer.prepareAsync(); // 使用异步准备避免阻塞UI线程

            // 如果已经选择了输出设备，使用MediaPlayer.setPreferredDevice设置
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && player2SelectedDevice != null) {
                applyPreferredDevice(player2MediaPlayer, player2SelectedDevice);
            }

            player2MediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    Log.d("MPDemo", "Player2 音频准备就绪");
                    mp.start();
                    isPlayer2Playing = true;
                    if (player2PlayPauseButton != null) {
                        player2PlayPauseButton.setText(R.string.pause);
                    }

                    // 开始更新进度条
                    player2SeekBar.setMax(100);
                    handler.removeCallbacks(updateSeekBars); // 先停止之前的更新任务
                    handler.post(updateSeekBars); // 开始新的更新任务
                    Log.d("MPDemo", "Player2 音频播放已启动");
                }
            });

            // 监听播放完成事件（用于实现自定义循环逻辑）
            player2MediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    Log.d("MPDemo", "Player2 音频播放完成，循环状态: " + isPlayer2Looping);
                    if (isPlayer2Looping) {
                        // 在自定义循环中重新应用首选设备
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && player2SelectedDevice != null) {
                            Log.d("MPDemo", "Player2 循环播放时重新应用首选设备");
                            applyPreferredDevice(mp, player2SelectedDevice);
                        }

                        // 手动循环播放
                        mp.seekTo(0); // 回到开头
                        mp.start();   // 重新开始播放
                        Log.d("MPDemo", "Player2 循环播放已启动");
                    } else {
                        isPlayer2Playing = false;
                        if (player2PlayPauseButton != null) {
                            player2PlayPauseButton.setText(R.string.play);
                        }
                        handler.removeCallbacks(updateSeekBars); // 停止更新进度条
                        Log.d("MPDemo", "Player2 音频播放完成，未启用循环");
                    }
                }
            });

            player2MediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Log.e("MPDemo", "Player2 播放错误: what=" + what + ", extra=" + extra);
                    handler.removeCallbacks(updateSeekBars); // 停止更新进度条
                    return false;
                }
            });
        } catch (IOException e) {
            Log.e("MPDemo", "Player2 设置音频数据源失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void togglePlayer1PlayPause() {
        if (isPlayer1Playing) {
            if (player1MediaPlayer != null) {
                player1MediaPlayer.pause();
            } else if (player1VideoView != null) {
                player1VideoView.pause();
            }
            isPlayer1Playing = false;
            if (player1PlayPauseButton != null) {
                player1PlayPauseButton.setText(R.string.play);
            }
        } else {
            if (player1MediaPlayer != null) {
                player1MediaPlayer.start();
            } else if (player1VideoView != null) {
                player1VideoView.start();
            }
            isPlayer1Playing = true;
            if (player1PlayPauseButton != null) {
                player1PlayPauseButton.setText(R.string.pause);
            }
        }
    }

    private void togglePlayer2PlayPause() {
        if (isPlayer2Playing) {
            if (player2MediaPlayer != null) {
                player2MediaPlayer.pause();
            } else if (player2VideoView != null) {
                player2VideoView.pause();
            }
            isPlayer2Playing = false;
            if (player2PlayPauseButton != null) {
                player2PlayPauseButton.setText(R.string.play);
            }
        } else {
            if (player2MediaPlayer != null) {
                player2MediaPlayer.start();
            } else if (player2VideoView != null) {
                player2VideoView.start();
            }
            isPlayer2Playing = true;
            if (player2PlayPauseButton != null) {
                player2PlayPauseButton.setText(R.string.pause);
            }
        }
    }

    private void updatePlayer1SeekBar() {
        if (player1MediaPlayer != null) {
            int currentPosition = player1MediaPlayer.getCurrentPosition();
            int totalDuration = player1MediaPlayer.getDuration();

            if (totalDuration > 0) {
                int progress = (int) (((float) currentPosition / totalDuration) * 100);
                player1SeekBar.setProgress(progress);
            }
        } else if (player1VideoView != null && player1VideoView.isPlaying()) {
            int currentPosition = player1VideoView.getCurrentPosition();
            int totalDuration = player1VideoView.getDuration();

            if (totalDuration > 0) {
                int progress = (int) (((float) currentPosition / totalDuration) * 100);
                player1SeekBar.setProgress(progress);
            }
        }
    }

    private void updatePlayer2SeekBar() {
        if (player2MediaPlayer != null) {
            int currentPosition = player2MediaPlayer.getCurrentPosition();
            int totalDuration = player2MediaPlayer.getDuration();

            if (totalDuration > 0) {
                int progress = (int) (((float) currentPosition / totalDuration) * 100);
                player2SeekBar.setProgress(progress);
            }
        } else if (player2VideoView != null && player2VideoView.isPlaying()) {
            int currentPosition = player2VideoView.getCurrentPosition();
            int totalDuration = player2VideoView.getDuration();

            if (totalDuration > 0) {
                int progress = (int) (((float) currentPosition / totalDuration) * 100);
                player2SeekBar.setProgress(progress);
            }
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 暂停时保存播放位置
        if (player1VideoView != null) {
            player1PausedPosition = player1VideoView.getCurrentPosition();
        } else if (player1MediaPlayer != null) {
            player1PausedPosition = player1MediaPlayer.getCurrentPosition();
        }

        if (player2VideoView != null) {
            player2PausedPosition = player2VideoView.getCurrentPosition();
        } else if (player2MediaPlayer != null) {
            player2PausedPosition = player2MediaPlayer.getCurrentPosition();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 检查是否需要恢复播放器状态
        if (player1NeedsResume) {
            player1NeedsResume = false;
            resumePlayer1FromPosition(player1ResumePosition);
        } else if (isPlayer1Playing) {
            // 如果player1之前是在播放状态，但在暂停后位置回到了0，说明可能出现了问题，恢复到之前的位置
            if (player1MediaPlayer != null && player1MediaPlayer.getCurrentPosition() < player1PausedPosition) {
                player1MediaPlayer.seekTo(player1PausedPosition);
            } else if (player1VideoView != null && player1VideoView.getCurrentPosition() < player1PausedPosition) {
                player1VideoView.seekTo(player1PausedPosition);
            }
        }

        if (player2NeedsResume) {
            player2NeedsResume = false;
            resumePlayer2FromPosition(player2ResumePosition);
        } else if (isPlayer2Playing) {
            // 如果player2之前是在播放状态，但在暂停后位置回到了0，说明可能出现了问题，恢复到之前的位置
            if (player2MediaPlayer != null && player2MediaPlayer.getCurrentPosition() < player2PausedPosition) {
                player2MediaPlayer.seekTo(player2PausedPosition);
            } else if (player2VideoView != null && player2VideoView.getCurrentPosition() < player2PausedPosition) {
                player2VideoView.seekTo(player2PausedPosition);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length <= 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                // 权限被拒绝，提示用户
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateSeekBars); // 清理定时任务

        // 释放Player 1的MediaPlayer
        if (player1MediaPlayer != null) {
            player1MediaPlayer.release();
            player1MediaPlayer = null;
        }
        if (player1VideoView != null) {
            player1VideoView.stopPlayback();
        }

        // 释放Player 2的MediaPlayer
        if (player2MediaPlayer != null) {
            player2MediaPlayer.release();
            player2MediaPlayer = null;
        }
        if (player2VideoView != null) {
            player2VideoView.stopPlayback();
        }
    }
}