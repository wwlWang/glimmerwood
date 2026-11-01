package cn.wwl.orimod.progression;
import cn.wwl.orimod.entity.GladesMerchant;
import cn.wwl.orimod.network.*;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.world.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Quotes are server-owned, one live session per player. Every purchase rechecks the quoted stock and player conditions. */
public final class MerchantTrading {
    private static final class Session {
        final ServerPlayer player;final GladesMerchant merchant;final long day;final UUID token=UUID.randomUUID();final List<String> stock;
        Session(ServerPlayer p,GladesMerchant m){player=p;merchant=m;day=MerchantRules.day(p.level().getDayTime());stock=m.stock(p);}
    }
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static boolean valid(ServerPlayer p,GladesMerchant m){return cn.wwl.orimod.spirit.PlayerLifecycle.current(p) && p.isAlive() && !p.isSpectator() && SpiritData.isAwakened(p) && m.isAlive() && !m.isRemoved() && m.level()==p.level() && p.distanceToSqr(m)<=36;}
    public static void open(ServerPlayer p,GladesMerchant m){
        if(!valid(p,m))return;close(p);var s=new Session(p,m);SESSIONS.put(p.getUUID(),s);send(p,s,"");
    }
    private static void send(ServerPlayer p,Session s,String message){
        var progress=p.getData(SpiritData.PROGRESS);
        var offers=s.stock.stream().map(id->new MerchantOfferPayload.Offer(id,quotedPrice(p,id),Shard.byId(id)==null || (progress.ownedShards()&Shard.byId(id).bit)==0)).toList();
        PacketDistributor.sendToPlayer(p,new MerchantOfferPayload(s.merchant.getId(),s.merchant.getUUID(),s.day,s.token,s.merchant.job(),progress.light(),offers,message,false));
    }
    private static int quotedPrice(ServerPlayer p,String id) {
        if (id.equals("tree") || id.equals("blight")) {
            var kind=id.equals("tree")?FeatureSites.Kind.TREE:FeatureSites.Kind.BLIGHT;
            var target=FeatureSites.get(p.serverLevel()).nearest(p,kind);
            if(target.isPresent()) {
                var guide=new RuinGuidePayload(p.level().dimension().location().toString(),target.get(),id.equals("tree")?"spirit_tree":"blighted_grounds");
                if(p.getData(SpiritData.PROGRESS).rewards().containsKey(locationReceipt(guide)))return 0;
            }
        }
        return MerchantRules.price(id);
    }
    public static void buy(ServerPlayer p,MerchantBuyPayload packet){
        var s=SESSIONS.get(p.getUUID());
        if(s!=null && !cn.wwl.orimod.spirit.PlayerLifecycle.current(s.player)){SESSIONS.remove(p.getUUID(),s);return;}
        if(s==null || s.player!=p || !s.token.equals(packet.token()) || s.merchant.getId()!=packet.entityId() || !s.merchant.getUUID().equals(packet.merchant()))return;
        if(packet.product().equals("close")){close(p);return;}
        String reason=refusal(p,s,packet.product(),packet.day(),Shard.byId(packet.product())==null);if(!reason.isEmpty()){refresh(p,s,reason);return;}
        String id=packet.product();
        if(Shard.byId(id)!=null){pay(p,s,id,null);return;}
        var kind=id.equals("tree")?FeatureSites.Kind.TREE:FeatureSites.Kind.BLIGHT;
        var found=FeatureSites.get(p.serverLevel()).nearest(p,kind);
        if(found.isEmpty())refresh(p,s,"not_found");else pay(p,s,id,new RuinGuidePayload(p.level().dimension().location().toString(),found.get(),id.equals("tree")?"spirit_tree":"blighted_grounds"));
    }
    private static String refusal(ServerPlayer p,Session s,String id,long day,boolean free){var progress=p.getData(SpiritData.PROGRESS);return MerchantRules.refusal(valid(p,s.merchant),p.distanceToSqr(s.merchant),day,MerchantRules.day(p.level().getDayTime()),s.stock.contains(id) && day==s.day,free?Integer.MAX_VALUE:progress.light(),id,progress.ownedShards());}
    private static void pay(ServerPlayer p,Session s,String id,RuinGuidePayload guide){
        String receipt = guide == null ? "" : locationReceipt(guide);
        boolean free = guide != null && p.getData(SpiritData.PROGRESS).rewards().containsKey(receipt);
        String reason=refusal(p,s,id,s.day,free);if(!reason.isEmpty()){refresh(p,s,reason);return;}
        var progress=p.getData(SpiritData.PROGRESS);var shard=Shard.byId(id);
        var updated = progress.withLight(progress.light()-(free?0:MerchantRules.price(id))).withOwned(progress.ownedShards()|(shard==null?0:shard.bit));
        if (guide != null) {
            var receipts = new HashMap<>(updated.rewards()); receipts.put(receipt, 1);
            updated = updated.withRewards(receipts);
        }
        p.setData(SpiritData.PROGRESS,updated);
        if(guide!=null)PacketDistributor.sendToPlayer(p,guide);refresh(p,s,free?"already_located":"purchased");
    }
    /** Receipt identifies the still-valid target returned by FeatureSites, not just the merchant/product. */
    public static String locationReceipt(RuinGuidePayload guide) {
        return "guide/"+guide.dimension()+"/"+guide.kind()+"/"+guide.entry().asLong();
    }
    private static void refresh(ServerPlayer p,Session previous,String message){
        if(SESSIONS.get(p.getUUID())!=previous)return;
        if(!valid(p,previous.merchant)){send(p,previous,"closed");close(p);return;}
        var s=new Session(p,previous.merchant);SESSIONS.put(p.getUUID(),s);send(p,s,message);
    }
    public static void tick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event){if(event.getEntity() instanceof ServerPlayer p){var s=SESSIONS.get(p.getUUID());if(s!=null){if(!valid(p,s.merchant)){send(p,s,"closed");close(p);}else if(s.day!=MerchantRules.day(p.level().getDayTime())){refresh(p,s,"refreshed");}}}}
    public static void close(ServerPlayer p){SESSIONS.remove(p.getUUID());}
    public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)close(p);}
    public static void clear(){SESSIONS.clear();}
    public static Map<String,Object> debug(ServerPlayer p){var s=SESSIONS.get(p.getUUID());return s==null?Map.of("open",false):Map.of("open",true,"entityId",s.merchant.getId(),"job",s.merchant.job(),"day",s.day,"stock",s.stock,"busy",false);}
    private MerchantTrading(){}
}
