package com.genersoft.iot.vmp.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.genersoft.iot.vmp.common.StreamInfo;
import com.genersoft.iot.vmp.conf.UserSetup;
import com.genersoft.iot.vmp.gb28181.bean.*;
import com.genersoft.iot.vmp.gb28181.event.SipSubscribe;
import com.genersoft.iot.vmp.gb28181.session.VideoStreamSessionManager;
import com.genersoft.iot.vmp.gb28181.transmit.callback.DeferredResultHolder;
import com.genersoft.iot.vmp.gb28181.transmit.callback.RequestMessage;
import com.genersoft.iot.vmp.gb28181.transmit.cmd.impl.SIPCommander;
import com.genersoft.iot.vmp.gb28181.transmit.cmd.impl.SIPCommanderFroPlatform;
import com.genersoft.iot.vmp.gb28181.utils.DateUtil;
import com.genersoft.iot.vmp.media.zlm.AssistRESTfulUtils;
import com.genersoft.iot.vmp.media.zlm.ZLMHttpHookSubscribe;
import com.genersoft.iot.vmp.media.zlm.ZLMRESTfulUtils;
import com.genersoft.iot.vmp.media.zlm.dto.MediaServerItem;
import com.genersoft.iot.vmp.service.IMediaServerService;
import com.genersoft.iot.vmp.service.bean.InviteTimeOutCallback;
import com.genersoft.iot.vmp.service.bean.PlayBackCallback;
import com.genersoft.iot.vmp.service.bean.PlayBackResult;
import com.genersoft.iot.vmp.service.bean.SSRCInfo;
import com.genersoft.iot.vmp.storager.IRedisCatchStorage;
import com.genersoft.iot.vmp.storager.IVideoManagerStorager;
import com.genersoft.iot.vmp.utils.redis.RedisUtil;
import com.genersoft.iot.vmp.vmanager.bean.WVPResult;
import com.genersoft.iot.vmp.vmanager.gb28181.play.bean.PlayResult;
import com.genersoft.iot.vmp.service.IMediaService;
import com.genersoft.iot.vmp.service.IPlayService;
import gov.nist.javax.sip.stack.SIPDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;
import org.springframework.web.context.request.async.DeferredResult;

import java.io.FileNotFoundException;
import java.math.BigDecimal;
import java.util.*;

@SuppressWarnings(value = {"rawtypes", "unchecked"})
@Service
public class PlayServiceImpl implements IPlayService {

    private final static Logger logger = LoggerFactory.getLogger(PlayServiceImpl.class);

    @Autowired
    private IVideoManagerStorager storager;

    @Autowired
    private SIPCommander cmder;

    @Autowired
    private SIPCommanderFroPlatform sipCommanderFroPlatform;

    @Autowired
    private IRedisCatchStorage redisCatchStorage;

    @Autowired
    private RedisUtil redis;

    @Autowired
    private DeferredResultHolder resultHolder;

    @Autowired
    private ZLMRESTfulUtils zlmresTfulUtils;

    @Autowired
    private AssistRESTfulUtils assistRESTfulUtils;

    @Autowired
    private IMediaService mediaService;

    @Autowired
    private IMediaServerService mediaServerService;

    @Autowired
    private VideoStreamSessionManager streamSession;

    @Autowired
    private UserSetup userSetup;




