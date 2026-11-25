"""Syllabus-aligned reviewer lessons, part 2: Forensic 4 (Questioned Documents), Forensic 5 (Lie
Detection), Forensic 6 (Ballistics). Built from the WCC course syllabi."""

from reviewers_part1 import S

QDE_REFS = [
    "Tumang & Mosqueda (2025). Questioned Document Examination with Laboratory Activities.",
    "Revised Penal Code of the Philippines, Articles 161 to 176 (Forgeries).",
    "Rules of Court of the Philippines, Rules 130 and 132 (Evidence), as amended in 2019.",
]
POLY_REFS = [
    "WCC Course Syllabus, Forensic 5: Lie Detection Techniques.",
    "National Research Council (2003). The Polygraph and Lie Detection. Washington, DC: The National Academies Press.",
]
BAL_REFS = [
    "Pacling, R. C. (2023). Laboratory manual in forensic ballistics (2nd ed.). Revisa.",
    "Republic Act No. 10591, Comprehensive Firearms and Ammunition Regulation Act (2013).",
]

MODULES = {}

# =============================================================================================
# FORENSIC 4 — QUESTIONED DOCUMENTS EXAMINATION
# =============================================================================================
MODULES["questioned-documents"] = [
    dict(title="Reviewer: The field of document examination, writing and forgery", minutes=8, refs=QDE_REFS, sections=[
        S("Forensic document examination",
          "Questioned document examination (QDE) is the branch of forensic science that examines documents whose authorship, genuineness, origin or history is disputed. Examiners study handwriting and signatures, typewriting and printing, paper and ink, alterations, erasures, indented writing, and counterfeit money and identification documents.\n\n"
          "Forensics appears at the crime scene (finding and preserving documents), in the laboratory (examination), in court (expert testimony), and, less accurately, in entertainment. Albert S. Osborn, author of \"Questioned Documents\" (1910), is regarded as the father of modern document examination."),
        S("Document examination in the Philippines",
          "Document examination is performed by the Questioned Document Examination Division of the PNP Forensic Group (formerly the PNP Crime Laboratory) and by the Questioned Documents Division of the National Bureau of Investigation (NBI). Typical cases involve forged signatures on deeds, checks and contracts, falsified public documents, threatening letters and counterfeit currency."),
        S("The evolution of writing",
          "Writing developed from pictures to sounds:\n"
          "• Pictographs: pictures of objects (cave drawings).\n"
          "• Ideographs: symbols for ideas.\n"
          "• Cuneiform (Sumerians, around 3200 BC) and hieroglyphics (Egypt).\n"
          "• Phonetic and alphabetic writing: the Phoenician alphabet, adapted by the Greeks and Romans into the Latin alphabet used today.\n"
          "• In the Philippines, the pre-colonial Baybayin script was used before the Latin alphabet spread."),
        S("A brief history of forgery",
          "Forgery is as old as writing itself. Roman law punished it under the Lex Cornelia de falsis (81 BC). As seals, signatures, banknotes and official records gained value, forgers imitated them, and courts began calling on experienced penmen, and later trained examiners, to compare writings.",
          video="in5_Cdw3-Ns", video_title="What handwriting analysis looks like: a state crime laboratory (KTVB)"),
        S("Quick review",
          "• QDE examines documents whose genuineness or authorship is disputed.\n"
          "• Albert S. Osborn: father of modern document examination (\"Questioned Documents\", 1910).\n"
          "• Writing evolved from pictographs and ideographs to alphabets.\n"
          "• In the Philippines: PNP Forensic Group and NBI."),
    ], terms=[("Questioned document", "Any document whose genuineness, authorship or history is in dispute."),
              ("Document examiner", "A trained specialist who scientifically examines questioned documents."),
              ("Pictograph", "A picture representing an object; the earliest form of writing."),
              ("Forgery", "The false making or material alteration of a writing with intent to defraud.")]),

    dict(title="Reviewer: The expert witness and the document examiner", minutes=7, refs=QDE_REFS, sections=[
        S("The expert witness",
          "An expert witness is a person who, because of special knowledge, skill, experience, training or education, may give an opinion in court on a matter outside common knowledge. Under the Rules on Evidence, such an opinion may be received if the witness is shown to possess that expertise (Rule 130, Section 52 of the 2019 amendments, formerly Section 49). Expert witnesses may be presented by the prosecution, the defence, or appointed by the court."),
        S("Weight of expert testimony",
          "Courts are not bound by expert opinions. The judge weighs the expert's qualifications, the reasons and methods given, and the other evidence. On handwriting, Rule 132, Section 22 provides that genuineness may be proved by a witness familiar with the person's writing, or by comparison, made by the witness or the court, with writings admitted or treated as genuine. A judge may therefore examine the writings personally."),
        S("The forensic document examiner",
          "A competent examiner usually completes a structured training programme of about two years under a qualified examiner, covering handwriting, typewriting and printing, paper and ink, instruments, and courtroom testimony, followed by proficiency testing.\n\n"
          "Moral and professional obligations: be objective and impartial; examine before concluding; use adequate standards; report limitations; never let the party that pays influence the opinion; keep evidence secure and confidential; and admit when a question cannot be answered."),
        S("Quick review",
          "• An expert witness has special knowledge shown in court (Rule 130, Sec. 52).\n"
          "• Courts are not bound by experts; Rule 132, Sec. 22 allows comparison by the court.\n"
          "• Examiners must be objective, impartial and honest about limitations."),
    ], terms=[("Expert witness", "A witness permitted to give opinions because of specialised knowledge or skill."),
              ("Standards (exemplars)", "Writings of known origin used for comparison."),
              ("Objectivity", "Basing conclusions only on the evidence, free from bias."),
              ("Proficiency test", "A test of an examiner's competence using samples with known answers.")]),

    dict(title="Reviewer: Documents and examination instruments", minutes=8, refs=QDE_REFS, sections=[
        S("Kinds of documents",
          "A document is any material bearing marks, symbols or writing that conveys a message. Under Philippine law:\n"
          "• Public documents: written official acts of public officers and documents acknowledged before a notary public.\n"
          "• Official documents: issued by a public official in the exercise of their functions.\n"
          "• Commercial documents: used by merchants or businesses, such as checks, promissory notes and bills of exchange.\n"
          "• Private documents: deeds or instruments executed by private persons without the intervention of a notary or official.\n\n"
          "A questioned document is one whose genuineness is disputed. Standards or exemplars are documents of known origin: requested standards are written on request for the examination; collected standards are writings made in the normal course of life."),
        S("Instruments and apparatus",
          "• Hand lens and magnifiers: first examination of strokes.\n"
          "• Stereomicroscope: three-dimensional view of line quality, pen pressure and stroke order.\n"
          "• Ultraviolet lamp: shows fluorescence differences in paper and ink, chemical erasures and invisible markings.\n"
          "• Infrared viewing and photography: distinguishes inks that look the same and reveals obliterated writing.\n"
          "• Video spectral comparator (VSC): combines different light sources and filters to compare inks and reveal alterations.\n"
          "• Electrostatic detection apparatus (ESDA): reveals indented writing left on pages beneath the one written on.\n"
          "• Oblique (side) lighting and transmitted light: show indentations, erasures, watermarks and paper thinning.\n"
          "• Measuring grids and test plates: compare size, spacing, slant and alignment.",
          image="qde_stereo.webp", caption="A stereomicroscope, used to study strokes, pen pressure and line crossings."),
        S("Procedures",
          "Handle documents with clean hands or gloves; never fold, staple, mark or write on them; keep them in protective folders. Examine non-destructively first (visual, magnification, lighting), photograph findings, and only then consider tests that could alter the document."),
        S("Quick review",
          "• Public, official, commercial and private documents.\n"
          "• Requested vs collected standards.\n"
          "• UV: fluorescence and chemical erasure. IR: ink differences and obliterations. ESDA: indented writing. VSC: combined light and filters."),
    ], terms=[("Public document", "An official act or a document acknowledged before a notary public."),
              ("Requested standard", "A writing made on request specifically for comparison."),
              ("Collected standard", "A writing made in the ordinary course of life, collected for comparison."),
              ("ESDA", "Electrostatic detection apparatus, which reveals indented writing."),
              ("VSC", "Video spectral comparator, used to examine inks and alterations under different light.")]),

    dict(title="Reviewer: Paper, writing instruments and handwriting identification", minutes=9, refs=QDE_REFS, sections=[
        S("Paper and its properties",
          "Paper is a sheet of matted cellulose fibres, usually from wood pulp, cotton or rag. Types include bond, ledger, onionskin, newsprint, coated and security papers. Properties examined include weight and thickness, colour and brightness, opacity, finish, fibre content, watermarks, and fluorescence from optical brighteners. Matching or differing paper can show whether pages belong together or were substituted."),
        S("Writing instruments",
          "• Pencil: graphite mixed with clay; grades from hard (H) to soft (B).\n"
          "• Fountain pen: liquid ink from a reservoir; may show nib tracks and ink pooling.\n"
          "• Ballpoint pen (commercialised in the 1940s): thick oil-based paste ink; may show striations, skipping and ink blobs (gooping).\n"
          "• Rollerball and gel pens: water-based or gel inks with smooth lines.\n"
          "• Felt-tip and markers: porous tips with broad strokes.\n"
          "The instrument affects line quality, so standards should be written with a similar instrument."),
        S("Handwriting: kinds and physiological basis",
          "Handwriting is a learned neuromuscular skill controlled by the brain; it is sometimes called \"brain writing\". Kinds include cursive (connected) writing, manuscript or print, and block capitals.\n\n"
          "Development: a person first copies a school model (copybook). With practice, writing becomes automatic and develops personal habits. In old age or illness it may show tremor and deterioration."),
        S("Principles of handwriting identification",
          "• No two persons write exactly alike.\n"
          "• No one writes exactly the same way twice: natural variation is normal.\n"
          "• Class characteristics come from the copybook system and are shared by many writers; individual characteristics are personal habits that identify a writer.\n"
          "• Identification rests on a combination of significant individual characteristics, with no unexplained fundamental differences."),
        S("Process of handwriting analysis",
          "1. Analyse the questioned writing and the standards separately.\n"
          "2. Compare them feature by feature: line quality and rhythm, slant, size and proportion, spacing, alignment to the baseline, pen pressure, letter formations, connecting strokes, beginning and ending strokes, pen lifts, and embellishments.\n"
          "3. Evaluate the significance of similarities and differences, considering natural variation.\n"
          "Good standards are adequate in number, contemporaneous (written around the same time) and similar in content, style and writing instrument.",
          video="MU6MF3ReuEY", video_title="Comparison of handwriting samples (Vidya-mitra lecture)"),
        S("Quick review",
          "• Ballpoint ink is oil-based paste; striations and gooping are typical.\n"
          "• Handwriting is \"brain writing\": learned and habitual.\n"
          "• No two people write alike; no one writes the same way twice.\n"
          "• Class characteristics are shared; individual characteristics identify."),
    ], terms=[("Natural variation", "The normal differences in repeated writings of the same person."),
              ("Class characteristics", "Features shared by a group of writers, such as a copybook style."),
              ("Individual characteristics", "Personal writing habits that distinguish one writer from others."),
              ("Line quality", "The smoothness, speed and fluency of the written stroke."),
              ("Contemporaneous standards", "Standards written at about the same time as the questioned writing.")]),

    dict(title="Reviewer: Signatures, forgery and alterations", minutes=9, refs=QDE_REFS, sections=[
        S("Signatures",
          "A signature is a person's name or mark written by them to show identity or approval. Styles include the formal or complete signature (for legal documents), the informal signature (for everyday use) and the careless scrawl (fast, often illegible). Because signatures are written often, they become highly automatic and personal.\n\n"
          "Characteristics of a genuine signature: free, rapid movement; good line quality with natural tapering at the start and end; consistent proportions; and natural variation between signatures. Two signatures that match exactly when overlaid are suspicious, because no one signs exactly the same way twice."),
        S("Classification of forged signatures",
          "• Simple or spurious forgery: no attempt to copy the genuine signature; the forger simply writes the name.\n"
          "• Simulated or copied forgery: a freehand imitation of a model signature.\n"
          "• Traced forgery: the outline of a genuine signature is followed, using transmitted light, carbon paper or an indented guide line.\n\n"
          "Indications of forgery: tremor, slow drawn strokes, blunt beginnings and endings, unusual pen lifts, patching or retouching, heavy uniform pressure, guide lines or carbon traces, and differences from the writer's habits.",
          image="qde_signatures.webp", caption="Genuine and forged signatures from a famous art forgery case, collected for comparison."),
        S("Steps in the examination of signatures",
          "1. Obtain adequate standards: several genuine signatures from around the same date and of the same type.\n"
          "2. Examine the questioned signature under magnification and different lighting for line quality, tremor, pen lifts and tracing marks.\n"
          "3. Compare letter forms, proportions, spacing, slant, baseline and pen pressure with the standards.\n"
          "4. Evaluate whether differences are natural variation or signs of imitation.\n"
          "5. Report the conclusion and its limitations."),
        S("Types of alteration",
          "• Erasure: mechanical (scraping or rubbing, which disturbs paper fibres) or chemical (ink eradicators that bleach the ink).\n"
          "• Obliteration: covering writing with ink, correction fluid or other material.\n"
          "• Addition, insertion and interlineation: writing added between lines or words.\n"
          "• Substitution: replacing a page or part of a document.\n"
          "Detection uses magnification, transmitted and oblique light, ultraviolet and infrared examination. Elements to prove an alteration: that a change was made, what was originally written if possible, and when or by whom when the evidence allows."),
        S("Quick review",
          "• Forgery types: simple (spurious), simulated (copied), traced.\n"
          "• Signs of forgery: tremor, drawn quality, blunt ends, patching, pen lifts.\n"
          "• Identical overlaid signatures suggest tracing.\n"
          "• Alterations: erasure, obliteration, addition or interlineation, substitution."),
    ], terms=[("Simple forgery", "A signature written without attempting to copy the genuine one."),
              ("Simulated forgery", "A freehand copy of a genuine signature."),
              ("Traced forgery", "A signature made by following the outline of a genuine signature."),
              ("Obliteration", "Covering writing so it cannot be read."),
              ("Interlineation", "Writing inserted between lines of an existing document.")]),

    dict(title="Reviewer: Currency, typewriting and the legal framework", minutes=9, refs=QDE_REFS, sections=[
        S("Anti-counterfeit measures in modern currency",
          "Philippine banknotes are issued by the Bangko Sentral ng Pilipinas (BSP). The New Generation Currency series began in 2010, and a polymer ₱1,000 note was introduced in 2022. The BSP teaches the public to Feel, Look and Tilt:\n"
          "• Feel: genuine notes have a distinct texture and raised (embossed) prints.\n"
          "• Look: hold the note against the light to see the watermark portrait, the security thread and the see-through mark that completes the denomination.\n"
          "• Tilt: optically variable ink and devices change colour or image when tilted.\n"
          "Counterfeits often feel smooth, lack a true watermark (a printed imitation instead), have blurred fine lines, and show no colour shift. Counterfeit coins may differ in weight, edge reeding, colour and sound.",
          image="qde_watermark.webp", caption="A banknote watermark seen against the light, one of the oldest anti-counterfeiting features."),
        S("Typewriting identification",
          "Typewriters are classified by their mechanism (type bar, typeball or element, daisywheel) and by typeface design and spacing (for example pica, 10 characters per inch, and elite, 12 characters per inch). Class characteristics identify the make and model; individual defects identify the specific machine: misaligned letters, broken or worn characters, letters printing too light on one side, and clogged type.\n\n"
          "To obtain exemplars: type the questioned text and the full keyboard several times on the suspect machine, with a similar ribbon and paper, and remove and keep the ribbon as evidence. The same principles of class and individual characteristics apply to modern printers and photocopiers.",
          image="qde_typewriter.webp", caption="Labelled parts of a portable typewriter."),
        S("Contents of an examination report",
          "Case and request details; documents received and how they were marked; the examination requested; methods and instruments used; findings; conclusion with its level of certainty; limitations; and the examiner's signature. In presenting handwriting evidence, use enlarged side-by-side charts and explain methods, observations, limitations and conclusions plainly, without exaggeration."),
        S("Legal framework in the Philippines",
          "• Revised Penal Code, Articles 161 to 176 (Forgeries): counterfeiting the Great Seal, signatures and stamps of the Chief Executive; counterfeiting coins (Arts. 163 to 165); forging treasury or bank notes (Art. 166); illegal possession and use of false notes (Art. 168); falsification by a public officer, employee or notary (Art. 171); falsification by private individuals and use of falsified documents (Art. 172).\n"
          "• Rule 132 of the Rules of Court: authentication and proof of documents, including how the genuineness of handwriting is proved (Section 22)."),
        S("Quick review",
          "• BSP: Feel, Look, Tilt. Watermark, security thread, see-through mark, embossed prints, colour-shifting ink.\n"
          "• Typewriter class characteristics give make and model; individual defects identify the machine.\n"
          "• RPC Arts. 161 to 176 cover forgeries; Art. 171 public officers, Art. 172 private individuals.\n"
          "• Rule 132, Sec. 22: proving handwriting."),
    ], terms=[("Watermark", "An image formed in paper during manufacture, visible against the light."),
              ("Security thread", "A thin strip embedded in a banknote, visible against the light."),
              ("Optically variable ink", "Ink that changes colour when the note is tilted."),
              ("Pica / elite", "Typewriter spacing of 10 and 12 characters per inch."),
              ("Falsification", "Making a document speak falsely, punished under Articles 171 and 172 of the Revised Penal Code.")]),
]

