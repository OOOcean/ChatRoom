package com.chifeng.xueba.student.util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

import org.springframework.beans.factory.annotation.Autowired;

import com.chifeng.xueba.SpringUtil;
import com.chifeng.xueba.common.model.service.ChatRoomService;

@ServerEndpoint(value="/chatRoomServer/{orgid}/{username}/{userid}")
public class ChatRoomUtil {
	private Session session;
	private String userName;
	private static final HashMap<String, Object> connectMap = new HashMap<String, Object>();
	private static final HashMap<String, String> userMap = new HashMap<String, String>();
	private static final HashMap<String, ChatRoomUtil> studentMap = new HashMap<>();
	private static final Map<Integer, CopyOnWriteArraySet<ChatRoomUtil>> rooms = new HashMap<>();
	private static final ExecutorService executor = Executors.newFixedThreadPool(5);
	private Integer roomId;
	private Long userId;
	
	@Autowired
	private static ChatRoomService chatRoomService = SpringUtil.getBean(ChatRoomService.class);
	
	@OnOpen
	public void start(@PathParam("orgid")String orgId,
					  @PathParam("username")String userName,
					  @PathParam("userid")String userId,
					  Session session){
		this.session = session;
		this.roomId = Integer.parseInt(orgId);
		this.userName = userName;
		this.userId = Long.parseLong(userId.split("-")[0]);
		CopyOnWriteArraySet<ChatRoomUtil> friends = rooms.get(roomId);
		if (friends == null) {
            synchronized (rooms) {
                if (!rooms.containsKey(roomId)) {
                    friends = new CopyOnWriteArraySet<>();
                    rooms.put(roomId, friends);
                }
            }
        }
        friends.add(this);
        chatRoomService.addOnlineUser(roomId, this.userId, userName);
        
        if(userId.endsWith("-first")){
        	String message = "<div class='system'> <span class='system-tip'>系统消息:</span> <span class='system-msg'>欢迎 " + userName + " 进入直播间 </span> <div>";
            for (ChatRoomUtil item : friends) {
    			item.session.getAsyncRemote().sendText(message);
    		}
        }

		this.userName = userName;
		userMap.put(session.getId(), userName);	
		connectMap.put(session.getId(), this);
		studentMap.put(roomId+"-"+this.userId, this);
	}
	
	@OnMessage
	public void chat(String clientMessage, Session session){
		if(clientMessage.equals("ping")){
			session.getAsyncRemote().sendText("ping");
        }else if(clientMessage.equals("shut up all") && userId == 0){
        	chatRoomService.shutAllInBlackRoom(roomId);
        	CopyOnWriteArraySet<ChatRoomUtil> friends = rooms.get(roomId);
        	if (friends != null) {
	        	for (ChatRoomUtil item : friends) {
	        		item.session.getAsyncRemote().sendText("<p>全体禁言</p>");
				}
	        }
        	
        }else if(clientMessage.equals("release all") && userId == 0){
        	chatRoomService.releaseAllofBlackRoom(roomId);
        	CopyOnWriteArraySet<ChatRoomUtil> friends = rooms.get(roomId);
        	if (friends != null) {
	        	for (ChatRoomUtil item : friends) {
	        		item.session.getAsyncRemote().sendText("<p>已解除全体禁言</p>");
				}
	        }
        }else if(clientMessage.indexOf("shut up ") == 0 && userId == 0){
        	try {
				Long userId = Long.valueOf(clientMessage.replaceAll(".*[^\\d](?=(\\d+))",""));
				chatRoomService.shutInBlackRoom(roomId, userId);
				System.out.println("你被禁言了："+roomId + "    " + userId);
				ChatRoomUtil thisUtil = studentMap.get(roomId+"-"+userId);
				thisUtil.session.getAsyncRemote().sendText("<p>您已被禁言</p>");
				studentMap.get(roomId+"-0").session.getAsyncRemote().sendText("<p>" + userId + " 已被你禁言</p>");
			} catch (NumberFormatException e) {
				e.printStackTrace();
			}
        }else if(clientMessage.indexOf("release ") == 0 && userId == 0){
        	try {
				Long userId = Long.valueOf(clientMessage.replaceAll(".*[^\\d](?=(\\d+))",""));
				chatRoomService.releaseBlackRoom(roomId, userId);
				System.out.println("已被解除禁言了："+roomId + "    " + userId);
				ChatRoomUtil thisUtil = studentMap.get(roomId+"-"+userId);
				thisUtil.session.getAsyncRemote().sendText("<p>已解除禁言</p>");
			} catch (NumberFormatException e) {
				e.printStackTrace();
			}
        }
        else{
	    	executor.execute(new Runnable() {
				@Override
				public void run() {
					chatRoomService.saveChatContent(roomId, 1L, userId, clientMessage);
				}
			});
	    	CopyOnWriteArraySet<ChatRoomUtil> friends = rooms.get(roomId);
	        if (friends != null) {
	        	for (ChatRoomUtil item : friends) {
					item.session.getAsyncRemote().sendText(htmlMessage(clientMessage));
				}
	        }
        }
	}
	
	@OnClose
	public void close(Session session){
		userMap.remove(session.getId());
		connectMap.remove(session.getId());
		CopyOnWriteArraySet<ChatRoomUtil> friends = rooms.get(roomId);
		if (friends != null) {
			friends.remove(this);
		}
		studentMap.remove(roomId+"-"+userId);
		chatRoomService.deleteOnlineUser(roomId, userId);
	}
	
	@OnError
	public void onError(Session session, Throwable error){
		System.out.println("直播间异常，房间号："+roomId);
		close(session);
	}
	
	private String htmlMessage(String message){
		StringBuffer messageBuffer = new StringBuffer();
		messageBuffer.append("<div class='paragraph'>");
		messageBuffer.append("<span class='username'>");
		messageBuffer.append(userName);
		messageBuffer.append(": ");
		messageBuffer.append("</span>");
		messageBuffer.append("<span class='message'>");
		messageBuffer.append(message);
		messageBuffer.append("</span>");
		messageBuffer.append("</div>");
		return messageBuffer.toString();
	}
	


}
