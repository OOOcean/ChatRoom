<template>
    <div id="chatRoom_moblie">
        <div class="content" id="content" @click="pageRecovery">
        </div>
        <div class="sendcontrol">
            <div class="sendtext" id="inputMessage" :contenteditable="allowInput"></div> 
            <button style="padding:2px 4px;border:1px solid rgb(138, 107, 107);border-radius:4px;font-size:0.9rem;position:absolute;transform: translate(12px, 0px);" @click="sendMessage">发送</button> 
        </div>        
    </div>
</template>

<script>
import { Notify} from 'vant';
import {getLiveChapter} from '@/api/live'
export default {
    name:'chatRoom_moblie',
    components:{
        Notify
    },
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
            shutupSwitch : false,//禁言开关
            allowInput : true,//允许输入
        }
    },
    mounted(){
        this.getLiveChapterInfo();
        this.wsUrl = "wss://localhost:8080/chatRoomServer/"+this.liveChapterId+"/" + this.user.nickName + "/" + localStorage.getItem("studentId") + "-first";
        this.createWebsocket();
        let _this = this;
        this.sendTimer = setInterval(()=>{
            this.sendNum = 0;
        },20000)
    },
    methods:{
        async getLiveChapterInfo(){
            let result = await getLiveChapter({
                LiveChapterId : this.liveChapterId
            })
            if(result.status == 0){
                this.$notify({ type: 'danger', message: "获取课程信息错误！",duration:1200 })
            }else if(result.status == 1){
                let data = result.liveChapter;
                this.shutupSwitch = data.shutUp==1?true:false;
                document.getElementById("inputMessage").innerText = data.shutUp==1?'全体禁言中':'';
                this.allowInput = false;
            }
        },
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
            this.wsUrl = "wss://localhost:8080/chatRoomServer/"+this.liveChapterId+"/"+this.user.nickName + "/" + localStorage.getItem("studentId");
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
                }else if(message.data == "<p>全体禁言</p>" || message.data == "<p>您已被禁言</p>"){
                    _this.allowInput = false;
                    _this.shutupSwitch = true;
                    if(message.data == "<p>全体禁言</p>"){
                        document.getElementById("inputMessage").innerText = '全体禁言中'
                    }else{
                        document.getElementById("inputMessage").innerText = '禁言中'
                    }
                }else if(message.data == "<p>已解除禁言</p>" || message.data == "<p>已解除全体禁言</p>"){
                    console.log("ok")
                    _this.allowInput = true;
                    _this.shutupSwitch = false;
                    document.getElementById("inputMessage").innerText = ''
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
            if(this.shutupSwitch == true){
                this.$notify({ type: 'danger', message: "禁言中，无法发送消息！",duration:1200 })
                return;
            }
            var message = document.getElementById("inputMessage")
            if(message.innerText==""){
                this.$notify({ type: 'danger', message: "请输入您要发送的消息",duration:1200 })
                return;
            }else{
                if(this.sendNum >= 10){
                    this.$notify({ type: 'danger', message: "您发送频率太快啦，休息会再发吧~",duration:1200 })
                    return;
                }
                if(message.innerText.length >= 100){
                    this.$notify({ type: 'danger', message: "超过了100字数限制哦~",duration:1200 })
                    return;
                }
                this.ws.send(message.innerText);
                message.innerText = "";
                this.sendNum ++;
            }
            $(window).scrollTop(0);
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
        pageRecovery(){
            $(window).scrollTop(0);
        },
        showErrorAlert(message){
            this.$message.error({
                message: message,
                center: true,
                customClass:"alert-style1"
            });
        },
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

<style lang="less">
    #chatRoom_moblie{
        width: 100%;
        height: 100%;
        border-radius: 8px;
        padding: 4px;
        position: relative;
        z-index: 1;
        .content{
            position: relative;
            width: 100%;
            height: 96%;
            padding: 6px 6px 1.5rem 6px;
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
            width: 98%;
            bottom: 1.5rem;
            text-align: left;
            .sendtext{
                width:86%; 
                border:1px solid #d8d8d8; 
                min-height:24px;max-height:48px; 
                background-color:#FFF; 
                border-radius:5px 5px; 
                overflow-y:auto; 
                font-size:0.9em;
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
        
    }
</style>
