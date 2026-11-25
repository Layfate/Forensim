-- Initial curriculum, quizzes and simulation scenarios (plan sections 4 and 5).
-- ALL lesson content is marked review_status = 'pending_review': it is a starting draft that a
-- qualified forensic/criminology instructor must review before release (plan section 1).

create or replace function private.seed_question(
  p_quiz uuid, p_pos int, p_prompt text, p_objective text, p_options text[], p_correct int, p_explanation text)
returns void language plpgsql set search_path = '' as $$
declare
  v_q uuid;
  v_opt uuid;
  v_correct uuid;
  i int;
begin
  insert into public.questions (quiz_id, position, prompt, objective) values (p_quiz, p_pos, p_prompt, p_objective)
  returning id into v_q;
  for i in 1 .. array_length(p_options, 1) loop
    insert into public.question_options (question_id, position, body) values (v_q, i, p_options[i]) returning id into v_opt;
    if i = p_correct then v_correct := v_opt; end if;
  end loop;
  insert into private.answer_keys (question_id, correct_option_id, explanation) values (v_q, v_correct, p_explanation);
end $$;
revoke execute on function private.seed_question(uuid, int, text, text, text[], int, text) from public, anon, authenticated;

do $seed$
declare
  c   uuid;
  m   uuid;
  qz  uuid;
