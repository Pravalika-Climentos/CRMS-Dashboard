package com.example.Call.Service;

import com.example.Call.DTO.Realtime.*;
import com.example.Call.Entity.CallSession;
import com.example.Call.Repository.CallSessionRepository;
import com.example.CRM.Entity.User;
import com.example.CRM.Repository.UserRepository;
import com.example.Common.Exception.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@Service
public class CallSignalService {
    private final CallSessionRepository calls; private final UserRepository users; private final SimpMessagingTemplate messaging;
    public CallSignalService(CallSessionRepository calls,UserRepository users,SimpMessagingTemplate messaging){this.calls=calls;this.users=users;this.messaging=messaging;}

    @Transactional(readOnly=true)
    public void offer(Long senderId, CallOfferRequest request){
        CallSession call=call(request.callId());
        if(!call.getCaller().getUserId().equals(senderId)) throw new ForbiddenOperationException("Only the caller can send the offer.");
        User caller=call.getCaller(); User callee=call.getCallee();
        var event=new CallOfferEvent(call.getCallId(),caller.getEmail(),caller.getUserId(),caller.getFullName(),caller.getEmail(),caller.getPhone(),caller.getAvatar(),request.type().toLowerCase(),request.offer());
        send(callee.getUserId(),"call:offer",event); send(caller.getUserId(),"call:ringing",Map.of("callId",call.getCallId(),"to",callee.getEmail()));
    }
    @Transactional(readOnly=true)
    public void answer(Long senderId,CallAnswerRequest request){
        CallSession c=call(request.callId()); if(!c.getCallee().getUserId().equals(senderId)) throw new ForbiddenOperationException("Only the recipient can send the answer.");
        send(c.getCaller().getUserId(),"call:answer",Map.of("callId",c.getCallId(),"answer",request.answer()));
    }
    @Transactional(readOnly=true)
    public void ice(Long senderId,CallIceCandidateRequest request){
        CallSession c=call(request.callId()); Long target=other(c,senderId);
        send(target,"call:ice",Map.of("callId",c.getCallId(),"candidate",request.candidate()));
    }
    @Transactional(readOnly=true)
    public void declined(Long senderId,CallControlRequest request){CallSession c=call(request.callId());Long target=other(c,senderId);send(target,"call:declined",Map.of("callId",c.getCallId()));}
    @Transactional(readOnly=true)
    public void ended(Long senderId,CallControlRequest request){CallSession c=call(request.callId());Long target=other(c,senderId);send(target,"call:ended",Map.of("callId",c.getCallId()));}
    private void send(Long id,String type,Object body){messaging.convertAndSendToUser(String.valueOf(id),"/queue/calls",new CallRealtimeEvent(type,body));}
    private CallSession call(String id){return calls.findById(id).orElseThrow(()->new ResourceNotFoundException("Call was not found."));}
    private Long other(CallSession c,Long id){if(c.getCaller().getUserId().equals(id))return c.getCallee().getUserId();if(c.getCallee().getUserId().equals(id))return c.getCaller().getUserId();throw new ForbiddenOperationException("You are not a participant in this call.");}
}
