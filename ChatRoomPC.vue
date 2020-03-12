<template>
    <div id="chatRoom-pc">
        <div style="width:65%">
            <div class="content" id="content">
            </div>
            <div class="sendcontrol">
                <div class="sendtext" id="inputMessage" contenteditable="true"></div> 
                &nbsp;
                <a> <span style="padding:3px 6px;border:1px solid rgb(138, 107, 107);border-radius:4px;font-size:0.8rem;position:relative;top:-8px;" @click="sendMessage">发送</span> </a> 
            </div>
        </div>
        

        <div style="width:30%;position:relative;left:5%;">
            <div>
                在线人员列表
                &nbsp;
                <a @click="loadOnlineList">
                    <i class="iconfont">&#xe6b0;</i>
                </a>
                &nbsp;
                <Button v-if="shutAllType==true" @click="releaseAll">解除全部禁言</Button>
                <Button v-else @click="shutUpAll">全部禁言</Button>
            </div>
            <div class="onlineList">
                <ul>
                    <li v-for="(item,index) in onlineList"> {{item.username}} &nbsp;&nbsp; 
                        <Button class="shutup-btn" size="small" v-if="blackRoomList.indexOf(item.userid) > -1" @click="release(item.userid)" type="warning">取消禁言</Button>
                        <Button class="shutup-btn" size="small" v-else @click="shutUp(item.userid)">禁言</Button> 
                        <hr v-if="index < onlineList.length">
                    </li>
                    <div style="text-align:center;margin:2rem 0;">———————— 没有更多了 ————————</div>
                </ul>
            </div>
        </div>
    </div>
</template>

<script>
import {loadOnlineList} from '@/api/ChatRoom'
export default {
    name:'chatRoom-pc',
    computed:{
        user(){
            return this.$store.state.user
        }
    },
    props:{
        orgId: {
            type: Number,
            default: 0
        },
        liveChapterId : {
            type : Number,
            default : 0
        }
    },
    data(){
        return{
            wsUrl : "",
            ws : null,
            contentList : [],
            timer : null,
            lockReconnect : false,//避免重复连接
            /****** 心跳参数 ******/
            heartTimeOut : 30000,
            timeoutObj : null,
            serverTimeObj : null,
            /** */
            flag : 1,
            sendNum : 0,//20秒内的发送次数
            sendTimer : null,//防刷屏神器
            onlineList : [],//在线人数列表
            blackRoomList : [],//小黑屋名单
            shutAllType : false,//全部禁言开关状态
        }
    },
    mounted(){
        this.wsUrl = "wss://studentb.fengxueba.com/xueba-student/chatRoomServer/"+this.liveChapterId+"/" + "老师" + "/" + 0 + "-first";
        this.createWebsocket();
        let _this = this;
        this.sendTimer = setInterval(()=>{
            this.sendNum = 0;
        },20000);
        this.loadOnlineList();
    },
    methods:{
        createWebsocket() {
            if(!!this.timer){
                clearTimeout(this.timer)
            }
            try {
                this.init();
            } catch (e) {
                //进行重连;
                console.log('websocket连接错误');
                this.reConnect();
            }
        },
        reConnect() {//重连函数
            if(!!this.timer){
                clearTimeout(this.timer)
            }
            if(this.lockReconnect) {
                return;
            };
            this.wsUrl = "wss://studentb.fengxueba.com/xueba-student/chatRoomServer/"+this.liveChapterId+"/" + "老师" + "/" + 0;
            this.lockReconnect = true;
            let _this = this;
            this.timer = setTimeout(function () {
                _this.init();
                _this.lockReconnect = false;
            }, 3000)
        },
        init(){
            this.ws = new WebSocket(this.wsUrl);
            let _this = this
            this.ws.onopen = function(message){            
                _this.contentList.push(message.data)
                _this.heart();
            }
            this.ws.onmessage = function(message){
                if(message.data == "ping"){
                    return;
                }else if(message.data == "<p>全体禁言</p>"){
                    _this.shutAllType = true;
                }else if(message.data == "<p>已解除全体禁言</p>"){
                    _this.shutAllType = false;
                }
                    $('.content').append(message.data)
                    var msg = document.getElementById("content")
                    var distance = msg.scrollHeight - msg.offsetHeight;
                    msg.scrollTop = distance;
                    // _this.heart();
                
            }
            this.ws.onclose = function(ev){
                if(_this.flag == 1)
                    _this.reConnect();
            };
            this.ws.onerror = function(val){
                console.log(val)
            }
        },

        sendMessage(){
            var message = document.getElementById("inputMessage")
            if(message.innerText==""){
                this.showErrorAlert("请输入您要发送的消息")
                return;
            }else{
                if(this.sendNum >= 10){
                    this.showErrorAlert("您发送频率太快啦，休息会再发吧~")
                    return;
                }
                if(message.innerText.length >= 100){
                    this.showErrorAlert("超过了100字数限制哦~")
                    return;
                }
                this.ws.send(message.innerText);
                message.innerText = "";
                this.sendNum ++;
            }
        },
        heart(){
                
            let _this = this;
            if(!!this.timeoutObj){
                clearTimeout(this.timeoutObj);
            }
            if(!!this.serverTimeoutObj){
                clearTimeout(this.serverTimeoutObj);
            }
            this.timeoutObj = setTimeout(function(){
                //这里发送一个心跳，后端收到后，返回一个心跳消息，
                //onmessage拿到返回的心跳就说明连接正常
                _this.ws.send("ping");
                _this.serverTimeoutObj = setTimeout(function(){//如果超过一定时间还没重置，说明后端主动断开了
                    _this.ws.close();     //如果onclose会执行reconnect，我们执行ws.close()就行了.如果直接执行reconnect 会触发onclose导致重连两次
                }, _this.heartTimeOut)
            }, this.heartTimeOut)
        },
        showErrorAlert(message){
            this.$message.error({
                message: message,
                center: true,
                customClass:"alert-style1"
            });
        },
        shutUpAll(){
            this.ws.send("shut up all");
        },
        shutUp(userId){
            this.blackRoomList.push(userId)
            this.ws.send("shut up " + userId);
        },
        release(userId){
            console.log(this.blackRoomList)
            for(let i=0;i < this.blackRoomList.length; i++){
                if(this.blackRoomList[i] == userId){
                    console.log('ok')
                    this.blackRoomList.splice(i,1);
                    break;
                }
            }
            console.log(this.blackRoomList)
            this.ws.send("release " + userId);
        },
        releaseAll(){
            this.blackRoomList = [];
            this.shutAllType = false;
            this.ws.send("release all");
        },
        async loadOnlineList(){
            let result = await loadOnlineList({
                liveChapterId : this.liveChapterId
            })
            if(result.status == 0){
                alert("加载在线列表失败");
            }else if(result.status == 1){
                this.onlineList = result.data;
                this.shutAllType = result.liveChapter.shutUp==1?true:false
                for(let i=0; i<result.blackRoomList.length; i++){
                    this.blackRoomList.push(result.blackRoomList[i].userid);
                }
            }
        }
    },
    beforeDestroy(){
        this.flag = -1
        this.ws.close();
        if(!!this.timer){
            clearTimeout(this.timer);
        }
        if(!!this.timeoutObj){
            clearTimeout(this.timeoutObj);
        }
        if(!!this.serverTimeoutObj){
            clearTimeout(this.serverTimeoutObj);
        }
        if(!!this.sendTimer){
            clearInterval(this.sendTimer)
        }
    },
}
</script>