begin
  insert into public.courses (title, description, institution)
  values ('Forensic Science for Criminology',
          'Seven-module forensic science course combining lessons, practice, a playable 3D crime scene and AR evidence study.',
          'Forensim Pilot Institution')
  returning id into c;

  insert into public.classes (course_id, name, term, join_code)
  values (c, 'CRIM 3A: Forensic Science', '2026-2027 Sem 1', 'FSCI-3A26');

  -- =========================================================================
  -- Module 1: Forensic Photography
  -- =========================================================================
  insert into public.modules (course_id, position, slug, title, summary, companion, published)
  values (c, 1, 'photography', 'Forensic Photography',
          'Document a scene completely and accurately: overall, mid-range and close-up views, with and without scale.',
          'Photograph each item of evidence in the 3D lab from overall, mid-range and close-up positions.', true)
  returning id into m;

  insert into public.lessons (module_id, position, title, reading_minutes, published, sections, key_terms, citations) values
  (m, 1, 'Why scenes are photographed', 6, true,
   '[{"heading":"Purpose","body":"Photography creates a permanent visual record of the scene as it was found, before anything is moved or collected. Investigators, examiners, attorneys and courts may rely on these images long after the scene has been released."},
     {"heading":"Photograph before you touch","body":"Evidence is photographed in place before it is collected. If an item must be moved for safety, that fact and the reason are recorded in notes. Photographs complement, but never replace, written notes and sketches."},
     {"heading":"Photo log","body":"Every image is listed in a photo log with its number, time, a short description and the direction or position from which it was taken. A gap in the log should be explainable."}]',
   '[{"term":"Photo log","definition":"A written list recording each photograph, its time, subject and camera position."},{"term":"Documentation","definition":"Recording the scene through notes, photographs and sketches before items are disturbed."}]',
   '["U.S. Department of Justice, National Institute of Justice (2000). Crime Scene Investigation: A Guide for Law Enforcement.","Robinson, E. M. Crime Scene Photography. Academic Press."]'),
  (m, 2, 'Overall, mid-range and close-up', 7, true,
   '[{"heading":"Overall photographs","body":"Wide views show the entire scene and how areas relate to each other, including entrances and exits. They are usually taken from corners or fixed reference points."},
     {"heading":"Mid-range photographs","body":"Mid-range views show an item of evidence together with a nearby fixed feature, such as furniture or a doorway, so its location can be understood."},
     {"heading":"Close-up photographs and scale","body":"Close-ups capture detail. They are taken first without a scale, then again with a scale (ruler) placed beside the item, keeping the camera perpendicular to the surface to limit distortion. The unscaled image shows nothing was added; the scaled image allows measurement."}]',
   '[{"term":"Scale","definition":"A measuring device included in a close-up photograph so dimensions can be determined later."},{"term":"Perpendicular","definition":"Camera sensor parallel to the evidence surface, reducing perspective distortion."}]',
   '["U.S. Department of Justice, National Institute of Justice (2000). Crime Scene Investigation: A Guide for Law Enforcement.","Robinson, E. M. Crime Scene Photography. Academic Press."]');

  insert into public.quizzes (module_id, title, kind, published) values (m, 'Photography practice', 'practice', true) returning id into qz;
  perform private.seed_question(qz, 1, 'Which photograph best shows where an item lies relative to a fixed feature in the room?', 'photo-ranges',
    array['Overall photograph','Mid-range photograph','Close-up with scale','Photo-log entry'], 2,
    'Mid-range photographs link an item to nearby fixed features so its position can be understood.');
  perform private.seed_question(qz, 2, 'Why is a close-up taken both without and with a scale?', 'scale',
    array['To save storage space','The unscaled image shows nothing was added; the scaled image allows measurement','Courts only accept black-and-white images','The scale should never be photographed'], 2,
    'Taking both shows the original condition and provides a measurable reference.');
  perform private.seed_question(qz, 3, 'When should evidence normally be photographed?', 'sequence',
    array['After packaging','Before it is moved or collected','Only if it is valuable','After the scene is released'], 2,
    'Evidence is documented in place before it is disturbed.');
  perform private.seed_question(qz, 4, 'For a close-up, how should the camera be positioned?', 'scale',
    array['At a sharp angle for drama','Perpendicular to the evidence surface','As far away as possible','Pointing at the light source'], 2,
    'A perpendicular camera limits perspective distortion so measurements stay accurate.');

  insert into public.quizzes (module_id, title, kind, time_limit_seconds, max_attempts, review_policy, published)
  values (m, 'Photography graded quiz', 'graded', 600, 2, 'after_submit', true) returning id into qz;
  perform private.seed_question(qz, 1, 'What is the main purpose of overall scene photographs?', 'photo-ranges',
    array['Capture fine detail of a single item','Show the whole scene and how areas relate','Replace the written report','Measure bloodstain size'], 2,
    'Overall photographs establish the layout of the entire scene.');
  perform private.seed_question(qz, 2, 'A photo log should record at least:', 'photo-log',
    array['Only the photographer''s name','Image number, time, subject and camera position','Only images used in court','The camera serial number only'], 2,
    'The log ties every image to time, subject and position.');
  perform private.seed_question(qz, 3, 'An item had to be moved before it was photographed. What should happen?', 'sequence',
    array['Put it back and photograph as if untouched','Record that it was moved and why in the notes','Do not mention it','Discard the item'], 2,
    'Never re-stage evidence; record the change and the reason.');
  perform private.seed_question(qz, 4, 'Which sequence is correct for documenting a single item?', 'sequence',
    array['Close-up, overall, mid-range','Overall, mid-range, close-up without scale, close-up with scale','Close-up with scale only','Mid-range, then collect, then overall'], 2,
    'Work from wide to detailed, taking the unscaled close-up before the scaled one.');
  perform private.seed_question(qz, 5, 'Photographs at a scene are best described as:', 'documentation',
    array['A replacement for notes and sketches','A complement to notes and sketches','Optional if notes exist','Only needed for homicide'], 2,
    'Photographs, notes and sketches support each other; none replaces the others.');

  -- =========================================================================
  -- Module 2: Personal Identification Techniques
  -- =========================================================================
  insert into public.modules (course_id, position, slug, title, summary, companion, published)
  values (c, 2, 'personal-identification', 'Personal Identification Techniques',
          'Friction ridge basics: pattern types, latent print development and the limits of comparison.',
          'Develop and lift a latent print on a glass surface in the 3D lab.', true)
  returning id into m;

  insert into public.lessons (module_id, position, title, reading_minutes, published, sections, key_terms, citations) values
  (m, 1, 'Friction ridge patterns', 7, true,
   '[{"heading":"What fingerprints are","body":"Friction ridge skin on the fingers and palms forms raised ridges. The arrangement of ridges and their details is used for comparison and identification."},
     {"heading":"Pattern types","body":"The three general pattern types are loops, whorls and arches. Loops are the most common, whorls are next, and arches are the least common. Pattern type alone cannot identify a person; it is a class characteristic used for sorting."},
     {"heading":"Ridge details","body":"Individual ridge features, sometimes called minutiae, include ridge endings, bifurcations and dots. Examiners compare these features and their relationships when evaluating whether two impressions may share a source."}]',
   '[{"term":"Loop","definition":"A pattern where ridges enter from one side, recurve and exit the same side."},{"term":"Bifurcation","definition":"A point where one ridge splits into two."},{"term":"Class characteristic","definition":"A feature shared by a group, useful for sorting but not individualising."}]',
   '["Saferstein, R. Criminalistics: An Introduction to Forensic Science. Pearson.","Expert Working Group on Human Factors in Latent Print Analysis (2012). Latent Print Examination and Human Factors. NIST/NIJ."]'),
  (m, 2, 'Developing and lifting latent prints', 7, true,
   '[{"heading":"Latent, patent and plastic prints","body":"Latent prints are not readily visible and need development. Patent prints are visible, for example in blood or paint. Plastic prints are impressions left in soft material such as wax."},
     {"heading":"Powder and lift on non-porous surfaces","body":"On smooth, non-porous surfaces such as glass, a print is photographed, carefully dusted with powder, photographed again with a scale, then lifted with tape and placed on a backing card that is labelled."},
     {"heading":"Limits and error","body":"Comparison relies on examiner judgement. Reviews such as the 2009 National Research Council report and the 2016 PCAST report emphasise documented methods, verification and awareness of error rates and cognitive bias."}]',
   '[{"term":"Latent print","definition":"A friction ridge impression that is not readily visible without development."},{"term":"Lift card","definition":"Backing card that holds a lifted print, labelled with case and location details."}]',
   '["National Research Council (2009). Strengthening Forensic Science in the United States: A Path Forward.","President''s Council of Advisors on Science and Technology (2016). Forensic Science in Criminal Courts: Ensuring Scientific Validity of Feature-Comparison Methods."]');

  insert into public.quizzes (module_id, title, kind, published) values (m, 'Identification practice', 'practice', true) returning id into qz;
  perform private.seed_question(qz, 1, 'Which fingerprint pattern type is the most common?', 'patterns',
    array['Arch','Loop','Whorl','Tented arch'], 2, 'Loops are the most frequently encountered general pattern.');
  perform private.seed_question(qz, 2, 'A print left in fresh paint is best described as:', 'print-types',
    array['Latent','Patent','Plastic','Invisible'], 2, 'Patent prints are visible because they are made with a coloured substance such as paint or blood.');
  perform private.seed_question(qz, 3, 'Before dusting a latent print on glass you should first:', 'procedure',
    array['Lift it with tape','Photograph it in place','Wipe the surface','Package the glass in plastic'], 2,
    'Document first, then develop, photograph again and lift.');
  perform private.seed_question(qz, 4, 'Can pattern type alone identify a specific person?', 'limits',
    array['Yes, always','No, it is a class characteristic','Only for whorls','Only if the print is complete'], 2,
    'Pattern type is shared by many people and is used for sorting, not identification.');

  -- =========================================================================
  -- Module 3: Forensic Chemistry and Toxicology
  -- =========================================================================
  insert into public.modules (course_id, position, slug, title, summary, companion, published)
  values (c, 3, 'chemistry-toxicology', 'Forensic Chemistry & Toxicology',
          'Laboratory safety, presumptive versus confirmatory testing and how toxicology results are interpreted.',
          'Explore labelled virtual laboratory tools and sample containers.', true)
  returning id into m;

  insert into public.lessons (module_id, position, title, reading_minutes, published, sections, key_terms, citations) values
  (m, 1, 'Presumptive and confirmatory tests', 7, true,
   '[{"heading":"Two stages of testing","body":"Presumptive tests, such as colour tests, are fast screening tools that indicate a substance may be present. They can give false positives, so a positive result must be confirmed."},
     {"heading":"Confirmatory testing","body":"Confirmatory methods, such as gas chromatography-mass spectrometry (GC-MS), identify a substance with much higher specificity and are performed in an accredited laboratory."},
     {"heading":"Laboratory safety","body":"Unknown substances are treated as hazardous. Use personal protective equipment, avoid tasting or smelling samples directly, and follow the laboratory''s chemical hygiene plan."}]',
   '[{"term":"Presumptive test","definition":"A screening test that indicates a substance may be present."},{"term":"GC-MS","definition":"Gas chromatography-mass spectrometry, a common confirmatory technique."}]',
   '["Saferstein, R. Criminalistics: An Introduction to Forensic Science. Pearson.","Levine, B. (Ed.). Principles of Forensic Toxicology. AACC Press."]'),
  (m, 2, 'Interpreting toxicology', 6, true,
   '[{"heading":"What toxicology asks","body":"Forensic toxicology detects and measures drugs, alcohol and poisons in biological samples and helps interpret their possible effects."},
     {"heading":"Context matters","body":"A concentration alone rarely answers whether someone was impaired or whether a substance caused death. Tolerance, timing of sampling, specimen type and post-mortem changes all influence interpretation."},
     {"heading":"Chain of custody","body":"Biological specimens are labelled, sealed and tracked so that the laboratory result can be linked reliably to the person and time of collection."}]',
   '[{"term":"Specimen","definition":"A biological sample such as blood or urine collected for analysis."},{"term":"Tolerance","definition":"Reduced response to a drug after repeated use, which complicates interpretation."}]',
   '["Levine, B. (Ed.). Principles of Forensic Toxicology. AACC Press."]');

  insert into public.quizzes (module_id, title, kind, published) values (m, 'Chemistry practice', 'practice', true) returning id into qz;
  perform private.seed_question(qz, 1, 'A colour test turns positive for a controlled drug. What is the correct conclusion?', 'presumptive',
    array['The drug is confirmed','The drug may be present and needs confirmatory testing','The sample is contaminated','No further testing is needed'], 2,
    'Presumptive tests screen; a confirmatory method is required.');
  perform private.seed_question(qz, 2, 'Which is a confirmatory technique?', 'confirmatory',
    array['Colour spot test','GC-MS','Visual inspection','Smell test'], 2, 'GC-MS provides high-specificity identification.');
  perform private.seed_question(qz, 3, 'Why can a blood drug concentration alone be misleading?', 'interpretation',
    array['It is always accurate','Tolerance, timing and specimen type affect interpretation','Concentrations are never measured','Only urine matters'], 2,
    'Interpretation requires context beyond a single number.');
  perform private.seed_question(qz, 4, 'How should an unknown powder be treated in the lab?', 'safety',
    array['Taste a small amount','As potentially hazardous, using PPE','Smell it closely','Handle without gloves'], 2, 'Treat unknowns as hazardous.');

  -- =========================================================================
  -- Module 4: Questioned Document Examination
  -- =========================================================================
  insert into public.modules (course_id, position, slug, title, summary, companion, published)
  values (c, 4, 'questioned-documents', 'Questioned Document Examination',
          'Handwriting comparison principles, alterations and careful handling of document evidence.',
          'Zoom into and compare document evidence in the 3D viewer.', true)
  returning id into m;

  insert into public.lessons (module_id, position, title, reading_minutes, published, sections, key_terms, citations) values
  (m, 1, 'Handwriting comparison principles', 7, true,
   '[{"heading":"Natural variation","body":"No one writes exactly the same way twice. Examiners study the range of natural variation in known writing before comparing it with a questioned sample."},
     {"heading":"Exemplars","body":"Known writing samples (exemplars) can be requested or collected from normal course of business. Comparable content, writing instrument and time period make comparison more meaningful."},
     {"heading":"Cautious conclusions","body":"Conclusions are expressed on a scale rather than as simple yes or no answers, and depend on the quantity and quality of material available."}]',
   '[{"term":"Exemplar","definition":"A known sample of writing used for comparison."},{"term":"Natural variation","definition":"Normal differences within one person''s writing."}]',
   '["Huber, R. A., & Headrick, A. M. (1999). Handwriting Identification: Facts and Fundamentals. CRC Press."]'),
  (m, 2, 'Alterations and handling', 6, true,
   '[{"heading":"Detecting alterations","body":"Erasures, overwriting and added entries can sometimes be revealed with oblique lighting, magnification and alternate light sources that show differences between inks or disturbed paper fibres."},
     {"heading":"Handling document evidence","body":"Wear gloves, handle by the edges, do not fold, staple, mark or write on the document, and avoid writing on paper placed on top of it, which can leave indentations."},
     {"heading":"Packaging","body":"Place documents flat in a labelled protective folder or envelope. Record chain of custody each time the document changes hands."}]',
   '[{"term":"Indented writing","definition":"Impressions left on a lower sheet by writing on a sheet above it."},{"term":"Oblique lighting","definition":"Light shone at a low angle to reveal surface texture and indentations."}]',
   '["Huber, R. A., & Headrick, A. M. (1999). Handwriting Identification: Facts and Fundamentals. CRC Press.","Saferstein, R. Criminalistics: An Introduction to Forensic Science. Pearson."]');

  insert into public.quizzes (module_id, title, kind, published) values (m, 'Documents practice', 'practice', true) returning id into qz;
  perform private.seed_question(qz, 1, 'Why do examiners study several known writing samples?', 'variation',
    array['To find one perfect sample','To understand natural variation','To speed up the case','Because one sample is illegal'], 2,
    'Multiple exemplars reveal the writer''s normal range of variation.');
  perform private.seed_question(qz, 2, 'Which action can damage a questioned document?', 'handling',
    array['Handling by the edges','Writing notes on paper placed on top of it','Using a protective folder','Wearing gloves'], 2,
    'Writing on top can create indented writing that contaminates the evidence.');
  perform private.seed_question(qz, 3, 'Low-angle (oblique) lighting is useful to reveal:', 'alterations',
    array['Indented writing and surface disturbance','DNA','Blood type','Fingerprint pattern type'], 1,
    'Oblique light casts shadows in indentations and disturbed fibres.');
  perform private.seed_question(qz, 4, 'Handwriting conclusions are best expressed as:', 'conclusions',
    array['Always certain','A scale reflecting strength of support','Only yes or no','Never reported'], 2,
    'Conclusion scales reflect the quantity and quality of material.');

  -- =========================================================================
  -- Module 5: Forensic Polygraph Procedures and Limitations
  -- =========================================================================
  insert into public.modules (course_id, position, slug, title, summary, companion, published)
  values (c, 5, 'polygraph', 'Forensic Polygraph Procedures and Limitations',
          'What a polygraph records, how examinations are structured and why results do not reliably establish deception.',
          'Examine virtual polygraph equipment and sample chart readouts.', true)
  returning id into m;

  insert into public.lessons (module_id, position, title, reading_minutes, published, sections, key_terms, citations) values
  (m, 1, 'What the instrument measures', 6, true,
   '[{"heading":"Physiological channels","body":"A polygraph records physiological responses such as breathing, electrodermal activity (skin conductance) and cardiovascular activity (blood pressure and pulse) while questions are asked."},
     {"heading":"It does not detect lies","body":"The instrument records arousal, not deception. Fear, anxiety, confusion or medical conditions can produce responses in truthful people, and some deceptive people may show little response."},
     {"heading":"Examination phases","body":"A typical examination includes a pre-test interview, the in-test question series and a post-test phase. Question formats include relevant, comparison and irrelevant questions."}]',
   '[{"term":"Electrodermal activity","definition":"Changes in skin conductance associated with sweat gland activity."},{"term":"Comparison question","definition":"A question designed to provoke a response for comparison with relevant questions."}]',
   '["National Research Council (2003). The Polygraph and Lie Detection. National Academies Press."]'),
  (m, 2, 'Scientific limitations', 6, true,
   '[{"heading":"Evidence on accuracy","body":"The National Research Council''s 2003 review concluded that the scientific basis for polygraph testing is weak and that accuracy in realistic screening settings is well below perfect."},
     {"heading":"Countermeasures and bias","body":"Results can be affected by countermeasures, examiner expectations and the way questions are framed."},
     {"heading":"Use in practice","body":"Many courts restrict or exclude polygraph results. In this course the polygraph is studied as an investigative tool with serious limits, never as proof of truthfulness or guilt."}]',
   '[{"term":"Countermeasure","definition":"A deliberate attempt to alter physiological responses during testing."},{"term":"False positive","definition":"A truthful person classified as deceptive."}]',
   '["National Research Council (2003). The Polygraph and Lie Detection. National Academies Press."]');

  insert into public.quizzes (module_id, title, kind, published) values (m, 'Polygraph practice', 'practice', true) returning id into qz;
  perform private.seed_question(qz, 1, 'What does a polygraph actually record?', 'channels',
    array['Lies directly','Physiological responses such as breathing, skin conductance and cardiovascular activity','Brain waves only','Voice stress only'], 2,
    'It records physiological arousal, not deception itself.');
  perform private.seed_question(qz, 2, 'An anxious but truthful examinee shows strong responses. This is an example of:', 'limits',
    array['A false positive','A confirmed lie','A countermeasure','A comparison question'], 1,
    'Truthful people classified as deceptive are false positives.');
  perform private.seed_question(qz, 3, 'The 2003 National Research Council review concluded that:', 'evidence',
    array['Polygraphs are nearly perfect','The scientific basis is weak and accuracy is limited','Polygraphs read thoughts','Courts must accept results'], 2,
    'The review found weak scientific grounding and limited accuracy.');
  perform private.seed_question(qz, 4, 'In this course, a polygraph result should be treated as:', 'use',
    array['Proof of guilt','An investigative aid with serious limitations','Proof of innocence','Irrelevant to procedure'], 2,
    'Results are not proof of truthfulness or deception.');

  -- =========================================================================
  -- Module 6: Forensic Ballistics
  -- =========================================================================
  insert into public.modules (course_id, position, slug, title, summary, companion, published)
  values (c, 6, 'ballistics', 'Forensic Ballistics',
          'Cartridge cases, class and individual characteristics, and safe documentation of firearm evidence.',
          'Locate, document and collect a cartridge case in the 3D crime scene.', true)
  returning id into m;

  insert into public.lessons (module_id, position, title, reading_minutes, published, sections, key_terms, citations) values
  (m, 1, 'Firearm evidence basics', 7, true,
   '[{"heading":"Components","body":"A cartridge consists of the case, primer, propellant and projectile (bullet). When fired, the bullet leaves the barrel and the case may be ejected at the scene, depending on the firearm."},
     {"heading":"Class and individual characteristics","body":"Rifling in a barrel leaves lands and grooves on a bullet. Calibre and rifling pattern are class characteristics. Fine striations and marks from the breech face, firing pin and ejector are compared for possible common origin."},
     {"heading":"Limits","body":"Firearm and toolmark comparison involves examiner judgement. Reviews have called for stronger validation and transparent reporting of the method''s limitations."}]',
   '[{"term":"Lands and grooves","definition":"Raised and recessed spiral surfaces of rifling inside a barrel."},{"term":"Breech face marks","definition":"Marks impressed on the cartridge case head by the firearm''s breech face."}]',
   '["Heard, B. J. Handbook of Firearms and Ballistics. Wiley.","President''s Council of Advisors on Science and Technology (2016). Forensic Science in Criminal Courts."]'),
  (m, 2, 'Documenting and collecting cartridge cases', 6, true,
   '[{"heading":"Safety first","body":"Any firearm found at a scene is treated as loaded and made safe only by trained personnel. Never point a firearm at anyone or place fingers in the trigger guard."},
     {"heading":"Document in place","body":"Mark each cartridge case with an evidence marker, photograph it with and without scale, and record its position before collection."},
     {"heading":"Collect and package","body":"Wear gloves and pick up the case by its edges to avoid destroying marks or prints. Package each item separately, seal and label it, and begin the chain of custody record."}]',
   '[{"term":"Evidence marker","definition":"A numbered placard placed beside an item to identify it in photographs and notes."},{"term":"Chain of custody","definition":"Documented record of every person who handled an item, when and why."}]',
   '["U.S. Department of Justice, National Institute of Justice (2000). Crime Scene Investigation: A Guide for Law Enforcement.","Heard, B. J. Handbook of Firearms and Ballistics. Wiley."]');

  insert into public.quizzes (module_id, title, kind, published) values (m, 'Ballistics practice', 'practice', true) returning id into qz;
  perform private.seed_question(qz, 1, 'Calibre and rifling pattern are examples of:', 'characteristics',
    array['Individual characteristics','Class characteristics','Chain of custody','Trace evidence'], 2,
    'They are shared by many firearms of the same make or model.');
  perform private.seed_question(qz, 2, 'Before collecting a cartridge case you should:', 'procedure',
    array['Pick it up quickly','Mark and photograph it in place','Clean it','Put it in your pocket'], 2, 'Document position and condition first.');
  perform private.seed_question(qz, 3, 'How should a cartridge case be handled?', 'handling',
    array['With bare hands','With gloves, by its edges','By rolling it with a pen through the opening','By shaking it'], 2,
    'Gloved handling by the edges protects marks and potential prints.');
  perform private.seed_question(qz, 4, 'A firearm found at a scene should be treated as:', 'safety',
    array['Unloaded','Loaded until made safe by trained personnel','A toy','Irrelevant'], 2, 'Always assume a firearm is loaded.');

  -- =========================================================================
  -- Module 7: Crime Scene Investigation
  -- =========================================================================
  insert into public.modules (course_id, position, slug, title, summary, companion, published)
  values (c, 7, 'crime-scene-investigation', 'Crime Scene Investigation',
          'Scene security, systematic search, documentation, collection, packaging and chain of custody.',
          'Complete a full multi-step investigation in the 3D lab and submit a case report.', true)
  returning id into m;

  insert into public.lessons (module_id, position, title, reading_minutes, published, sections, key_terms, citations) values
  (m, 1, 'Securing and assessing the scene', 7, true,
   '[{"heading":"Safety and security","body":"The first priorities are safety and medical aid. The scene is then secured with a perimeter, and a scene entry log records everyone who enters and leaves."},
     {"heading":"Initial walk-through","body":"The lead investigator performs a careful walk-through along a defined path to assess the scene, identify fragile evidence and plan the search without disturbing items."},
     {"heading":"Contamination control","body":"Personal protective equipment such as gloves, masks and shoe covers reduces contamination. Change gloves between items when cross-transfer is possible."}]',
   '[{"term":"Scene entry log","definition":"A record of every person entering or leaving the scene, with times and purpose."},{"term":"Perimeter","definition":"The boundary that controls access to the scene."}]',
   '["U.S. Department of Justice, National Institute of Justice (2000). Crime Scene Investigation: A Guide for Law Enforcement."]'),
  (m, 2, 'Documentation, collection and chain of custody', 8, true,
   '[{"heading":"Systematic search","body":"Search patterns such as line, grid, zone or spiral help ensure no area is missed. The choice depends on the size and type of scene."},
     {"heading":"Order of operations","body":"For each item: mark it, photograph it without and with scale, record its location, then collect it using the appropriate technique. Biological material such as bloodstains is collected with swabs, air-dried and packaged in paper, not plastic, to prevent degradation."},
     {"heading":"Chain of custody","body":"Each package is sealed, labelled with case number, item number, description, date, time, location and collector, and every transfer is recorded. A broken chain can make evidence unreliable."}]',
   '[{"term":"Grid search","definition":"Two overlapping line searches at right angles, for thorough coverage."},{"term":"Packaging","definition":"Sealing an item in an appropriate container that protects it and preserves its condition."}]',
   '["U.S. Department of Justice, National Institute of Justice (2000). Crime Scene Investigation: A Guide for Law Enforcement.","Saferstein, R. Criminalistics: An Introduction to Forensic Science. Pearson."]');

  insert into public.quizzes (module_id, title, kind, published) values (m, 'CSI practice', 'practice', true) returning id into qz;
  perform private.seed_question(qz, 1, 'What is the purpose of a scene entry log?', 'security',
    array['To list evidence','To record everyone who enters and leaves','To store photographs','To record the weather'], 2,
    'The entry log controls and documents access.');
  perform private.seed_question(qz, 2, 'Why are bloodstain swabs packaged in paper rather than plastic?', 'packaging',
    array['Paper is cheaper','Plastic traps moisture that can degrade biological material','Plastic is illegal','Paper is waterproof'], 2,
    'Trapped moisture encourages degradation; air-dry and use paper.');
  perform private.seed_question(qz, 3, 'Which order is correct for a single item?', 'sequence',
    array['Collect, photograph, mark','Mark, photograph, collect, package','Package, mark, collect','Photograph only'], 2,
    'Document first, then collect, then package.');
  perform private.seed_question(qz, 4, 'A grid search is:', 'search',
    array['A single walk across the room','Two overlapping line searches at right angles','A spiral from the centre','Random searching'], 2,
    'Grid searches double-cover the area.');

  insert into public.quizzes (module_id, title, kind, time_limit_seconds, max_attempts, review_policy, published)
  values (m, 'Crime Scene Investigation graded quiz', 'graded', 900, 2, 'after_submit', true) returning id into qz;
  perform private.seed_question(qz, 1, 'What are the first priorities on arrival at a scene?', 'security',
    array['Photography','Safety and medical aid','Packaging','Writing the report'], 2, 'People come first: safety and medical aid.');
  perform private.seed_question(qz, 2, 'Why change gloves between items?', 'contamination',
    array['Fashion','To prevent cross-transfer between items','Gloves expire in minutes','It is never necessary'], 2,
    'Changing gloves reduces cross-contamination.');
  perform private.seed_question(qz, 3, 'Which information belongs on an evidence package label?', 'custody',
    array['Case and item number, description, date, time, location, collector','Only the item name','The suspect''s name only','Nothing; labels are optional'], 1,
    'Complete labels support a reliable chain of custody.');
  perform private.seed_question(qz, 4, 'A latent print on a glass table should be:', 'procedure',
    array['Lifted immediately without photos','Photographed, developed, photographed with scale, then lifted','Wiped clean','Packaged with the bloodstain swab'], 2,
    'Document, develop, document with scale, then lift.');
  perform private.seed_question(qz, 5, 'What can make evidence unreliable in court?', 'custody',
    array['A complete custody record','A gap in the chain of custody','Photographs with scale','Sealed packaging'], 2,
    'Unexplained gaps undermine confidence in the item''s integrity.');