# =============================================================================================
# FORENSIC 5 — LIE DETECTION TECHNIQUES
# =============================================================================================
MODULES["polygraph"] = [
    dict(title="Reviewer: Lie detection, history and theories of deception", minutes=8, refs=POLY_REFS, sections=[
        S("Introduction",
          "Lie detection is the use of methods to determine whether a person is telling the truth. The polygraph (from the Greek poly, many, and graphein, to write) records several physiological responses at the same time while a person answers questions. It does not detect lies directly; it records bodily changes that may be associated with deception, and an examiner interprets them."),
        S("History and important personalities",
          "• Ancient methods relied on fear and superstition: the rice test (a suspect chewed dry rice; a dry mouth suggested guilt), trial by ordeal, and similar practices.\n"
          "• Angelo Mosso (late 1800s): studied changes in blood circulation and breathing caused by fear.\n"
          "• Cesare Lombroso (1895): used the hydrosphygmograph to record blood pressure and pulse changes during interrogation.\n"
          "• Vittorio Benussi (1914): studied breathing patterns in deception.\n"
          "• William Moulton Marston (1910s): developed a systolic blood pressure deception test.\n"
          "• John A. Larson (1921): built the first instrument that continuously recorded blood pressure, pulse and breathing for police use.\n"
          "• Leonarde Keeler (1920s to 1930s): made the polygraph portable and practical and added a galvanometer for skin responses; often called the father of the modern polygraph.\n"
          "• John E. Reid (1947): introduced the control question technique.\n"
          "• Cleve Backster (1960s): developed the zone comparison technique and numerical scoring.",
          image="poly_keeler.webp", caption="Leonarde Keeler demonstrating his polygraph in 1937."),
        S("Psychological theories of deception",
          "• Fear of detection or punishment: the person fears the consequences of being caught.\n"
          "• Conflict theory: two opposing tendencies, to tell the truth and to lie, cause arousal.\n"
          "• Conditioned response: lying has been associated with punishment since childhood.\n"
          "• Psychological set: the examinee's attention focuses on the question that poses the greatest threat to them, producing the strongest reaction."),
        S("Behavioural indicators",
          "Nervousness, avoiding eye contact, fidgeting, hesitation and inconsistent stories are often thought to signal lying, but research shows these cues are unreliable: truthful people may be nervous and skilled liars may be calm. Studies find that people judge truth and lies only slightly better than chance. Behaviour should guide further questioning, never decide guilt."),
        S("Quick review",
          "• Polygraph = poly (many) + graphein (to write).\n"
          "• Lombroso: hydrosphygmograph (1895). Larson: first continuous polygraph (1921). Keeler: modern portable polygraph. Reid: control question technique (1947). Backster: zone comparison.\n"
          "• Psychological set: attention goes to the most threatening question.\n"
          "• Behavioural cues alone are unreliable."),
    ], terms=[("Polygraph", "An instrument that records several physiological responses simultaneously."),
              ("Psychological set", "The tendency of attention and reaction to focus on the most threatening question."),
              ("Hydrosphygmograph", "Lombroso's device for recording blood pressure and pulse changes."),
              ("Deception", "The deliberate attempt to make another person believe something untrue.")]),

    dict(title="Reviewer: The nervous system and the polygraph instrument", minutes=8, refs=POLY_REFS, sections=[
        S("The autonomic nervous system",
          "The autonomic nervous system controls involuntary body functions and has two divisions:\n"
          "• Sympathetic division (\"fight or flight\"): activated by stress or threat. Heart rate and blood pressure rise, breathing changes, sweating increases, pupils dilate, and digestion slows.\n"
          "• Parasympathetic division (\"rest and digest\"): calms the body and restores normal functions.\n"
          "The polygraph records the sympathetic changes that occur when a question is perceived as threatening.",
          image="poly_ans.webp", caption="The sympathetic and parasympathetic divisions of the autonomic nervous system."),
        S("Physiological changes recorded",
          "• Respiration: rate, depth and pattern of breathing (suppression, holding, irregularity).\n"
          "• Cardiovascular activity: relative blood pressure and heart rate.\n"
          "• Electrodermal activity: changes in the skin's electrical conductance caused by sweat gland activity.\n"
          "• Peripheral blood flow (in some instruments)."),
        S("Polygraph instrumentation and sensors",
          "• Pneumograph: two sensors around the chest (thoracic) and abdomen record breathing.\n"
          "• Cardiosphygmograph: a blood pressure cuff on the upper arm records relative blood pressure and pulse.\n"
          "• Galvanograph or electrodermal sensor: electrodes on two fingers record skin conductance.\n"
          "• Motion or activity sensor: a seat pad detects movements that could distort the recording or be used as countermeasures.\n"
          "Older instruments recorded with pens on moving chart paper; modern computerised polygraphs display and store the tracings digitally.",
          image="poly_lafayette.webp", caption="A Lafayette polygraph instrument."),
        S("Watch: how a polygraph test is done",
          "A police examiner explains the sensors and demonstrates the test.",
          video="po_DCBGExP4", video_title="How it Works: Polygraph Test (City of Bloomington, Minnesota)"),
        S("Quick review",
          "• Sympathetic = fight or flight; parasympathetic = rest and digest.\n"
          "• Pneumograph = breathing; cardiosphygmograph = blood pressure and pulse; galvanograph = skin conductance; motion sensor = movement.\n"
          "• The polygraph records arousal, not lies."),
    ], terms=[("Sympathetic nervous system", "The division that prepares the body for fight or flight."),
              ("Pneumograph", "Sensor that records breathing."),
              ("Cardiosphygmograph", "Component that records relative blood pressure and heart rate."),
              ("Electrodermal activity", "Changes in the skin's electrical conductance due to sweating."),
              ("Chart", "The recorded tracings from one presentation of the question list.")]),

    dict(title="Reviewer: Polygraph techniques and the pre-test interview", minutes=8, refs=POLY_REFS, sections=[
        S("Main testing techniques",
          "• Relevant-Irrelevant Test (RIT): the oldest format; compares reactions to relevant questions (about the crime) with irrelevant ones (\"Is today Monday?\"). Criticised because innocent people may react strongly to any accusatory question.\n"
          "• Control (Comparison) Question Test (CQT): compares reactions to relevant questions with comparison questions about general past misconduct that most people find uncomfortable (for example \"Before this year, did you ever lie to get out of trouble?\"). Stronger reactions to relevant questions suggest deception; stronger reactions to comparison questions suggest truthfulness. Variants include the Reid technique and the Backster zone comparison.\n"
          "• Guilty Knowledge Test, also called the Concealed Information Test (GKT or CIT): multiple-choice questions about details only the offender would know (\"Was the weapon a knife? a gun? a rope?\"). A consistent reaction to the correct detail suggests knowledge of the crime.\n"
          "• Peak of Tension test: a related format that presents items in a known order and looks for rising tension toward the critical item."),
        S("Pre-test interview",
          "The pre-test is the most important phase. The examiner:\n"
          "1. Explains the examinee's rights and obtains written, voluntary consent.\n"
          "2. Collects background and checks suitability: health, medication, sleep, food, drugs or alcohol, and mental condition.\n"
          "3. Discusses the case and lets the examinee give their version.\n"
          "4. Explains how the polygraph works.\n"
          "5. Reviews every question with the examinee; there are no surprise questions.\n"
          "6. Often runs an acquaintance (stimulation) test, such as a number test, to show the examinee that the instrument responds."),
        S("Question formulation",
          "Questions should be short, simple and direct; answerable by yes or no; about one issue only; free of legal terms and emotionally loaded words (use \"take\" rather than \"steal\", for example); and clearly limited in time and subject. Relevant questions concern the specific issue under investigation."),
        S("Quick review",
          "• RIT: relevant vs irrelevant. CQT: relevant vs comparison questions. GKT/CIT: details only the offender knows.\n"
          "• Pre-test: rights and consent, suitability, case discussion, question review, acquaintance test.\n"
          "• Questions: short, yes or no, one issue, no loaded words."),
    ], terms=[("Relevant question", "A question about the specific issue being investigated."),
              ("Comparison (control) question", "A question about general past misconduct used for comparison with relevant questions."),
              ("Irrelevant question", "A neutral question used to establish a baseline."),
              ("Concealed Information Test", "A test using details known only to the perpetrator."),
              ("Acquaintance test", "A practice test that familiarises the examinee with the procedure.")]),

    dict(title="Reviewer: Test administration and chart analysis", minutes=8, refs=POLY_REFS, sections=[
        S("Phases of the examination",
          "1. Pre-test interview.\n"
          "2. In-test (chart collection): the sensors are attached and the question list is asked, usually three to five times (charts), with about 20 to 30 seconds between questions so responses can return to baseline. The examinee sits still, looks ahead and answers only yes or no.\n"
          "3. Post-test: the examiner analyses the charts and discusses the results; the examinee may explain reactions."),
        S("Test administration protocols",
          "Use a quiet, private, comfortable room free from distractions. The examinee should be rested and fit. Questions are asked in a steady, neutral voice. Every chart is labelled and annotated with the questions, answers, and any movements, coughs or noises that could cause artefacts."),
        S("Chart analysis and scoring",
          "• Global evaluation: the examiner reviews the charts as a whole. It is more subjective.\n"
          "• Numerical scoring: each relevant question is compared with nearby comparison questions, channel by channel, and given a score (in the common seven-position scale from -3 to +3). Scores are totalled and compared with set cut-off values.\n"
          "• Computer algorithms and empirical scoring systems help standardise decisions.\n"
          "Results are reported as: No Deception Indicated (NDI), Deception Indicated (DI), or Inconclusive or No Opinion when the data are insufficient."),
        S("Quick review",
          "• Phases: pre-test, in-test (chart collection), post-test.\n"
          "• Usually three to five charts, 20 to 30 seconds between questions.\n"
          "• Numerical scoring compares relevant and comparison questions (-3 to +3).\n"
          "• Results: NDI, DI, Inconclusive."),
    ], terms=[("In-test phase", "The phase in which the charts are recorded."),
              ("Artefact", "A distortion in the tracing not caused by a response to the question, such as movement or coughing."),
              ("Numerical scoring", "A systematic method of assigning scores to reactions on each channel."),
              ("NDI / DI", "No Deception Indicated / Deception Indicated.")]),

    dict(title="Reviewer: Admissibility, ethics, validity and reporting", minutes=8, refs=POLY_REFS + ["1987 Constitution of the Philippines, Article III (Bill of Rights)."], sections=[
        S("Admissibility in Philippine courts",
          "Philippine courts do not accept polygraph results as evidence of guilt or innocence. The Supreme Court has said that the polygraph has not attained scientific acceptance as a reliable and accurate means of ascertaining truth or deception (for example, People v. Adoviso, 1999). Polygraph examinations are therefore used as investigative aids: to focus an investigation, verify statements, or eliminate suspects, not as proof in court."),
        S("Constitutional rights and voluntariness",
          "Under Article III of the Constitution, a person under investigation has the right to remain silent and to counsel (Section 12) and the right against self-incrimination (Section 17). A polygraph examination must be voluntary, with written consent. A person may refuse or stop the test at any time, and refusal must not be treated as evidence of guilt."),
        S("Professional conduct",
          "The examiner must be properly trained and certified; avoid bias and remain neutral regardless of who requested the test; keep the results confidential and release them only to authorised persons; never use threats, promises or coercion; and record the examination fully."),
        S("Validity issues, countermeasures and criticisms",
          "A major scientific review by the U.S. National Research Council (2003) found that specific-incident polygraph tests discriminate lying from truth-telling at rates above chance but well below perfection, and that accuracy for broad screening is weaker.\n\n"
          "• False positives: truthful but anxious people may appear deceptive.\n"
          "• False negatives: deceptive people may pass.\n"
          "• Countermeasures: physical (biting the tongue, pressing the toes), mental (counting backwards, thinking exciting thoughts) or drugs, used to distort responses.\n"
          "• Examiner bias and poorly worded questions affect results.",
          video="ZsgInxmk92Q", video_title="Do Lie Detectors (Polygraphs) Really Work? (Dr. Todd Grande)"),
        S("Report writing",
          "A polygraph report includes the case reference and requesting party; examinee information and consent; date, time and place; the technique and instrument used; the relevant questions and answers; the result (NDI, DI or Inconclusive) and the examiner's opinion; any admissions made; and the examiner's name and signature. It should state that the result is an investigative opinion."),
        S("Quick review",
          "• Polygraph results are not admissible as proof of guilt in Philippine courts.\n"
          "• The test must be voluntary; refusal is not evidence of guilt.\n"
          "• Accuracy is above chance but not perfect; false positives and countermeasures occur.\n"
          "• Reports state technique, questions, answers, result and opinion."),
    ], terms=[("Investigative aid", "A tool that guides an investigation but is not itself proof in court."),
              ("False positive", "A truthful person judged deceptive."),
              ("False negative", "A deceptive person judged truthful."),
              ("Countermeasure", "A deliberate attempt to distort polygraph responses.")]),
]