    @Override
    public PlayResult play(MediaServerItem mediaServerItem, String deviceId, String channelId,
                           ZLMHttpHookSubscribe.Event hookEvent, SipSubscribe.Event errorEvent,
                           Runnable timeoutCallback) {
        PlayResult playResult = new PlayResult();
        RequestMessage msg = new RequestMessage();
        String key = DeferredResultHolder.CALLBACK_CMD_PLAY + deviceId + channelId;
        msg.setKey(key);
        String uuid = UUID.randomUUID().toString();
        msg.setId(uuid);
        playResult.setUuid(uuid);
        DeferredResult<ResponseEntity<String>> result = new DeferredResult<>(userSetup.getPlayTimeout());
        playResult.setResult(result);
        // ???????????????channelId??????deviceId??????
        resultHolder.put(key, uuid, result);
        if (mediaServerItem == null) {
            WVPResult wvpResult = new WVPResult();
            wvpResult.setCode(-1);
            wvpResult.setMsg("??????????????????zlm");
            msg.setData(wvpResult);
            resultHolder.invokeResult(msg);
            return playResult;
        }
        Device device = redisCatchStorage.getDevice(deviceId);
        StreamInfo streamInfo = redisCatchStorage.queryPlayByDevice(deviceId, channelId);
        playResult.setDevice(device);

        result.onCompletion(()->{
            // ?????????????????????????????????
            // TODO ???????????????????????????????????????????????????????????????
            try {
                String classPath = ResourceUtils.getURL("classpath:").getPath();
                // ???????????????jar???class??????
                if(classPath.contains("jar")) {
                    classPath = classPath.substring(0, classPath.lastIndexOf("."));
                    classPath = classPath.substring(0, classPath.lastIndexOf("/") + 1);
                }
                if (classPath.startsWith("file:")) {
                    classPath = classPath.substring(classPath.indexOf(":") + 1);
                }
                String path = classPath + "static/static/snap/";
                // ??????Windows?????????????????????????????????/??????
                if(System.getProperty("os.name").contains("indows")) {
                    path = path.substring(1);
                }
                String fileName =  deviceId + "_" + channelId + ".jpg";
                ResponseEntity responseEntity =  (ResponseEntity)result.getResult();
                if (responseEntity != null && responseEntity.getStatusCode() == HttpStatus.OK) {
                    WVPResult wvpResult = (WVPResult)responseEntity.getBody();
                    if (Objects.requireNonNull(wvpResult).getCode() == 0) {
                        StreamInfo streamInfoForSuccess = (StreamInfo)wvpResult.getData();
                        MediaServerItem mediaInfo = mediaServerService.getOne(streamInfoForSuccess.getMediaServerId());
                        String streamUrl = streamInfoForSuccess.getFmp4();
                        // ????????????
                        logger.info("[????????????]: " + fileName);
                        zlmresTfulUtils.getSnap(mediaInfo, streamUrl, 15, 1, path, fileName);
                    }
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        });
        if (streamInfo != null) {
            String streamId = streamInfo.getStream();
            if (streamId == null) {
                WVPResult wvpResult = new WVPResult();
                wvpResult.setCode(-1);
                wvpResult.setMsg("??????????????? redis??????streamId??????null");
                msg.setData(wvpResult);
                resultHolder.invokeAllResult(msg);
                return playResult;
            }
            String mediaServerId = streamInfo.getMediaServerId();
            MediaServerItem mediaInfo = mediaServerService.getOne(mediaServerId);

            JSONObject rtpInfo = zlmresTfulUtils.getRtpInfo(mediaInfo, streamId);
            if (rtpInfo != null && rtpInfo.getBoolean("exist")) {

                WVPResult wvpResult = new WVPResult();
                wvpResult.setCode(0);
                wvpResult.setMsg("success");
                wvpResult.setData(streamInfo);
                msg.setData(wvpResult);

                resultHolder.invokeAllResult(msg);
                if (hookEvent != null) {
                    hookEvent.response(mediaServerItem, JSONObject.parseObject(JSON.toJSONString(streamInfo)));
                }
            }else {
                redisCatchStorage.stopPlay(streamInfo);
                storager.stopPlay(streamInfo.getDeviceID(), streamInfo.getChannelId());
                streamInfo = null;
            }

        }
        if (streamInfo == null) {
            String streamId = null;
            if (mediaServerItem.isRtpEnable()) {
                streamId = String.format("%s_%s", device.getDeviceId(), channelId);
            }
            SSRCInfo ssrcInfo = mediaServerService.openRTPServer(mediaServerItem, streamId);
            play(mediaServerItem, ssrcInfo, device, channelId, (mediaServerItemInUse, response)->{
                if (hookEvent != null) {
                    hookEvent.response(mediaServerItem, response);
                }
            }, event -> {
                // sip error??????
                WVPResult wvpResult = new WVPResult();
                wvpResult.setCode(-1);
                wvpResult.setMsg(String.format("??????????????? ???????????? %s, %s", event.statusCode, event.msg));
                msg.setData(wvpResult);
                resultHolder.invokeAllResult(msg);
                if (errorEvent != null) {
                    errorEvent.response(event);
                }
            }, (code, msgStr)->{
                // invite????????????
                WVPResult wvpResult = new WVPResult();
                wvpResult.setCode(-1);
                if (code == 0) {
                    wvpResult.setMsg("??????????????????????????????");
                }else if (code == 1) {
                    wvpResult.setMsg("??????????????????????????????");
                }
                msg.setData(wvpResult);
                // ?????????????????????????????????
                resultHolder.invokeAllResult(msg);
            }, uuid);
        }
        return playResult;
    }



    @Override
    public void play(MediaServerItem mediaServerItem, SSRCInfo ssrcInfo, Device device, String channelId,
                           ZLMHttpHookSubscribe.Event hookEvent, SipSubscribe.Event errorEvent,
                           InviteTimeOutCallback timeoutCallback, String uuid) {

        String streamId = null;
        if (mediaServerItem.isRtpEnable()) {
            streamId = String.format("%s_%s", device.getDeviceId(), channelId);
        }
        if (ssrcInfo == null) {
            ssrcInfo = mediaServerService.openRTPServer(mediaServerItem, streamId);
        }

        // ????????????
        Timer timer = new Timer();
        SSRCInfo finalSsrcInfo = ssrcInfo;
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                logger.warn(String.format("?????????????????????deviceId???%s ???channelId???%s", device.getDeviceId(), channelId));

                SIPDialog dialog = streamSession.getDialogByStream(device.getDeviceId(), channelId, finalSsrcInfo.getStream());
                if (dialog != null) {
                    timeoutCallback.run(1, "????????????");
                    // ??????????????????BYE ????????????ssrc???????????????????????????
                    cmder.streamByeCmd(device.getDeviceId(), channelId, finalSsrcInfo.getStream(), null);
                }else {
                    timeoutCallback.run(0, "????????????");
                    mediaServerService.releaseSsrc(mediaServerItem.getId(), finalSsrcInfo.getSsrc());
                    mediaServerService.closeRTPServer(device.getDeviceId(), channelId, finalSsrcInfo.getStream());
                    streamSession.remove(device.getDeviceId(), channelId, finalSsrcInfo.getStream());
                }
            }
        }, userSetup.getPlayTimeout());