<style lang="less" scoped>
    #chatRoom-pc{
        width: 80%;
        height: 450px;
        border-radius: 8px;
        padding: 4px;
        position: relative;
        z-index: 1;
        display: inline-flex;
        hr{
            border: .5px solid #dedede;
            margin: 1rem auto;
        }
        .content{
            width: 100%;
            height: 84%;
            padding: 6px;
            text-align: left;
            overflow-y: scroll;

            .system{
                padding-bottom: 0.4rem;
                .system-tip{
                    color: #f00;
                }
                .system-msg{
                    color: #ffa500;
                }
            }
            .paragraph{
                padding-bottom: 0.4rem;
                word-wrap:break-word;
                .time{
                    font-size: 0.8rem;
                }
                .username{
                    color: #0007;
                    font-size: 0.9rem;
                }
                .message{
                    padding: 2px;
                }
            }
        }
        .sendcontrol{
            position: relative;
            width: 100%;
            bottom: 0.4rem;
            .sendtext{
                width: 90%;
                border: 1px solid #d8d8d8;
                min-height: 24px;
                max-height: 48px;
                background-color: #fff;
                border-radius: 5px 5px;
                overflow-y: auto;
                font-size: .9em;
                position: relative;
                display: inline-block;
                padding: 2px 4px;
            }
            .send-icon{
                font-size: 1.6rem;
                color: skyblue;
            }
            .buttons{
                width: 100%;
                height: 40px;
                position: relative;
                bottom: 0;
                text-align: right;
            }
        }
        .onlineList{
            background: #ececec;
            padding: 5px 0;
            height: 350px;
            overflow-y: auto;
            margin-top:6px; 
            li{
                margin: 1rem 0.5rem;
            }
            .shutup-btn{
                float: right;
            }
        }
        
    }
</style>