end $seed$;

-- ---------------------------------------------------------------------------
-- Simulation scenarios (public templates) and private rubrics.
-- Coordinates in metres. Room spans x 0..width, z 0..depth. yaw 0 faces -z (north).
-- ---------------------------------------------------------------------------
insert into public.simulation_scenarios (id, title, summary, kind, module_slug, est_minutes, version, published, template) values
('tutorial_residence', 'Tutorial: Living Room', 'Learn the controls and the basic order of operations on two items of evidence.',
 'tutorial', 'crime-scene-investigation', 8, 1, true,
 '{
   "fixed_seed": 1001,
   "evidence_count": 2,
   "briefing": "Patrol officers responded to a reported break-in. The occupant is safe and the scene is secured. Sign the scene log, then document, collect and package every item of evidence.",
   "objectives": ["Sign the scene entry log", "Wear gloves before touching evidence", "Mark and photograph each item, with and without scale", "Collect with the correct technique", "Package and seal each item", "Submit your case report"],
   "room": {"width": 7.0, "depth": 8.0, "height": 2.8, "wall_color": "#3A4558", "floor_color": "#4A3F35"},
   "door": {"wall": "south", "offset": 3.5, "width": 1.2},
   "player_start": {"x": 3.5, "z": 7.2, "yaw": 0},
   "scene_log": {"x": 2.4, "z": 7.6},
   "furniture": [
     {"id": "sofa", "x": 1.4, "z": 2.2, "w": 2.2, "d": 0.9, "h": 0.8, "color": "#56677F"},
     {"id": "coffee_table", "x": 3.6, "z": 3.4, "w": 1.2, "d": 0.7, "h": 0.45, "color": "#8FA3B8", "top_label": "glass coffee table"},
     {"id": "bookshelf", "x": 6.6, "z": 1.0, "w": 0.6, "d": 1.6, "h": 2.0, "color": "#6B4E3D"},
     {"id": "side_table", "x": 5.6, "z": 5.2, "w": 0.6, "d": 0.6, "h": 0.6, "color": "#6B4E3D"}
   ],
   "spawn_points": [
     {"id": "floor_window", "label": "floor below the window", "x": 4.8, "z": 1.2, "y": 0.0, "surface": "floor"},
     {"id": "floor_center", "label": "floor near the centre", "x": 2.6, "z": 4.8, "y": 0.0, "surface": "floor"},
     {"id": "table_top", "label": "glass coffee table", "x": 3.6, "z": 3.4, "y": 0.45, "surface": "furniture"},
     {"id": "side_table_top", "label": "side table", "x": 5.6, "z": 5.2, "y": 0.6, "surface": "furniture"}
   ],
   "evidence_pool": [
     {"type": "cartridge_case", "required": true},
     {"type": "latent_print", "required": true}
   ],
   "practice_guide": true
 }'),
