package com.genersoft.iot.vmp.service.impl;

import com.genersoft.iot.vmp.gb28181.bean.DeviceAlarm;
import com.genersoft.iot.vmp.service.IDeviceAlarmService;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
import java.util.Date;


@SpringBootTest
@RunWith(SpringRunner.class)
class DeviceAlarmServiceImplTest {

    @Resource
    private IDeviceAlarmService deviceAlarmService;

    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @org.junit.jupiter.api.Test
    void getAllAlarm() {
//        deviceAlarmService.getAllAlarm(0, 10000, "11111111111111111111",null,null,null, null, null);
//        System.out.println(deviceAlarmService.getAllAlarm(0, 10000, null, null, null, null,
//                null, null).getSize());
//
//        System.out.println(deviceAlarmService.getAllAlarm(0, 10000, "11111111111111111111", null, null, null,
//                null, null).getSize());
//
//        System.out.println(deviceAlarmService.getAllAlarm(0, 10000, "11111111111111111111", "1", null, null,
//                null, null).getSize());
//
//        System.out.println(deviceAlarmService.getAllAlarm(0, 10000, "11111111111111111111", "2", null, null,
//                null, null).getSize());
//
//        System.out.println(deviceAlarmService.getAllAlarm(0, 10000, "11111111111111111111", "3", null, null,
//                null, null).getSize());
//
//        System.out.println(deviceAlarmService.getAllAlarm(0, 10000, "11111111111111111111", "4", null, null,
//                null, null).getSize());
//
//        System.out.println(deviceAlarmService.getAllAlarm(0, 10000, "11111111111111111111", "5", null, null,
//                null, null).getSize());
//
//        System.out.println(deviceAlarmService.getAllAlarm(0, 10000, "11111111111111111111", null, "1", null,
//                null, null).getSize());

//        System.out.println(deviceAlarmService.getAllAlarm(0, 10000, "11111111111111111111", null, "1", null,
//                null, null).getSize());


    }


    @org.junit.jupiter.api.Test
    void add() {
        for (int i = 0; i < 1000; i++) {
            DeviceAlarm deviceAlarm = new DeviceAlarm();
            deviceAlarm.setDeviceId("11111111111111111111");
            deviceAlarm.setAlarmDescription("test_" + i);

            /**
             * ???????????? , 1???????????????, 2???????????????, 3???????????????, 4??? GPS??????, 5???????????????, 6?????????????????????,
             * 	 * 7????????????;????????????????????????12?????????????????? ????????????-
             */
            deviceAlarm.setAlarmMethod((int)(Math.random()*7 + 1) + "");
            Date date = randomDate("2021-01-01 00:00:00", "2021-06-01 00:00:00");
            deviceAlarm.setAlarmTime(format.format(date));
            /**
             * ????????????, 1???????????????, 2???????????????, 3???????????????, 4????????? ??????-
             */
            deviceAlarm.setAlarmPriority((int)(Math.random()*4 + 1) + "");
            deviceAlarm.setLongitude(116.325);
            deviceAlarm.setLatitude(39.562);
            deviceAlarmService.add(deviceAlarm);
        }

    }

    @org.junit.jupiter.api.Test
    void clearAlarmBeforeTime() {
        deviceAlarmService.clearAlarmBeforeTime(null,null, null);
    }




    private Date randomDate(String beginDate, String endDate) {
        try {

            Date start = format.parse(beginDate);//??????????????????
            Date end = format.parse(endDate);//??????????????????
            //getTime()??????????????? 1970 ??? 1 ??? 1 ??? 00:00:00 GMT ????????? Date ???????????????????????????
            if (start.getTime() >= end.getTime()) {
                return null;
            }
            long date = random(start.getTime(), end.getTime());
            return new Date(date);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static long random(long begin, long end) {
        long rtn = begin + (long) (Math.random() * (end - begin));
        //???????????????????????????????????????????????????????????????????????????????????????
        if (rtn == begin || rtn == end) {
            return random(begin, end);
        }
        return rtn;
    }
}