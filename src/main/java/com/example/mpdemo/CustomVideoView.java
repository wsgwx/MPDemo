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

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.MediaPlayer;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.VideoView;

import java.lang.reflect.Field;

public class CustomVideoView extends VideoView {
    public CustomVideoView(Context context) {
        super(context);
    }

    public CustomVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomVideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    // 提供方法访问内部的MediaPlayer
    public void setPreferredDevice(AudioDeviceInfo deviceInfo) {
        try {
            // 使用反射获取内部MediaPlayer实例
            Field field = VideoView.class.getDeclaredField("mMediaPlayer");
            field.setAccessible(true);
            MediaPlayer mediaPlayer = (MediaPlayer) field.get(this);
            
            if (mediaPlayer != null) {
                boolean success = mediaPlayer.setPreferredDevice(deviceInfo);
                Log.d("CustomVideoView", "setPreferredDevice " + (success ? "成功" : "失败"));
            } else {
                Log.w("CustomVideoView", "内部MediaPlayer为null");
            }
        } catch (Exception e) {
            Log.e("CustomVideoView", "设置首选设备失败: " + e.getMessage());
        }
    }
}