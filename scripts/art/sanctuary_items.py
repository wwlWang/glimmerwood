"""Shared item-display contract for the existing sanctuary assemblies; no mesh/texture edits."""
PARTICLES = {
    'glades_well': 'forest_shrine_side', 'forest_spring_shrine': 'forest_shrine_side',
    'creek_ruins_shrine': 'creek_shrine_side', 'cave_altar_shrine': 'cave_shrine_side',
    'gloom_altar': 'gloom_altar_side', 'storm_altar': 'storm_altar_side',
    'map_stone': 'map_stone_side', 'keystone_door': 'keystone_door',
    'great_tree_heart': 'spirit_tree_heart', 'ginso_water_heart': 'ginso_sap',
}
def model(name):
    def display(rotation, translation, scale):
        return {'rotation': rotation, 'translation': translation, 'scale': [scale]*3}
    return {'parent': 'minecraft:builtin/entity', 'gui_light': 'side',
            'textures': {'particle': 'orimod:block/'+PARTICLES[name]},
            'display': {
                'gui': display([25, 215, 0], [0, 0, 0], .95),
                'ground': display([0, 0, 0], [0, 2, 0], .55),
                'fixed': display([0, 180, 0], [0, 0, 0], .9),
                'firstperson_righthand': display([0, 45, 0], [-1.5, 3.5, -4], .4),
                'firstperson_lefthand': display([0, 45, 0], [-1.5, 3.5, -4], .4),
                'thirdperson_righthand': display([75, 45, 0], [0, 2.5, 0], .375),
                'thirdperson_lefthand': display([75, 45, 0], [0, 2.5, 0], .375),
            }}