('practice_apartment', 'Practice: Apartment Burglary', 'A larger apartment with four randomly placed items. Hints explain mistakes as you go.',
 'practice', 'crime-scene-investigation', 15, 1, true,
 '{
   "evidence_count": 4,
   "briefing": "A neighbour reported shouting and a loud bang. The apartment is secured and the occupant is at hospital. Process the scene completely.",
   "objectives": ["Sign the scene entry log", "Wear gloves before touching evidence", "Mark and photograph each item, with and without scale", "Collect with the correct technique", "Package each item appropriately", "Submit your case report"],
   "room": {"width": 9.0, "depth": 10.0, "height": 2.8, "wall_color": "#3B4A52", "floor_color": "#544636"},
   "door": {"wall": "south", "offset": 2.0, "width": 1.2},
   "player_start": {"x": 2.0, "z": 9.2, "yaw": 0},
   "scene_log": {"x": 0.9, "z": 9.6},
   "furniture": [
     {"id": "bed", "x": 7.4, "z": 2.0, "w": 2.0, "d": 2.4, "h": 0.6, "color": "#4E5D73"},
     {"id": "desk", "x": 1.2, "z": 1.0, "w": 1.6, "d": 0.8, "h": 0.75, "color": "#7A5A44"},
     {"id": "dining_table", "x": 4.5, "z": 5.5, "w": 1.6, "d": 1.0, "h": 0.75, "color": "#9AAFC2", "top_label": "glass dining table"},
     {"id": "cabinet", "x": 8.5, "z": 7.5, "w": 0.8, "d": 1.6, "h": 1.2, "color": "#6B4E3D"},
     {"id": "partition", "x": 5.0, "z": 3.0, "w": 3.0, "d": 0.15, "h": 2.2, "color": "#3B4A52"}
   ],
   "spawn_points": [
     {"id": "floor_bedside", "label": "floor beside the bed", "x": 5.8, "z": 1.4, "y": 0.0, "surface": "floor"},
     {"id": "floor_hall", "label": "hallway floor", "x": 2.2, "z": 6.8, "y": 0.0, "surface": "floor"},
     {"id": "floor_kitchen", "label": "floor near the cabinet", "x": 7.2, "z": 8.4, "y": 0.0, "surface": "floor"},
     {"id": "floor_desk", "label": "floor under the desk chair", "x": 2.4, "z": 2.0, "y": 0.0, "surface": "floor"},
     {"id": "desk_top", "label": "desk surface", "x": 1.2, "z": 1.0, "y": 0.75, "surface": "furniture"},
     {"id": "dining_top", "label": "glass dining table", "x": 4.5, "z": 5.5, "y": 0.75, "surface": "furniture"},
     {"id": "cabinet_top", "label": "top of the cabinet", "x": 8.5, "z": 7.5, "y": 1.2, "surface": "furniture"}
   ],
   "evidence_pool": [
     {"type": "cartridge_case"}, {"type": "latent_print"}, {"type": "bloodstain"},
     {"type": "questioned_document"}, {"type": "hair_fiber"}
   ],
   "practice_guide": true
 }'),
