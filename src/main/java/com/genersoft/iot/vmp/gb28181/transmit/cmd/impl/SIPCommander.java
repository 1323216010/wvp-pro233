package com.genersoft.iot.vmp.gb28181.transmit.cmd.impl;

import com.alibaba.fastjson.JSONObject;
import com.genersoft.iot.vmp.common.StreamInfo;
import com.genersoft.iot.vmp.conf.DynamicTask;
import com.genersoft.iot.vmp.conf.SipConfig;
import com.genersoft.iot.vmp.conf.UserSetup;
import com.genersoft.iot.vmp.gb28181.bean.Device;
import com.genersoft.iot.vmp.gb28181.bean.InviteStreamCallback;
import com.genersoft.iot.vmp.gb28181.bean.InviteStreamInfo;
import com.genersoft.iot.vmp.gb28181.bean.SsrcTransaction;
import com.genersoft.iot.vmp.gb28181.event.SipSubscribe;
import com.genersoft.iot.vmp.gb28181.session.VideoStreamSessionManager;
import com.genersoft.iot.vmp.gb28181.transmit.cmd.ISIPCommander;
import com.genersoft.iot.vmp.gb28181.transmit.cmd.SIPRequestHeaderProvider;
import com.genersoft.iot.vmp.gb28181.utils.DateUtil;
import com.genersoft.iot.vmp.gb28181.utils.NumericUtil;
import com.genersoft.iot.vmp.media.zlm.ZLMHttpHookSubscribe;
import com.genersoft.iot.vmp.media.zlm.dto.MediaServerItem;
import com.genersoft.iot.vmp.service.IMediaServerService;
import com.genersoft.iot.vmp.service.bean.SSRCInfo;
import com.genersoft.iot.vmp.storager.IRedisCatchStorage;
import com.genersoft.iot.vmp.storager.IVideoManagerStorager;
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.stack.SIPDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sip.*;
import javax.sip.address.SipURI;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import java.lang.reflect.Field;
import java.text.ParseException;
import java.util.HashSet;

/**    
 * @description:???????????????????????????????????????????????????????????????   
 * @author: swwheihei
 * @date:   2020???5???3??? ??????9:22:48     
 */
@Component
@DependsOn("sipLayer")
public class SIPCommander implements ISIPCommander {

	private final Logger logger = LoggerFactory.getLogger(SIPCommander.class);
	
	@Autowired
	private SipConfig sipConfig;

	@Autowired
	@Qualifier(value="tcpSipProvider")
	private SipProviderImpl tcpSipProvider;

	@Autowired
	@Qualifier(value="udpSipProvider")
	private SipProviderImpl udpSipProvider;

	@Autowired
	private SIPRequestHeaderProvider headerProvider;
	
	@Autowired
	private VideoStreamSessionManager streamSession;

	@Autowired
	private IVideoManagerStorager storager;

	@Autowired
	private IRedisCatchStorage redisCatchStorage;

	@Autowired
	private UserSetup userSetup;

	@Autowired
	private ZLMHttpHookSubscribe subscribe;

	@Autowired
	private SipSubscribe sipSubscribe;

	@Autowired
	private IMediaServerService mediaServerService;

	@Autowired
	private DynamicTask dynamicTask;


	/**
	 * ????????????????????????????????????????????????????????????????????????
	 * 
	 * @param device  ????????????
	 * @param channelId  ????????????
	 * @param leftRight  ?????????????????? 0:?????? 1:?????? 2:??????
     * @param upDown     ?????????????????? 0:?????? 1:?????? 2:??????
	 */
	@Override
	public boolean ptzdirectCmd(Device device, String channelId, int leftRight, int upDown) {
		return ptzCmd(device, channelId, leftRight, upDown, 0, sipConfig.getPtzSpeed(), 0);
	}

	/**
	 * ?????????????????????
	 * 
	 * @param device  ????????????
	 * @param channelId  ????????????
	 * @param leftRight  ?????????????????? 0:?????? 1:?????? 2:??????
     * @param upDown     ?????????????????? 0:?????? 1:?????? 2:??????
     * @param moveSpeed  ??????????????????
	 */
	@Override
	public boolean ptzdirectCmd(Device device, String channelId, int leftRight, int upDown, int moveSpeed) {
		return ptzCmd(device, channelId, leftRight, upDown, 0, moveSpeed, 0);
	}

	/**
	 * ?????????????????????????????????????????????????????????????????????
	 * 
	 * @param device  ????????????
	 * @param channelId  ????????????
     * @param inOut      ?????????????????? 0:?????? 1:?????? 2:??????
	 */  
	@Override
	public boolean ptzZoomCmd(Device device, String channelId, int inOut) {
		return ptzCmd(device, channelId, 0, 0, inOut, 0, sipConfig.getPtzSpeed());
	}

	/**
	 * ??????????????????
	 * 
	 * @param device  ????????????
	 * @param channelId  ????????????
     * @param inOut      ?????????????????? 0:?????? 1:?????? 2:??????
     * @param zoomSpeed  ??????????????????
	 */ 
	@Override
	public boolean ptzZoomCmd(Device device, String channelId, int inOut, int zoomSpeed) {
		return ptzCmd(device, channelId, 0, 0, inOut, 0, zoomSpeed);
	}
  
   /**
	* ????????????????????? 
	*
    * @param leftRight  ?????????????????? 0:?????? 1:?????? 2:??????
    * @param upDown     ?????????????????? 0:?????? 1:?????? 2:??????
    * @param inOut      ?????????????????? 0:?????? 1:?????? 2:??????
    * @param moveSpeed  ?????????????????? ?????? 0XFF (0-255)
    * @param zoomSpeed  ?????????????????? ?????? 0X1 (0-255)
    */
    public static String cmdString(int leftRight, int upDown, int inOut, int moveSpeed, int zoomSpeed) {
		int cmdCode = 0;
		if (leftRight == 2) {
			cmdCode|=0x01;		// ??????
		} else if(leftRight == 1) {
			cmdCode|=0x02;		// ??????
		}
		if (upDown == 2) {
			cmdCode|=0x04;		// ??????
		} else if(upDown == 1) {
			cmdCode|=0x08;		// ??????
		}
		if (inOut == 2) {
			cmdCode |= 0x10;	// ??????
		} else if(inOut == 1) {
			cmdCode |= 0x20;	// ??????
		}
		StringBuilder builder = new StringBuilder("A50F01");
		String strTmp;
		strTmp = String.format("%02X", cmdCode);
		builder.append(strTmp, 0, 2);
		strTmp = String.format("%02X", moveSpeed);
		builder.append(strTmp, 0, 2);
		builder.append(strTmp, 0, 2);
		strTmp = String.format("%X", zoomSpeed);
		builder.append(strTmp, 0, 1).append("0");
		//???????????????
		int checkCode = (0XA5 + 0X0F + 0X01 + cmdCode + moveSpeed + moveSpeed + (zoomSpeed /*<< 4*/ & 0XF0)) % 0X100;
		strTmp = String.format("%02X", checkCode);
		builder.append(strTmp, 0, 2);
		return builder.toString();
}

   /**
	* ????????????????????? 
	*
	 * @param cmdCode 		?????????
	 * @param parameter1	??????1
	 * @param parameter2	??????2
	 * @param combineCode2	?????????2
	 */
    public static String frontEndCmdString(int cmdCode, int parameter1, int parameter2, int combineCode2) {
		StringBuilder builder = new StringBuilder("A50F01");
		String strTmp;
		strTmp = String.format("%02X", cmdCode);
		builder.append(strTmp, 0, 2);
		strTmp = String.format("%02X", parameter1);
		builder.append(strTmp, 0, 2);
		strTmp = String.format("%02X", parameter2);
		builder.append(strTmp, 0, 2);
		strTmp = String.format("%X", combineCode2);
		builder.append(strTmp, 0, 1).append("0");
		//???????????????
		int checkCode = (0XA5 + 0X0F + 0X01 + cmdCode + parameter1 + parameter2 + (combineCode2 & 0XF0)) % 0X100;
		strTmp = String.format("%02X", checkCode);
		builder.append(strTmp, 0, 2);
		return builder.toString();
	}

	/**
	 * ??????????????????????????????????????????
	 * 
	 * @param device  	????????????
	 * @param channelId	????????????
	 * @param leftRight	?????????????????? 0:?????? 1:?????? 2:??????
     * @param upDown	?????????????????? 0:?????? 1:?????? 2:??????
     * @param inOut		?????????????????? 0:?????? 1:?????? 2:??????
     * @param moveSpeed	??????????????????
     * @param zoomSpeed	??????????????????
	 */
	@Override
	public boolean ptzCmd(Device device, String channelId, int leftRight, int upDown, int inOut, int moveSpeed,
			int zoomSpeed) {
		try {
			String cmdStr= cmdString(leftRight, upDown, inOut, moveSpeed, zoomSpeed);
			StringBuffer ptzXml = new StringBuffer(200);
			ptzXml.append("<?xml version=\"1.0\" ?>\r\n");
			ptzXml.append("<Control>\r\n");
			ptzXml.append("<CmdType>DeviceControl</CmdType>\r\n");
			ptzXml.append("<SN>" + (int)((Math.random()*9+1)*100000) + "</SN>\r\n");
			ptzXml.append("<DeviceID>" + channelId + "</DeviceID>\r\n");
			ptzXml.append("<PTZCmd>" + cmdStr + "</PTZCmd>\r\n");
			ptzXml.append("<Info>\r\n");
			ptzXml.append("</Info>\r\n");
			ptzXml.append("</Control>\r\n");
			
			String tm = Long.toString(System.currentTimeMillis());

			CallIdHeader callIdHeader = device.getTransport().equals("TCP") ? tcpSipProvider.getNewCallId()
					: udpSipProvider.getNewCallId();

			Request request = headerProvider.createMessageRequest(device, ptzXml.toString(), "z9hG4bK-ViaPtz-" + tm, "FromPtz" + tm, null, callIdHeader);
			
			transmitRequest(device, request);
			return true;
		} catch (SipException | ParseException | InvalidArgumentException e) {
			e.printStackTrace();
		} 
		return false;
	}

