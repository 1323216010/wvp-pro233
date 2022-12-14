package com.genersoft.iot.vmp.media.zlm;

import com.alibaba.fastjson.JSONObject;
import com.genersoft.iot.vmp.conf.UserSetup;
import com.genersoft.iot.vmp.gb28181.bean.GbStream;
import com.genersoft.iot.vmp.media.zlm.dto.MediaItem;
import com.genersoft.iot.vmp.media.zlm.dto.MediaServerItem;
import com.genersoft.iot.vmp.media.zlm.dto.StreamProxyItem;
import com.genersoft.iot.vmp.media.zlm.dto.StreamPushItem;
import com.genersoft.iot.vmp.service.IStreamProxyService;
import com.genersoft.iot.vmp.service.IStreamPushService;
import com.genersoft.iot.vmp.service.bean.ThirdPartyGB;
import com.genersoft.iot.vmp.storager.IRedisCatchStorage;
import com.genersoft.iot.vmp.storager.IVideoManagerStorager;
import com.genersoft.iot.vmp.storager.dao.GbStreamMapper;
import com.genersoft.iot.vmp.storager.dao.PlatformGbStreamMapper;
import com.genersoft.iot.vmp.storager.dao.StreamPushMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ZLMMediaListManager {

    private Logger logger = LoggerFactory.getLogger("ZLMMediaListManager");

    @Autowired
    private ZLMRESTfulUtils zlmresTfulUtils;

    @Autowired
    private IRedisCatchStorage redisCatchStorage;

    @Autowired
    private IVideoManagerStorager storager;

    @Autowired
    private GbStreamMapper gbStreamMapper;

    @Autowired
    private PlatformGbStreamMapper platformGbStreamMapper;

    @Autowired
    private IStreamPushService streamPushService;

    @Autowired
    private IStreamProxyService streamProxyService;

    @Autowired
    private StreamPushMapper streamPushMapper;

    @Autowired
    private ZLMHttpHookSubscribe subscribe;

    @Autowired
    private UserSetup userSetup;


    public void updateMediaList(MediaServerItem mediaServerItem) {
        storager.clearMediaList();

        // ??????????????????????????????????????????
        zlmresTfulUtils.getMediaList(mediaServerItem, (mediaList ->{
            if (mediaList == null) return;
            String dataStr = mediaList.getString("data");

            Integer code = mediaList.getInteger("code");
            Map<String, StreamPushItem> result = new HashMap<>();
            List<StreamPushItem> streamPushItems = null;
            // ???????????????????????????
//            List<GbStream> gbStreams = gbStreamMapper.selectAllByMediaServerId(mediaServerItem.getId());
            if (code == 0 ) {
                if (dataStr != null) {
                    streamPushItems = streamPushService.handleJSON(dataStr, mediaServerItem);
                }
            }else {
                logger.warn("??????????????????????????????code??? " + code);
            }

            if (streamPushItems != null) {
                storager.updateMediaList(streamPushItems);
                for (StreamPushItem streamPushItem : streamPushItems) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("app", streamPushItem.getApp());
                    jsonObject.put("stream", streamPushItem.getStream());
                    jsonObject.put("mediaServerId", mediaServerItem.getId());
                    subscribe.addSubscribe(ZLMHttpHookSubscribe.HookType.on_play,jsonObject,
                            (MediaServerItem mediaServerItemInuse, JSONObject response)->{
                                updateMedia(mediaServerItem, response.getString("app"), response.getString("stream"));
                            }
                    );
                }
            }
        }));

    }

    public void addMedia(MediaServerItem mediaServerItem, String app, String streamId) {
        //????????????????????????
        updateMedia(mediaServerItem, app, streamId);
    }

    public StreamPushItem addPush(MediaItem mediaItem) {
        // ??????????????????????????????redis??????gbId
        StreamPushItem transform = streamPushService.transform(mediaItem);
        // ???streamId?????????????????????
        Pattern pattern = Pattern.compile(userSetup.getThirdPartyGBIdReg());
        Matcher matcher = pattern.matcher(mediaItem.getStream());// ???????????????????????????
        String queryKey = null;
        if (matcher.find()) { //??????find??????????????????????????????????????????????????????
            queryKey = matcher.group();
        }
        if (queryKey != null) {
            ThirdPartyGB thirdPartyGB = redisCatchStorage.queryMemberNoGBId(queryKey);
            if (thirdPartyGB != null && !StringUtils.isEmpty(thirdPartyGB.getNationalStandardNo())) {
                transform.setGbId(thirdPartyGB.getNationalStandardNo());
                transform.setName(thirdPartyGB.getName());
            }
        }
        if (!StringUtils.isEmpty(transform.getGbId())) {
            // ??????????????????ID???????????????????????????????????????????????????????????????
            List<GbStream> gbStreams = gbStreamMapper.selectByGBId(transform.getGbId());
            if (gbStreams.size() > 0) {
                for (GbStream gbStream : gbStreams) {
                    // ????????????????????????Id?????????????????????????????????????????????
                    if (queryKey != null && gbStream.getApp().equals(mediaItem.getApp())) {
                        Matcher matcherForStream = pattern.matcher(gbStream.getStream());
                        String queryKeyForStream = null;
                        if (matcherForStream.find()) { //??????find??????????????????????????????????????????????????????
                            queryKeyForStream = matcherForStream.group();
                        }
                        if (queryKeyForStream == null || !queryKeyForStream.equals(queryKey)) {
                            // ????????????????????????
                            gbStreamMapper.del(gbStream.getApp(), gbStream.getStream());
                            if (!gbStream.isStatus()) {
                                streamPushMapper.del(gbStream.getApp(), gbStream.getStream());
                            }
                        }
                    }
                }
            }
            //            StreamProxyItem streamProxyItem = gbStreamMapper.selectOne(transform.getApp(), transform.getStream());
            List<GbStream> gbStreamList = gbStreamMapper.selectByGBId(transform.getGbId());
            if (gbStreamList != null && gbStreamList.size() == 1) {
                transform.setGbStreamId(gbStreamList.get(0).getGbStreamId());
                transform.setPlatformId(gbStreamList.get(0).getPlatformId());
                transform.setCatalogId(gbStreamList.get(0).getCatalogId());
                transform.setGbId(gbStreamList.get(0).getGbId());
                gbStreamMapper.update(transform);
                streamPushMapper.del(gbStreamList.get(0).getApp(), gbStreamList.get(0).getStream());
            }else {
                transform.setCreateStamp(System.currentTimeMillis());
                gbStreamMapper.add(transform);
            }
        }
        storager.updateMedia(transform);
        return transform;
    }


    public void updateMedia(MediaServerItem mediaServerItem, String app, String streamId) {
        //????????????????????????
        zlmresTfulUtils.getMediaList(mediaServerItem, app, streamId, "rtmp", json->{

            if (json == null) return;
            String dataStr = json.getString("data");

            Integer code = json.getInteger("code");
            Map<String, StreamPushItem> result = new HashMap<>();
            List<StreamPushItem> streamPushItems = null;
            if (code == 0 ) {
                if (dataStr != null) {
                    streamPushItems = streamPushService.handleJSON(dataStr, mediaServerItem);
                }
            }else {
                logger.warn("??????????????????????????????code??? " + code);
            }

            if (streamPushItems != null && streamPushItems.size() == 1) {
                storager.updateMedia(streamPushItems.get(0));
            }
        });
    }


    public int removeMedia(String app, String streamId) {
        // ?????????????????????????????? ????????????????????? ????????????
        StreamProxyItem streamProxyItem = gbStreamMapper.selectOne(app, streamId);
        int result = 0;
        if (streamProxyItem == null) {
            result = storager.removeMedia(app, streamId);
        }else {
            result =storager.mediaOutline(app, streamId);
        }
        return result;
    }



