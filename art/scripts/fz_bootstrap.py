# Sets up the shared namespace the fz_* build scripts expect (bpy.app.driver_namespace['fz'], ['fz_M'],
# ['fz_tex'], ['fz_G']). Run this first in a fresh Blender session, then exec the build scripts into
# bpy.app.driver_namespace['fz_G'], e.g.
#   G = bpy.app.driver_namespace['fz_G']
#   exec(bpy.data.texts["fz_furn_common.py"].as_string(), G)
#   exec(bpy.data.texts["fz_furn_v2_a.py"].as_string(), G)
import bpy

ns = {}
exec(bpy.data.texts["fz_lib.py"].as_string(), ns)
bpy.app.driver_namespace['fz'] = ns
ns['scene']()
D = bpy.data.materials
M = dict(
    fabric_dk=D['fabric_blue_dark'], fabric=D['fabric_blue'], wood_dark=D['wood_dark'], wood=D['wood_mid'], wood_light=D['wood_light'],
    glass=D['glass'], chrome=D['metal_chrome'], handle=D['handle_brass'], metal=D['metal_dark'], steel=D['metal_grey'], sheet=D['fabric_sheet'],
    bedding=D['fabric_bedding'], plastic_blk=D['plastic_black'], screen=D['screen'], slate=D['paint_slate'], pot=D['pot_terracotta'],
    soil=D['soil'], leaf=D['leaf'], counter_top=D['counter_top'], frame_white=D['frame_white'], door=D['door_wood'], sky=D['window_sky'],
    light_panel=D['light_panel'], ceiling=D['ceiling'], baseboard=D['baseboard'], rug=D['rug'], rug2=D['rug_border'],
    picture=[D['art_a'], D['art_b'], D['art_c']], whiteboard=D['whiteboard'],
    prod=[D['prod_red'], D['prod_blue'], D['prod_white'], D['prod_yellow'], D['prod_green']], yellow=D['paint_yellow'], red=D['paint_red'],
    crate=D['wood_crate'], pallet=D['wood_pallet'], blue=D['rack_blue'], orange=D['rack_orange'], fridge=D['fridge_white'],
    cardboard=D['cardboard'], book=[D['book_red'], D['book_navy'], D['book_green'], D['book_cream']],
)
bpy.app.driver_namespace['fz_M'] = M
bpy.app.driver_namespace['fz_tex'] = {i.name: i for i in bpy.data.images}
bpy.app.driver_namespace['fz_G'] = {}
