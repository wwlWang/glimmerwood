"""Short and long real teleports end Bash and reconcile both clients' airborne charges."""
import argparse
from devtools_client import DevClient

p = argparse.ArgumentParser(); p.add_argument('build_id'); c = DevClient(p.parse_args().build_id)
try:
    c.action('focus_game'); c.action('close_screen'); c.ready(); c.action('release_keys')
    c.command('gamemode creative'); c.command('fill 137 219 -3 143 219 0 moss_block')
    c.command('fill 137 220 -3 163 228 8 air'); c.command('tp @s 140.5 220 .5 0 0'); c.ticks(8)
    c.command('gamemode survival'); c.command('difficulty peaceful'); c.command('gamerule fallDamage false')
    c.command('setblock 140 221 4 orimod:bash_anchor')
    if not c.spirit()['server']['form']: c.action('toggle_form'); c.ticks(4)
    c.action('grant_bash'); c.action('grant_air_movement')
    for distance in [.5, 20]:
        c.reset(140.5, 220, .5); c.ticks(40); c.key('orimod.bash', 30); c.ticks(2)
        before = c.start(); assert before['server']['movement']['mode'] == 'bash_aim'
        c.command(f'tp @s {140.5 + distance} 225 .5'); c.ticks(3)
        after = c.finish('bash-teleport-' + str(distance).replace('.', '-'), before)
        for side in ['server', 'client']:
            movement = after[side]['movement']
            assert movement['relocations'] == before[side]['movement']['relocations'] + 1
            assert movement['mode'] != 'bash_aim' and movement['airJumpSpent'] and movement['airDashSpent']
            assert movement['pendingPredictions'] == 0
        assert after['server']['movement']['cancelledBashes'] == before['server']['movement']['cancelledBashes'] + 1
        assert after['server']['position'][1] > 223, after
        print('Bash teleport', distance, 'cancelled on both sides; charges spent', flush=True)
        c.action('release_keys')
    c.reset(140.5, 220, .5); before = c.start(); c.key('jump', 1); c.ticks(3)
    c.command('tp @s 145.5 225 .5'); c.ticks(3)
    after = c.finish('jump-teleport', before)
    assert after['server']['movement']['jumps'] == before['server']['movement']['jumps'] + 1
    assert after['client']['movement']['pendingPredictions'] == 0
    assert after['client']['movement']['airJumpSpent'] and after['server']['movement']['airJumpSpent']
    c.key('jump', 1); c.ticks(4)
    assert c.spirit()['server']['movement']['jumps'] == after['server']['movement']['jumps']
    print('Jump teleport: prediction cleared and extra airborne jump rejected', flush=True)
    c.records['status'] = 'passed'
except Exception as error:
    c.records['status'] = 'failed'; c.records['error'] = repr(error); raise
finally:
    c.save(); c.action('release_keys'); c.command('gamemode creative'); c.command('tp @s .5 160 .5')
    c.ticks(8); c.command('gamemode survival'); c.command('gamerule fallDamage true'); c.action('open_pause')