	/**
	 * ?????????????????????PTZ?????????FI???????????????????????????????????????????????????????????????????????????
	 * 
	 * @param device  		????????????
	 * @param channelId		????????????
	 * @param cmdCode		?????????
     * @param parameter1	??????1
     * @param parameter2	??????2
     * @param combineCode2	?????????2
	 */
	@Override
	public boolean frontEndCmd(Device device, String channelId, int cmdCode, int parameter1, int parameter2, int combineCode2) {
		try {
			String cmdStr= frontEndCmdString(cmdCode, parameter1, parameter2, combineCode2);
			logger.debug("??????????????????" + cmdStr);
			StringBuffer ptzXml = new StringBuffer(200);
			ptzXml.append("<?xml version=\"1.0\" ?>\r\n");
			ptzXml.append("<Control>\r\n");
			ptzXml.append("<CmdType>DeviceControl</CmdType>\r\n");
			ptzXml.append("<SN>" + (int)((Math.random()*9+1)*100000) + "</SN>\r\n");
			ptzXml.append("<DeviceID>" + channelId + "</DeviceID>\r\n");
			ptzXml.append("<PTZCmd>" + cmdStr + "</PTZCmd>\r\n");
			ptzXml.append("<Info>\r\n");
			ptzXml.append("</Info>\r\n");
			ptzXml.append("</Control>\r\n");
			
			String tm = Long.toString(System.currentTimeMillis());

			CallIdHeader callIdHeader = device.getTransport().equals("TCP") ? tcpSipProvider.getNewCallId()
					: udpSipProvider.getNewCallId();

			Request request = headerProvider.createMessageRequest(device, ptzXml.toString(), "z9hG4bK-ViaPtz-" + tm, "FromPtz" + tm, null, callIdHeader);
			transmitRequest(device, request);
			return true;
		} catch (SipException | ParseException | InvalidArgumentException e) {
			e.printStackTrace();
		} 
		return false;
	}

	/**
	 * ????????????????????????????????????????????????
	 * @param device		????????????
	 * @param channelId		????????????
	 * @param cmdString		?????????????????????
	 */
	@Override
	public boolean fronEndCmd(Device device, String channelId, String cmdString) {
		try {
			StringBuffer ptzXml = new StringBuffer(200);
			ptzXml.append("<?xml version=\"1.0\" ?>\r\n");
			ptzXml.append("<Control>\r\n");
			ptzXml.append("<CmdType>DeviceControl</CmdType>\r\n");
			ptzXml.append("<SN>" + (int)((Math.random()*9+1)*100000) + "</SN>\r\n");
			ptzXml.append("<DeviceID>" + channelId + "</DeviceID>\r\n");
			ptzXml.append("<PTZCmd>" + cmdString + "</PTZCmd>\r\n");
			ptzXml.append("<Info>\r\n");
			ptzXml.append("</Info>\r\n");
			ptzXml.append("</Control>\r\n");
			
			String tm = Long.toString(System.currentTimeMillis());

			CallIdHeader callIdHeader = device.getTransport().equals("TCP") ? tcpSipProvider.getNewCallId()
					: udpSipProvider.getNewCallId();

			Request request = headerProvider.createMessageRequest(device, ptzXml.toString(), "z9hG4bK-ViaPtz-" + tm, "FromPtz" + tm, null, callIdHeader);
			transmitRequest(device, request);
			return true;
		} catch (SipException | ParseException | InvalidArgumentException e) {
			e.printStackTrace();
		} 
		return false;
	}
	
