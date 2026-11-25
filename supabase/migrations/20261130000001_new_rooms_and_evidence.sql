-- Five new rooms and a second set of evidence (2026-11):
--   * five new rooms (hotel room, kitchen, garage, bar, classroom) live in the app; the location
--     check already accepts any lowercase id, so nothing changes for them here. The five older
--     rooms got a new layout version that places evidence at random (seeded per case), so a case
--     row still only stores location, location_version, seed and manifest;
--   * 12 new evidence types join the case pools (duct tape, zip ties, bolt cutters, ...);
--   * 8 new red herrings (stapler, calculator, lighter, ...).
-- The app mirrors this in engine/scene/ScenarioTemplate.kt (EvidenceType) and CaseCatalog.kt
-- (CaseType pools); submit-simulation/scoring.ts holds the rubric and answer key.
-- professor_create_case is unchanged: it reads both functions below.

-- ---------------------------------------------------------------------------- red herrings
create or replace function private.red_herring_types() returns text[]
language sql immutable set search_path = '' as $$
  select array[
    'coffee_mug', 'tv_remote', 'house_keys', 'eyeglasses', 'newspaper', 'soda_can', 'water_bottle', 'receipt', 'pen', 'book',
    'umbrella', 'toy_car', 'hairbrush', 'lipstick', 'coins', 'headphones', 'tissue_box', 'candle', 'sock', 'cap',
    'matchbox', 'phone_charger', 'playing_cards', 'food_wrapper', 'apple', 'ashtray', 'paper_cup', 'scissors', 'notebook', 'watch',
    'stapler', 'calculator', 'lighter', 'hand_sanitizer', 'game_controller', 'sticky_notes', 'tape_measure', 'flip_flop'
  ]::text[]
$$;

-- ---------------------------------------------------------------------------- evidence pools
create or replace function private.case_evidence_types(p_case_type text) returns text[]
language sql immutable set search_path = '' as $$
  select case p_case_type
    when 'theft'           then array['latent_print', 'questioned_document', 'shoe_print', 'hair_fiber', 'tool_mark',
                                      'wallet', 'jewelry_box', 'cigarette_butt', 'flashlight', 'handbag']
    when 'burglary'        then array['tool_mark', 'broken_glass', 'shoe_print', 'latent_print', 'hair_fiber',
                                      'crowbar', 'screwdriver', 'glove', 'torn_fabric', 'bolt_cutters', 'flashlight']
    when 'assault'         then array['bloodstain', 'blunt_weapon', 'hair_fiber', 'shoe_print', 'latent_print',
                                      'hammer', 'bloody_cloth', 'shoe', 'cigarette_butt', 'broken_bottle', 'brass_knuckles']
    when 'homicide'        then array['bloodstain', 'knife', 'latent_print', 'hair_fiber', 'shoe_print',
                                      'rope', 'bloody_cloth', 'drinking_glass', 'cigarette_butt', 'duct_tape', 'zip_ties']
    when 'shooting'        then array['cartridge_case', 'bullet', 'firearm', 'bloodstain', 'latent_print',
                                      'magazine', 'cell_phone', 'bloody_cloth', 'shotgun_shell', 'ammo_box']
    when 'drug_possession' then array['drug_packet', 'digital_scale', 'questioned_document', 'latent_print', 'hair_fiber',
                                      'syringe', 'pill_bottle', 'cash_bundle', 'cell_phone', 'burnt_spoon', 'glass_pipe']
    when 'robbery'         then array['firearm', 'questioned_document', 'shoe_print', 'latent_print', 'hair_fiber',
                                      'ski_mask', 'cash_bundle', 'glove', 'cell_phone', 'duct_tape', 'zip_ties', 'handbag']
    when 'vandalism'       then array['broken_glass', 'blunt_weapon', 'shoe_print', 'latent_print', 'tool_mark',
                                      'spray_can', 'brick', 'hammer', 'shoe', 'paint_marker', 'broken_bottle']
    else array[]::text[]
  end
$$;
