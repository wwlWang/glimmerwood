package cn.wwl.orimod.progression;

/** Pure admission/timeline rules shared by the server, UI and tests. */
public final class RestorationRules {
    public static final int DURATION=300, REWARD=500;
    public static String refusal(boolean alive,boolean spirit,int wisps,boolean blessed,boolean completed,boolean busy){
        if(!alive || !spirit)return "dormant";
        if(completed)return "complete";
        if(busy)return "busy";
        if((wisps&7)!=7)return "wisps";
        return blessed?null:"blessing";
    }
    public static int waveSize(int original){return (original+1)/2;}
    /** GLFW ESC/PRESS constants kept here so this policy is testable without a client or GL context. */
    public static boolean interceptEscape(boolean connected,boolean active,int key,int action){return connected && active && key==256 && action==1;}
    public static float progress(int remaining){return Math.clamp(1-remaining/(float)DURATION,0,1);}
    private RestorationRules(){}
}
