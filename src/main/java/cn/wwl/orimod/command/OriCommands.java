package cn.wwl.orimod.command;

import cn.wwl.orimod.entity.OriMobs;
import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.progression.Ability;
import cn.wwl.orimod.progression.Shard;
import cn.wwl.orimod.progression.SpiritCells;
import cn.wwl.orimod.progression.SpiritProgress;
import cn.wwl.orimod.progression.SpiritProgression;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.spirit.SpiritForm;
import cn.wwl.orimod.world.OriEntities;
import cn.wwl.orimod.world.RuinKind;
import cn.wwl.orimod.world.SpiritTreeSearch;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * R83: {@code /orimod} (alias {@code /ori}) playtest commands. Permission level 2: operators, or a single-player world
 * with cheats allowed (Open to LAN > Allow Cheats also works). Every subcommand works on the caller or on {@code <targets>}.
 */
public final class OriCommands {
    public static final int PERMISSION = 2;
    public static final Map<String, Supplier<? extends EntityType<?>>> SPAWNS = Map.of(
            "beetle", OriMobs.GLOOM_BEETLE, "spitter", OriMobs.GLOOM_SPITTER, "hawk", OriMobs.GLOOM_HAWK, "heart", OriMobs.GLOOM_HEART,
            "matriarch", OriMobs.GLOOM_MATRIARCH, "target", OriEntities.PRACTICE_TARGET);
    private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(v -> Component.translatable("commands.orimod.unknown", v));
    private OriCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) { register(event.getDispatcher()); }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = dispatcher.register(tree("orimod"));
        cn.wwl.orimod.world.GreatTreeCommands.register(root);
        dispatcher.register(Commands.literal("ori").requires(s -> s.hasPermission(PERMISSION)).redirect(root));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> wispCommands(){
        var give=Commands.literal("give");
        for(String kind:java.util.List.of("heart","wing","earth","all")){
            int mask=kind.equals("all")?7:cn.wwl.orimod.progression.WillWisp.byId(kind).bit;
            give.then(players(kind,p->{cn.wwl.orimod.progression.WillWisps.give(p,mask);return true;}));
        }
        return Commands.literal("wisp").then(give).then(players("clear",cn.wwl.orimod.progression.WillWisps::clear));
    }
    static LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
        return Commands.literal(name).requires(s -> s.hasPermission(PERMISSION))
                .then(players("awaken", OriCommands::awaken))
                .then(Commands.literal("form")
                        .then(players("on", p -> form(p, true))).then(players("off", p -> form(p, false))).then(players("toggle", SpiritForm::toggle)))
                .then(Commands.literal("unlock")
                        .then(players("all", OriCommands::unlockAll))
                        .then(Commands.argument("ability", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(Ability.values()).map(Ability::id), b))
                                .executes(c -> unlock(c, java.util.List.of(c.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players()).executes(c -> unlock(c, EntityArgument.getPlayers(c, "targets"))))))
                .then(players("giveall", OriCommands::giveAll))
                .then(Commands.literal("companion").then(Commands.literal("flame")
                        .then(players("on", p -> companionFlame(p, true)))
                        .then(players("off", p -> companionFlame(p, false)))))
                .then(wispCommands())
                .then(Commands.literal("restoration")
                        .then(Commands.literal("status").executes(c->{var d=cn.wwl.orimod.world.RestorationData.get(c.getSource().getServer());c.getSource().sendSuccess(()->Component.translatable("commands.orimod.restoration.status",d.restored(),d.epoch(),cn.wwl.orimod.world.RestorationBloom.pending()),false);return 1;}))
                        .then(Commands.literal("reset").executes(c->{cn.wwl.orimod.progression.Restoration.reset(c.getSource().getServer());c.getSource().sendSuccess(()->Component.translatable("commands.orimod.restoration.reset"),true);return 1;})))
                .then(Commands.literal("ruin").then(Commands.literal("secrets").executes(OriCommands::secrets)))
                .then(Commands.literal("blight").then(Commands.literal("nearest")
                        .executes(c -> nearestBlight(c, 256))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 1024))
                                .executes(c -> nearestBlight(c, IntegerArgumentType.getInteger(c, "radius"))))))
                .then(Commands.literal("tree").then(Commands.literal("nearest")
                        .executes(c -> nearestTree(c, SpiritTreeSearch.DEFAULT_RADIUS))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, SpiritTreeSearch.MAX_RADIUS))
                                .executes(c -> nearestTree(c, IntegerArgumentType.getInteger(c, "radius"))))))
                .then(Commands.literal("tide")
                        .then(Commands.literal("start").executes(c -> tide(c, "start")))
                        .then(Commands.literal("stop").executes(c -> tide(c, "stop")))
                        .then(Commands.literal("status").executes(c -> tide(c, "status"))))
                .then(Commands.literal("hints")
                        .then(players("reset", cn.wwl.orimod.progression.CompanionHints::reset))
                        .then(players("list", p -> {
                            var ids = cn.wwl.orimod.progression.CompanionHints.seen(p.getData(SpiritData.PROGRESS)).stream().sorted().toList();
                            p.sendSystemMessage(Component.translatable("commands.orimod.hints.list", String.join(", ", ids)));
                            return true;
                        })))
                .then(amount("light", 0, SpiritProgress.LIGHT_LIMIT, (p, n) -> { SpiritProgression.grantLight(p, n); return true; }))
                .then(Commands.literal("energy")
                        .then(players("fill", p -> { p.setData(SpiritData.ENERGY, SpiritProgression.maxEnergy(p)); return true; }))
                        .then(amount("set", 0, 1000, (p, n) -> { p.setData(SpiritData.ENERGY, (float) Math.min(n, SpiritProgression.maxEnergy(p))); return true; })))
                .then(Commands.literal("cells")
                        .then(amount("life", 0, SpiritCells.MAX, (p, n) -> cells(p, SpiritCells.Kind.LIFE, n)))
                        .then(amount("energy", 0, SpiritCells.MAX, (p, n) -> cells(p, SpiritCells.Kind.ENERGY, n))))
                .then(Commands.literal("locate").then(Commands.argument("ruin", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(RuinKind.values()).map(k -> k.id), b))
                        .executes(OriCommands::locate)))
                .then(Commands.literal("spawn").then(Commands.argument("mob", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(SPAWNS.keySet(), b))
                        .executes(OriCommands::spawn)))
                .then(players("god", p -> { p.getAbilities().invulnerable = !p.getAbilities().invulnerable; p.onUpdateAbilities();
                        p.sendSystemMessage(Component.translatable(p.getAbilities().invulnerable ? "commands.orimod.god.on" : "commands.orimod.god.off")); return true; }))
                .then(players("reset", OriCommands::reset))
                .then(players("status", OriCommands::status));
    }

    private static boolean companionFlame(ServerPlayer player, boolean enabled) {
        player.setData(SpiritData.COMPANION_FLAME, enabled);
        player.sendSystemMessage(Component.translatable("commands.orimod.companion.flame." + (enabled ? "on" : "off")));
        return true;
    }

    private static int tide(CommandContext<CommandSourceStack> c, String action) throws CommandSyntaxException {
        var source = c.getSource();
        if (action.equals("start")) {
            String failure = cn.wwl.orimod.world.GloomTide.start(source.getLevel());
            if (failure != null) { source.sendFailure(Component.translatable(failure)); return 0; }
            source.sendSuccess(() -> Component.translatable("commands.orimod.tide.started"), false);
        } else if (action.equals("stop")) {
            cn.wwl.orimod.world.GloomTide.stop(source.getLevel());
            source.sendSuccess(() -> Component.translatable("commands.orimod.tide.stopped"), false);
        } else {
            var status = cn.wwl.orimod.world.GloomTide.status(source.getPlayerOrException());
            source.sendSuccess(() -> Component.translatable("commands.orimod.tide.status", status.get("active"), status.get("nearby"),
                    status.get("kills"), status.get("nextWaveIn")), false);
        }
        return 1;
    }

    private static int secrets(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        var result = cn.wwl.orimod.world.RuinSecrets.find(c.getSource().getPlayerOrException());
        if (result.secrets().isEmpty()) {
            c.getSource().sendFailure(Component.translatable(result.inRuin() ? "commands.orimod.ruin.no_secrets" : "commands.orimod.ruin.outside")); return 0;
        }
        for (var secret : result.secrets()) {
            var p = secret.entrance();
            c.getSource().sendSuccess(() -> Component.translatable("commands.orimod.ruin.secret",secret.kind().title(),p.getX(),p.getY(),p.getZ(),secret.facing().getName(),secret.blocks()),false);
        }
        return result.secrets().size();
    }

    // ---- actions (public for GameTests) --------------------------------------------------------------------------
    public static boolean awaken(ServerPlayer p) {
        p.setData(SpiritData.AWAKENED, true); SpiritCells.apply(p); return true;
    }

    public static boolean form(ServerPlayer p, boolean spirit) {
        if (!SpiritData.isAwakened(p)) awaken(p);
        return SpiritData.isSpirit(p) == spirit || SpiritForm.toggle(p);
    }

    public static int allAbilities() { int mask = 0; for (var a : Ability.values()) mask |= a.bit; return mask; }

    public static boolean unlockAll(ServerPlayer p) {
        awaken(p);
        p.setData(SpiritData.ABILITIES, allAbilities());
        var progress = p.getData(SpiritData.PROGRESS);
        int shards = 0;
        for (var shard : Shard.values()) shards |= shard.bit;
        p.setData(SpiritData.PROGRESS, progress.withOwned(shards));
        return true;
    }

    public static boolean giveAll(ServerPlayer p) {
        for (var entry : OriItems.ITEMS.getEntries()) {
            var stack = new ItemStack(entry.get());
            if (!p.getInventory().add(stack)) p.drop(stack, false);
        }
        return true;
    }

    public static boolean cells(ServerPlayer p, SpiritCells.Kind kind, int count) {
        var progress = p.getData(SpiritData.PROGRESS);
        var rewards = new java.util.HashMap<>(progress.rewards());
        rewards.put(kind.key(), Math.clamp(count, 0, SpiritCells.MAX));
        p.setData(SpiritData.PROGRESS, progress.withRewards(rewards));
        SpiritCells.apply(p);
        return true;
    }

    public static boolean reset(ServerPlayer p) {
        if (SpiritData.isSpirit(p)) SpiritForm.toggle(p);
        p.setData(SpiritData.FORM, false);
        p.setData(SpiritData.ABILITIES, 0);
        p.setData(SpiritData.PROGRESS, SpiritProgress.EMPTY);
        p.setData(SpiritData.AWAKENED, false);
        p.refreshDimensions();
        SpiritCells.apply(p);
        return true;
    }

    private static boolean status(ServerPlayer p) {
        var progress = p.getData(SpiritData.PROGRESS);
        p.sendSystemMessage(Component.translatable("commands.orimod.status", p.getName(), SpiritData.isAwakened(p), SpiritData.isSpirit(p),
                Integer.toBinaryString(p.getData(SpiritData.ABILITIES)), progress.light(), (int) SpiritProgression.energy(p),
                SpiritCells.count(p, SpiritCells.Kind.LIFE), SpiritCells.count(p, SpiritCells.Kind.ENERGY)));
        return true;
    }

    // ---- command plumbing -------------------------------------------------------------------------------------------
    interface PlayerAction { boolean run(ServerPlayer player); }
    interface AmountAction { boolean run(ServerPlayer player, int amount); }

    private static LiteralArgumentBuilder<CommandSourceStack> players(String name, PlayerAction action) {
        return Commands.literal(name)
                .executes(c -> apply(c, name, java.util.List.of(c.getSource().getPlayerOrException()), action))
                .then(Commands.argument("targets", EntityArgument.players()).executes(c -> apply(c, name, EntityArgument.getPlayers(c, "targets"), action)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> amount(String name, int min, int max, AmountAction action) {
        return Commands.literal(name).then(Commands.argument("amount", IntegerArgumentType.integer(min, max))
                .executes(c -> apply(c, name, java.util.List.of(c.getSource().getPlayerOrException()), p -> action.run(p, IntegerArgumentType.getInteger(c, "amount"))))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(c -> apply(c, name, EntityArgument.getPlayers(c, "targets"), p -> action.run(p, IntegerArgumentType.getInteger(c, "amount"))))));
    }

    private static int apply(CommandContext<CommandSourceStack> c, String name, Collection<ServerPlayer> targets, PlayerAction action) {
        int done = 0;
        for (var p : targets) if (action.run(p)) done++;
        int count = done;
        c.getSource().sendSuccess(() -> Component.translatable("commands.orimod.done", name, count), true);
        return done;
    }

    private static int unlock(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> targets) throws CommandSyntaxException {
        String id = StringArgumentType.getString(c, "ability");
        var ability = Ability.byId(id);
        if (ability == null) throw UNKNOWN.create(id);
        return apply(c, "unlock " + id, targets, p -> { awaken(p); p.setData(SpiritData.ABILITIES, p.getData(SpiritData.ABILITIES) | ability.bit); return true; });
    }

    private static int locate(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String id = StringArgumentType.getString(c, "ruin");
        if (java.util.Arrays.stream(RuinKind.values()).noneMatch(k -> k.id.equals(id))) throw UNKNOWN.create(id);
        // Same search, distance report and clickable teleport as vanilla /locate.
        c.getSource().getServer().getCommands().performPrefixedCommand(c.getSource(), "locate structure orimod:" + id);
        return 1;
    }

    private static int nearestTree(CommandContext<CommandSourceStack> c, int radius) {
        var source = c.getSource();
        var found = SpiritTreeSearch.nearest(source.getLevel(), source.getPosition(), radius);
        if (found.isEmpty()) {
            source.sendFailure(Component.translatable("commands.orimod.tree.none", radius));
            return 0;
        }
        var p = found.orElseThrow();
        var coords = Component.literal("[" + p.getX() + ", " + p.getY() + ", " + p.getZ() + "]")
                .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.AQUA)
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND,
                                "/tp @s " + p.getX() + " " + p.getY() + " " + (p.getZ() - 3))));
        String distance = String.format(java.util.Locale.ROOT, "%.1f", Math.sqrt(p.distToCenterSqr(source.getPosition())));
        source.sendSuccess(() -> Component.translatable("commands.orimod.tree.nearest", coords, distance, radius), false);
        return 1;
    }

    private static int nearestBlight(CommandContext<CommandSourceStack> c, int radius) {
        var source = c.getSource();
        var found = cn.wwl.orimod.world.BlightSearch.nearest(source.getLevel(), source.getPosition(), radius);
        if (found.isEmpty()) {
            source.sendFailure(Component.translatable("commands.orimod.blight.none", radius));
            return 0;
        }
        var p = found.orElseThrow();
        var coords = Component.literal("[" + p.getX() + ", " + p.getY() + ", " + p.getZ() + "]")
                .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.AQUA)
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND,
                                "/tp @s " + p.getX() + " " + p.getY() + " " + (p.getZ() - 3))));
        String distance = String.format(java.util.Locale.ROOT, "%.1f", Math.sqrt(p.distToCenterSqr(source.getPosition())));
        source.sendSuccess(() -> Component.translatable("commands.orimod.blight.nearest", coords, distance, radius), false);
        return 1;
    }

    private static int spawn(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String id = StringArgumentType.getString(c, "mob");
        var type = SPAWNS.get(id);
        if (type == null) throw UNKNOWN.create(id);
        var p = c.getSource().getPlayerOrException();
        var look = p.getLookAngle().multiply(1, 0, 1).normalize().scale(id.equals("matriarch") || id.equals("heart") ? 8 : 4);
        var pos = net.minecraft.core.BlockPos.containing(p.position().add(look).add(0, id.equals("hawk") || id.equals("matriarch") ? 4 : 0, 0));
        var entity = type.get().spawn(p.serverLevel(), pos, MobSpawnType.COMMAND);
        if (entity == null) throw UNKNOWN.create(id);
        c.getSource().sendSuccess(() -> Component.translatable("commands.orimod.spawned", entity.getDisplayName()), true);
        return 1;
    }
}