        cmder.playStreamCmd(mediaServerItem, ssrcInfo, device, channelId, (MediaServerItem mediaServerItemInuse, JSONObject response) -> {
            logger.info("????????????????????? " + response.toJSONString());
            timer.cancel();
            // hook??????
            onPublishHandlerForPlay(mediaServerItemInuse, response, device.getDeviceId(), channelId, uuid);
            hookEvent.response(mediaServerItemInuse, response);
        }, (event) -> {
            timer.cancel();
            mediaServerService.closeRTPServer(device.getDeviceId(), channelId, finalSsrcInfo.getStream());
            // ??????ssrc
            mediaServerService.releaseSsrc(mediaServerItem.getId(), finalSsrcInfo.getSsrc());
            streamSession.remove(device.getDeviceId(), channelId, finalSsrcInfo.getStream());
            errorEvent.response(event);
        });
    }

    @Override
    public void onPublishHandlerForPlay(MediaServerItem mediaServerItem, JSONObject response, String deviceId, String channelId, String uuid) {
        RequestMessage msg = new RequestMessage();
        if (uuid != null) {
            msg.setId(uuid);
        }
        msg.setKey(DeferredResultHolder.CALLBACK_CMD_PLAY + deviceId + channelId);
        StreamInfo streamInfo = onPublishHandler(mediaServerItem, response, deviceId, channelId);
        if (streamInfo != null) {
            DeviceChannel deviceChannel = storager.queryChannel(deviceId, channelId);
            if (deviceChannel != null) {
                deviceChannel.setStreamId(streamInfo.getStream());
                storager.startPlay(deviceId, channelId, streamInfo.getStream());
            }
            redisCatchStorage.startPlay(streamInfo);

            WVPResult wvpResult = new WVPResult();
            wvpResult.setCode(0);
            wvpResult.setMsg("success");
            wvpResult.setData(streamInfo);
            msg.setData(wvpResult);

            resultHolder.invokeAllResult(msg);
        } else {
            logger.warn("????????????API???????????????");
            msg.setData("????????????API???????????????");
            resultHolder.invokeAllResult(msg);
        }
    }

    @Override
    public MediaServerItem getNewMediaServerItem(Device device) {
        if (device == null) return null;
        String mediaServerId = device.getMediaServerId();
        MediaServerItem mediaServerItem;
        if (mediaServerId == null) {
            mediaServerItem = mediaServerService.getMediaServerForMinimumLoad();
        }else {
            mediaServerItem = mediaServerService.getOne(mediaServerId);
        }
        if (mediaServerItem == null) {
            logger.warn("??????????????????????????????ZLM...");
        }
        return mediaServerItem;
    }

    @Override
    public DeferredResult<ResponseEntity<String>> playBack(String deviceId, String channelId, String startTime,
                                                           String endTime,InviteStreamCallback inviteStreamCallback,
                                                           PlayBackCallback callback) {
        Device device = storager.queryVideoDevice(deviceId);
        if (device == null) return null;
        MediaServerItem newMediaServerItem = getNewMediaServerItem(device);
        SSRCInfo ssrcInfo = mediaServerService.openRTPServer(newMediaServerItem, null, true);

        return playBack(newMediaServerItem, ssrcInfo, deviceId, channelId, startTime, endTime, inviteStreamCallback, callback);
    }

    @Override
    public DeferredResult<ResponseEntity<String>> playBack(MediaServerItem mediaServerItem, SSRCInfo ssrcInfo,
                                                           String deviceId, String channelId, String startTime,
                                                           String endTime, InviteStreamCallback infoCallBack,
                                                           PlayBackCallback playBackCallback) {
        if (mediaServerItem == null || ssrcInfo == null) return null;
        String uuid = UUID.randomUUID().toString();
        String key = DeferredResultHolder.CALLBACK_CMD_PLAYBACK + deviceId + channelId;
        DeferredResult<ResponseEntity<String>> result = new DeferredResult<>(30000L);
        Device device = storager.queryVideoDevice(deviceId);
        if (device == null) {
            result.setResult(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
            return result;
        }

        resultHolder.put(DeferredResultHolder.CALLBACK_CMD_PLAYBACK + deviceId + channelId, uuid, result);
        RequestMessage msg = new RequestMessage();
        msg.setId(uuid);
        msg.setKey(key);
        PlayBackResult<RequestMessage> playBackResult = new PlayBackResult<>();

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                logger.warn(String.format("?????????????????????deviceId???%s ???channelId???%s", deviceId, channelId));
                playBackResult.setCode(-1);
                playBackResult.setData(msg);
                playBackCallback.call(playBackResult);
                SIPDialog dialog = streamSession.getDialogByStream(deviceId, channelId, ssrcInfo.getStream());
                // ??????????????????BYE ????????????ssrc???????????????????????????
                if (dialog != null) {
                    // ??????????????????BYE ????????????ssrc???????????????????????????
                    cmder.streamByeCmd(device.getDeviceId(), channelId, ssrcInfo.getStream(), null);
                }else {
                    mediaServerService.releaseSsrc(mediaServerItem.getId(), ssrcInfo.getSsrc());
                    mediaServerService.closeRTPServer(deviceId, channelId, ssrcInfo.getStream());
                    streamSession.remove(deviceId, channelId, ssrcInfo.getStream());
                }
                cmder.streamByeCmd(device.getDeviceId(), channelId, ssrcInfo.getStream(), null);
                // ?????????????????????????????????
                playBackCallback.call(playBackResult);
            }
        }, userSetup.getPlayTimeout());
        cmder.playbackStreamCmd(mediaServerItem, ssrcInfo, device, channelId, startTime, endTime, infoCallBack,
                (InviteStreamInfo inviteStreamInfo) -> {
                    logger.info("????????????????????? " + inviteStreamInfo.getResponse().toJSONString());
                    timer.cancel();
                    StreamInfo streamInfo = onPublishHandler(inviteStreamInfo.getMediaServerItem(), inviteStreamInfo.getResponse(), deviceId, channelId);
                    if (streamInfo == null) {
                        logger.warn("????????????API???????????????");
                        msg.setData("????????????API???????????????");
                        playBackResult.setCode(-1);
                        playBackResult.setData(msg);
                        playBackCallback.call(playBackResult);
                        return;
                    }
                    redisCatchStorage.startPlayback(streamInfo, inviteStreamInfo.getCallId());
                    msg.setData(JSON.toJSONString(streamInfo));
                    playBackResult.setCode(0);
                    playBackResult.setData(msg);
                    playBackResult.setMediaServerItem(inviteStreamInfo.getMediaServerItem());
                    playBackResult.setResponse(inviteStreamInfo.getResponse());
                    playBackCallback.call(playBackResult);
                }, event -> {
                    timer.cancel();
                    msg.setData(String.format("??????????????? ???????????? %s, %s", event.statusCode, event.msg));
                    playBackResult.setCode(-1);
                    playBackResult.setData(msg);
                    playBackResult.setEvent(event);
                    playBackCallback.call(playBackResult);
                    streamSession.remove(device.getDeviceId(), channelId, ssrcInfo.getStream());
                });
        return result;
    }

    @Override
    public DeferredResult<ResponseEntity<String>> download(String deviceId, String channelId, String startTime, String endTime, int downloadSpeed, InviteStreamCallback infoCallBack, PlayBackCallback hookCallBack) {
        Device device = storager.queryVideoDevice(deviceId);
        if (device == null) return null;
        MediaServerItem newMediaServerItem = getNewMediaServerItem(device);
        SSRCInfo ssrcInfo = mediaServerService.openRTPServer(newMediaServerItem, null, true);

        return download(newMediaServerItem, ssrcInfo, deviceId, channelId, startTime, endTime, downloadSpeed,infoCallBack, hookCallBack);
    }

    @Override
    public DeferredResult<ResponseEntity<String>> download(MediaServerItem mediaServerItem, SSRCInfo ssrcInfo, String deviceId, String channelId, String startTime, String endTime, int downloadSpeed, InviteStreamCallback infoCallBack, PlayBackCallback hookCallBack) {
        if (mediaServerItem == null || ssrcInfo == null) return null;
        String uuid = UUID.randomUUID().toString();
        String key = DeferredResultHolder.CALLBACK_CMD_DOWNLOAD + deviceId + channelId;
        DeferredResult<ResponseEntity<String>> result = new DeferredResult<>(30000L);
        Device device = storager.queryVideoDevice(deviceId);
        if (device == null) {
            result.setResult(new ResponseEntity<>(HttpStatus.BAD_REQUEST));
            return result;
        }

        resultHolder.put(key, uuid, result);
        RequestMessage msg = new RequestMessage();
        msg.setId(uuid);
        msg.setKey(key);
        WVPResult<StreamInfo> wvpResult = new WVPResult<>();
        msg.setData(wvpResult);
        PlayBackResult<RequestMessage> downloadResult = new PlayBackResult<>();
        downloadResult.setData(msg);

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                logger.warn(String.format("???????????????????????????deviceId???%s ???channelId???%s", deviceId, channelId));
                wvpResult.setCode(-1);
                wvpResult.setMsg("????????????????????????");
                downloadResult.setCode(-1);
                hookCallBack.call(downloadResult);
                SIPDialog dialog = streamSession.getDialogByStream(deviceId, channelId, ssrcInfo.getStream());
                // ??????????????????BYE ????????????ssrc???????????????????????????
                if (dialog != null) {
                    // ??????????????????BYE ????????????ssrc???????????????????????????
                    cmder.streamByeCmd(device.getDeviceId(), channelId, ssrcInfo.getStream(), null);
                }else {
                    mediaServerService.releaseSsrc(mediaServerItem.getId(), ssrcInfo.getSsrc());
                    mediaServerService.closeRTPServer(deviceId, channelId, ssrcInfo.getStream());
                    streamSession.remove(deviceId, channelId, ssrcInfo.getStream());
                }
                cmder.streamByeCmd(device.getDeviceId(), channelId, ssrcInfo.getStream(), null);
                // ?????????????????????????????????
                hookCallBack.call(downloadResult);
            }
        }, userSetup.getPlayTimeout());
        cmder.downloadStreamCmd(mediaServerItem, ssrcInfo, device, channelId, startTime, endTime, downloadSpeed, infoCallBack,
                inviteStreamInfo -> {
                    logger.info("????????????????????? " + inviteStreamInfo.getResponse().toJSONString());
                    timer.cancel();
                    StreamInfo streamInfo = onPublishHandler(inviteStreamInfo.getMediaServerItem(), inviteStreamInfo.getResponse(), deviceId, channelId);
                    streamInfo.setStartTime(startTime);
                    streamInfo.setEndTime(endTime);
                    if (streamInfo == null) {
                        logger.warn("????????????API???????????????");
                        wvpResult.setCode(-1);
                        wvpResult.setMsg("????????????API????????????");
                        downloadResult.setCode(-1);
                        hookCallBack.call(downloadResult);
                        return ;
                    }
                    redisCatchStorage.startDownload(streamInfo, inviteStreamInfo.getCallId());
                    wvpResult.setCode(0);
                    wvpResult.setMsg("success");
                    wvpResult.setData(streamInfo);
                    downloadResult.setCode(0);
                    downloadResult.setMediaServerItem(inviteStreamInfo.getMediaServerItem());
                    downloadResult.setResponse(inviteStreamInfo.getResponse());
                    hookCallBack.call(downloadResult);
                }, event -> {
                    timer.cancel();
                    downloadResult.setCode(-1);
                    wvpResult.setCode(-1);
                    wvpResult.setMsg(String.format("????????????????????? ???????????? %s, %s", event.statusCode, event.msg));
                    downloadResult.setEvent(event);
                    hookCallBack.call(downloadResult);
                    streamSession.remove(device.getDeviceId(), channelId, ssrcInfo.getStream());
                });
        return result;
    }

    @Override
    public StreamInfo getDownLoadInfo(String deviceId, String channelId, String stream) {
        StreamInfo streamInfo = redisCatchStorage.queryDownload(deviceId, channelId, stream, null);
        if (streamInfo != null) {
            if (streamInfo.getProgress() == 1) {
                return streamInfo;
            }

            // ???????????????????????????
            String mediaServerId = streamInfo.getMediaServerId();
            MediaServerItem mediaServerItem = mediaServerService.getOne(mediaServerId);
            if (mediaServerItem == null) {
                logger.warn("??????????????????????????????????????????");
                return null;
            }
            if (mediaServerItem.getRecordAssistPort() != 0) {
                JSONObject jsonObject = assistRESTfulUtils.fileDuration(mediaServerItem, streamInfo.getApp(), streamInfo.getStream(), null);
                if (jsonObject != null && jsonObject.getInteger("code") == 0) {
                    long duration = jsonObject.getLong("data");

                    if (duration == 0) {
                        streamInfo.setProgress(0);
                    }else {
                        String startTime = streamInfo.getStartTime();
                        String endTime = streamInfo.getEndTime();
                        long start = DateUtil.yyyy_MM_dd_HH_mm_ssToTimestamp(startTime);
                        long end = DateUtil.yyyy_MM_dd_HH_mm_ssToTimestamp(endTime);

                        BigDecimal currentCount = new BigDecimal(duration/1000);
                        BigDecimal totalCount = new BigDecimal(end-start);
                        BigDecimal divide = currentCount.divide(totalCount,2, BigDecimal.ROUND_HALF_UP);
                        double process = divide.doubleValue();
                        streamInfo.setProgress(process);
                    }
                }
            }
        }
        return streamInfo;
    }

    @Override
    public void onPublishHandlerForDownload(InviteStreamInfo inviteStreamInfo, String deviceId, String channelId, String uuid) {
        RequestMessage msg = new RequestMessage();
        msg.setKey(DeferredResultHolder.CALLBACK_CMD_DOWNLOAD + deviceId + channelId);
        msg.setId(uuid);
        StreamInfo streamInfo = onPublishHandler(inviteStreamInfo.getMediaServerItem(), inviteStreamInfo.getResponse(), deviceId, channelId);
        if (streamInfo != null) {
            redisCatchStorage.startDownload(streamInfo, inviteStreamInfo.getCallId());
            msg.setData(JSON.toJSONString(streamInfo));
            resultHolder.invokeResult(msg);
        } else {
            logger.warn("????????????API???????????????");
            msg.setData("????????????API???????????????");
            resultHolder.invokeResult(msg);
        }
    }


    public StreamInfo onPublishHandler(MediaServerItem mediaServerItem, JSONObject resonse, String deviceId, String channelId) {
        String streamId = resonse.getString("stream");
        JSONArray tracks = resonse.getJSONArray("tracks");
        StreamInfo streamInfo = mediaService.getStreamInfoByAppAndStream(mediaServerItem,"rtp", streamId, tracks);
        streamInfo.setDeviceID(deviceId);
        streamInfo.setChannelId(channelId);
        return streamInfo;
    }

    @Override
    public void zlmServerOffline(String mediaServerId) {
        // ???????????????????????????????????????
        List<SendRtpItem> sendRtpItems = redisCatchStorage.querySendRTPServer(null);
        if (sendRtpItems.size() > 0) {
            for (SendRtpItem sendRtpItem : sendRtpItems) {
                if (sendRtpItem.getMediaServerId().equals(mediaServerId)) {
                    ParentPlatform platform = storager.queryParentPlatByServerGBId(sendRtpItem.getPlatformId());
                    sipCommanderFroPlatform.streamByeCmd(platform, sendRtpItem.getCallId());
                }
            }
        }
        // ?????????????????????????????????
        List<SsrcTransaction> allSsrc = streamSession.getAllSsrc();
        if (allSsrc.size() > 0) {
            for (SsrcTransaction ssrcTransaction : allSsrc) {
                if(ssrcTransaction.getMediaServerId().equals(mediaServerId)) {
                    cmder.streamByeCmd(ssrcTransaction.getDeviceId(), ssrcTransaction.getChannelId(),
                            ssrcTransaction.getStream(), null);
                }
            }
        }
    }
}