//    public void clearAllSessions() {
//        logger.info("???????????????????????????session");
//        JSONObject allSessionJSON = zlmresTfulUtils.getAllSession();
//        ZLMServerConfig mediaInfo = redisCatchStorage.getMediaInfo();
//        HashSet<String> allLocalPorts = new HashSet();
//        if (allSessionJSON.getInteger("code") == 0) {
//            JSONArray data = allSessionJSON.getJSONArray("data");
//            if (data.size() > 0) {
//                for (int i = 0; i < data.size(); i++) {
//                    JSONObject sessionJOSN = data.getJSONObject(i);
//                    Integer local_port = sessionJOSN.getInteger("local_port");
//                    if (!local_port.equals(Integer.valueOf(mediaInfo.getHttpPort())) &&
//                        !local_port.equals(Integer.valueOf(mediaInfo.getHttpSSLport())) &&
//                        !local_port.equals(Integer.valueOf(mediaInfo.getRtmpPort())) &&
//                        !local_port.equals(Integer.valueOf(mediaInfo.getRtspPort())) &&
//                        !local_port.equals(Integer.valueOf(mediaInfo.getRtspSSlport())) &&
//                        !local_port.equals(Integer.valueOf(mediaInfo.getHookOnFlowReport()))){
//                        allLocalPorts.add(sessionJOSN.getInteger("local_port") + "");
//                     }
//                }
//            }
//        }
//        if (allLocalPorts.size() > 0) {
//            List<String> result = new ArrayList<>(allLocalPorts);
//            String localPortSStr = String.join(",", result);
//            zlmresTfulUtils.kickSessions(localPortSStr);
//        }
//    }
}
