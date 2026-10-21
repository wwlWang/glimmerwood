package cn.wwl.orimod.fx;

import cn.wwl.orimod.network.RestorationPayload;
import cn.wwl.orimod.progression.RestorationRules;
import java.util.UUID;

/** Client session lease is refreshed each server second. Disconnect clears it even without an end packet. */
public final class RestorationDisplay {
    private UUID session;
    private int remaining,lease;
    public void receive(RestorationPayload p){session=p.session().orElse(null);remaining=p.remaining();lease=session==null?0:60;}
    public boolean active(){return session!=null && lease>0;}
    public UUID session(){return session;}
    public int remaining(){return remaining;}
    public void tick(){if(lease>0 && --lease==0)clear();else if(active() && remaining>0)remaining--;}
    public boolean escape(boolean connected,int key,int action){return RestorationRules.interceptEscape(connected,active(),key,action);}
    public void clear(){session=null;remaining=lease=0;}
}