('assessment_office', 'Assessment: Office Shooting', 'Graded scenario. No hints. Your actions are scored on the server against the instructor rubric.',
 'assessment', 'crime-scene-investigation', 20, 1, true,
 '{
   "evidence_count": 4,
   "briefing": "Shots were reported in a small office after hours. The scene has been secured. Process it using correct procedure. This is a graded assessment: hints and the AI tutor are disabled until you submit.",
   "objectives": ["Process the scene according to correct procedure", "Submit your case report"],
   "room": {"width": 10.0, "depth": 9.0, "height": 3.0, "wall_color": "#424B5A", "floor_color": "#3F4148"},
   "door": {"wall": "south", "offset": 8.0, "width": 1.2},
   "player_start": {"x": 8.0, "z": 8.2, "yaw": 0},
   "scene_log": {"x": 9.1, "z": 8.6},
   "furniture": [
     {"id": "desk_a", "x": 2.0, "z": 2.0, "w": 1.8, "d": 0.9, "h": 0.75, "color": "#7A5A44"},
     {"id": "desk_b", "x": 5.5, "z": 2.0, "w": 1.8, "d": 0.9, "h": 0.75, "color": "#7A5A44"},
     {"id": "meeting_table", "x": 4.0, "z": 6.0, "w": 2.2, "d": 1.2, "h": 0.75, "color": "#9AAFC2", "top_label": "glass meeting table"},
     {"id": "filing", "x": 9.5, "z": 1.5, "w": 0.8, "d": 1.2, "h": 1.4, "color": "#5D6B7E"},
     {"id": "plant", "x": 0.6, "z": 8.2, "w": 0.6, "d": 0.6, "h": 1.1, "color": "#3E6B4A"}
   ],
   "spawn_points": [
     {"id": "floor_desk_a", "label": "floor beside desk A", "x": 3.4, "z": 2.4, "y": 0.0, "surface": "floor"},
     {"id": "floor_desk_b", "label": "floor behind desk B", "x": 5.5, "z": 0.8, "y": 0.0, "surface": "floor"},
     {"id": "floor_center", "label": "floor in the centre", "x": 6.8, "z": 4.6, "y": 0.0, "surface": "floor"},
     {"id": "floor_west", "label": "floor by the west wall", "x": 0.8, "z": 5.0, "y": 0.0, "surface": "floor"},
     {"id": "desk_a_top", "label": "desk A surface", "x": 2.0, "z": 2.0, "y": 0.75, "surface": "furniture"},
     {"id": "meeting_top", "label": "glass meeting table", "x": 4.0, "z": 6.0, "y": 0.75, "surface": "furniture"},
     {"id": "filing_top", "label": "top of the filing cabinet", "x": 9.5, "z": 1.5, "y": 1.4, "surface": "furniture"}
   ],
   "evidence_pool": [
     {"type": "cartridge_case", "required": true}, {"type": "latent_print"}, {"type": "bloodstain"},
     {"type": "questioned_document"}, {"type": "hair_fiber"}
   ],
   "practice_guide": false
 }');