# =============================================================================================
# FORENSIC 6 — FORENSIC BALLISTICS
# =============================================================================================
MODULES["ballistics"] = [
    dict(title="Reviewer: Introduction and history of forensic ballistics", minutes=8, refs=BAL_REFS, sections=[
        S("Meaning and branches",
          "Ballistics comes from the Greek ballein, \"to throw\", and is related to the Roman ballista, a war machine that hurled stones. It is the science of the motion of projectiles. Forensic ballistics, more precisely firearms identification, is the scientific examination of firearms, ammunition and fired bullets and cartridge cases to answer legal questions.\n\n"
          "Branches:\n"
          "• Interior ballistics: what happens inside the firearm, from the firing pin striking the primer until the bullet leaves the muzzle.\n"
          "• Exterior ballistics: the flight of the bullet from the muzzle to the target (velocity, trajectory, gravity, air resistance).\n"
          "• Terminal ballistics: the effect of the bullet on the target, including wound ballistics.\n"
          "• Forensic ballistics: the application of all of these to criminal investigation and the courts."),
        S("Historical background",
          "• Henry Goddard (1835, London): traced a bullet to the mould that made it through a small flaw, one of the first recorded cases of bullet comparison.\n"
          "• Alexandre Lacassagne (1889, France): compared rifling marks on a bullet with a suspect's gun.\n"
          "• Charles Waite, Philip Gravelle and Calvin Goddard (1920s, New York): founded the Bureau of Forensic Ballistics; Gravelle adapted the comparison microscope (1925).\n"
          "• Col. Calvin H. Goddard: regarded as the father of modern firearms identification. His work on the St. Valentine's Day Massacre (1929) led to the first independent scientific crime laboratory, at Northwestern University."),
        S("What firearms examiners determine",
          "Whether a firearm is functional; the type, make and calibre of a firearm from fired components; whether a bullet or cartridge case was fired from a particular firearm; the approximate distance of a shot from gunshot residue patterns; and serial numbers that have been removed or altered. In the Philippines, the ownership and possession of firearms are regulated by R.A. 10591."),
        S("Quick review",
          "• Ballistics: from ballein, to throw.\n"
          "• Interior (inside the gun), exterior (flight), terminal (effect on target), forensic (court).\n"
          "• Calvin Goddard: father of modern firearms identification; Gravelle: comparison microscope.\n"
          "• R.A. 10591 regulates firearms in the Philippines."),
    ], terms=[("Ballistics", "The science of the motion of projectiles."),
              ("Interior ballistics", "Study of events inside the firearm during firing."),
              ("Exterior ballistics", "Study of the projectile's flight from muzzle to target."),
              ("Terminal ballistics", "Study of the projectile's effect on the target."),
              ("Firearms identification", "Determining whether fired components came from a particular firearm.")]),

    dict(title="Reviewer: Ammunition and the cartridge", minutes=9, refs=BAL_REFS, sections=[
        S("Parts of a cartridge",
          "A cartridge (also called a round) is one complete unit of ammunition. It has four parts:\n"
          "1. Bullet: the projectile that leaves the barrel.\n"
          "2. Cartridge case: the container, usually brass, that holds everything together.\n"
          "3. Gunpowder (propellant): burns rapidly to produce the gas that pushes the bullet.\n"
          "4. Primer: a small, impact-sensitive charge that ignites the gunpowder when struck by the firing pin.",
          image="bal_cartridge.webp", caption="Parts of a center-fire cartridge: bullet, cartridge case, gunpowder (propellant), rim and primer."),
        S("Classification of cartridges by priming",
          "• Pin-fire: an early type (developed by Lefaucheux in the 1830s and 1840s) with a small pin sticking out of the side of the case; the hammer strikes the pin to fire the primer. Now obsolete.\n"
          "• Rim-fire: the priming compound is inside the hollow rim; the firing pin crushes the rim. Used in small calibres such as .22.\n"
          "• Center-fire: a separate primer sits in the centre of the base. Boxer primers have their own anvil and the case has one flash hole; Berdan primers have the anvil formed in the case, with two or more flash holes. Most modern ammunition is center-fire.",
          image="bal_ignition.webp", caption="Cartridge ignition types, including pin-fire, rim-fire and center-fire (Boxer and Berdan)."),
        S("Gunpowder",
          "• Black powder: the oldest propellant, a mixture of about 75 percent potassium nitrate (saltpetre), 15 percent charcoal and 10 percent sulfur. It produces heavy smoke and residue.\n"
          "• Smokeless powder: modern propellant based on nitrocellulose. Single-base contains nitrocellulose only; double-base adds nitroglycerin; triple-base adds nitroguanidine."),
        S("Primers",
          "A primer has a metal cup, the priming mixture and, in Boxer primers, an anvil. Early primers used mercury fulminate, later potassium chlorate (which corroded barrels). Modern primers commonly contain lead styphnate, barium nitrate and antimony sulfide; their particles are a major part of gunshot residue.",
          image="bal_fired_cases.webp", caption="Fired rim-fire (left) and center-fire (right) cases. Note where each firing pin struck."),
        S("Watch: cartridge components",
          "A short explanation of the parts of a cartridge and how they work together when fired.",
          video="o7chlJAD8FI", video_title="Cartridge Components (hunter safety course)"),
        S("Quick review",
          "• Cartridge = bullet + case + gunpowder + primer.\n"
          "• Pin-fire (obsolete, pin on the side), rim-fire (primer in the rim), center-fire (primer in the centre).\n"
          "• Boxer: one flash hole, anvil in primer. Berdan: two or more flash holes, anvil in case.\n"
          "• Black powder 75:15:10; smokeless powder single, double, triple base."),
    ], terms=[("Cartridge", "A complete round of ammunition: bullet, case, propellant and primer."),
              ("Primer", "Impact-sensitive charge that ignites the propellant."),
              ("Rim-fire", "Cartridge with priming compound inside the rim."),
              ("Center-fire", "Cartridge with a separate primer in the centre of the base."),
              ("Smokeless powder", "Modern nitrocellulose-based propellant.")]),

    dict(title="Reviewer: Bullets and firearms", minutes=9, refs=BAL_REFS, sections=[
        S("The bullet",
          "A bullet is the projectile fired from a rifled firearm. It may be made of lead, a lead alloy, or lead with a jacket of copper or gilding metal. Common types:\n"
          "• Lead bullet (plain lead, round nose, wadcutter).\n"
          "• Full metal jacket (FMJ): the core is fully covered except the base.\n"
          "• Semi-jacketed, soft point and hollow point: partly exposed or hollow tips designed to expand.\n"
          "• Boat tail: tapered base for better flight.\n"
          "Shotguns fire shells containing many pellets (shot) or a single slug, with wads separating the powder and shot.",
          image="bal_bullets.webp", caption="Different bullet types and constructions."),
        S("Calibre and gauge",
          "Calibre is the diameter of the bore measured from land to land, expressed in hundredths of an inch (.38, .45) or millimetres (9 mm). Shotgun bores are expressed in gauge: the number of lead balls of the bore's diameter that make one pound (a 12-gauge bore fits balls weighing 1/12 pound each); the smaller the gauge number, the larger the bore."),
        S("Rifling",
          "The inside of most handgun and rifle barrels has spiral grooves (cut into the bore) and lands (the raised areas between them). Rifling spins the bullet so it flies point-first and stable. Rifling has a direction of twist (right or left), a number of lands and grooves, their widths, and a rate of twist. Some barrels use polygonal rifling with rounded lands instead of sharp edges.",
          image="bal_rifling_diagram.webp", caption="Conventional rifling (left) and polygonal rifling (right), seen from the muzzle."),
        S("Firearms and their parts",
          "Firearms are classified by bore as smooth-bore (shotguns, muskets) or rifled (pistols, revolvers, rifles), and by action as single-shot, revolver, semi-automatic or automatic. Major parts and their functions:\n"
          "• Barrel: guides the bullet and imparts spin.\n"
          "• Chamber: holds the cartridge at the moment of firing.\n"
          "• Breech and breech face: close the rear of the chamber.\n"
          "• Firing pin: strikes the primer.\n"
          "• Extractor: pulls the fired case from the chamber; ejector: throws it out.\n"
          "• Trigger and hammer or striker: fire the weapon.\n"
          "• Magazine or cylinder: holds cartridges.\n"
          "• Frame or receiver and stock or grip.",
          image="bal_rifling_bore.webp", caption="Looking down a rifled barrel: the spiral lands and grooves."),
        S("Quick review",
          "• FMJ = fully jacketed; hollow point and soft point expand.\n"
          "• Calibre: bore diameter land to land. Gauge: smaller number = bigger bore.\n"
          "• Lands are raised; grooves are cut. Rifling spins the bullet.\n"
          "• Extractor pulls; ejector throws out the fired case."),
    ], terms=[("Lands", "The raised portions between the grooves inside a rifled barrel."),
              ("Grooves", "The spiral channels cut into the bore of a rifled barrel."),
              ("Calibre", "The diameter of a rifled bore measured from land to land."),
              ("Gauge", "Shotgun bore size based on the number of bore-sized lead balls per pound."),
              ("Extractor / ejector", "Parts that pull the fired case from the chamber and throw it out of the firearm.")]),

    dict(title="Reviewer: Class and individual characteristics", minutes=9, refs=BAL_REFS, sections=[
        S("Class characteristics",
          "Class characteristics are features determined by the manufacturer's design, shared by every firearm of the same make and model. They can narrow a bullet down to a group of firearms but cannot identify a single gun:\n"
          "• Calibre.\n"
          "• Number of lands and grooves.\n"
          "• Direction of twist (right or left).\n"
          "• Width of lands and grooves.\n"
          "• Shape of the firing pin and other design features."),
        S("Individual characteristics",
          "Individual characteristics are random, microscopic marks unique to one firearm. They come from the manufacturing process and from use, wear, rust and damage. On fired bullets they appear as fine striations scratched by the barrel. On fired cartridge cases they include:\n"
          "• Firing pin impression.\n"
          "• Breech face marks (the case is pushed back against the breech face).\n"
          "• Extractor and ejector marks.\n"
          "• Chamber marks."),
        S("Manufacturing processes and identification",
          "Barrels are rifled by cutting, broaching, button rifling, hammer forging or electrochemical methods. The tools wear slightly with every barrel, and machining leaves tiny random irregularities. That is why two barrels made one after the other by the same tool still leave different striations, which is the scientific basis of firearms identification."),
        S("The comparison microscope",
          "The comparison microscope joins two microscopes with an optical bridge so the examiner sees two objects side by side in one split field. A questioned bullet or case from the scene is placed under one objective and a test-fired specimen from the suspect firearm under the other. If the striations or impressions line up in sufficient agreement, the examiner can conclude that both were fired from the same firearm. Digital systems such as ballistic imaging networks help search large collections.",
          image="bal_comparison.webp", caption="A comparison microscope: two specimens viewed side by side through one eyepiece."),
        S("Watch: inside a ballistics laboratory",
          "A university forensic laboratory demonstrates the comparison of bullets and cartridge cases.",
          video="WGHCTMTh7TE", video_title="Ohio University Forensic Chemistry Lab: Ballistics"),
        S("Quick review",
          "• Class characteristics (design): calibre, lands and grooves, twist, widths.\n"
          "• Individual characteristics (random): striations, firing pin, breech face, extractor and ejector marks.\n"
          "• Tool wear makes every barrel unique.\n"
          "• Comparison microscope: questioned vs test-fired specimen side by side."),
    ], terms=[("Class characteristics", "Design features shared by all firearms of the same make and model."),
              ("Individual characteristics", "Random microscopic marks unique to one firearm."),
              ("Striations", "Fine parallel scratches on a bullet made by the barrel."),
              ("Breech face marks", "Marks impressed on the cartridge base by the breech face."),
              ("Comparison microscope", "Two microscopes joined by an optical bridge for side-by-side comparison.")]),

    dict(title="Reviewer: Test firing, evidence handling and ballistic reports", minutes=8, refs=BAL_REFS, sections=[
        S("Test firing and the bullet recovery box",
          "To compare a suspect firearm with evidence, the examiner test-fires it and recovers undamaged bullets and cartridge cases. A bullet recovery box is a long box filled with cotton waste or similar material that slows the bullet gradually; a water tank is also commonly used. The recovered test specimens keep the marks needed for comparison."),
        S("Handling firearms evidence",
          "• Make the firearm safe first: unload it and record the position of the cartridges, including which chamber of a revolver was under the hammer.\n"
          "• Record the make, model, calibre and serial number.\n"
          "• Never insert anything into the barrel and avoid touching surfaces that may hold fingerprints or DNA.\n"
          "• Mark fired bullets on the base or nose, never on the bearing surface with the striations.\n"
          "• Mark cartridge cases inside or near the mouth, away from the firing pin and breech face marks.\n"
          "• Package each item separately, seal, label and record the chain of custody."),
        S("Ballistic report writing",
          "A ballistic report states: the case and request details; a description of each item received (firearm, bullets or slugs, cartridge cases or empty shells) with markings; the examinations performed (function test, class characteristics, test firing, microscopic comparison); the findings; and the conclusion (for example, that the questioned bullet was fired from the submitted firearm). Reports for legal proceedings are illustrated with photographs, macrophotographs and photomicrographs of the firearm and the matching marks."),
        S("Quick review",
          "• Bullet recovery box (cotton waste) or water tank for test firing.\n"
          "• Unload first; record cartridge positions and serial number.\n"
          "• Mark bullets on the base or nose; cases inside the mouth.\n"
          "• Reports include descriptions, examinations, findings, conclusion and photomicrographs."),
    ], terms=[("Test firing", "Firing a suspect weapon to obtain known specimens for comparison."),
              ("Bullet recovery box", "A cotton-filled box used to stop test bullets without damaging their marks."),
              ("Slug", "A fired bullet recovered as evidence."),
              ("Photomicrograph", "A photograph taken through a microscope, used to illustrate matching marks.")]),
]
