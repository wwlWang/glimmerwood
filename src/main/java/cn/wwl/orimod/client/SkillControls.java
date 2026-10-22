package cn.wwl.orimod.client;

import cn.wwl.orimod.progression.WheelSkill;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.network.ProgressActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Slots route to the existing server actions; the wheel never casts a skill. */
public final class SkillControls {
    private static final PanelGesture PANEL = new PanelGesture();
    private static final WheelSkill[] active = new WheelSkill[3];
    private static final int[] held = new int[3];
    private static final boolean[] blocked = new boolean[3];
    private static Object owner;
    private SkillControls() {}
    public static KeyMapping key(int slot) { return switch(slot) {
        case 0 -> SpiritControls.LIGHT_BURST; case 1 -> SpiritControls.CHARGE_FLAME; default -> SpiritControls.FLASH;
    }; }
    private static long now() { return System.nanoTime()/1_000_000; }
    public static void input(InputEvent.Key e) {
        var mc=Minecraft.getInstance();
        for(int i=0;i<3;i++)if(key(i).matches(e.getKey(),e.getScanCode())) {
            if(e.getAction()==GLFW.GLFW_RELEASE) { blocked[i]=false; finish(i,false); }
            else if(e.getAction()==GLFW.GLFW_PRESS)blocked[i]=false;
        }
        if(!SpiritControls.PANEL.matches(e.getKey(),e.getScanCode()))return;
        if(e.getAction()==GLFW.GLFW_PRESS&&SpiritControls.PANEL.isActiveAndMatches(com.mojang.blaze3d.platform.InputConstants.getKey(e.getKey(),e.getScanCode()))&&mc.screen==null&&mc.player!=null&&mc.isWindowActive()&&!RestorationClient.active())PANEL.press(now());
        else if(e.getAction()==GLFW.GLFW_RELEASE&&mc.screen==null)releasePanel();
    }
    public static void frame(RenderFrameEvent.Pre e) { updatePanel(); }
    private static void releasePanel() {
        var mc=Minecraft.getInstance();
        if(PANEL.release(now())&&mc.player!=null&&mc.screen==null&&mc.isWindowActive()&&!RestorationClient.active())
            mc.setScreen(new SpiritScreen(!SpiritData.isAwakened(mc.player)));
    }
    private static void updatePanel() {
        var mc=Minecraft.getInstance();
        if(mc.player==null||!mc.isWindowActive()||RestorationClient.active()) {
            PANEL.cancel();if(mc.screen instanceof SkillWheelScreen)mc.setScreen(null);return;
        }
        if(mc.screen instanceof SkillWheelScreen)return; // Screens release all key mappings on opening; use the real release edge.
        if(mc.screen!=null){PANEL.cancel();return;}
        if(PANEL.poll(now())&&SpiritData.isAwakened(mc.player))mc.setScreen(new SkillWheelScreen());
    }
    public static void closeWheel() { PANEL.cancel(); }
    public static void bindPressed(int slot) { blocked[slot]=true;finish(slot,true); }
    public static void slotReleased(int slot) { blocked[slot]=false; }
    public static void cancel() {
        for(int i=0;i<3;i++){if(key(i).isDown()||active[i]!=null)blocked[i]=true;finish(i,true);}
    }
    private static void send(String action) {
        if(!action.isEmpty()&&Minecraft.getInstance().getConnection()!=null)PacketDistributor.sendToServer(new ProgressActionPayload(action,""));
    }
    private static void finish(int i,boolean cancel) {
        var s=active[i];
        if(s!=null&&s.style!=WheelSkill.Style.TAP)send(cancel||held[i]<s.minimumHold?s.cancel:s.release);
        active[i]=null;held[i]=0;
    }
    public static void tick() {
        var mc=Minecraft.getInstance();
        if(owner!=mc.player){cancel();java.util.Arrays.fill(blocked,false);PANEL.cancel();owner=mc.player;}
        while(SpiritControls.PANEL.consumeClick()){}
        // Also support synthetic key mappings (devtools); physical keyboard uses timestamped edges above.
        if(mc.screen==null&&mc.player!=null) {
            if(SpiritControls.PANEL.isDown()&&!PANEL.pressed())PANEL.press(now());
            else if(!SpiritControls.PANEL.isDown()&&PANEL.pressed())releasePanel();
        }
        updatePanel();
        boolean valid=mc.player!=null&&mc.screen==null&&mc.isWindowActive()&&!RestorationClient.active()
                &&SpiritData.isSpirit(mc.player)&&mc.player.isAlive();
        for(int i=0;i<3;i++) {
            boolean clicked=false;while(key(i).consumeClick())clicked=true;
            if(!valid){finish(i,true);continue;}
            var skill=mc.player.getData(SpiritData.SKILL_BINDINGS).skill(i);
            if(active[i]!=null&&active[i]!=skill){finish(i,true);blocked[i]=key(i).isDown();}
            if(blocked[i]||skill==null)continue;
            boolean down=key(i).isDown();
            if(skill==WheelSkill.FLASH&&(mc.player.isUsingItem()||mc.options.keyAttack.isDown()||mc.options.keyUse.isDown()
                    ||chargeDown(mc))){finish(i,true);continue;}
            if(!down){finish(i,false);if(clicked&&skill.style==WheelSkill.Style.TAP)send(skill.start);continue;}
            if(active[i]==null){active[i]=skill;held[i]=0;}
            if(held[i]++==0)send(skill.start);
            else if(skill.style!=WheelSkill.Style.TAP&&(held[i]-1)%5==0)send(skill.start);
            if(skill==WheelSkill.CHARGE_FLAME&&held[i]<=skill.minimumHold&&held[i]%2==0) {
                double a=held[i]*.7;
                mc.player.level().addParticle(new cn.wwl.orimod.fx.SpiritParticleOptions(cn.wwl.orimod.fx.OriParticles.SPIRIT_SPARK.get(),
                        held[i]>=skill.minimumHold?0xFFE080:0xFF9A40,.35f,8),true,mc.player.getX()+Math.cos(a)*1.6,
                        mc.player.getY()+.8,mc.player.getZ()+Math.sin(a)*1.6,-Math.cos(a)*.2,0,-Math.sin(a)*.2);
            }
        }
    }
    private static boolean chargeDown(Minecraft mc) {
        for(int i=0;i<3;i++)if(mc.player.getData(SpiritData.SKILL_BINDINGS).skill(i)==WheelSkill.CHARGE_FLAME&&key(i).isDown())return true;
        return false;
    }
    public static java.util.Map<String,Object> diagnostics() {
        var mc=Minecraft.getInstance();return mc.screen instanceof SkillWheelScreen wheel?wheel.diagnostics():java.util.Map.of("open",false);
    }
}
