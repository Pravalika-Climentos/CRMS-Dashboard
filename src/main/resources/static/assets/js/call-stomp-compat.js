/* Socket.IO-shaped compatibility layer for communication.js, backed by authenticated STOMP. */
(() => {
  class CallSocketCompat {
    constructor() {
      this.connected=false; this.handlers=new Map(); this.roomSubscription=null; this.pendingRoom=null; this.timeoutAck=false;
      const scheme=location.protocol==='https:'?'wss':'ws';
      this.client=new StompJs.Client({
        brokerURL:`${scheme}://${location.host}/ws-chat`,
        connectHeaders:{Authorization:`Bearer ${CrmsAuth.getAccessToken()}`},
        reconnectDelay:5000,
        onConnect:()=>{
          this.connected=true;
          this.client.subscribe('/user/queue/calls',frame=>{const event=JSON.parse(frame.body);this.dispatch(event.type,event.payload);});
          this.client.subscribe('/topic/calls/presence',frame=>{const event=JSON.parse(frame.body);this.dispatch(event.type,event.payload);});
          this.subscribeToRoom();
          fetch('/api/calls/presence').then(required).then(r=>r.json()).then(list=>this.dispatch('presence:snapshot',list)).catch(()=>{});
          this.dispatch('connect');
        },
        onWebSocketClose:()=>{this.connected=false;this.dispatch('disconnect');},
        onStompError:frame=>this.dispatch('connect_error',new Error(frame.headers.message||'STOMP_ERROR'))
      });
      this.client.activate();
    }
    on(name,handler){if(!this.handlers.has(name))this.handlers.set(name,[]);this.handlers.get(name).push(handler);return this;}
    dispatch(name,payload){(this.handlers.get(name)||[]).forEach(handler=>handler(payload));}
    subscribeToRoom(){
      if(!this.connected||!this.pendingRoom)return;
      this.roomSubscription?.unsubscribe();
      const room=this.pendingRoom;
      this.roomSubscription=this.client.subscribe(`/topic/call-rooms/${room}`,frame=>{
        const event=JSON.parse(frame.body);
        this.dispatch(event.type,event.payload);
      });
    }
    disconnect(){this.pendingRoom=null;this.roomSubscription?.unsubscribe();this.roomSubscription=null;this.client.deactivate();}
    timeout(){this.timeoutAck=true;return this;}
    emit(name,payload={},callback){
      const ack=typeof callback==='function'?callback:()=>{};
      const timeoutAck=this.timeoutAck; this.timeoutAck=false;
      const ok=value=>timeoutAck?ack(null,value):ack(value);
      const fail=error=>timeoutAck?ack(error):ack({ok:false,message:error.message});
      const json=body=>({method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
      const publish=(destination,body)=>this.client.publish({destination,body:JSON.stringify(body)});
      (async()=>{
        if(name==='presence:check'){
          const response=await required(fetch(`/api/calls/presence?email=${encodeURIComponent(payload.email||'')}`));
          return ok(await response.json());
        }
        if(name==='presence:snapshot'){
          const response=await required(fetch('/api/calls/presence'));
          return ok(await response.json());
        }
        if(name==='team:snapshot')return ok([]);
        if(name==='presence:set')return ok({ok:true});
        if(name==='call:invite'){
          const response=await fetch('/api/calls',json({callId:payload.callId,to:payload.to,type:payload.type}));
          if(!response.ok)throw new Error((await response.json().catch(()=>({}))).message||'Call could not be started.');
          publish('/app/calls/offer',payload);return ok({ok:true});
        }
        if(name==='call:answer'){await required(fetch(`/api/calls/${payload.callId}/answer`,{method:'PATCH'}));publish('/app/calls/answer',payload);return ok({ok:true});}
        if(name==='call:ice'){publish('/app/calls/ice',payload);return;}
        if(name==='call:decline'){await required(fetch(`/api/calls/${payload.callId}/decline`,{method:'PATCH'}));publish('/app/calls/declined',payload);return ok({ok:true});}
        if(name==='call:end'){await required(fetch(`/api/calls/${payload.callId}/end`,{method:'PATCH'}));publish('/app/calls/ended',payload);return ok({ok:true});}
        if(name==='team:join'){
          this.pendingRoom=payload.room;
          this.subscribeToRoom();
          return ok({ok:true});
        }
        if(name==='team:leave'){this.pendingRoom=null;this.roomSubscription?.unsubscribe();this.roomSubscription=null;await fetch(`/api/team-rooms/${encodeURIComponent(payload.room)}/leave`,{method:'POST'});return ok({ok:true});}
        if(name==='team:chat'){publish('/app/call-rooms/chat',payload);return ok({ok:true});}
        if(name==='team:notes'){publish('/app/call-rooms/notes',payload);return ok({ok:true});}
        if(name==='chat:join')return ok({ok:true});
        if(name==='chat:send')return ok({ok:false,message:'Use the existing Chat page for messaging.'});
      })().catch(error=>{this.dispatch('call:error',{message:error.message});fail(error);});
      return this;
    }
  }
  async function required(promise){const response=await promise;if(!response.ok){const body=await response.json().catch(()=>({}));throw new Error(body.message||`HTTP_${response.status}`);}return response;}
  window.io=()=>new CallSocketCompat();
})();
