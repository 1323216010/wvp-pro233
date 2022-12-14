package com.genersoft.iot.vmp.media.zlm;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.genersoft.iot.vmp.conf.MediaConfig;
import com.genersoft.iot.vmp.gb28181.event.EventPublisher;
import com.genersoft.iot.vmp.media.zlm.dto.MediaServerItem;
import com.genersoft.iot.vmp.service.IMediaServerService;
import com.genersoft.iot.vmp.service.IStreamProxyService;
import com.genersoft.iot.vmp.storager.IRedisCatchStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;

@Component
@Order(value=1)
public class ZLMRunner implements CommandLineRunner {

    private final static Logger logger = LoggerFactory.getLogger(ZLMRunner.class);

    private Map<String, Boolean> startGetMedia;

    @Autowired
    private ZLMRESTfulUtils zlmresTfulUtils;

    @Autowired
    private ZLMHttpHookSubscribe hookSubscribe;

    @Autowired
    private IStreamProxyService streamProxyService;

    @Autowired
    private EventPublisher publisher;

    @Autowired
    private IMediaServerService mediaServerService;

    @Autowired
    private IRedisCatchStorage redisCatchStorage;

    @Autowired
    private MediaConfig mediaConfig;

    @Qualifier("taskExecutor")
    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    @Override
    public void run(String... strings) throws Exception {
        mediaServerService.clearMediaServerForOnline();
        MediaServerItem defaultMediaServer = mediaServerService.getDefaultMediaServer();
        if (defaultMediaServer == null) {
            mediaServerService.addToDatabase(mediaConfig.getMediaSerItem());
        }else {
            MediaServerItem mediaSerItem = mediaConfig.getMediaSerItem();
            mediaServerService.updateToDatabase(mediaSerItem);
        }
        mediaServerService.syncCatchFromDatabase();
        // ?????? zlm????????????, ??????zlm???????????????????????????
        hookSubscribe.addSubscribe(ZLMHttpHookSubscribe.HookType.on_server_started,null,
                (MediaServerItem mediaServerItem, JSONObject response)->{
            ZLMServerConfig zlmServerConfig = JSONObject.toJavaObject(response, ZLMServerConfig.class);
            if (zlmServerConfig !=null ) {
                if (startGetMedia != null) {
                    startGetMedia.remove(zlmServerConfig.getGeneralMediaServerId());
                }
                mediaServerService.zlmServerOnline(zlmServerConfig);
            }
        });

        // ?????? zlm????????????, ???zlm???????????????????????????
        hookSubscribe.addSubscribe(ZLMHttpHookSubscribe.HookType.on_server_keepalive,null,
                (MediaServerItem mediaServerItem, JSONObject response)->{
                    String mediaServerId = response.getString("mediaServerId");
                    if (mediaServerId !=null ) {
                        mediaServerService.updateMediaServerKeepalive(mediaServerId, response.getJSONObject("data"));
                    }
                });

        // ??????zlm??????
        logger.info("[zlm??????]????????????zlm???...");

        // ???????????????zlm??? ?????????????????????
        List<MediaServerItem> all = mediaServerService.getAllFromDatabase();
        mediaServerService.updateVmServer(all);
        if (all.size() == 0) {
            all.add(mediaConfig.getMediaSerItem());
        }
        for (MediaServerItem mediaServerItem : all) {
            if (startGetMedia == null) startGetMedia = new HashMap<>();
            startGetMedia.put(mediaServerItem.getId(), true);
            taskExecutor.execute(()->{
                connectZlmServer(mediaServerItem);
            });
        }
        Timer timer = new Timer();
        // 10?????????????????????????????????????????????, TODO ???????????????????????????zlm???????????????bye
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
            if (startGetMedia != null) {
                Set<String> allZlmId = startGetMedia.keySet();
                for (String id : allZlmId) {
                    logger.error("[ {} ]]???????????????????????????????????????", id);
                }
                startGetMedia = null;
            }
            //  TODO ?????????????????????redis????????????zlm
            }
        }, 60 * 1000 * 10);
    }

    @Async
    public void connectZlmServer(MediaServerItem mediaServerItem){
        ZLMServerConfig zlmServerConfig = getMediaServerConfig(mediaServerItem, 1);
        if (zlmServerConfig != null) {
            zlmServerConfig.setIp(mediaServerItem.getIp());
            zlmServerConfig.setHttpPort(mediaServerItem.getHttpPort());
            startGetMedia.remove(mediaServerItem.getId());
            mediaServerService.zlmServerOnline(zlmServerConfig);
        }
    }

    public ZLMServerConfig getMediaServerConfig(MediaServerItem mediaServerItem, int index) {
        if (startGetMedia == null) { return null;}
        if (!mediaServerItem.isDefaultServer() && mediaServerService.getOne(mediaServerItem.getId()) == null) {
            return null;
        }
        if ( startGetMedia.get(mediaServerItem.getId()) == null || !startGetMedia.get(mediaServerItem.getId())) {
            return null;
        }
        JSONObject responseJSON = zlmresTfulUtils.getMediaServerConfig(mediaServerItem);
        ZLMServerConfig ZLMServerConfig = null;
        if (responseJSON != null) {
            JSONArray data = responseJSON.getJSONArray("data");
            if (data != null && data.size() > 0) {
                ZLMServerConfig = JSON.parseObject(JSON.toJSONString(data.get(0)), ZLMServerConfig.class);
            }
        } else {
            logger.error("[ {} ]-[ {}:{} ]???{}?????????????????????, 2s?????????",
                    mediaServerItem.getId(), mediaServerItem.getIp(), mediaServerItem.getHttpPort(), index);
            if (index == 1 && !StringUtils.isEmpty(mediaServerItem.getId())) {
                logger.info("[ {} ]-[ {}:{} ]???{}?????????????????????, ????????????????????????",
                        mediaServerItem.getId(), mediaServerItem.getIp(), mediaServerItem.getHttpPort(), index);
                publisher.zlmOfflineEventPublish(mediaServerItem.getId());
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            ZLMServerConfig = getMediaServerConfig(mediaServerItem, index += 1);
        }
        return ZLMServerConfig;

    }

    /**
     * zlm ??????????????????zlm?????????
     */
//    private void zLmRunning(ZLMServerConfig zlmServerConfig){
//        logger.info( "[ id: " + zlmServerConfig.getGeneralMediaServerId() + "] zlm????????????...");
//        // ??????????????????zlm??????
//        startGetMedia = false;
//        MediaServerItem mediaServerItem = new MediaServerItem(zlmServerConfig, sipIp);
//        storager.updateMediaServer(mediaServerItem);
//
//        if (mediaServerItem.isAutoConfig()) setZLMConfig(mediaServerItem);
//        zlmServerManger.updateServerCatchFromHook(zlmServerConfig);
//
//        // ????????????session
////        zlmMediaListManager.clearAllSessions();
//
//        // ???????????????
//        zlmMediaListManager.updateMediaList(mediaServerItem);
//        // ???????????????, ??????????????????????????????
//        List<StreamProxyItem> streamProxyListForEnable = storager.getStreamProxyListForEnableInMediaServer(
//                mediaServerItem.getId(), true);
//        for (StreamProxyItem streamProxyDto : streamProxyListForEnable) {
//            logger.info("??????????????????" + streamProxyDto.getApp() + "/" + streamProxyDto.getStream());
//            JSONObject jsonObject = streamProxyService.addStreamProxyToZlm(streamProxyDto);
//            if (jsonObject == null) {
//                // ??????????????????
//                logger.info("?????????????????????????????????????????????????????????" + streamProxyDto.getApp() + "/" + streamProxyDto.getStream());
//                streamProxyService.stop(streamProxyDto.getApp(), streamProxyDto.getStream());
//            }
//        }
//    }
}