	 /**
	 * 	?????????????????????
	  * @param device  ????????????
	  * @param channelId  ????????????
	  * @param event hook??????
	  * @param errorEvent sip????????????
	  */
	@Override
	public void playStreamCmd(MediaServerItem mediaServerItem, SSRCInfo ssrcInfo, Device device, String channelId,
							  ZLMHttpHookSubscribe.Event event, SipSubscribe.Event errorEvent) {
		String streamId = ssrcInfo.getStream();
		try {
			if (device == null) return;
			String streamMode = device.getStreamMode().toUpperCase();

			logger.info("{} ?????????ZLM???: {} [{}:{}]", streamId, mediaServerItem.getId(), mediaServerItem.getIp(), ssrcInfo.getPort());
			// ????????????
			JSONObject subscribeKey = new JSONObject();
			subscribeKey.put("app", "rtp");
			subscribeKey.put("stream", streamId);
			subscribeKey.put("regist", true);
			subscribeKey.put("schema", "rtmp");
			subscribeKey.put("mediaServerId", mediaServerItem.getId());
			subscribe.addSubscribe(ZLMHttpHookSubscribe.HookType.on_stream_changed, subscribeKey,
					(MediaServerItem mediaServerItemInUse, JSONObject json)->{
				if (event != null) {
					event.response(mediaServerItemInUse, json);
				}
			});
			//
			StringBuffer content = new StringBuffer(200);
			content.append("v=0\r\n");
			content.append("o="+ sipConfig.getId()+" 0 0 IN IP4 "+ mediaServerItem.getSdpIp() +"\r\n");
			content.append("s=Play\r\n");
			content.append("c=IN IP4 "+ mediaServerItem.getSdpIp() +"\r\n");
			content.append("t=0 0\r\n");

			if (userSetup.isSeniorSdp()) {
				if("TCP-PASSIVE".equals(streamMode)) {
					content.append("m=video "+ ssrcInfo.getPort() +" TCP/RTP/AVP 96 126 125 99 34 98 97\r\n");
				}else if ("TCP-ACTIVE".equals(streamMode)) {
					content.append("m=video "+ ssrcInfo.getPort() +" TCP/RTP/AVP 96 126 125 99 34 98 97\r\n");
				}else if("UDP".equals(streamMode)) {
					content.append("m=video "+ ssrcInfo.getPort() +" RTP/AVP 96 126 125 99 34 98 97\r\n");
				}
				content.append("a=recvonly\r\n");
				content.append("a=rtpmap:96 PS/90000\r\n");
				content.append("a=fmtp:126 profile-level-id=42e01e\r\n");
				content.append("a=rtpmap:126 H264/90000\r\n");
				content.append("a=rtpmap:125 H264S/90000\r\n");
				content.append("a=fmtp:125 profile-level-id=42e01e\r\n");
				content.append("a=rtpmap:99 MP4V-ES/90000\r\n");
				content.append("a=fmtp:99 profile-level-id=3\r\n");
				content.append("a=rtpmap:98 H264/90000\r\n");
				content.append("a=rtpmap:97 MPEG4/90000\r\n");
				if("TCP-PASSIVE".equals(streamMode)){ // tcp????????????
					content.append("a=setup:passive\r\n");
					content.append("a=connection:new\r\n");
				}else if ("TCP-ACTIVE".equals(streamMode)) { // tcp????????????
					content.append("a=setup:active\r\n");
					content.append("a=connection:new\r\n");
				}
			}else {
				if("TCP-PASSIVE".equals(streamMode)) {
					content.append("m=video "+ ssrcInfo.getPort() +" TCP/RTP/AVP 96 98 97\r\n");
				}else if ("TCP-ACTIVE".equals(streamMode)) {
					content.append("m=video "+ ssrcInfo.getPort() +" TCP/RTP/AVP 96 98 97\r\n");
				}else if("UDP".equals(streamMode)) {
					content.append("m=video "+ ssrcInfo.getPort() +" RTP/AVP 96 98 97\r\n");
				}
				content.append("a=recvonly\r\n");
				content.append("a=rtpmap:96 PS/90000\r\n");
				content.append("a=rtpmap:98 H264/90000\r\n");
				content.append("a=rtpmap:97 MPEG4/90000\r\n");
				if ("TCP-PASSIVE".equals(streamMode)) { // tcp????????????
					content.append("a=setup:passive\r\n");
					content.append("a=connection:new\r\n");
				} else if ("TCP-ACTIVE".equals(streamMode)) { // tcp????????????
					content.append("a=setup:active\r\n");
					content.append("a=connection:new\r\n");
				}
			}

			content.append("y="+ssrcInfo.getSsrc()+"\r\n");//ssrc
			// f??????:f= v/????????????/?????????/??????/????????????/????????????a/????????????/????????????/?????????
//			content.append("f=v/2/5/25/1/4000a/1/8/1" + "\r\n"); // ?????????????????????????????????

			String tm = Long.toString(System.currentTimeMillis());

			CallIdHeader callIdHeader = device.getTransport().equals("TCP") ? tcpSipProvider.getNewCallId()
					: udpSipProvider.getNewCallId();

			Request request = headerProvider.createInviteRequest(device, channelId, content.toString(), null, "FromInvt" + tm, null, ssrcInfo.getSsrc(), callIdHeader);

			transmitRequest(device, request, (e -> {
				streamSession.remove(device.getDeviceId(), channelId, ssrcInfo.getStream());
				mediaServerService.releaseSsrc(mediaServerItem.getId(), ssrcInfo.getSsrc());
				errorEvent.response(e);
			}), e ->{
				// ???????????????????????????????????????????????????callID?????????????????????????????????
				streamSession.put(device.getDeviceId(), channelId ,"play", streamId, ssrcInfo.getSsrc(), mediaServerItem.getId(), ((ResponseEvent)e.event).getClientTransaction(), VideoStreamSessionManager.SessionType.play);
				streamSession.put(device.getDeviceId(), channelId ,"play", e.dialog);
			});

			
		} catch ( SipException | ParseException | InvalidArgumentException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * ?????????????????????
	 * 
	 * @param device  ????????????
	 * @param channelId  ????????????
	 * @param startTime ????????????,???????????????yyyy-MM-dd HH:mm:ss
	 * @param endTime ????????????,???????????????yyyy-MM-dd HH:mm:ss
	 */ 
	@Override
	public void playbackStreamCmd(MediaServerItem mediaServerItem, SSRCInfo ssrcInfo, Device device, String channelId,
								  String startTime, String endTime, InviteStreamCallback inviteStreamCallback, InviteStreamCallback hookEvent,
								  SipSubscribe.Event errorEvent) {
		try {

			logger.info("{} ?????????ZLM???: {} [{}:{}]", ssrcInfo.getStream(), mediaServerItem.getId(), mediaServerItem.getIp(), ssrcInfo.getPort());

			StringBuffer content = new StringBuffer(200);
	        content.append("v=0\r\n");
	        content.append("o="+sipConfig.getId()+" 0 0 IN IP4 " + mediaServerItem.getSdpIp() + "\r\n");
	        content.append("s=Playback\r\n");
	        content.append("u="+channelId+":0\r\n");
	        content.append("c=IN IP4 "+mediaServerItem.getSdpIp()+"\r\n");
	        content.append("t="+DateUtil.yyyy_MM_dd_HH_mm_ssToTimestamp(startTime)+" "
					+DateUtil.yyyy_MM_dd_HH_mm_ssToTimestamp(endTime) +"\r\n");

			String streamMode = device.getStreamMode().toUpperCase();

			if (userSetup.isSeniorSdp()) {
				if("TCP-PASSIVE".equals(streamMode)) {
					content.append("m=video "+ ssrcInfo.getPort() +" TCP/RTP/AVP 96 126 125 99 34 98 97\r\n");
				}else if ("TCP-ACTIVE".equals(streamMode)) {
					content.append("m=video "+ ssrcInfo.getPort() +" TCP/RTP/AVP 96 126 125 99 34 98 97\r\n");
				}else if("UDP".equals(streamMode)) {
					content.append("m=video "+ ssrcInfo.getPort() +" RTP/AVP 96 126 125 99 34 98 97\r\n");
				}
				content.append("a=recvonly\r\n");
				content.append("a=rtpmap:96 PS/90000\r\n");
				content.append("a=fmtp:126 profile-level-id=42e01e\r\n");
				content.append("a=rtpmap:126 H264/90000\r\n");
				content.append("a=rtpmap:125 H264S/90000\r\n");
				content.append("a=fmtp:125 profile-level-id=42e01e\r\n");
				content.append("a=rtpmap:99 MP4V-ES/90000\r\n");
				content.append("a=fmtp:99 profile-level-id=3\r\n");
				content.append("a=rtpmap:98 H264/90000\r\n");
				content.append("a=rtpmap:97 MPEG4/90000\r\n");
				if("TCP-PASSIVE".equals(streamMode)){ // tcp????????????
					content.append("a=setup:passive\r\n");
					content.append("a=connection:new\r\n");
				}else if ("TCP-ACTIVE".equals(streamMode)) { // tcp????????????
					content.append("a=setup:active\r\n");
					content.append("a=connection:new\r\n");
				}
			}else {
				if("TCP-PASSIVE".equals(streamMode)) {
					content.append("m=video "+ ssrcInfo.getPort() +" TCP/RTP/AVP 96 98 97\r\n");
				}else if ("TCP-ACTIVE".equals(streamMode)) {
					content.append("m=video "+ ssrcInfo.getPort() +" TCP/RTP/AVP 96 98 97\r\n");
				}else if("UDP".equals(streamMode)) {
					content.append("m=video "+ ssrcInfo.getPort() +" RTP/AVP 96 98 97\r\n");
				}
				content.append("a=recvonly\r\n");
				content.append("a=rtpmap:96 PS/90000\r\n");
				content.append("a=rtpmap:98 H264/90000\r\n");
				content.append("a=rtpmap:97 MPEG4/90000\r\n");
				if("TCP-PASSIVE".equals(streamMode)){ // tcp????????????
					content.append("a=setup:passive\r\n");
					content.append("a=connection:new\r\n");
				}else if ("TCP-ACTIVE".equals(streamMode)) { // tcp????????????
					content.append("a=setup:active\r\n");
					content.append("a=connection:new\r\n");
				}
			}

	        content.append("y=" + ssrcInfo.getSsrc() + "\r\n");//ssrc
	        
			String tm = Long.toString(System.currentTimeMillis());

			CallIdHeader callIdHeader = device.getTransport().equals("TCP") ? tcpSipProvider.getNewCallId()
					: udpSipProvider.getNewCallId();

			// ????????????
			JSONObject subscribeKey = new JSONObject();
			subscribeKey.put("app", "rtp");
			subscribeKey.put("stream", ssrcInfo.getStream());
			subscribeKey.put("regist", true);
			subscribeKey.put("schema", "rtmp");
			subscribeKey.put("mediaServerId", mediaServerItem.getId());
			logger.debug("??????????????????????????????????????????" + subscribeKey);
			subscribe.addSubscribe(ZLMHttpHookSubscribe.HookType.on_stream_changed, subscribeKey,
					(MediaServerItem mediaServerItemInUse, JSONObject json)->{
						if (hookEvent != null) {
							InviteStreamInfo inviteStreamInfo = new InviteStreamInfo(mediaServerItemInUse, json, callIdHeader.getCallId(), "rtp", ssrcInfo.getStream());
							hookEvent.call(inviteStreamInfo);
						}
					});
	        Request request = headerProvider.createPlaybackInviteRequest(device, channelId, content.toString(), null, "fromplybck" + tm, null, callIdHeader, ssrcInfo.getSsrc());

	        transmitRequest(device, request, errorEvent, okEvent -> {
				ResponseEvent responseEvent = (ResponseEvent) okEvent.event;
	        	streamSession.put(device.getDeviceId(), channelId, callIdHeader.getCallId(), ssrcInfo.getStream(), ssrcInfo.getSsrc(), mediaServerItem.getId(), responseEvent.getClientTransaction(), VideoStreamSessionManager.SessionType.playback);
				streamSession.put(device.getDeviceId(), channelId, callIdHeader.getCallId(), okEvent.dialog);
			});
			if (inviteStreamCallback != null) {
				inviteStreamCallback.call(new InviteStreamInfo(mediaServerItem, null, callIdHeader.getCallId(), "rtp", ssrcInfo.getStream()));
			}
		} catch ( SipException | ParseException | InvalidArgumentException e) {
			e.printStackTrace();
		}
	}

	/**
	 * ????????????????????????
	 * 
	 * @param device  ????????????
	 * @param channelId  ????????????
	 * @param startTime ????????????,???????????????yyyy-MM-dd HH:mm:ss
	 * @param endTime ????????????,???????????????yyyy-MM-dd HH:mm:ss
	 * @param downloadSpeed ??????????????????
	 */ 
	@Override
	public void downloadStreamCmd(MediaServerItem mediaServerItem, SSRCInfo ssrcInfo, Device device, String channelId,
								  String startTime, String endTime, int downloadSpeed, InviteStreamCallback inviteStreamCallback, InviteStreamCallback hookEvent,
								  SipSubscribe.Event errorEvent) {
		try {
			logger.info("{} ?????????ZLM???: {} [{}:{}]", ssrcInfo.getStream(), mediaServerItem.getId(), mediaServerItem.getIp(), ssrcInfo.getPort());

			StringBuffer content = new StringBuffer(200);
	        content.append("v=0\r\n");
	        content.append("o="+sipConfig.getId()+" 0 0 IN IP4 " + mediaServerItem.getSdpIp() + "\r\n");
	        content.append("s=Download\r\n");
	        content.append("u="+channelId+":0\r\n");
	        content.append("c=IN IP4 "+mediaServerItem.getSdpIp()+"\r\n");
	        content.append("t="+DateUtil.yyyy_MM_dd_HH_mm_ssToTimestamp(startTime)+" "
					+DateUtil.yyyy_MM_dd_HH_mm_ssToTimestamp(endTime) +"\r\n");

			String streamMode = device.getStreamMode().toUpperCase();

			if (userSetup.isSeniorSdp()) {
				if("TCP-PASSIVE".equals(streamMode)) {
					content.append("m=video "+ ssrcInfo.getPort() +" TCP/RTP/AVP 96 126 125 99 34 98 97\r\n");
				}else if ("TCP-ACTIVE".equals(streamMode)) {
					content.append("m=video "+ ssrcInfo.getPort() +" TCP/RTP/AVP 96 126 125 99 34 98 97\r\n");
				}else if("UDP".equals(streamMode)) {
					content.append("m=video "+ ssrcInfo.getPort() +" RTP/AVP 96 126 125 99 34 98 97\r\n");
				}
				content.append("a=recvonly\r\n");
				content.append("a=rtpmap:96 PS/90000\r\n");
				content.append("a=fmtp:126 profile-level-id=42e01e\r\n");
				content.append("a=rtpmap:126 H264/90000\r\n");
				content.append("a=rtpmap:125 H264S/90000\r\n");
				content.append("a=fmtp:125 profile-level-id=42e01e\r\n");
				content.append("a=rtpmap:99 MP4V-ES/90000\r\n");
				content.append("a=fmtp:99 profile-level-id=3\r\n");
				content.append("a=rtpmap:98 H264/90000\r\n");
				content.append("a=rtpmap:97 MPEG4/90000\r\n");
				if("TCP-PASSIVE".equals(streamMode)){ // tcp????????????
					content.append("a=setup:passive\r\n");
					content.append("a=connection:new\r\n");
				}else if ("TCP-ACTIVE".equals(streamMode)) { // tcp????????????
					content.append("a=setup:active\r\n");
					content.append("a=connection:new\r\n");
				}
			}else {
				if("TCP-PASSIVE".equals(streamMode)) {
					content.append("m=video "+ ssrcInfo.getPort() +" TCP/RTP/AVP 96 98 97\r\n");
				}else if ("TCP-ACTIVE".equals(streamMode)) {
					content.append("m=video "+ ssrcInfo.getPort() +" TCP/RTP/AVP 96 98 97\r\n");
				}else if("UDP".equals(streamMode)) {
					content.append("m=video "+ ssrcInfo.getPort() +" RTP/AVP 96 98 97\r\n");
				}
				content.append("a=recvonly\r\n");
				content.append("a=rtpmap:96 PS/90000\r\n");
				content.append("a=rtpmap:98 H264/90000\r\n");
				content.append("a=rtpmap:97 MPEG4/90000\r\n");
				if("TCP-PASSIVE".equals(streamMode)){ // tcp????????????
					content.append("a=setup:passive\r\n");
					content.append("a=connection:new\r\n");
				}else if ("TCP-ACTIVE".equals(streamMode)) { // tcp????????????
					content.append("a=setup:active\r\n");
					content.append("a=connection:new\r\n");
				}
			}
			content.append("a=downloadspeed:" + downloadSpeed + "\r\n");

	        content.append("y=" + ssrcInfo.getSsrc() + "\r\n");//ssrc
	        
			String tm = Long.toString(System.currentTimeMillis());

			CallIdHeader callIdHeader = device.getTransport().equals("TCP") ? tcpSipProvider.getNewCallId()
					: udpSipProvider.getNewCallId();

			// ????????????
			JSONObject subscribeKey = new JSONObject();
			subscribeKey.put("app", "rtp");
			subscribeKey.put("stream", ssrcInfo.getStream());
			subscribeKey.put("regist", true);
			subscribeKey.put("mediaServerId", mediaServerItem.getId());
			logger.debug("??????????????????????????????????????????" + subscribeKey.toString());
			subscribe.addSubscribe(ZLMHttpHookSubscribe.HookType.on_stream_changed, subscribeKey,
					(MediaServerItem mediaServerItemInUse, JSONObject json)->{
						hookEvent.call(new InviteStreamInfo(mediaServerItem, json, callIdHeader.getCallId(), "rtp", ssrcInfo.getStream()));
						subscribe.removeSubscribe(ZLMHttpHookSubscribe.HookType.on_stream_changed, subscribeKey);
					});

	        Request request = headerProvider.createPlaybackInviteRequest(device, channelId, content.toString(), null, "fromplybck" + tm, null, callIdHeader, ssrcInfo.getSsrc());
			if (inviteStreamCallback != null) {
				inviteStreamCallback.call(new InviteStreamInfo(mediaServerItem, null, callIdHeader.getCallId(), "rtp", ssrcInfo.getStream()));
			}
	        transmitRequest(device, request, errorEvent, okEvent->{
				ResponseEvent responseEvent = (ResponseEvent) okEvent.event;
				streamSession.put(device.getDeviceId(), channelId, callIdHeader.getCallId(), ssrcInfo.getStream(), ssrcInfo.getSsrc(), mediaServerItem.getId(), responseEvent.getClientTransaction(), VideoStreamSessionManager.SessionType.download);
				streamSession.put(device.getDeviceId(), channelId, callIdHeader.getCallId(), okEvent.dialog);
			});


		} catch ( SipException | ParseException | InvalidArgumentException e) {
			e.printStackTrace();
		}
	}

	/**
	 * ???????????????, ???????????????
	 */
	@Override
	public void streamByeCmd(String deviceId, String channelId, String stream, String callId) {
		streamByeCmd(deviceId, channelId, stream, callId, null);
	}

	/**
	 * ???????????????
	 */
	@Override
	public void streamByeCmd(String deviceId, String channelId, String stream, String callId, SipSubscribe.Event okEvent) {
		try {
			SsrcTransaction ssrcTransaction = streamSession.getSsrcTransaction(deviceId, channelId, null, stream);
			ClientTransaction transaction = streamSession.getTransactionByStream(deviceId, channelId, stream);

			if (transaction == null) {
				logger.warn("[ {} -> {}]?????????????????????????????????????????????", deviceId, channelId);
				SipSubscribe.EventResult<Object> eventResult = new SipSubscribe.EventResult<>();
				if (okEvent != null) {
					okEvent.response(eventResult);
				}
				return;
			}
			SIPDialog dialog;
			if (callId != null) {
				dialog = streamSession.getDialogByCallId(deviceId, channelId, callId);
			}else {
				if (stream == null) return;
				dialog = streamSession.getDialogByStream(deviceId, channelId, stream);
			}
			if (ssrcTransaction != null) {
				MediaServerItem mediaServerItem = mediaServerService.getOne(ssrcTransaction.getMediaServerId());
				mediaServerService.releaseSsrc(mediaServerItem.getId(), ssrcTransaction.getSsrc());
				mediaServerService.closeRTPServer(deviceId, channelId, ssrcTransaction.getStream());
				streamSession.remove(deviceId, channelId, ssrcTransaction.getStream());
			}

			if (dialog == null) {
				logger.warn("[ {} -> {}]?????????????????????????????????????????????", deviceId, channelId);
				return;
			}
			SipStack sipStack = udpSipProvider.getSipStack();
			SIPDialog sipDialog = ((SipStackImpl) sipStack).putDialog(dialog);
			if (dialog != sipDialog) {
				dialog = sipDialog;
			}else {
				dialog.setSipProvider(udpSipProvider);
				try {
					Field sipStackField = SIPDialog.class.getDeclaredField("sipStack");
					sipStackField.setAccessible(true);
					sipStackField.set(dialog, sipStack);
					Field eventListenersField = SIPDialog.class.getDeclaredField("eventListeners");
					eventListenersField.setAccessible(true);
					eventListenersField.set(dialog, new HashSet<>());
				} catch (NoSuchFieldException | IllegalAccessException e) {
					e.printStackTrace();
				}
			}

			Request byeRequest = dialog.createRequest(Request.BYE);
			SipURI byeURI = (SipURI) byeRequest.getRequestURI();
			SIPRequest request = (SIPRequest)transaction.getRequest();
			byeURI.setHost(request.getRemoteAddress().getHostName());
			byeURI.setPort(request.getRemotePort());
			ViaHeader viaHeader = (ViaHeader) byeRequest.getHeader(ViaHeader.NAME);
			String protocol = viaHeader.getTransport().toUpperCase();
			ClientTransaction clientTransaction = null;
			if("TCP".equals(protocol)) {
				clientTransaction = tcpSipProvider.getNewClientTransaction(byeRequest);
			} else if("UDP".equals(protocol)) {
				clientTransaction = udpSipProvider.getNewClientTransaction(byeRequest);
			}

			CallIdHeader callIdHeader = (CallIdHeader) byeRequest.getHeader(CallIdHeader.NAME);
			if (okEvent != null) {
				sipSubscribe.addOkSubscribe(callIdHeader.getCallId(), okEvent);
			}

			dialog.sendRequest(clientTransaction);

		} catch (SipException | ParseException e) {
			e.printStackTrace();
		}
	}

	/**
	 * ????????????
	 * 
	 * @param device  ????????????
	 * @param channelId  ????????????
	 */
	@Override
	public boolean audioBroadcastCmd(Device device, String channelId) {
		// ??????????????????
		return false;
	}

	/**
	 * ????????????
	 * 
	 * @param device  ????????????
	 */
	@Override
	public boolean audioBroadcastCmd(Device device) {
		try {
			StringBuffer broadcastXml = new StringBuffer(200);
			broadcastXml.append("<?xml version=\"1.0\" ?>\r\n");
			broadcastXml.append("<Notify>\r\n");
			broadcastXml.append("<CmdType>Broadcast</CmdType>\r\n");
			broadcastXml.append("<SN>" + (int)((Math.random()*9+1)*100000) + "</SN>\r\n");
			broadcastXml.append("<SourceID>" + sipConfig.getId() + "</SourceID>\r\n");
			broadcastXml.append("<TargetID>" + device.getDeviceId() + "</TargetID>\r\n");
			broadcastXml.append("</Notify>\r\n");
			
			String tm = Long.toString(System.currentTimeMillis());
			
			CallIdHeader callIdHeader = device.getTransport().equals("TCP") ? tcpSipProvider.getNewCallId()
					: udpSipProvider.getNewCallId();
								
			Request request = headerProvider.createMessageRequest(device, broadcastXml.toString(), "z9hG4bK-ViaBcst-" + tm, "FromBcst" + tm, null, callIdHeader);
			transmitRequest(device, request);
			return true;
		} catch (SipException | ParseException | InvalidArgumentException e) {
			e.printStackTrace();
		} 
		return false;
	}
	@Override
	public void audioBroadcastCmd(Device device, SipSubscribe.Event errorEvent) {
		try {
			StringBuffer broadcastXml = new StringBuffer(200);
			broadcastXml.append("<?xml version=\"1.0\" ?>\r\n");
			broadcastXml.append("<Notify>\r\n");
			broadcastXml.append("<CmdType>Broadcast</CmdType>\r\n");
			broadcastXml.append("<SN>" + (int)((Math.random()*9+1)*100000) + "</SN>\r\n");
			broadcastXml.append("<SourceID>" + sipConfig.getId() + "</SourceID>\r\n");
			broadcastXml.append("<TargetID>" + device.getDeviceId() + "</TargetID>\r\n");
			broadcastXml.append("</Notify>\r\n");
			
			String tm = Long.toString(System.currentTimeMillis());

			CallIdHeader callIdHeader = device.getTransport().equals("TCP") ? tcpSipProvider.getNewCallId()
					: udpSipProvider.getNewCallId();
								
			Request request = headerProvider.createMessageRequest(device, broadcastXml.toString(), "z9hG4bK-ViaBcst-" + tm, "FromBcst" + tm, null, callIdHeader);
			transmitRequest(device, request, errorEvent);
		} catch (SipException | ParseException | InvalidArgumentException e) {
			e.printStackTrace();
		} 
	} 
	
	
	/**
	 * ?????????????????????
	 * 
	 * @param device		????????????
	 * @param channelId  	????????????
	 * @param recordCmdStr	???????????????Record / StopRecord
	 */  
	@Override
	public boolean recordCmd(Device device, String channelId, String recordCmdStr, SipSubscribe.Event errorEvent) {
		try {
			StringBuffer cmdXml = new StringBuffer(200);
			cmdXml.append("<?xml version=\"1.0\" ?>\r\n");
			cmdXml.append("<Control>\r\n");
			cmdXml.append("<CmdType>DeviceControl</CmdType>\r\n");
			cmdXml.append("<SN>" + (int)((Math.random()*9+1)*100000) + "</SN>\r\n");
			if (StringUtils.isEmpty(channelId)) {
				cmdXml.append("<DeviceID>" + device.getDeviceId() + "</DeviceID>\r\n");
			} else {
				cmdXml.append("<DeviceID>" + channelId + "</DeviceID>\r\n");
			}
			cmdXml.append("<RecordCmd>" + recordCmdStr + "</RecordCmd>\r\n");
			cmdXml.append("</Control>\r\n");
			
			String tm = Long.toString(System.currentTimeMillis());

			CallIdHeader callIdHeader = device.getTransport().equals("TCP") ? tcpSipProvider.getNewCallId()
					: udpSipProvider.getNewCallId();

			Request request = headerProvider.createMessageRequest(device, cmdXml.toString(), null, "FromRecord" + tm, null, callIdHeader);
			transmitRequest(device, request, errorEvent);
			return true;
		} catch (SipException | ParseException | InvalidArgumentException e) {
			e.printStackTrace();
			return false;
		} 
	}

	/**
	 * ????????????????????????
	 * 
	 * @param device	????????????
	 */
	@Override
	public boolean teleBootCmd(Device device) {
		try {
			StringBuffer cmdXml = new StringBuffer(200);
			cmdXml.append("<?xml version=\"1.0\" ?>\r\n");
			cmdXml.append("<Control>\r\n");
			cmdXml.append("<CmdType>DeviceControl</CmdType>\r\n");
			cmdXml.append("<SN>" + (int)((Math.random()*9+1)*100000) + "</SN>\r\n");
			cmdXml.append("<DeviceID>" + device.getDeviceId() + "</DeviceID>\r\n");
			cmdXml.append("<TeleBoot>Boot</TeleBoot>\r\n");
			cmdXml.append("</Control>\r\n");
			
			String tm = Long.toString(System.currentTimeMillis());

			CallIdHeader callIdHeader = device.getTransport().equals("TCP") ? tcpSipProvider.getNewCallId()
					: udpSipProvider.getNewCallId();

			Request request = headerProvider.createMessageRequest(device, cmdXml.toString(), null, "FromBoot" + tm, null, callIdHeader);
			transmitRequest(device, request);
			return true;
		} catch (SipException | ParseException | InvalidArgumentException e) {
			e.printStackTrace();
			return false;
		} 
	}
	
	/**
	 * ????????????/????????????
	 * 
	 * @param device  		????????????
	 * @param guardCmdStr	"SetGuard"/"ResetGuard"
	 */
	@Override
	public boolean guardCmd(Device device, String guardCmdStr, SipSubscribe.Event errorEvent) {
		try {
			StringBuffer cmdXml = new StringBuffer(200);
			cmdXml.append("<?xml version=\"1.0\" ?>\r\n");
			cmdXml.append("<Control>\r\n");
			cmdXml.append("<CmdType>DeviceControl</CmdType>\r\n");
			cmdXml.append("<SN>" + (int)((Math.random()*9+1)*100000) + "</SN>\r\n");
			cmdXml.append("<DeviceID>" + device.getDeviceId() + "</DeviceID>\r\n");
			cmdXml.append("<GuardCmd>" + guardCmdStr + "</GuardCmd>\r\n");
			cmdXml.append("</Control>\r\n");
			
			String tm = Long.toString(System.currentTimeMillis());

			CallIdHeader callIdHeader = device.getTransport().equals("TCP") ? tcpSipProvider.getNewCallId()
					: udpSipProvider.getNewCallId();

			Request request = headerProvider.createMessageRequest(device, cmdXml.toString(), null, "FromGuard" + tm, null, callIdHeader);
			transmitRequest(device, request, errorEvent);
			return true;
		} catch (SipException | ParseException | InvalidArgumentException e) {
			e.printStackTrace();
			return false;
		} 
	}

	/**
	 * ??????????????????
	 * 
	 * @param device  ????????????
	 */  
	@Override
	public boolean alarmCmd(Device device, String alarmMethod, String alarmType, SipSubscribe.Event errorEvent) {
		try {
			StringBuffer cmdXml = new StringBuffer(200);
			cmdXml.append("<?xml version=\"1.0\" ?>\r\n");
			cmdXml.append("<Control>\r\n");
			cmdXml.append("<CmdType>DeviceControl</CmdType>\r\n");
			cmdXml.append("<SN>" + (int)((Math.random()*9+1)*100000) + "</SN>\r\n");
			cmdXml.append("<DeviceID>" + device.getDeviceId() + "</DeviceID>\r\n");
			cmdXml.append("<AlarmCmd>ResetAlarm</AlarmCmd>\r\n");
			if (!StringUtils.isEmpty(alarmMethod) || !StringUtils.isEmpty(alarmType)) {
				cmdXml.append("<Info>\r\n");
			}
			if (!StringUtils.isEmpty(alarmMethod)) {
				cmdXml.append("<AlarmMethod>" + alarmMethod + "</AlarmMethod>\r\n");
			}
			if (!StringUtils.isEmpty(alarmType)) {
				cmdXml.append("<AlarmType>" + alarmType + "</AlarmType>\r\n");
			}
			if (!StringUtils.isEmpty(alarmMethod) || !StringUtils.isEmpty(alarmType)) {
				cmdXml.append("</Info>\r\n");
			}
			cmdXml.append("</Control>\r\n");
			
			String tm = Long.toString(System.currentTimeMillis());

			CallIdHeader callIdHeader = device.getTransport().equals("TCP") ? tcpSipProvider.getNewCallId()
					: udpSipProvider.getNewCallId();

			Request request = headerProvider.createMessageRequest(device, cmdXml.toString(), null, "FromAlarm" + tm, null, callIdHeader);
			transmitRequest(device, request, errorEvent);
			return true;
		} catch (SipException | ParseException | InvalidArgumentException e) {
			e.printStackTrace();
			return false;
		} 
	}

	/**
	 * ?????????????????????,??????????????????????????????????????????IDR???
	 * 
	 * @param device  ????????????
	 * @param channelId  ????????????
	 */ 
	@Override
	public boolean iFrameCmd(Device device, String channelId) {
		try {
			StringBuffer cmdXml = new StringBuffer(200);
			cmdXml.append("<?xml version=\"1.0\" ?>\r\n");
			cmdXml.append("<Control>\r\n");
			cmdXml.append("<CmdType>DeviceControl</CmdType>\r\n");
			cmdXml.append("<SN>" + (int)((Math.random()*9+1)*100000) + "</SN>\r\n");
			if (StringUtils.isEmpty(channelId)) {
				cmdXml.append("<DeviceID>" + device.getDeviceId() + "</DeviceID>\r\n");
			} else {
				cmdXml.append("<DeviceID>" + channelId + "</DeviceID>\r\n");
			}
			cmdXml.append("<IFameCmd>Send</IFameCmd>\r\n");
			cmdXml.append("</Control>\r\n");
			
			String tm = Long.toString(System.currentTimeMillis());

			CallIdHeader callIdHeader = device.getTransport().equals("TCP") ? tcpSipProvider.getNewCallId()
					: udpSipProvider.getNewCallId();

			Request request = headerProvider.createMessageRequest(device, cmdXml.toString(), null, "FromBoot" + tm, null, callIdHeader);
			transmitRequest(device, request);
			return true;
		} catch (SipException | ParseException | InvalidArgumentException e) {
			e.printStackTrace();
			return false;
		} 
	}

	/**
	 * ?????????????????????
	 * 
	 * @param device		????????????
	 * @param enabled		??????????????????1 = ?????????0 = ??????
	 * @param resetTime		????????????????????????????????????????????????????????????:???(s)
	 * @param presetIndex	???????????????????????????????????????????????????????????????0~255
	 */  
	@Override
	public boolean homePositionCmd(Device device, String channelId, String enabled, String resetTime, String presetIndex, SipSubscribe.Event errorEvent) {
		try {
			StringBuffer cmdXml = new StringBuffer(200);
			cmdXml.append("<?xml version=\"1.0\" ?>\r\n");
			cmdXml.append("<Control>\r\n");
			cmdXml.append("<CmdType>DeviceControl</CmdType>\r\n");
			cmdXml.append("<SN>" + (int)((Math.random()*9+1)*100000) + "</SN>\r\n");
			if (StringUtils.isEmpty(channelId)) {
				cmdXml.append("<DeviceID>" + device.getDeviceId() + "</DeviceID>\r\n");
			} else {
				cmdXml.append("<DeviceID>" + channelId + "</DeviceID>\r\n");
			}
			cmdXml.append("<HomePosition>\r\n");
			if (NumericUtil.isInteger(enabled) && (!enabled.equals("0"))) {
				cmdXml.append("<Enabled>1</Enabled>\r\n");
				if (NumericUtil.isInteger(resetTime)) {
					cmdXml.append("<ResetTime>" + resetTime + "</ResetTime>\r\n");
				} else {
					cmdXml.append("<ResetTime>0</ResetTime>\r\n");
				}
				if (NumericUtil.isInteger(presetIndex)) {
					cmdXml.append("<PresetIndex>" + presetIndex + "</PresetIndex>\r\n");
				} else {
					cmdXml.append("<PresetIndex>0</PresetIndex>\r\n");
				}
			} else {
				cmdXml.append("<Enabled>0</Enabled>\r\n");
			}
			cmdXml.append("</HomePosition>\r\n");
			cmdXml.append("</Control>\r\n");
			
			String tm = Long.toString(System.currentTimeMillis());

			CallIdHeader callIdHeader = device.getTransport().equals("TCP") ? tcpSipProvider.getNewCallId()
					: udpSipProvider.getNewCallId();

			Request request = headerProvider.createMessageRequest(device, cmdXml.toString(), null, "FromGuard" + tm, null, callIdHeader);
			transmitRequest(device, request, errorEvent);
			return true;
		} catch (SipException | ParseException | InvalidArgumentException e) {
			e.printStackTrace();
			return false;
		} 
	}

	/**
	 * ??????????????????
	 * 
	 * @param device  ????????????
	 */  
	@Override
	public boolean deviceConfigCmd(Device device) {
		// TODO Auto-generated method stub
		return false;
	}

	/**
	 * ?????????????????????basicParam
	 * 
	 * @param device  			????????????
	 * @param channelId			????????????????????????
	 * @param name				??????/????????????????????????
	 * @param expiration		??????????????????????????????
	 * @param heartBeatInterval	??????????????????????????????
	 * @param heartBeatCount	??????????????????????????????
	 */  
	@Override
	public boolean deviceBasicConfigCmd(Device device, String channelId, String name, String expiration, 
										String heartBeatInterval, String heartBeatCount, SipSubscribe.Event errorEvent) {
		try {
			StringBuffer cmdXml = new StringBuffer(200);
			cmdXml.append("<?xml version=\"1.0\" ?>\r\n");
			cmdXml.append("<Control>\r\n");
			cmdXml.append("<CmdType>DeviceConfig</CmdType>\r\n");
			cmdXml.append("<SN>" + (int)((Math.random()*9+1)*100000) + "</SN>\r\n");
			if (StringUtils.isEmpty(channelId)) {
				cmdXml.append("<DeviceID>" + device.getDeviceId() + "</DeviceID>\r\n");
			} else {
				cmdXml.append("<DeviceID>" + channelId + "</DeviceID>\r\n");
			}
			cmdXml.append("<BasicParam>\r\n");
			if (!StringUtils.isEmpty(name)) {
				cmdXml.append("<Name>" + name + "</Name>\r\n");
			}
			if (NumericUtil.isInteger(expiration)) {
				if (Integer.valueOf(expiration) > 0) {
					cmdXml.append("<Expiration>" + expiration + "</Expiration>\r\n");
				}
			}
			if (NumericUtil.isInteger(heartBeatInterval)) {
				if (Integer.valueOf(heartBeatInterval) > 0) {
					cmdXml.append("<HeartBeatInterval>" + heartBeatInterval + "</HeartBeatInterval>\r\n");
				}
			}
			if (NumericUtil.isInteger(heartBeatCount)) {
				if (Integer.valueOf(heartBeatCount) > 0) {
					cmdXml.append("<HeartBeatCount>" + heartBeatCount + "</HeartBeatCount>\r\n");
				}
			}
			cmdXml.append("</BasicParam>\r\n");
			cmdXml.append("</Control>\r\n");
			
			String tm = Long.toString(System.currentTimeMillis());

			CallIdHeader callIdHeader = device.getTransport().equals("TCP") ? tcpSipProvider.getNewCallId()
					: udpSipProvider.getNewCallId();

			Request request = headerProvider.createMessageRequest(device, cmdXml.toString(), null, "FromConfig" + tm, null, callIdHeader);
			transmitRequest(device, request, errorEvent);
			return true;
		} catch (SipException | ParseException | InvalidArgumentException e) {
			e.printStackTrace();
			return false;
		} 
	}

	/**
	 * ??????????????????
	 * 
	 * @param device ????????????
	 */  
	@Override
	public boolean deviceStatusQuery(Device device, SipSubscribe.Event errorEvent) {
		try {
			String charset = device.getCharset();
			StringBuffer catalogXml = new StringBuffer(200);
			catalogXml.append("<?xml version=\"1.0\" encoding=\"" + charset + "\"?>\r\n");
			catalogXml.append("<Query>\r\n");
			catalogXml.append("<CmdType>DeviceStatus</CmdType>\r\n");
			catalogXml.append("<SN>" + (int)((Math.random()*9+1)*100000) + "</SN>\r\n");
			catalogXml.append("<DeviceID>" + device.getDeviceId() + "</DeviceID>\r\n");
			catalogXml.append("</Query>\r\n");
			
			String tm = Long.toString(System.currentTimeMillis());

			CallIdHeader callIdHeader = device.getTransport().equals("TCP") ? tcpSipProvider.getNewCallId()
					: udpSipProvider.getNewCallId();

			Request request = headerProvider.createMessageRequest(device, catalogXml.toString(), null, "FromStatus" + tm, null, callIdHeader);

			transmitRequest(device, request, errorEvent);
			return true;
			
		} catch (SipException | ParseException | InvalidArgumentException e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * ??????????????????
	 * 
	 * @param device ????????????
	 */  
	@Override
	public boolean deviceInfoQuery(Device device) {
		try {
			StringBuffer catalogXml = new StringBuffer(200);
			String charset = device.getCharset();
			catalogXml.append("<?xml version=\"1.0\" encoding=\"" + charset + "\"?>\r\n");
			catalogXml.append("<Query>\r\n");
			catalogXml.append("<CmdType>DeviceInfo</CmdType>\r\n");
			catalogXml.append("<SN>" + (int)((Math.random()*9+1)*100000) + "</SN>\r\n");
			catalogXml.append("<DeviceID>" + device.getDeviceId() + "</DeviceID>\r\n");
			catalogXml.append("</Query>\r\n");
			
			String tm = Long.toString(System.currentTimeMillis());

			CallIdHeader callIdHeader = device.getTransport().equals("TCP") ? tcpSipProvider.getNewCallId()
					: udpSipProvider.getNewCallId();

			Request request = headerProvider.createMessageRequest(device, catalogXml.toString(), "z9hG4bK-ViaDeviceInfo-" + tm, "FromDev" + tm, null, callIdHeader);

			transmitRequest(device, request);
			
		} catch (SipException | ParseException | InvalidArgumentException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	/**
	 * ??????????????????
	 * 
	 * @param device ????????????
	 */ 
	@Override
	public boolean catalogQuery(Device device, SipSubscribe.Event errorEvent) {
		try {
			StringBuffer catalogXml = new StringBuffer(200);
			String charset = device.getCharset();
			catalogXml.append("<?xml version=\"1.0\" encoding=\"" + charset + "\"?>\r\n");
			catalogXml.append("<Query>\r\n");
			catalogXml.append("<CmdType>Catalog</CmdType>\r\n");
			catalogXml.append("<SN>" + (int)((Math.random()*9+1)*100000) + "</SN>\r\n");
			catalogXml.append("<DeviceID>" + device.getDeviceId() + "</DeviceID>\r\n");
			catalogXml.append("</Query>\r\n");
			
			String tm = Long.toString(System.currentTimeMillis());

			CallIdHeader callIdHeader = device.getTransport().equals("TCP") ? tcpSipProvider.getNewCallId()
					: udpSipProvider.getNewCallId();

			Request request = headerProvider.createMessageRequest(device, catalogXml.toString(), "z9hG4bK-ViaCatalog-" + tm, "FromCat" + tm, null, callIdHeader);

			transmitRequest(device, request, errorEvent);
		} catch (SipException | ParseException | InvalidArgumentException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	/**
	 * ??????????????????
	 * 
	 * @param device ????????????
	 * @param startTime ????????????,???????????????yyyy-MM-dd HH:mm:ss
	 * @param endTime ????????????,???????????????yyyy-MM-dd HH:mm:ss
	 */  
	@Override
	public boolean recordInfoQuery(Device device, String channelId, String startTime, String endTime, int sn, Integer secrecy, String type, SipSubscribe.Event okEvent, SipSubscribe.Event errorEvent) {
		if (secrecy == null) {
			secrecy = 0;
		}
		if (type == null) {
			type = "all";
		}
		try {
			StringBuffer recordInfoXml = new StringBuffer(200);
			String charset = device.getCharset();
			recordInfoXml.append("<?xml version=\"1.0\" encoding=\"" + charset + "\"?>\r\n");
			recordInfoXml.append("<Query>\r\n");
			recordInfoXml.append("<CmdType>RecordInfo</CmdType>\r\n");
			recordInfoXml.append("<SN>" + sn + "</SN>\r\n");
			recordInfoXml.append("<DeviceID>" + channelId + "</DeviceID>\r\n");
			if (startTime != null) {
				recordInfoXml.append("<StartTime>" + DateUtil.yyyy_MM_dd_HH_mm_ssToISO8601(startTime) + "</StartTime>\r\n");
			}
			if (endTime != null) {
				recordInfoXml.append("<EndTime>" + DateUtil.yyyy_MM_dd_HH_mm_ssToISO8601(endTime) + "</EndTime>\r\n");
			}
			if (secrecy != null) {
				recordInfoXml.append("<Secrecy> "+ secrecy + " </Secrecy>\r\n");
			}
			if (type != null) {
				// ??????NVR??????????????????????????????all?????????????????????Type
				recordInfoXml.append("<Type>" + type+"</Type>\r\n");
			}
			recordInfoXml.append("</Query>\r\n");
			
			String tm = Long.toString(System.currentTimeMillis());

			CallIdHeader callIdHeader = device.getTransport().equals("TCP") ? tcpSipProvider.getNewCallId()
					: udpSipProvider.getNewCallId();

			Request request = headerProvider.createMessageRequest(device, recordInfoXml.toString(),
					"z9hG4bK-ViaRecordInfo-" + tm, "fromRec" + tm, null, callIdHeader);

			transmitRequest(device, request, errorEvent, okEvent);
		} catch (SipException | ParseException | InvalidArgumentException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	/**
	 * ??????????????????
	 * 
	 * @param device		????????????
	 * @param startPriority	??????????????????????????????
	 * @param endPriority	??????????????????????????????
	 * @param alarmMethod	??????????????????????????????
	 * @param alarmType		????????????
	 * @param startTime		????????????????????????????????????
	 * @param endTime		????????????????????????????????????
	 * @return				true = ??????????????????
	 */
	@Override
	public boolean alarmInfoQuery(Device device, String startPriority, String endPriority, String alarmMethod, String alarmType,
								 String startTime, String endTime, SipSubscribe.Event errorEvent) {
		try {
			StringBuffer cmdXml = new StringBuffer(200);
			cmdXml.append("<?xml version=\"1.0\" ?>\r\n");
			cmdXml.append("<Query>\r\n");
			cmdXml.append("<CmdType>Alarm</CmdType>\r\n");
			cmdXml.append("<SN>" + (int)((Math.random()*9+1)*100000) + "</SN>\r\n");
			cmdXml.append("<DeviceID>" + device.getDeviceId() + "</DeviceID>\r\n");
			if (!StringUtils.isEmpty(startPriority)) {
				cmdXml.append("<StartAlarmPriority>" + startPriority + "</StartAlarmPriority>\r\n");
			}
			if (!StringUtils.isEmpty(endPriority)) {
				cmdXml.append("<EndAlarmPriority>" + endPriority + "</EndAlarmPriority>\r\n");
			}
			if (!StringUtils.isEmpty(alarmMethod)) {
				cmdXml.append("<AlarmMethod>" + alarmMethod + "</AlarmMethod>\r\n");
			}
			if (!StringUtils.isEmpty(alarmType)) {
				cmdXml.append("<AlarmType>" + alarmType + "</AlarmType>\r\n");
			}
			if (!StringUtils.isEmpty(startTime)) {
				cmdXml.append("<StartAlarmTime>" + startTime + "</StartAlarmTime>\r\n");
			}
			if (!StringUtils.isEmpty(endTime)) {
				cmdXml.append("<EndAlarmTime>" + endTime + "</EndAlarmTime>\r\n");
			}
			cmdXml.append("</Query>\r\n");
			
			String tm = Long.toString(System.currentTimeMillis());

			CallIdHeader callIdHeader = device.getTransport().equals("TCP") ? tcpSipProvider.getNewCallId()
					: udpSipProvider.getNewCallId();

			Request request = headerProvider.createMessageRequest(device, cmdXml.toString(), null, "FromAlarm" + tm, null, callIdHeader);
			transmitRequest(device, request, errorEvent);
			return true;
		} catch (SipException | ParseException | InvalidArgumentException e) {
			e.printStackTrace();
			return false;
		} 
	}

	/**
	 * ??????????????????
	 * 
	 * @param device 		????????????
	 * @param channelId		????????????????????????
	 * @param configType	???????????????
	 */
	@Override
	public boolean deviceConfigQuery(Device device, String channelId, String configType,  SipSubscribe.Event errorEvent) {
		try {
			StringBuffer cmdXml = new StringBuffer(200);
			cmdXml.append("<?xml version=\"1.0\" ?>\r\n");
			cmdXml.append("<Query>\r\n");
			cmdXml.append("<CmdType>ConfigDownload</CmdType>\r\n");
			cmdXml.append("<SN>" + (int)((Math.random()*9+1)*100000) + "</SN>\r\n");
			if (StringUtils.isEmpty(channelId)) {
				cmdXml.append("<DeviceID>" + device.getDeviceId() + "</DeviceID>\r\n");
			} else {
				cmdXml.append("<DeviceID>" + channelId + "</DeviceID>\r\n");
			}
			cmdXml.append("<ConfigType>" + configType + "</ConfigType>\r\n");
			cmdXml.append("</Query>\r\n");
			
			String tm = Long.toString(System.currentTimeMillis());

			CallIdHeader callIdHeader = device.getTransport().equals("TCP") ? tcpSipProvider.getNewCallId()
					: udpSipProvider.getNewCallId();

			Request request = headerProvider.createMessageRequest(device, cmdXml.toString(), null, "FromConfig" + tm, null, callIdHeader);
			transmitRequest(device, request, errorEvent);
			return true;
		} catch (SipException | ParseException | InvalidArgumentException e) {
			e.printStackTrace();
			return false;
		} 
	}

	/**
	 * ????????????????????????
	 * 
	 * @param device ????????????
	 */  
	@Override
	public boolean presetQuery(Device device, String channelId, SipSubscribe.Event errorEvent) {
		try {
			StringBuffer cmdXml = new StringBuffer(200);
			cmdXml.append("<?xml version=\"1.0\" ?>\r\n");
			cmdXml.append("<Query>\r\n");
			cmdXml.append("<CmdType>PresetQuery</CmdType>\r\n");
			cmdXml.append("<SN>" + (int)((Math.random()*9+1)*100000) + "</SN>\r\n");
			if (StringUtils.isEmpty(channelId)) {
				cmdXml.append("<DeviceID>" + device.getDeviceId() + "</DeviceID>\r\n");
			} else {
				cmdXml.append("<DeviceID>" + channelId + "</DeviceID>\r\n");
			}
			cmdXml.append("</Query>\r\n");
			
			String tm = Long.toString(System.currentTimeMillis());

			CallIdHeader callIdHeader = device.getTransport().equals("TCP") ? tcpSipProvider.getNewCallId()
					: udpSipProvider.getNewCallId();

			Request request = headerProvider.createMessageRequest(device, cmdXml.toString(), null, "FromConfig" + tm, null, callIdHeader);
			transmitRequest(device, request, errorEvent);
			return true;
		} catch (SipException | ParseException | InvalidArgumentException e) {
			e.printStackTrace();
			return false;
		} 
	}

	/**
	 * ??????????????????????????????
	 * 
	 * @param device ????????????
	 */  
	@Override
	public boolean mobilePostitionQuery(Device device, SipSubscribe.Event errorEvent) {
		try {
			StringBuffer mobilePostitionXml = new StringBuffer(200);
			String charset = device.getCharset();
			mobilePostitionXml.append("<?xml version=\"1.0\" encoding=\"" + charset + "\"?>\r\n");
			mobilePostitionXml.append("<Query>\r\n");
			mobilePostitionXml.append("<CmdType>MobilePosition</CmdType>\r\n");
			mobilePostitionXml.append("<SN>" + (int)((Math.random()*9+1)*100000) + "</SN>\r\n");
			mobilePostitionXml.append("<DeviceID>" + device.getDeviceId() + "</DeviceID>\r\n");
			mobilePostitionXml.append("<Interval>60</Interval>\r\n");
			mobilePostitionXml.append("</Query>\r\n");
			
			String tm = Long.toString(System.currentTimeMillis());

			CallIdHeader callIdHeader = device.getTransport().equals("TCP") ? tcpSipProvider.getNewCallId()
					: udpSipProvider.getNewCallId();

			Request request = headerProvider.createMessageRequest(device, mobilePostitionXml.toString(), "z9hG4bK-viaPos-" + tm, "fromTagPos" + tm, null, callIdHeader);

			transmitRequest(device, request, errorEvent);
			
		} catch (SipException | ParseException | InvalidArgumentException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	/**
	 * ?????????????????????????????????
	 * 
	 * @param device	????????????
	 * @param expires	??????????????????
	 * @param interval	??????????????????
	 * @return			true = ??????????????????
	 */
	public boolean mobilePositionSubscribe(Device device, int expires, int interval) {
		try {
			StringBuffer subscribePostitionXml = new StringBuffer(200);
			String charset = device.getCharset();
			subscribePostitionXml.append("<?xml version=\"1.0\" encoding=\"" + charset + "\"?>\r\n");
			subscribePostitionXml.append("<Query>\r\n");
			subscribePostitionXml.append("<CmdType>MobilePosition</CmdType>\r\n");
			subscribePostitionXml.append("<SN>" + (int)((Math.random()*9+1)*100000) + "</SN>\r\n");
			subscribePostitionXml.append("<DeviceID>" + device.getDeviceId() + "</DeviceID>\r\n");
			if (expires > 0) {
				subscribePostitionXml.append("<Interval>" + String.valueOf(interval) + "</Interval>\r\n");
			}
			subscribePostitionXml.append("</Query>\r\n");

			String tm = Long.toString(System.currentTimeMillis());

			CallIdHeader callIdHeader = device.getTransport().equals("TCP") ? tcpSipProvider.getNewCallId()
					: udpSipProvider.getNewCallId();

			Request request = headerProvider.createSubscribeRequest(device, subscribePostitionXml.toString(), "z9hG4bK-viaPos-" + tm, "fromTagPos" + tm, null, expires, "presence" ,callIdHeader); //Position;id=" + tm.substring(tm.length() - 4));
			transmitRequest(device, request);

			return true;

		} catch ( NumberFormatException | ParseException | InvalidArgumentException	| SipException e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * ?????????????????????????????????
	 * 
	 * @param device		????????????
	 * @param expires		?????????????????????0 = ???????????????
	 * @param startPriority	??????????????????????????????
	 * @param endPriority	??????????????????????????????
	 * @param alarmMethod	??????????????????????????????
	 * @param alarmType		????????????
	 * @param startTime		????????????????????????????????????
	 * @param endTime		????????????????????????????????????
	 * @return				true = ??????????????????
	 */
	public boolean alarmSubscribe(Device device, int expires, String startPriority, String endPriority, String alarmMethod, String alarmType, String startTime, String endTime) {
		try {
			StringBuffer cmdXml = new StringBuffer(200);
			String charset = device.getCharset();
			cmdXml.append("<?xml version=\"1.0\" encoding=\"" + charset + "\"?>\r\n");
			cmdXml.append("<Query>\r\n");
			cmdXml.append("<CmdType>Alarm</CmdType>\r\n");
			cmdXml.append("<SN>" + (int)((Math.random()*9+1)*100000) + "</SN>\r\n");
			cmdXml.append("<DeviceID>" + device.getDeviceId() + "</DeviceID>\r\n");
			if (!StringUtils.isEmpty(startPriority)) {
				cmdXml.append("<StartAlarmPriority>" + startPriority + "</StartAlarmPriority>\r\n");
			}
			if (!StringUtils.isEmpty(endPriority)) {
				cmdXml.append("<EndAlarmPriority>" + endPriority + "</EndAlarmPriority>\r\n");
			}
			if (!StringUtils.isEmpty(alarmMethod)) {
				cmdXml.append("<AlarmMethod>" + alarmMethod + "</AlarmMethod>\r\n");
			}
			if (!StringUtils.isEmpty(alarmType)) {
				cmdXml.append("<AlarmType>" + alarmType + "</AlarmType>\r\n");
			}
			if (!StringUtils.isEmpty(startTime)) {
				cmdXml.append("<StartAlarmTime>" + startTime + "</StartAlarmTime>\r\n");
			}
			if (!StringUtils.isEmpty(endTime)) {
				cmdXml.append("<EndAlarmTime>" + endTime + "</EndAlarmTime>\r\n");
			}
			cmdXml.append("</Query>\r\n");

			String tm = Long.toString(System.currentTimeMillis());

			CallIdHeader callIdHeader = device.getTransport().equals("TCP") ? tcpSipProvider.getNewCallId()
					: udpSipProvider.getNewCallId();

			Request request = headerProvider.createSubscribeRequest(device, cmdXml.toString(), "z9hG4bK-viaPos-" + tm, "fromTagPos" + tm, null, expires, "presence" , callIdHeader);
			transmitRequest(device, request);

			return true;

		} catch ( NumberFormatException | ParseException | InvalidArgumentException	| SipException e) {
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public boolean catalogSubscribe(Device device, SipSubscribe.Event okEvent, SipSubscribe.Event errorEvent) {
		try {
			StringBuffer cmdXml = new StringBuffer(200);
			String charset = device.getCharset();
			cmdXml.append("<?xml version=\"1.0\" encoding=\"" + charset + "\"?>\r\n");
			cmdXml.append("<Query>\r\n");
			cmdXml.append("<CmdType>Catalog</CmdType>\r\n");
			cmdXml.append("<SN>" + (int)((Math.random()*9+1)*100000) + "</SN>\r\n");
			cmdXml.append("<DeviceID>" + device.getDeviceId() + "</DeviceID>\r\n");
			cmdXml.append("</Query>\r\n");

			String tm = Long.toString(System.currentTimeMillis());

			CallIdHeader callIdHeader = device.getTransport().equals("TCP") ? tcpSipProvider.getNewCallId()
					: udpSipProvider.getNewCallId();

			// ?????????????????????60?????????
			Request request = headerProvider.createSubscribeRequest(device, cmdXml.toString(), "z9hG4bK-viaPos-" + tm,
					"fromTagPos" + tm, null, device.getSubscribeCycleForCatalog(), "Catalog" ,
					callIdHeader);
			transmitRequest(device, request, errorEvent, okEvent);

			return true;

		} catch ( NumberFormatException | ParseException | InvalidArgumentException	| SipException e) {
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public boolean dragZoomCmd(Device device, String channelId, String cmdString) {
		try {
			StringBuffer dragXml = new StringBuffer(200);
			dragXml.append("<?xml version=\"1.0\" ?>\r\n");
			dragXml.append("<Control>\r\n");
			dragXml.append("<CmdType>DeviceControl</CmdType>\r\n");
			dragXml.append("<SN>" + (int) ((Math.random() * 9 + 1) * 100000) + "</SN>\r\n");
			if (StringUtils.isEmpty(channelId)) {
				dragXml.append("<DeviceID>" + device.getDeviceId() + "</DeviceID>\r\n");
			} else {
				dragXml.append("<DeviceID>" + channelId + "</DeviceID>\r\n");
			}
			dragXml.append(cmdString);
			dragXml.append("</Control>\r\n");
			String tm = Long.toString(System.currentTimeMillis());
			CallIdHeader callIdHeader = device.getTransport().equals("TCP") ? tcpSipProvider.getNewCallId()
					: udpSipProvider.getNewCallId();
			Request request = headerProvider.createMessageRequest(device, dragXml.toString(), "z9hG4bK-ViaPtz-" + tm, "FromPtz" + tm, null, callIdHeader);
			logger.debug("??????????????? " + request.toString());
			transmitRequest(device, request);
			return true;
		} catch (SipException | ParseException | InvalidArgumentException e) {
			e.printStackTrace();
		}
		return false;
	}


	private ClientTransaction transmitRequest(Device device, Request request) throws SipException {
		return transmitRequest(device, request, null, null);
	}

	private ClientTransaction transmitRequest(Device device, Request request, SipSubscribe.Event errorEvent) throws SipException {
		return transmitRequest(device, request, errorEvent, null);
	}

	private ClientTransaction transmitRequest(Device device, Request request, SipSubscribe.Event errorEvent , SipSubscribe.Event okEvent) throws SipException {
		ClientTransaction clientTransaction = null;
		if("TCP".equals(device.getTransport())) {
			clientTransaction = tcpSipProvider.getNewClientTransaction(request);
		} else if("UDP".equals(device.getTransport())) {
			clientTransaction = udpSipProvider.getNewClientTransaction(request);
		}

		CallIdHeader callIdHeader = (CallIdHeader)request.getHeader(CallIdHeader.NAME);
		// ??????????????????
		if (errorEvent != null) {
			sipSubscribe.addErrorSubscribe(callIdHeader.getCallId(), (eventResult -> {
				errorEvent.response(eventResult);
				sipSubscribe.removeErrorSubscribe(eventResult.callId);
			}));
		}
		// ????????????
		if (okEvent != null) {
			sipSubscribe.addOkSubscribe(callIdHeader.getCallId(), eventResult ->{
				okEvent.response(eventResult);
				sipSubscribe.removeOkSubscribe(eventResult.callId);
			});
		}

		clientTransaction.sendRequest();
		return clientTransaction;
	}

	/**
	 * ????????????
	 */
	@Override
	public void playPauseCmd(Device device, StreamInfo streamInfo) {
		try {
			Long cseq = redisCatchStorage.getCSEQ(Request.INFO);
			StringBuffer content = new StringBuffer(200);
			content.append("PAUSE RTSP/1.0\r\n");
			content.append("CSeq: " + cseq + "\r\n");
			content.append("PauseTime: now\r\n");
			Request request = headerProvider.createInfoRequest(device, streamInfo, content.toString());
			if (request == null) {
				return;
			}
			logger.info(request.toString());
			ClientTransaction clientTransaction = null;
			if ("TCP".equals(device.getTransport())) {
				clientTransaction = tcpSipProvider.getNewClientTransaction(request);
			} else if ("UDP".equals(device.getTransport())) {
				clientTransaction = udpSipProvider.getNewClientTransaction(request);
			}
			if (clientTransaction != null) {
				clientTransaction.sendRequest();
			}

		} catch (SipException | ParseException | InvalidArgumentException e) {
			e.printStackTrace();
		}
	}

	/**
	 * ????????????
	 */
	@Override
	public void playResumeCmd(Device device, StreamInfo streamInfo) {
		try {
			Long cseq = redisCatchStorage.getCSEQ(Request.INFO);
			StringBuffer content = new StringBuffer(200);
			content.append("PLAY RTSP/1.0\r\n");
			content.append("CSeq: " + cseq + "\r\n");
			content.append("Range: npt=now-\r\n");
			Request request = headerProvider.createInfoRequest(device, streamInfo, content.toString());
			if (request == null) return;
			logger.info(request.toString());
			ClientTransaction clientTransaction = null;
			if ("TCP".equals(device.getTransport())) {
				clientTransaction = tcpSipProvider.getNewClientTransaction(request);
			} else if ("UDP".equals(device.getTransport())) {
				clientTransaction = udpSipProvider.getNewClientTransaction(request);
			}

			clientTransaction.sendRequest();

		} catch (SipException | ParseException | InvalidArgumentException e) {
			e.printStackTrace();
		}
	}

	/**
	 * ??????????????????
	 */
	@Override
	public void playSeekCmd(Device device, StreamInfo streamInfo, long seekTime) {
		try {
			Long cseq = redisCatchStorage.getCSEQ(Request.INFO);
			StringBuffer content = new StringBuffer(200);
			content.append("PLAY RTSP/1.0\r\n");
			content.append("CSeq: " + cseq + "\r\n");
			content.append("Range: npt=" + Math.abs(seekTime) + "-\r\n");

			Request request = headerProvider.createInfoRequest(device, streamInfo, content.toString());
			if (request == null) return;
			logger.info(request.toString());
			ClientTransaction clientTransaction = null;
			if ("TCP".equals(device.getTransport())) {
				clientTransaction = tcpSipProvider.getNewClientTransaction(request);
			} else if ("UDP".equals(device.getTransport())) {
				clientTransaction = udpSipProvider.getNewClientTransaction(request);
			}

			clientTransaction.sendRequest();

		} catch (SipException | ParseException | InvalidArgumentException e) {
			e.printStackTrace();
		}
	}

	/**
	 * ??????????????????
	 */
	@Override
	public void playSpeedCmd(Device device, StreamInfo streamInfo, Double speed) {
		try {
			Long cseq = redisCatchStorage.getCSEQ(Request.INFO);
			StringBuffer content = new StringBuffer(200);
			content.append("PLAY RTSP/1.0\r\n");
			content.append("CSeq: " + cseq + "\r\n");
			content.append("Scale: " + String.format("%.1f",speed) + "\r\n");
			Request request = headerProvider.createInfoRequest(device, streamInfo, content.toString());
			if (request == null) return;
			logger.info(request.toString());
			ClientTransaction clientTransaction = null;
			if ("TCP".equals(device.getTransport())) {
				clientTransaction = tcpSipProvider.getNewClientTransaction(request);
			} else if ("UDP".equals(device.getTransport())) {
				clientTransaction = udpSipProvider.getNewClientTransaction(request);
			}

			clientTransaction.sendRequest();

		} catch (SipException | ParseException | InvalidArgumentException e) {
			e.printStackTrace();
		}
	}
}