-- Instructor rubric (server only). Same structure for each scenario; weights may differ per scenario.
insert into private.simulation_rubrics (scenario_id, version, rubric)
select s.id, 1, '{
  "global": {"sign_log_before_evidence": 2, "submit_report": 1},
  "evidence": {
    "cartridge_case":      {"collect_tool": "hand",      "package_tools": ["paper_bag", "plastic_bag"]},
    "latent_print":        {"collect_tool": "print_kit", "package_tools": ["paper_bag"]},
    "bloodstain":          {"collect_tool": "swab_kit",  "package_tools": ["paper_bag"]},
    "questioned_document": {"collect_tool": "hand",      "package_tools": ["paper_bag", "plastic_bag"]},
    "hair_fiber":          {"collect_tool": "forceps",   "package_tools": ["paper_bag"]}
  },
  "step_points": {"marker": 1, "photograph": 1, "photograph_scale": 1, "collect": 2, "package": 2},
  "order": ["marker", "photograph", "photograph_scale", "collect", "package"],
  "penalties": {"no_gloves": 3, "wrong_tool": 2, "wrong_package": 2, "out_of_order": 1},
  "pass_ratio": 0.7
}'::jsonb
from public.simulation_scenarios s;

insert into public.system_settings (key, value) values
  ('ai_daily_limit', '40'::jsonb),
  ('system_notice', '"Welcome to the Forensim pilot. All lesson content is pending instructor review."'::jsonb);
