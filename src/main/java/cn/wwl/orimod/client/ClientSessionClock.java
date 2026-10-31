package cn.wwl.orimod.client;

/** Client ticks never go backwards. Identity changes do not require an intervening null player. */
public final class ClientSessionClock {
    private Object player, world;
    private long ticks;
    public long now() { return ticks; }
    public void tick() { ticks++; }
    public boolean observe(Object nextPlayer, Object nextWorld) {
        if (player == nextPlayer && world == nextWorld) return false;
        player = nextPlayer; world = nextWorld;
        return true;
    }
    public void clear() { player = world = null; }
}
