package cn.wwl.orimod.client.render;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import static org.junit.jupiter.api.Assertions.*;

class SpiritWeaponRigTest {
    static float rad(float v) { return (float)Math.toRadians(v); }
    static final java.util.Map<SpiritFirstPersonGrip.Kind,com.google.gson.JsonObject> MODELS=new java.util.HashMap<>();
    static Matrix4f display(SpiritFirstPersonGrip.Kind kind,boolean left) {
        var root=MODELS.computeIfAbsent(kind,k->{
            var path="/assets/orimod/models/item/spirit_"+k.name().toLowerCase(java.util.Locale.ROOT)+".json";
            try(var in=SpiritWeaponRigTest.class.getResourceAsStream(path)) {
                return com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(java.util.Objects.requireNonNull(in))).getAsJsonObject();
            }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
        });
        var model=root.getAsJsonObject("display").getAsJsonObject("firstperson_"+(left?"lefthand":"righthand"));
        var t=model.getAsJsonArray("translation");var a=model.getAsJsonArray("rotation");var z=model.getAsJsonArray("scale");float side=left?-1:1;
        return new Matrix4f().translation(side*t.get(0).getAsFloat()/16,t.get(1).getAsFloat()/16,t.get(2).getAsFloat()/16)
                .rotateXYZ(rad(a.get(0).getAsFloat()),rad(side*a.get(1).getAsFloat()),rad(side*a.get(2).getAsFloat()))
                .scale(z.get(0).getAsFloat(),z.get(1).getAsFloat(),z.get(2).getAsFloat());
    }
    static Matrix4f input(SpiritFirstPersonMotion.Pose motion,boolean left) {
        var p=motion.side(left);
        return new Matrix4f().translation((left?-1:1)*.56f+p.x(),-.36f+p.y(),-.82f+p.z())
                .rotateY(rad(p.yaw())).rotateZ(rad(p.roll())).rotateX(rad(p.pitch()));
    }
    static Matrix4f toolInput(SpiritFirstPersonGrip.Kind kind,SpiritFirstPersonMotion.Pose p,boolean left) {
        boolean hammer=kind==SpiritFirstPersonGrip.Kind.HAMMER;
        return input(p,left).translateLocal((left?-1:1)*(hammer?.05f:.22f),hammer?0:-.16f,hammer?-.04f:0);
    }
    @ParameterizedTest
    @EnumSource(value=SpiritFirstPersonGrip.Kind.class,names={"BOW","HAMMER","SPEAR"})
    void reviewedWeaponsUseFixedBonesInsteadOfTheLegacyStretch(SpiritFirstPersonGrip.Kind kind) {
        assertTrue(SpiritFirstPersonGrip.newRig(kind),kind+" is still routed to the unbounded legacy forearm");
    }
    @ParameterizedTest @EnumSource(net.minecraft.world.entity.HumanoidArm.class)
    void sameWeaponInOffHandDoesNotBorrowMainHandDrawOrDissolve(net.minecraft.world.entity.HumanoidArm mainArm) {
        for(var context:net.minecraft.world.item.ItemDisplayContext.values()) {
            boolean expected=context==(mainArm==net.minecraft.world.entity.HumanoidArm.LEFT
                    ?net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                    :net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
            assertEquals(expected,SpiritFirstPersonGrip.mainHandView(context,mainArm));
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void bowBothHandsStayOnTheirContactsDuringDraw(boolean left) {
        for(int t=0;t<=20;t++) {
            var pose=SpiritFirstPersonMotion.bowDraw(t);
            var fit=SpiritWeaponRig.bow(left,pose.raise(),pose.pull(),pose.kick(),0,true);
            assertEquals(0,fit.mainError(),.001,"bow grip t="+t);
            assertEquals(0,fit.stringError(),.015,"string contact t="+t);
            for(var arm:new SpiritArmRig.Frame[]{fit.main(),fit.string()}) {
                assertEquals(.36,arm.shoulder().distance(arm.elbow()),.0001);
                assertEquals(.38,arm.elbow().distance(arm.wrist()),.0001);
                assertTrue(arm.wristDegrees()<=55.001);
                assertTrue(Math.abs(arm.twistDegrees())<=75.001);
            }
            var b=SpiritWeaponProjection.measure(SpiritFirstPersonGrip.Kind.BOW,fit.item(),pose.pull(),1920f/1015);
            assertTrue(b.near()>=.25,"bow near "+b.near());
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void usingBowBeforeEquipFinishesStillKeepsStringInThePaw(boolean left) {
        for(float equip:new float[]{.4f,.6f,1}) {
            var b=SpiritFirstPersonMotion.bowDraw(1.01f);
            var fit=SpiritWeaponRig.bow(left,b.raise(),b.pull(),0,SpiritFirstPersonMotion.equipLowering(equip,true),true);
            assertEquals(0,fit.stringError(),.001,"draw during equip="+equip);
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void attackStrengthEquipDipDoesNotPullSpearThroughNearMargin(boolean left) {
        var k=SpiritFirstPersonGrip.Kind.SPEAR;
        for(float equip:new float[]{.4f,.6f,1}) for(int i=1;i<90;i++) {
            var p=SpiritFirstPersonMotion.spearThrow(i*.1f);
            var in=toolInput(k,p,left).translateLocal(0,-.6f*SpiritFirstPersonMotion.equipLowering(equip,true),0);
            var fit=SpiritWeaponRig.tool(k,left,in,display(k,left),true);
            assertTrue(SpiritWeaponProjection.measure(k,fit.item(),1,1920f/1015).near()>=.25,"spear during equip="+equip+" t="+i*.1f);
        }
        assertEquals(.6f,SpiritFirstPersonMotion.equipLowering(.6f,false),"idle re-equip stays vanilla");
    }
    @ParameterizedTest @EnumSource(value=SpiritFirstPersonGrip.Kind.class,names={"HAMMER","SPEAR"})
    void toolsKeepTheirGripAndClearCameraThroughTheAction(SpiritFirstPersonGrip.Kind kind) {
        for(boolean left:new boolean[]{false,true})for(int i=0;i<=100;i++) {
            float t=i*.3f;
            var motion=kind==SpiritFirstPersonGrip.Kind.HAMMER
                    ?(t<=12?SpiritFirstPersonMotion.hammerCharge(t/12,0):SpiritFirstPersonMotion.hammerStrike(t-12,false))
                    :SpiritFirstPersonMotion.spearThrow(t);
            var f=SpiritWeaponRig.tool(kind,left,toolInput(kind,motion,left),display(kind,left),true);
            var b=SpiritWeaponProjection.measure(kind,f.item(),1,1920f/1015);
            assertEquals(0,f.mainError(),.0001);
            assertEquals(.36,f.main().shoulder().distance(f.main().elbow()),.0001);
            assertEquals(.38,f.main().elbow().distance(f.main().wrist()),.0001);
            assertTrue(b.near()>=.25,kind+" t="+t+" near="+b.near());
            if((kind==SpiritFirstPersonGrip.Kind.HAMMER&&Math.abs(t-12)<.01)||(kind==SpiritFirstPersonGrip.Kind.SPEAR&&Math.abs(t-1.5)<.01))
                assertTrue(b.visible()>=.4,kind+" full windup visible="+b.visible());
        }
    }
    @ParameterizedTest @EnumSource(value=SpiritFirstPersonGrip.Kind.class,names={"HAMMER","SPEAR"})
    void completeItemAndBothBonesMirrorWithoutChangingTheGrip(SpiritFirstPersonGrip.Kind kind) {
        for(float t:new float[]{0,.25f,.5f,1}) {
            var pose=kind==SpiritFirstPersonGrip.Kind.HAMMER?SpiritFirstPersonMotion.hammerCharge(t,0):SpiritFirstPersonMotion.spearThrow(t*SpiritFirstPersonMotion.SPEAR_FOLLOW_TICKS);
            var right=SpiritWeaponRig.tool(kind,false,toolInput(kind,pose,false),display(kind,false),true);
            var left=SpiritWeaponRig.tool(kind,true,toolInput(kind,pose,true),display(kind,true),true);
            assertTrue(new Matrix4f().scaling(-1,1,1).mul(right.item()).equals(left.item(),1e-5f));
            assertEquals(-right.main().palm().x,left.main().palm().x,1e-5f);
            assertTrue(left.item().transformPosition(SpiritWeaponRig.grip(kind)).distance(SpiritWeaponRig.contact(left.main(),true))<1e-5f);
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void hammerPlungeAndLandingAlsoKeepTheNearPlaneClear(boolean left) {
        for(int i=0;i<=140;i++) {
            float t=i*.1f;
            for(var motion:new SpiritFirstPersonMotion.Pose[]{SpiritFirstPersonMotion.hammerPlunge(t),SpiritFirstPersonMotion.hammerStrike(t,true)}) {
                var f=SpiritWeaponRig.tool(SpiritFirstPersonGrip.Kind.HAMMER,left,toolInput(SpiritFirstPersonGrip.Kind.HAMMER,motion,left),display(SpiritFirstPersonGrip.Kind.HAMMER,left),true);
                assertTrue(SpiritWeaponProjection.measure(SpiritFirstPersonGrip.Kind.HAMMER,f.item(),1,1920f/1015).near()>=.25,"air slam t="+t);
            }
        }
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void diagnosticsDoNotChangeTheBowOrToolPose(boolean left) {
        var bow=SpiritWeaponRig.bow(left,1,.5f,0,0,false);var measured=SpiritWeaponRig.bow(left,1,.5f,0,0,true);
        assertEquals(measured.item(),bow.item());assertEquals(measured.main(),bow.main());assertEquals(measured.string(),bow.string());
        assertTrue(Float.isNaN(bow.mainError())&&Float.isNaN(bow.stringError()));
        for(var k:new SpiritFirstPersonGrip.Kind[]{SpiritFirstPersonGrip.Kind.HAMMER,SpiritFirstPersonGrip.Kind.SPEAR}) {
            var p=SpiritFirstPersonMotion.hammerCharge(.5f,0);var a=SpiritWeaponRig.tool(k,left,toolInput(k,p,left),display(k,left),false);var b=SpiritWeaponRig.tool(k,left,toolInput(k,p,left),display(k,left),true);
            assertEquals(a.item(),b.item());assertEquals(a.main(),b.main());assertTrue(Float.isNaN(a.mainError()));
        }
    }

    @ParameterizedTest @EnumSource(value=SpiritFirstPersonGrip.Kind.class,names={"HAMMER","SPEAR"})
    void idleHandleAxisTracksTheVanillaRuler(SpiritFirstPersonGrip.Kind kind) {
        for(boolean left:new boolean[]{false,true}) {
            var f=SpiritWeaponRig.tool(kind,left,toolInput(kind,SpiritFirstPersonMotion.Pose.REST,left),display(kind,left),true);
            float axis=SpiritWeaponProjection.measure(kind,f.item(),0,1920f/1015).axis();
            float vanilla=(kind==SpiritFirstPersonGrip.Kind.HAMMER?-54.33f:66.50f)*(left?-1:1);
            float delta=Math.abs(axis-vanilla)%180;delta=Math.min(delta,180-delta);
            assertTrue(delta<=12,"axis error="+delta);
        }
    }

    static Vector3f screenPoint(Matrix4f item, Vector3f local) {
        var p=item.transformPosition(new Vector3f(local));
        return new Vector3f(960+507.5f*p.x/(-p.z*.7002075f),507.5f-507.5f*p.y/(-p.z*.7002075f),-p.z-.05f);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void hammerChargeRemainsReadableAfterWristAndReachLimits(boolean left) {
        var k=SpiritFirstPersonGrip.Kind.HAMMER;
        var idle=SpiritWeaponRig.tool(k,left,toolInput(k,SpiritFirstPersonMotion.Pose.REST,left),display(k,left),true);
        var full=SpiritWeaponRig.tool(k,left,toolInput(k,SpiritFirstPersonMotion.hammerCharge(1,0),left),display(k,left),true);
        var a=SpiritWeaponProjection.measure(k,idle.item(),1,1920f/1015);
        var b=SpiritWeaponProjection.measure(k,full.item(),1,1920f/1015);
        float angle=Math.abs(a.axis()-b.axis())%180;angle=Math.min(angle,180-angle);
        float lift=screenPoint(idle.item(),new Vector3f(.24f,.24f,0)).y-screenPoint(full.item(),new Vector3f(.24f,.24f,0)).y;
        assertTrue(angle>=30||lift>=1015*.15,"solved hammer angle="+angle+", head lift="+lift);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void spearHandFollowsThroughWithinTwoTicksOfTheActualThrow(boolean left) {
        var k=SpiritFirstPersonGrip.Kind.SPEAR;
        var idle=SpiritWeaponRig.tool(k,left,toolInput(k,SpiritFirstPersonMotion.Pose.REST,left),display(k,left),true);
        var thrown=SpiritWeaponRig.tool(k,left,toolInput(k,SpiritFirstPersonMotion.spearThrow(2),left),display(k,left),true);
        float distance=screenPoint(idle.item(),gripForTest(k)).distance(screenPoint(thrown.item(),gripForTest(k)));
        assertTrue(distance>=80,"palm displacement at throw+2="+distance);
        assertEquals(0,SpiritFirstPersonMotion.spearPresence(2,1),1e-6,"held spear must already have left the hand");
    }
    static Vector3f gripForTest(SpiritFirstPersonGrip.Kind kind) { return SpiritWeaponRig.grip(kind); }

}
