-- More evidence and red herrings (2026-11):
--   * 20 new evidence types join the case pools (cigarette butt, crowbar, syringe, ...);
--   * 30 red herrings (everyday objects) can be added to a case, up to 3 per case. Students process
--     them like anything else; collecting one costs nothing, citing one as proof costs a point
--     (submit-simulation/scoring.ts);
--   * a case can now hold up to 10 items: at most 7 evidence plus at most 3 red herrings.
-- The app mirrors this in engine/scene/ScenarioTemplate.kt (EvidenceType) and CaseCatalog.kt (CaseType pools).

-- ---------------------------------------------------------------------------- red herrings
create or replace function private.red_herring_types() returns text[]
language sql immutable set search_path = '' as $$
  select array[
    'coffee_mug', 'tv_remote', 'house_keys', 'eyeglasses', 'newspaper', 'soda_can', 'water_bottle', 'receipt', 'pen', 'book',
    'umbrella', 'toy_car', 'hairbrush', 'lipstick', 'coins', 'headphones', 'tissue_box', 'candle', 'sock', 'cap',
    'matchbox', 'phone_charger', 'playing_cards', 'food_wrapper', 'apple', 'ashtray', 'paper_cup', 'scissors', 'notebook', 'watch'
  ]::text[]
$$;

-- ---------------------------------------------------------------------------- evidence pools
create or replace function private.case_evidence_types(p_case_type text) returns text[]
language sql immutable set search_path = '' as $$
  select case p_case_type
    when 'theft'           then array['latent_print', 'questioned_document', 'shoe_print', 'hair_fiber', 'tool_mark',
                                      'wallet', 'jewelry_box', 'cigarette_butt']
    when 'burglary'        then array['tool_mark', 'broken_glass', 'shoe_print', 'latent_print', 'hair_fiber',
                                      'crowbar', 'screwdriver', 'glove', 'torn_fabric']
    when 'assault'         then array['bloodstain', 'blunt_weapon', 'hair_fiber', 'shoe_print', 'latent_print',
                                      'hammer', 'bloody_cloth', 'shoe', 'cigarette_butt']
    when 'homicide'        then array['bloodstain', 'knife', 'latent_print', 'hair_fiber', 'shoe_print',
                                      'rope', 'bloody_cloth', 'drinking_glass', 'cigarette_butt']
    when 'shooting'        then array['cartridge_case', 'bullet', 'firearm', 'bloodstain', 'latent_print',
                                      'magazine', 'cell_phone', 'bloody_cloth']
    when 'drug_possession' then array['drug_packet', 'digital_scale', 'questioned_document', 'latent_print', 'hair_fiber',
                                      'syringe', 'pill_bottle', 'cash_bundle', 'cell_phone']
    when 'robbery'         then array['firearm', 'questioned_document', 'shoe_print', 'latent_print', 'hair_fiber',
                                      'ski_mask', 'cash_bundle', 'glove', 'cell_phone']
    when 'vandalism'       then array['broken_glass', 'blunt_weapon', 'shoe_print', 'latent_print', 'tool_mark',
                                      'spray_can', 'brick', 'hammer', 'shoe']
    else array[]::text[]
  end
$$;

-- ---------------------------------------------------------------------------- create a case
-- Same as 20261126000001 except the item rules: up to 7 items from the case type's pool plus up
-- to 3 red herrings (10 in all).
create or replace function public.professor_create_case(
  p_class_id uuid, p_title text, p_case_type text, p_location text, p_location_version int,
  p_briefing text, p_seed int, p_manifest jsonb, p_graded boolean,
  p_victim_sex text default null, p_victim_pose text default null
) returns public.case_sessions
language plpgsql security definer set search_path = '' as $$
declare
  v_pool     text[] := private.case_evidence_types(p_case_type);
  v_herrings text[] := private.red_herring_types();
  v_count    int;
  v_red      int;
  v_code     text;
  v_row      public.case_sessions;
  v_alpha    constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  v_sex      text := nullif(lower(trim(coalesce(p_victim_sex, ''))), '');
  v_pose     text := nullif(lower(trim(coalesce(p_victim_pose, ''))), '');
begin
  if not (public.is_admin() or (public.my_role() = 'professor' and public.is_class_professor(p_class_id))) then
    raise exception 'You can only create cases for your own classes' using errcode = '42501';
  end if;
  if coalesce(array_length(v_pool, 1), 0) = 0 then
    raise exception 'Unknown case type' using errcode = '22023';
  end if;
  if jsonb_typeof(p_manifest) <> 'array' then
    raise exception 'Invalid evidence list' using errcode = '22023';
  end if;
  v_count := jsonb_array_length(p_manifest);
  select count(*) into v_red from jsonb_array_elements(p_manifest) e where (e ->> 'type') = any (v_herrings);
  if v_count - v_red not between 2 and 7 then
    raise exception 'A case needs 2 to 7 items of evidence' using errcode = '22023';
  end if;
  if v_red > 3 then
    raise exception 'A case can have at most 3 red herrings' using errcode = '22023';
  end if;
  if exists (
    select 1 from jsonb_array_elements(p_manifest) e
    where jsonb_typeof(e) <> 'object' or (e ->> 'id') !~ '^ev_[0-9]{1,2}$'
       or not ((e ->> 'type') = any (v_pool) or (e ->> 'type') = any (v_herrings))
  ) or (select count(distinct e ->> 'id') from jsonb_array_elements(p_manifest) e) <> v_count then
    raise exception 'Evidence does not match the case type' using errcode = '22023';
  end if;
  if v_sex is null then
    v_pose := null;
  elsif v_sex not in ('male', 'female') or coalesce(v_pose, '') not in ('face_up', 'face_down') then
    raise exception 'Invalid victim' using errcode = '22023';
  end if;

  loop
    select string_agg(substr(v_alpha, 1 + floor(random() * 32)::int, 1), '') into v_code from generate_series(1, 6);
    exit when not exists (select 1 from public.case_sessions where code = v_code);
  end loop;

  insert into public.case_sessions (code, class_id, title, case_type, location, location_version, briefing, seed, manifest, graded,
                                    victim_sex, victim_pose)
  values (v_code, p_class_id, trim(p_title), p_case_type, p_location, coalesce(p_location_version, 1), trim(p_briefing), p_seed,
          (select jsonb_agg(jsonb_build_object('id', e ->> 'id', 'type', e ->> 'type')) from jsonb_array_elements(p_manifest) e),
          coalesce(p_graded, false), v_sex, v_pose)
  returning * into v_row;
  perform private.audit('case.create', 'case_session', v_row.id::text,
    jsonb_build_object('code', v_row.code, 'class_id', p_class_id, 'case_type', p_case_type, 'graded', v_row.graded,
                       'red_herrings', v_red,
                       'victim', case when v_sex is null then null else v_sex || ',' || v_pose end));
  return v_row;
end $$;
revoke all on function public.professor_create_case(uuid, text, text, text, int, text, int, jsonb, boolean, text, text) from public, anon;
grant execute on function public.professor_create_case(uuid, text, text, text, int, text, int, jsonb, boolean, text, text) to authenticated;
