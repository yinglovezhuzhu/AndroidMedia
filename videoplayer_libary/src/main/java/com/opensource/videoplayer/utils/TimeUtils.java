/*
 * Copyright (C) 2014 The Android Open Source Project.
 *
 *        yinglovezhuzhu@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.opensource.videoplayer.utils;

/**
 * Use:
 * Created by yinglovezhuzhu@gmail.com on 2014-05-19.
 */
public class TimeUtils {

    private TimeUtils() {}

    /**
     * Format time to H:mm:ss
     * @param milliseconds
     * @return
     */
    public static String formatTime(int milliseconds) {
        if(milliseconds < 0) {
            return "00:00";
        }
        StringBuilder sb = new StringBuilder();
        int seconds = milliseconds / 1000 + (milliseconds % 1000 == 0 ? 0 : 1);
        int secondsNum = seconds % 60;
        int minutesNum = seconds / 60;
        int hoursNum = 0;
        if(minutesNum > 59) {
            hoursNum = minutesNum / 60;
            minutesNum = minutesNum % 60;
        }
        if(hoursNum > 59) {
            if(hoursNum < 10) {
                sb.append(0)
                        .append(hoursNum);
            } else {
                sb.append(hoursNum);
            }
            sb.append(":");
        }
        if(minutesNum < 10) {
            sb.append(0)
                    .append(minutesNum);
        } else {
            sb.append(minutesNum);
        }
        sb.append(":");
        if(secondsNum < 10) {
            sb.append(0)
                    .append(secondsNum);
        } else {
            sb.append(secondsNum);
        }
        return sb.toString();
    }
}
