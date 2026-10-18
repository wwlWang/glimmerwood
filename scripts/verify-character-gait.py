"""Frame-linked foot positions and real key movement at three frame caps."""
import sys,time,shutil
from devtools_client import DevClient

c=DevClient(sys.argv[1])
try:
    c.action('close_screen');c.action('focus_game');c.ready()
    c.command('item replace entity @s hotbar.0 with air')
    c.action('settings',fov=45,hideGui=True,vsync=False)
    for fps in [30,60,120]:
        c.action('settings',fpsLimit=fps)
        for kind in ['walk','run','sprint']:
            c.reset();c.command('effect clear @s')
            if kind=='walk':c.command('effect give @s minecraft:slowness 15 3 true')
            c.action('review_camera',enabled=True,x=-3,y=.7,z=0,targetHeight=.6)
            c.ticks(15)
            name=f'{kind}-{fps}'
            if fps==60:c.action('record_video',name=c.build_id+'-'+name,fps=30,seconds=6)
            c.action('record_pose');before=c.start()
            steps=[{'at':0,'key':'key.forward','down':True},{'at':100,'key':'key.forward','down':False}]
            if kind=='sprint':steps.extend([{'at':0,'key':'key.sprint','down':True},{'at':100,'key':'key.sprint','down':False}])
            c.action('input_script',steps=steps);c.ticks(120)
            result=c.action('save_pose',name=c.build_id+'-'+name+'-pose')
            shutil.copy2(result['path'],c.out/(name+'-pose.json'))
            after=c.finish(name,before)
            assert after['client']['position'][2]-before['client']['position'][2]>3, 'Player did not travel along the route'
            if fps==60:c.records['steps'][name]['video']=c.action('save_video')
            c.screenshot(name+'-end')
            print(name,'recorded',flush=True)
    c.action('release_keys');c.command('effect clear @s');c.reset()
    c.action('review_camera',enabled=False)
    c.action('settings',fpsLimit=260,fov=70,hideGui=False)
    c.action('camera',mode='FIRST_PERSON');c.action('open_pause')
    c.records.update(status='recorded',visual_review='pending')
except Exception as error:
    c.records.update(status='failed',error=str(error));raise
finally:c.save()
