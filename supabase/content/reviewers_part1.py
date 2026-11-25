"""Syllabus-aligned reviewer lessons, part 1: Forensic 1 (Photography), Forensic 2 (Personal Identification),
Forensic 3 (Chemistry & Toxicology). Built from the WCC course syllabi. Each section may carry an
image (bundled asset in app/src/main/assets/lesson-media) or a YouTube video id (played in-app).
Credits for images are filled from media_credits.json by build_reviewers.py."""

PHOTO_REFS = [
    "Tad-awan, F. P. F. (2024). Forensic photography. Quezon City: Wiseman's Books.",
    "Villarba, W. H. (2022). Forensic photography for criminology students and practitioners with laboratory manual. Quezon City: Wiseman's Books.",
    "Cabrillas, T. L., & Abelleja, I. W. B. (2023). Forensic science 1: Forensic photography laboratory manual. Wiseman's Books Trading, Inc.",
]
PID_REFS = [
    "WCC Course Syllabus, Forensic 2: Personal Identification Techniques.",
    "Saferstein, R. Criminalistics: An Introduction to Forensic Science. Pearson.",
    "Supreme Court of the Philippines. Rule on DNA Evidence, A.M. No. 06-11-5-SC (2007).",
]
CHEM_REFS = [
    "Dascil, A. M., & Santos, R. Q. (2021). Forensic chemistry and toxicology: A worktext (3rd ed.). Wiseman's Book Trading Inc.",
    "Girard, J. E. (2023). Criminalistics: Forensic science, crime, and terrorism (5th ed.). Jones & Bartlett Learning.",
    "van Asten, A. (2023). Chemical analysis for forensic evidence. Elsevier.",
    "Republic Act No. 9165, Comprehensive Dangerous Drugs Act of 2002, as amended by R.A. No. 10640.",
]


def S(heading, body, **media):
    return {"heading": heading, "body": body, **media}


MODULES = {}

# =============================================================================================
# FORENSIC 1 — FORENSIC PHOTOGRAPHY
# =============================================================================================
MODULES["photography"] = [
    dict(title="Reviewer: Photography, its history and police uses", minutes=8, refs=PHOTO_REFS, sections=[
        S("Meaning of photography",
          "The word photography comes from two Greek words: phos or photos (light) and graphein (to write or draw). Photography is therefore \"drawing with light\": recording an image of an object through the action of light on a sensitive material (film) or an electronic sensor. The term was popularised in 1839 by the English scientist Sir John Herschel."),
        S("Important personalities and dates",
          "• Ibn al-Haytham (Alhazen), around 1000 AD: described the camera obscura (\"dark chamber\"), where light through a small hole projects an inverted image.\n"
          "• Joseph Nicéphore Niépce, around 1826 to 1827: made the oldest surviving camera photograph, \"View from the Window at Le Gras\", using bitumen on a pewter plate (heliography). The exposure took many hours.\n"
          "• Louis Jacques Mandé Daguerre, 1839: introduced the daguerreotype, a one-of-a-kind image on a silvered copper plate. 1839 is generally treated as the birth year of practical photography.\n"
          "• William Henry Fox Talbot, 1841: patented the calotype, the first negative-positive process, which allowed many prints from one negative.\n"
          "• Alphonse Bertillon, 1880s: standardised police \"mug shots\" (front and profile views) as part of his identification system.",
          image="photo_niepce.webp", caption="The oldest surviving camera photograph: Niépce's \"View from the Window at Le Gras\" (about 1826 to 1827)."),
        S("Importance and objectives in police work",
          "Photography gives investigators a permanent, accurate and complete record that can be studied long after a scene is released. Its objectives are to record the scene as it was found, to identify persons and objects, to preserve evidence that may change or deteriorate, and to present facts clearly in court.\n\n"
          "Uses in police work include: identification of persons (mug shots, rogues' gallery), crime scene documentation, recording of evidence before collection, documenting injuries, surveillance, traffic accident investigation, laboratory work (fingerprints, documents, firearms), and training."),
        S("Basic photographic principles",
          "Every photograph needs three things: light from the subject, a lens to focus that light, and a light-sensitive material (film or sensor) to record it. The camera controls how much light enters (aperture), for how long (shutter speed) and how sensitive the recording medium is (ISO). A sharp, correctly exposed, accurately coloured image is the goal of every forensic photograph."),
        S("Watch: photography in crime scene documentation",
          "This short lecture shows how photography fits into documenting a crime scene together with notes and sketches.",
          video="I8_LAZgN6wU", video_title="Documenting the Crime Scene with Photography (CSInetwork)"),
        S("Quick review",
          "• Photography = phos (light) + graphein (to write): drawing with light.\n"
          "• Herschel coined the term (1839); Niépce made the oldest surviving photograph (about 1826 to 1827); Daguerre introduced the daguerreotype (1839); Talbot introduced the negative-positive calotype (1841).\n"
          "• Police photography records, identifies, preserves and presents evidence."),
    ], terms=[("Photography", "The art and science of producing an image through the action of light on a sensitive material or sensor."),
              ("Camera obscura", "Latin for \"dark chamber\"; a darkened box or room where light through a small opening forms an inverted image."),
              ("Daguerreotype", "Early photographic process (1839) producing a single image on a silvered copper plate."),
              ("Calotype", "Talbot's negative-positive paper process that allowed multiple prints from one negative."),
              ("Mug shot", "Standard police identification photograph, usually front view and profile.")]),

    dict(title="Reviewer: Light and its properties", minutes=8, refs=PHOTO_REFS, sections=[
        S("Theory and nature of light",
          "Light is a form of electromagnetic energy that makes things visible. It travels in straight lines at about 300,000 km (186,000 miles) per second in a vacuum.\n\n"
          "• Corpuscular theory (Isaac Newton): light is made of tiny particles.\n"
          "• Wave theory (Christiaan Huygens): light travels as waves.\n"
          "• Quantum theory (Max Planck and Albert Einstein): light is emitted and absorbed in small packets of energy called photons. Modern physics treats light as having both wave and particle properties."),
        S("Electromagnetic radiation and the visible spectrum",
          "Light is one small part of the electromagnetic spectrum, which also includes radio waves, microwaves, infrared, ultraviolet, X-rays and gamma rays. The human eye sees only wavelengths of roughly 400 to 700 nanometres.\n\n"
          "The colours of the visible spectrum, from longest to shortest wavelength, are red, orange, yellow, green, blue, indigo and violet (ROYGBIV). Just beyond red is infrared (invisible, longer waves); just beyond violet is ultraviolet (invisible, shorter waves). Both are used in forensic photography to reveal things the eye cannot see.",
          image="photo_em_spectrum.webp", caption="The electromagnetic spectrum. Visible light is the narrow band between ultraviolet and infrared."),
        S("Properties of light and the RAT law",
          "When light strikes an object, one or more of three things happen. This is the RAT law:\n"
          "• Reflection: light bounces off the surface. We see most objects by reflected light.\n"
          "• Absorption: the object takes in the light and converts it to heat. A black object absorbs most light.\n"
          "• Transmission: light passes through the object, as with clear glass.\n\n"
          "Other properties include refraction (bending of light as it passes from one medium to another, which is how lenses work), diffraction (spreading around edges) and polarisation (vibration in one plane, used with filters to reduce glare)."),
        S("Qualities of light and their importance",
          "Light is described by its intensity (brightness), colour (wavelength or colour temperature), direction, and quality. Hard light from a small, direct source gives sharp shadows; soft or diffused light from a large source gives gentle shadows.\n\n"
          "Without light there is no photograph. Correct lighting shows the true shape, texture and colour of evidence."),
        S("Guidelines and lighting styles in forensic photography",
          "• Front lighting: shows the general appearance but flattens texture.\n"
          "• Side or oblique lighting: light from a low angle creates shadows that reveal impressions such as footwear marks, tool marks and indented writing.\n"
          "• Back lighting or transmitted light: light passes through the subject, useful for watermarks and paper.\n"
          "• Bounce or diffused flash: softens harsh shadows and reflections.\n"
          "Take several exposures with light from different directions when photographing impressions."),
        S("Quick review",
          "• Light travels about 300,000 km per second; visible light is about 400 to 700 nm.\n"
          "• ROYGBIV: red has the longest wavelength, violet the shortest.\n"
          "• RAT law: Reflection, Absorption, Transmission.\n"
          "• Oblique lighting reveals impressions and texture."),
    ], terms=[("RAT law", "When light strikes an object it is Reflected, Absorbed or Transmitted."),
              ("Refraction", "The bending of light as it passes between materials of different density; the basis of lenses."),
              ("Visible spectrum", "The part of the electromagnetic spectrum the eye can see, about 400 to 700 nanometres."),
              ("Infrared", "Invisible radiation with wavelengths longer than red light."),
              ("Ultraviolet", "Invisible radiation with wavelengths shorter than violet light."),
              ("Oblique lighting", "Light from a low, raking angle that casts shadows to reveal surface detail.")]),

    dict(title="Reviewer: The camera and its lenses", minutes=9, refs=PHOTO_REFS, sections=[
        S("Early forms of the camera",
          "The ancestor of every camera is the camera obscura, a dark box or room with a small hole on one side. Later a lens replaced the hole to make the image brighter and sharper, and a light-sensitive plate or film was placed where the image formed. The box camera, view camera and folding camera followed in the 1800s and early 1900s.",
          image="photo_camera_obscura.webp", caption="A camera obscura box: light through the lens forms an image that a mirror reflects onto a viewing screen."),
        S("Essential parts of a camera",
          "• Light-tight box (body): keeps out all light except what passes through the lens.\n"
          "• Lens: gathers and focuses light to form the image.\n"
          "• Diaphragm or aperture: an adjustable opening that controls how much light enters. It is expressed as an f-number; a smaller f-number means a larger opening.\n"
          "• Shutter: opens for a set time (shutter speed) to let light reach the film or sensor.\n"
          "• Film or image sensor: records the image.\n"
          "• Viewfinder: shows what will be photographed.\n"
          "• Focusing mechanism: moves the lens so the subject is sharp."),
        S("Main kinds of cameras",
          "• Point-and-shoot (compact) and box cameras: simple, fixed settings.\n"
          "• View camera: large format, bellows, used for highest detail.\n"
          "• Rangefinder: focuses with a separate viewing window.\n"
          "• Twin-lens reflex (TLR): one lens for viewing, one for taking the picture.\n"
          "• Single-lens reflex (SLR) and digital SLR (DSLR): a mirror and prism let the photographer see exactly through the taking lens; the mirror flips up when the picture is taken.\n"
          "• Mirrorless cameras and smartphone cameras: show the image electronically from the sensor.\n"
          "SLRs and DSLRs are preferred in forensic work because of interchangeable lenses and full manual control.",
          image="photo_slr.webp", caption="Cross-section of a single-lens reflex camera: 1 lens, 2 reflex mirror, 3 focal-plane shutter, 4 film or sensor, 5 focusing screen, 6 viewing hood. The mirror flips up when the picture is taken."),
        S("Watch: how a DSLR works",
          "An animation of the light path through the lens, mirror, shutter and sensor of a DSLR.",
          video="W34sLbAsFhM", video_title="How Does a DSLR Camera Work (Animations Xplaned)"),
        S("Types of lenses",
          "• Convex (converging or positive) lens: thicker at the centre; brings light rays together to form a real image. Camera lenses are converging.\n"
          "• Concave (diverging or negative) lens: thinner at the centre; spreads light rays apart.\n\n"
          "Camera lenses by focal length:\n"
          "• Normal lens: angle of view similar to the human eye (about 50 mm on a 35 mm camera).\n"
          "• Wide-angle lens: shorter focal length, wider view; useful for overall scene photographs in small rooms.\n"
          "• Telephoto lens: longer focal length, magnifies distant subjects.\n"
          "• Zoom lens: variable focal length.\n"
          "• Macro lens: focuses very close for life-size images of small evidence."),
        S("Lens aberrations and their correction",
          "An aberration is a defect that prevents a lens from forming a perfect image.\n"
          "• Spherical aberration: rays through the edge and centre focus at different points, giving a soft image.\n"
          "• Chromatic aberration: different colours focus at different points, causing colour fringes.\n"
          "• Astigmatism: vertical and horizontal lines cannot be focused at the same time.\n"
          "• Coma: off-centre points look like comet-shaped blurs.\n"
          "• Curvature of field: a flat subject focuses on a curved surface; centre sharp, edges soft.\n"
          "• Distortion: straight lines bend outward (barrel) or inward (pincushion).\n\n"
          "Corrected lenses: an achromatic lens corrects chromatic aberration for two colours; an apochromatic lens corrects it for three colours; an anastigmat corrects astigmatism. Stopping down the aperture also reduces several aberrations.",
          image="photo_chromatic.webp", caption="Chromatic aberration: a simple lens brings different colours to focus at different distances."),
        S("Quick review",
          "• Camera essentials: light-tight box, lens, aperture, shutter, film or sensor, viewfinder.\n"
          "• Smaller f-number = bigger opening = more light, shallower depth of field.\n"
          "• Camera lenses are convex (converging).\n"
          "• Six aberrations: spherical, chromatic, astigmatism, coma, curvature of field, distortion.\n"
          "• Achromatic and apochromatic lenses correct colour; anastigmats correct astigmatism."),
    ], terms=[("Aperture", "The adjustable lens opening that controls the amount of light, expressed as an f-number."),
              ("Shutter speed", "The length of time the shutter stays open to expose the film or sensor."),
              ("Focal length", "Distance from the optical centre of the lens to the image plane when focused at infinity."),
              ("Depth of field", "The range of distance in front of and behind the subject that appears sharp."),
              ("SLR / DSLR", "Single-lens reflex camera (film or digital) that lets the photographer view through the taking lens."),
              ("Aberration", "A lens defect that prevents a perfectly sharp, accurate image.")]),

    dict(title="Reviewer: Film, memory cards and printing", minutes=8, refs=PHOTO_REFS, sections=[
        S("Photographic film",
          "Film has three main layers: the base (clear plastic support), the emulsion (light-sensitive silver halide crystals suspended in gelatin) and an anti-halation backing that stops light from reflecting back through the film.\n\n"
          "Characteristics of film:\n"
          "• Speed (ISO): sensitivity to light. Fast film (high ISO) needs less light but shows more grain.\n"
          "• Grain: the visible clumps of silver that form the image.\n"
          "• Colour sensitivity: blue-sensitive (ordinary) film responds only to blue and ultraviolet; orthochromatic film responds to blue and green but not red; panchromatic film responds to all visible colours; infrared film also records infrared.\n"
          "• Contrast and latitude: how the film handles bright and dark areas."),
        S("Digital memory cards and the evolution of the SD card",
          "Digital cameras store images on memory cards. The Secure Digital (SD) card was introduced in 1999. Later formats raised the capacity: SDHC (up to 32 GB), SDXC (up to 2 TB) and SDUC (up to 128 TB). Smaller versions, miniSD and microSD, are used in phones and compact devices.\n\n"
          "For evidence, the original card or an exact copy of the original files must be preserved. Working copies are used for enhancement so the original is never altered.",
          image="photo_sd_cards.webp", caption="SD, miniSD and microSD cards."),
        S("Black-and-white processing",
          "After exposure, film holds an invisible latent image. Processing makes it visible and permanent:\n"
          "1. Developer: changes exposed silver halide into black metallic silver. Common developing agents are metol and hydroquinone, with sodium sulfite (preservative), sodium carbonate (accelerator) and potassium bromide (restrainer).\n"
          "2. Stop bath: dilute acetic acid stops development.\n"
          "3. Fixer (hypo, sodium thiosulfate): removes the unexposed silver halide so the image is no longer light-sensitive.\n"
          "4. Washing: removes the chemicals so the image lasts.\n"
          "5. Drying.\n"
          "The result is a negative: light areas of the subject appear dark."),
        S("Photographic paper and printing",
          "Photographic paper carries a light-sensitive emulsion similar to film. Papers differ in base (resin-coated or fibre), surface (glossy, matte) and contrast grade.\n\n"
          "Printing makes a positive from a negative. In contact printing the negative is placed directly on the paper (print is the same size). In projection printing an enlarger projects the negative onto the paper, allowing enlargement. The paper is then developed, stopped, fixed, washed and dried like film. Modern laboratories print digitally with inkjet, laser or dye-sublimation printers."),
        S("Quick review",
          "• Film layers: base, emulsion (silver halide in gelatin), anti-halation backing.\n"
          "• Orthochromatic = not sensitive to red; panchromatic = sensitive to all colours.\n"
          "• Processing order: developer → stop bath → fixer → wash → dry.\n"
          "• Fixer (hypo) is sodium thiosulfate; stop bath is acetic acid.\n"
          "• Keep the original digital file untouched; work on copies."),
    ], terms=[("Emulsion", "The light-sensitive layer of film or paper: silver halide crystals in gelatin."),
              ("Latent image", "The invisible image formed on exposed film before development."),
              ("Panchromatic film", "Film sensitive to all colours of visible light."),
              ("Orthochromatic film", "Film sensitive to blue and green light but not red."),
              ("Fixer (hypo)", "Sodium thiosulfate solution that removes unexposed silver halide."),
              ("Enlarger", "Projector used to make enlarged prints from negatives.")]),

    dict(title="Reviewer: Special photography and crime scene photography", minutes=9, refs=PHOTO_REFS, sections=[
        S("Types of forensic photography",
          "• Photomicrography: photographing a small object through a microscope to show it enlarged (for example fibres, hair, tool marks).\n"
          "• Microphotography: the opposite; making a very small photograph of a large object, such as microfilm of documents.\n"
          "• Macrophotography (close-up photography): photographing small objects at life size or larger with a macro lens, without a microscope.\n"
          "• Ultraviolet photography: reveals fluorescence of body fluids, some inks and document alterations.\n"
          "• Infrared photography: reveals erased or obliterated writing, differences in inks, and details hidden under soot or dark stains.\n"
          "• Aerial photography and panoramic photography: show the wider area of a scene."),
        S("Basic photographic equipment at the scene",
          "A camera with manual controls (usually a DSLR), normal, wide-angle and macro lenses, an external flash with an off-camera cord for oblique lighting, a tripod, scales (rulers), evidence markers, a photo log, spare batteries and memory cards, and a grey card or colour chart for accurate colour."),
        S("Advantages of digital cameras",
          "Instant review of images at the scene, no film processing, easy copying and transmission, recorded date and time (metadata), and low cost per image. The main concern is that digital files can be edited, so agencies keep the original files, record who handled them, and use only documented, reversible enhancement on copies."),
        S("Guidelines in photographing a crime scene",
          "1. Photograph the scene before anything is moved, touched or collected.\n"
          "2. Work from the general to the specific: overall, mid-range, then close-up photographs.\n"
          "3. Take close-ups first without a scale, then with a scale, with the camera perpendicular to the surface.\n"
          "4. Record every photograph in a photo log (number, time, description, camera position).\n"
          "5. Do not include investigators, equipment or vehicles in the frame.\n"
          "6. Photograph entrances, exits, victims, injuries and every item of evidence.\n"
          "7. Never delete images, even poor ones; gaps raise questions in court."),
        S("Watch: crime scene photography in practice",
          "A crime scene technician explains how scenes and evidence are photographed.",
          video="ihj4y_EeLRs", video_title="Forensic Files: Crime Scene Photography (City of Chandler Police Department)"),
        S("Quick review",
          "• Photomicrography = through a microscope (enlarged); microphotography = tiny photographs (microfilm).\n"
          "• UV shows fluorescence; IR shows erased or obliterated writing.\n"
          "• Overall → mid-range → close-up; without scale, then with scale.\n"
          "• Keep a photo log and never delete images."),
    ], terms=[("Photomicrography", "Photography through a microscope to record an enlarged image of a small object."),
              ("Microphotography", "Making very small photographs, such as microfilm copies of documents."),
              ("Macrophotography", "Close-up photography at life size or larger without a microscope."),
              ("Photo log", "A written record of every photograph taken at a scene."),
              ("Metadata", "Information stored with a digital image, such as date, time and camera settings.")]),

    dict(title="Reviewer: Photographs as evidence", minutes=7, refs=PHOTO_REFS + ["Supreme Court of the Philippines. Rules on Electronic Evidence, A.M. No. 01-7-01-SC (2001)."], sections=[
        S("Theories on photographic evidence",
          "• Pictorial testimony theory: the photograph illustrates a witness's testimony. A witness who knows the scene testifies that the photograph is a fair and accurate representation of what they saw.\n"
          "• Silent witness theory: the photograph \"speaks for itself\". It is admitted on proof that the process that produced it is reliable, as with an automatic surveillance camera, even when no person saw the event."),
        S("Requirements for admissibility",
          "A photograph is generally admitted when it is:\n"
          "• Relevant to a fact in issue.\n"
          "• Authenticated: shown to be a faithful and accurate representation of the subject, by the photographer or another competent witness.\n"
          "• Not misleading or unduly prejudicial; gruesome images must still help the court understand a fact.\n\n"
          "Under the Philippine Rules on Electronic Evidence, photographic, audio and video evidence of events, acts or transactions is admissible if it is shown, presented or displayed to the court and identified, explained or authenticated by the person who made the recording or by another competent witness."),
        S("Guidelines for admissible digital photographs",
          "• Preserve the original files and store them securely.\n"
          "• Keep a chain of custody: who took, copied, transferred and stored each file.\n"
          "• Use a photo log and keep the image numbering complete.\n"
          "• Enhance only copies, using documented methods that do not add or remove content (for example brightness and contrast). Record every step.\n"
          "• The photographer should be ready to testify on the camera, settings, lighting and scale used."),
        S("Quick review",
          "• Pictorial testimony: a witness vouches for the photo. Silent witness: the reliable process vouches for it.\n"
          "• Admissibility: relevant, authenticated, not misleading.\n"
          "• Keep originals, a chain of custody and a photo log."),
    ], terms=[("Pictorial testimony theory", "A photograph is admitted to illustrate a witness's testimony about what they observed."),
              ("Silent witness theory", "A photograph is admitted on proof of the reliability of the process that produced it."),
              ("Authentication", "Proof that evidence is what it claims to be."),
              ("Chain of custody", "The documented, unbroken record of everyone who handled an item of evidence.")]),
]

# =============================================================================================
# FORENSIC 2 — PERSONAL IDENTIFICATION TECHNIQUES
# =============================================================================================
MODULES["personal-identification"] = [
    dict(title="Reviewer: Introduction and history of personal identification", minutes=8, refs=PID_REFS, sections=[
        S("What personal identification is",
          "Personal identification is the process of establishing the identity of a person, living or dead, by comparing characteristics with known records. In criminal investigation it is used to identify suspects, victims, witnesses and unknown remains, to link a person to a scene, and to prove identity in court."),
        S("Classification of identification methods",
          "• Anthropometry: body measurements (Bertillon system).\n"
          "• Dactyloscopy: fingerprints, palm prints and footprints.\n"
          "• Photography and facial identification: mug shots, portrait parlé, facial comparison.\n"
          "• Scars, marks and tattoos (SMT).\n"
          "• DNA profiling.\n"
          "• Forensic odontology: teeth and dental records.\n"
          "• Biometrics: iris, retina, voice, face and other automated systems.\n"
          "• Others: handwriting, personal effects, documents and visual recognition by witnesses."),
        S("The Bertillon system",
          "Alphonse Bertillon, a clerk of the Paris police, introduced anthropometry in the early 1880s. Repeat offenders were identified by a set of precise body measurements (such as head length, head breadth, length of the middle finger, foot and forearm), combined with standard photographs and a systematic verbal description called the portrait parlé. Bertillon is often called the father of criminal identification.",
          image="pid_bertillon_card.webp", caption="Bertillon's own anthropometric record card, with measurements and front and profile photographs."),
        S("The development of fingerprint science",
          "• Johannes Purkinje (1823): described nine fingerprint patterns.\n"
          "• Sir William Herschel (1858, India): used handprints and fingerprints on contracts to prevent impersonation.\n"
          "• Henry Faulds (1880): proposed using fingerprints left at crime scenes to identify offenders.\n"
          "• Sir Francis Galton (1892): published \"Finger Prints\", showing that fingerprints are individual and permanent. Ridge details are still called Galton details.\n"
          "• Juan Vucetich (1891 to 1892, Argentina): developed a classification system; his office solved the Rojas murder case, the first criminal case solved by fingerprints.\n"
          "• Sir Edward Henry (1897 to 1901): developed the Henry Classification System, adopted by Scotland Yard in 1901 and later worldwide.\n"
          "• The Will West case (1903, Leavenworth, USA): two prisoners with nearly identical measurements and appearance were told apart by fingerprints, showing the weakness of anthropometry."),
        S("Quick review",
          "• Bertillon: anthropometry and portrait parlé, father of criminal identification.\n"
          "• Galton: individuality and permanence of fingerprints. Henry: classification system (1901, Scotland Yard). Vucetich: first criminal case (Rojas, 1892).\n"
          "• The Will West case ended reliance on anthropometry."),
    ], terms=[("Personal identification", "Establishing the identity of a person by comparison with known records."),
              ("Anthropometry", "Identification by systematic body measurements (Bertillon system)."),
              ("Dactyloscopy", "The science of fingerprint identification."),
              ("Portrait parlé", "\"Spoken picture\": Bertillon's systematic verbal description of a person's features."),
              ("Henry Classification System", "Fingerprint filing system by Sir Edward Henry, adopted by Scotland Yard in 1901.")]),

    dict(title="Reviewer: Fingerprint principles, patterns and minutiae", minutes=9, refs=PID_REFS, sections=[
        S("Fundamental principles",
          "• Individuality: no two fingers have identical ridge detail, not even those of identical twins.\n"
          "• Permanence: ridge patterns form before birth and do not change during life (except by deep scars) until decomposition.\n"
          "• Classifiability: patterns fall into recognisable types, so records can be filed and searched systematically."),
        S("The three basic patterns",
          "• Loop (about 60 to 65 percent of all patterns): ridges enter from one side, recurve, and exit on the same side. A loop has one delta and one core. An ulnar loop opens toward the little finger; a radial loop opens toward the thumb.\n"
          "• Whorl (about 30 to 35 percent): circular or spiral ridges with at least two deltas. Types: plain whorl, central pocket loop whorl, double loop whorl and accidental whorl.\n"
          "• Arch (about 5 percent): ridges enter on one side and flow out the other with a rise in the centre. A plain arch has no delta; a tented arch has a sharp upthrust or angle.",
          image="pid_patterns.webp", caption="Arch, loop and whorl: the three basic fingerprint pattern types."),
        S("Focal points, ridge counting and tracing",
          "The delta is the point on a ridge nearest the divergence of two type lines; the core is the approximate centre of the pattern. In loops, the ridge count is the number of ridges crossing an imaginary line between the delta and the core. In whorls, ridge tracing follows the ridge from the left delta toward the right delta and classifies the whorl as inner, meeting or outer."),
        S("Minutiae (ridge characteristics)",
          "Identification depends on minutiae, the small details of individual ridges:\n"
          "• Ridge ending: a ridge that stops.\n"
          "• Bifurcation: a ridge that splits into two.\n"
          "• Dot or island: a very short ridge.\n"
          "• Short ridge.\n"
          "• Enclosure or lake: a ridge that splits and rejoins.\n"
          "• Spur, bridge and crossover.\n"
          "Examiners compare the type, position and relationship of minutiae between a questioned print and a known print.",
          image="pid_minutiae.webp", caption="Common fingerprint minutiae."),
        S("Watch: interpreting fingerprint patterns",
          "A walkthrough of arches, loops and whorls and their sub-types.",
          video="CmNs-VUldrI", video_title="Fingerprint Classification Part 1: Pattern Interpretation (CSInetwork)"),
        S("Quick review",
          "• Principles: individuality, permanence, classifiability.\n"
          "• Loops ~60 to 65% (1 delta), whorls ~30 to 35% (2 deltas), arches ~5% (plain arch has no delta).\n"
          "• Ulnar loop opens toward the little finger; radial toward the thumb.\n"
          "• Minutiae: ridge ending, bifurcation, dot, short ridge, enclosure."),
    ], terms=[("Delta", "The triangular point where ridges diverge, nearest the type lines."),
              ("Core", "The approximate centre of a fingerprint pattern."),
              ("Ulnar loop", "A loop that opens toward the little finger (ulna side)."),
              ("Radial loop", "A loop that opens toward the thumb (radius side)."),
              ("Minutiae", "Individual ridge characteristics such as endings and bifurcations, used to identify a print."),
              ("Ridge count", "Number of ridges crossing a line between the delta and the core of a loop.")]),

    dict(title="Reviewer: Taking fingerprints and kinds of prints", minutes=7, refs=PID_REFS, sections=[
        S("Fingerprint taking procedure",
          "1. Clean and dry the subject's fingers; ink a glass slab or pad thinly and evenly with a roller.\n"
          "2. Stand behind and to the left of the subject's right hand; control the finger, not the whole hand.\n"
          "3. Roll each finger from nail edge to nail edge so the full pattern and both deltas are recorded. Thumbs are rolled toward the body; fingers are rolled away from the body.\n"
          "4. Take the plain (flat) impressions of the four fingers together and the thumbs at the bottom of the card. These check that the rolled prints are in the correct boxes.\n"
          "5. Complete the personal data, sign, and have the subject sign."),
        S("The ten-print card",
          "The ten-print card records rolled impressions of all ten fingers in numbered boxes (right thumb is finger 1, right little finger is 5, left thumb is 6, left little finger is 10) and plain impressions at the bottom, together with the subject's personal information. Many agencies now use livescan devices that capture the same impressions digitally and send them to an Automated Fingerprint Identification System (AFIS)."),
        S("Kinds of prints found at scenes",
          "• Latent prints: invisible prints left by sweat and oils; they must be developed. Powders are used on smooth, non-porous surfaces; ninhydrin (reacts with amino acids, turns purple) on paper and other porous surfaces; cyanoacrylate (super glue) fuming on non-porous items such as plastic and metal; iodine fuming for fresh prints on paper.\n"
          "• Patent or visible prints: made by fingers coated with blood, ink, paint or dirt. They are photographed first.\n"
          "• Plastic prints: three-dimensional impressions in soft material such as wax, putty, soap or wet paint. They are photographed with oblique lighting and may be cast."),
        S("Quick review",
          "• Roll nail to nail; thumbs toward the body, fingers away from the body.\n"
          "• Plain impressions verify the order of rolled prints.\n"
          "• Latent = invisible; patent = visible; plastic = impressed.\n"
          "• Ninhydrin for porous surfaces; cyanoacrylate for non-porous."),
    ], terms=[("Ten-print card", "Card recording rolled and plain impressions of all ten fingers with personal data."),
              ("Latent print", "An invisible fingerprint that must be developed to be seen."),
              ("Patent print", "A visible print made by a finger coated with a substance such as blood or ink."),
              ("Plastic print", "A three-dimensional print impressed in a soft material."),
              ("AFIS", "Automated Fingerprint Identification System: a computer database that searches fingerprints.")]),

    dict(title="Reviewer: Facial identification, DNA and biometrics", minutes=9, refs=PID_REFS, sections=[
        S("Facial identification and portrait parlé",
          "Facial identification compares a person's face with photographs or descriptions. The portrait parlé (\"spoken picture\") is Bertillon's method of describing a face in standard terms: forehead, nose, ears, lips, chin, eyebrows, eyes and general shape. Witness descriptions, cartographic sketches and computer-generated composites build on the same idea.\n\n"
          "Scars, marks and tattoos (SMT) and other physical characteristics such as height, build, complexion, hair, deformities and amputations are recorded because they are distinctive and help narrow down identity.",
          image="pid_portrait_parle.webp", caption="Bertillon's synoptic chart of facial features used to teach the portrait parlé."),
        S("DNA basics",
          "Deoxyribonucleic acid (DNA) is the genetic material in almost every cell. Except for identical twins, each person's DNA profile is unique. Sources at a scene include blood, saliva, semen, hair roots, skin cells (touch DNA), bones and teeth.\n\n"
          "Sir Alec Jeffreys developed DNA fingerprinting in 1984. Modern laboratories amplify DNA with the polymerase chain reaction (PCR) and type short tandem repeats (STRs) to produce a profile. Mitochondrial DNA, inherited from the mother, is used for hair shafts, old bones and degraded samples."),
        S("DNA collection, uses and limitations",
          "Collection: wear gloves and a mask, change gloves between items, air-dry wet stains, package biological evidence in paper (not plastic, which traps moisture and causes decay), label, and keep cool. Collect reference samples (buccal swabs) from victims and suspects.\n\n"
          "Uses: identifying offenders and victims, linking crimes, paternity and kinship, identifying disaster victims, and freeing the wrongly convicted.\n\n"
          "Limitations: contamination, degradation by heat and moisture, mixtures from several people, identical twins, and the fact that finding someone's DNA shows presence or contact, not necessarily guilt.",
          video="jaRKLSWS-d0", video_title="Forensics expert explains how DNA is used in criminal investigations"),
        S("Biometrics",
          "Biometrics identifies people automatically by physiological or behavioural traits:\n"
          "• Iris scan: the patterns of the coloured ring of the eye.\n"
          "• Retina scan: the pattern of blood vessels at the back of the eye.\n"
          "• Face recognition, fingerprint and palm scanners, hand geometry.\n"
          "• Voice recognition and signature dynamics (behavioural traits).\n"
          "A system can verify (one-to-one: is this person who they claim to be?) or identify (one-to-many: who is this person?). Accuracy, privacy, consent and data protection must be considered.",
          image="pid_iris.webp", caption="How an iris recognition system works: capture, segmentation, encoding and matching."),
        S("Quick review",
          "• Portrait parlé = systematic verbal description of the face.\n"
          "• DNA is unique except for identical twins; package biological evidence in paper.\n"
          "• PCR amplifies DNA; STRs are typed for profiles; mtDNA is maternal.\n"
          "• Iris = coloured ring patterns; retina = blood vessel pattern."),
    ], terms=[("SMT", "Scars, marks and tattoos recorded for identification."),
              ("DNA profile", "The pattern of genetic markers (usually STRs) that distinguishes one person from another."),
              ("PCR", "Polymerase chain reaction: a technique that copies small amounts of DNA for analysis."),
              ("Mitochondrial DNA", "DNA in the mitochondria, inherited from the mother; useful for degraded samples."),
              ("Biometrics", "Automated identification by physiological or behavioural characteristics.")]),

    dict(title="Reviewer: Reliability, law, ethics and identification reports", minutes=8, refs=PID_REFS + ["Republic Act No. 10173, Data Privacy Act of 2012."], sections=[
        S("Errors and reliability in identification",
          "Every method can fail when used carelessly. Common causes of error are poor-quality or partial samples, contamination, examiner bias, pressure to reach a result, and over-reliance on database \"hits\". A well-known example is the 2004 Madrid train bombing case, in which a latent print was wrongly attributed to an American lawyer, Brandon Mayfield, before the error was found.\n\n"
          "Safeguards include documented procedures, independent verification by a second examiner, blind review, proficiency testing, and reporting limitations honestly."),
        S("Comparing identification methods",
          "• Fingerprints: highly individual and permanent, inexpensive, large databases; partial or smudged prints limit conclusions.\n"
          "• DNA: highly discriminating, works on small traces; slower, costly, sensitive to contamination; does not separate identical twins.\n"
          "• Facial identification and eyewitnesses: quick but the least reliable; appearance changes and memory errors are common.\n"
          "• Biometrics: fast and automated; depends on system quality and raises privacy concerns."),
        S("Legal basis and rights",
          "• The Rule on DNA Evidence (A.M. No. 06-11-5-SC, 2007) governs the collection, preservation and presentation of DNA evidence and the factors courts consider in weighing it.\n"
          "• Identification evidence must be properly collected, documented and authenticated, with an intact chain of custody, to be admitted.\n"
          "• The right against self-incrimination protects against compelled testimony. Taking fingerprints, photographs or physical samples is generally treated as non-testimonial, but it must still follow lawful procedure.\n"
          "• The Data Privacy Act of 2012 (R.A. 10173) treats biometric and genetic data as sensitive personal information that must be processed lawfully and protected."),
        S("Ethics and misidentification",
          "Investigators must respect the dignity and privacy of every person, obtain samples humanely and lawfully, keep records confidential, avoid jumping to conclusions, and correct errors promptly. A misidentification can convict the innocent and let the guilty go free."),
        S("Writing identification reports and court presentation",
          "A report should state: the request and case details; the items received and how they were marked; the methods and equipment used; the observations; the conclusion and its limitations; and the examiner's name and signature. In court, explain methods in plain language, use enlarged charts to show points of comparison, stay within your expertise, and remain objective."),
        S("Quick review",
          "• Safeguards: verification, documentation, proficiency testing.\n"
          "• Rule on DNA Evidence: A.M. No. 06-11-5-SC (2007).\n"
          "• Biometric and genetic data are sensitive personal information (R.A. 10173).\n"
          "• A good report states items, methods, findings, conclusion and limitations."),
    ], terms=[("Misidentification", "Wrongly concluding that evidence came from a particular person."),
              ("Verification", "Independent re-examination of a conclusion by a second qualified examiner."),
              ("Sensitive personal information", "Data such as genetic and biometric information given special protection under the Data Privacy Act."),
              ("Rule on DNA Evidence", "Supreme Court rule (A.M. No. 06-11-5-SC) on the use of DNA evidence in Philippine courts.")]),
]

# =============================================================================================
# FORENSIC 3 — FORENSIC CHEMISTRY AND TOXICOLOGY
# =============================================================================================
MODULES["chemistry-toxicology"] = [
    dict(title="Reviewer: Introduction, laboratory safety and evidence handling", minutes=8, refs=CHEM_REFS, sections=[
        S("Nature and scope",
          "Forensic chemistry applies chemistry to legal questions: identifying drugs, poisons, explosives, fire accelerants, paints, inks and other substances. Forensic toxicology is the study of poisons and drugs in the body, their effects, and their role in death, injury or impaired behaviour.\n\n"
          "Their objectives are to identify the substance, measure how much is present, interpret what it did to the person, and present the findings to the court. Both play a central role in drug cases, poisoning, drunk or drugged driving, arson and explosions."),
        S("Pioneers",
          "• Paracelsus (1493 to 1541): \"All things are poison and nothing is without poison; only the dose makes a thing not a poison.\"\n"
          "• Mathieu Orfila (1787 to 1853): published a treatise on poisons in 1814; known as the father of toxicology.\n"
          "• James Marsh (1836): developed the Marsh test for detecting arsenic."),
        S("Laboratory rules and hazards",
          "• Wear personal protective equipment (PPE): lab coat, gloves, safety goggles, and a mask or respirator when needed.\n"
          "• Use a fume hood for volatile or toxic chemicals.\n"
          "• Never eat, drink or smell chemicals directly; waft vapours toward the nose only when instructed.\n"
          "• Label every container; read the Safety Data Sheet (SDS) before use.\n"
          "• Add acid to water, never water to acid.\n"
          "• Know the location of the eyewash, safety shower, fire extinguisher and spill kit.\n"
          "• Dispose of waste in the proper containers.",
          image="chem_ghs.webp", caption="GHS hazard pictograms found on chemical labels and Safety Data Sheets."),
        S("Contamination prevention and chain of custody",
          "Change gloves between items, use clean or disposable tools, package each item separately, and keep evidence away from reference standards. The chain of custody is the written record of every person who received, held, examined or transferred an item, with dates, times and purpose. Any unexplained gap can make the evidence inadmissible, especially in drug cases.",
          image="chem_evidence_bag.webp", caption="Marking and sealing an evidence bag during crime scene training."),
        S("Quick review",
          "• Forensic chemistry identifies substances; forensic toxicology studies poisons and drugs in the body.\n"
          "• Paracelsus: the dose makes the poison. Orfila: father of toxicology. Marsh: arsenic test.\n"
          "• PPE, fume hood, labels, SDS; acid into water.\n"
          "• Chain of custody must be complete and documented."),
    ], terms=[("Forensic chemistry", "The application of chemistry to legal investigations."),
              ("Forensic toxicology", "The study of poisons and drugs in the body and their legal significance."),
              ("PPE", "Personal protective equipment such as gloves, lab coat and goggles."),
              ("SDS", "Safety Data Sheet describing a chemical's hazards and safe handling."),
              ("Chain of custody", "Documented record of the handling of evidence from collection to court.")]),

    dict(title="Reviewer: Collection, preservation and preliminary examination", minutes=9, refs=CHEM_REFS, sections=[
        S("Collection and preservation of chemical evidence",
          "• Solids and powders: in clean, sealed containers or evidence bags; never mix items.\n"
          "• Liquids: in leak-proof, clean glass or suitable plastic containers.\n"
          "• Volatile substances (such as fire accelerants): in airtight metal cans or special bags so vapours do not escape.\n"
          "• Biological specimens: refrigerate. Blood for alcohol is collected in tubes with sodium fluoride (preservative) and an anticoagulant such as potassium oxalate.\n"
          "• Postmortem specimens commonly include peripheral (femoral) blood, urine, vitreous humour, stomach contents, liver and hair.\n"
          "Every package is sealed, labelled (case number, description, date, time, collector) and listed in the chain of custody before submission to the laboratory."),
        S("Basic laboratory procedures",
          "• Measurement: use calibrated balances; read volumetric glassware at eye level at the bottom of the meniscus.\n"
          "• Solution preparation: prepare and label solutions by concentration (for example percent weight per volume or molarity).\n"
          "• Basic reactions: observe colour change, precipitate, gas formation or temperature change.\n"
          "• Documentation: record every observation in ink in a bound laboratory notebook, with date and signature. Results that are not documented cannot be defended in court."),
        S("Preliminary (presumptive) examination",
          "Physical properties such as colour, form, odour (never sniffed directly), solubility, melting point and density give the first clues. Presumptive tests are quick screening tests that suggest, but do not prove, the identity of a substance:\n"
          "• Marquis reagent: purple to violet for opiates such as heroin and morphine; orange to brown for amphetamine and methamphetamine.\n"
          "• Simon's test: blue for secondary amines such as methamphetamine and MDMA.\n"
          "• Scott test (cobalt thiocyanate): blue for cocaine.\n"
          "• Duquenois-Levine test: purple in the chloroform layer for marijuana.\n"
          "• Thin-layer chromatography (TLC): separates the components of a mixture for comparison with standards.",
          image="chem_marquis.webp", caption="A positive Marquis reagent presumptive test."),
        S("Confirmatory testing",
          "Because presumptive tests can give false positives, results are confirmed by specific instrumental methods such as gas chromatography-mass spectrometry (GC-MS), regarded as the gold standard, and infrared spectroscopy (FTIR). Only confirmed results are reported as the identity of a substance.",
          image="chem_tlc.webp", caption="A thin-layer chromatography plate viewed under ultraviolet light."),
        S("Watch: testing procedures in forensic toxicology",
          "An overview of how toxicology laboratories screen and confirm drugs and poisons.",
          video="lBHZyhzttQo", video_title="Forensic Toxicology Part 2: Testing Procedures and Techniques"),
        S("Quick review",
          "• Volatiles in airtight cans; biological samples refrigerated; blood with sodium fluoride.\n"
          "• Presumptive tests screen; confirmatory tests (GC-MS) identify.\n"
          "• Marquis: purple = opiates, orange-brown = amphetamines. Scott: blue = cocaine. Simon's: blue = methamphetamine. Duquenois-Levine: purple = marijuana."),
    ], terms=[("Presumptive test", "A screening test that indicates a substance may be present."),
              ("Confirmatory test", "A specific test that positively identifies a substance, such as GC-MS."),
              ("GC-MS", "Gas chromatography-mass spectrometry; separates and identifies compounds."),
              ("TLC", "Thin-layer chromatography; separates mixtures on a coated plate."),
              ("Vitreous humour", "Fluid in the eye, useful in postmortem toxicology.")]),

    dict(title="Reviewer: Forensic toxicology, poisons and dose", minutes=9, refs=CHEM_REFS, sections=[
        S("Toxic substances and poisoning",
          "A poison is any substance that, when introduced into the body in sufficient quantity, causes illness, injury or death. Poisoning may be accidental, suicidal, homicidal, or occupational and environmental. Branches of forensic toxicology include postmortem toxicology, human performance toxicology (such as impaired driving) and forensic drug testing.",
          image="chem_paracelsus.webp", caption="Paracelsus, who taught that the dose makes the poison."),
        S("Routes of exposure",
          "• Ingestion: through the mouth.\n"
          "• Inhalation: breathing in gases, vapours or smoke; the fastest route into the blood after injection.\n"
          "• Injection: into a vein, muscle or under the skin.\n"
          "• Dermal absorption: through the skin or mucous membranes.\n"
          "The effect also depends on the dose, frequency, age, body weight, health, tolerance and the presence of other substances."),
        S("The dose-response relationship",
          "As the dose increases, the effect increases. Toxicity is compared with the LD50: the dose that kills 50 percent of a test population. The lower the LD50, the more toxic the substance. Acute poisoning results from a single large exposure; chronic poisoning from repeated small exposures over time.",
          image="chem_dose_response.webp", caption="A dose-response curve. The LD50 is the dose at which half of the test population dies."),
        S("Classification and effects of toxic substances",
          "• Corrosives: strong acids (sulfuric, nitric, hydrochloric) and alkalis (sodium hydroxide) destroy tissue on contact.\n"
          "• Irritants: metallic poisons such as arsenic, mercury and lead, and some plant and animal poisons, inflame tissues.\n"
          "• Neurotics: act on the nervous system, such as narcotics, sedatives and deliriants.\n"
          "• Asphyxiants: prevent the body from using oxygen. Carbon monoxide binds to haemoglobin (carboxyhaemoglobin, cherry-red discolouration); cyanide blocks cellular respiration and may give a bitter almond odour.\n"
          "• Others: alcohol, drugs, pesticides and toxic gases."),
        S("Watch: what forensic toxicology is",
          "An introduction to the work of a forensic toxicologist.",
          video="Mt9YJx_TfHI", video_title="Forensic Toxicology Part 1: What is it?"),
        S("Quick review",
          "• Routes: ingestion, inhalation, injection, dermal absorption.\n"
          "• Lower LD50 = more toxic.\n"
          "• Corrosives destroy tissue; irritants inflame; neurotics affect nerves; asphyxiants block oxygen use.\n"
          "• Carbon monoxide: cherry-red discolouration. Cyanide: bitter almond odour."),
    ], terms=[("Poison", "A substance that causes harm or death when it enters the body in sufficient quantity."),
              ("LD50", "The dose that is lethal to 50 percent of a test population."),
              ("Acute poisoning", "Harm from a single large exposure."),
              ("Chronic poisoning", "Harm from repeated small exposures over time."),
              ("Asphyxiant", "A substance that prevents the body from using oxygen, such as carbon monoxide or cyanide.")]),

    dict(title="Reviewer: Drugs, controlled substances and alcohol", minutes=9, refs=CHEM_REFS + ["Republic Act No. 10586, Anti-Drunk and Drugged Driving Act of 2013."], sections=[
        S("Classification of drugs by effect",
          "• Stimulants: speed up the central nervous system, such as methamphetamine (\"shabu\"), amphetamine, cocaine and MDMA (ecstasy).\n"
          "• Depressants: slow the nervous system, such as alcohol, benzodiazepines and barbiturates.\n"
          "• Narcotics and opioids: relieve pain and cause drowsiness, such as morphine, heroin and codeine. Overdose stops breathing.\n"
          "• Hallucinogens: alter perception, such as LSD and psilocybin.\n"
          "• Cannabinoids: marijuana, whose main active compound is THC.\n"
          "• Inhalants: solvents and glues that are sniffed."),
        S("Dangerous drugs law in the Philippines",
          "Republic Act No. 9165, the Comprehensive Dangerous Drugs Act of 2002, penalises acts such as the importation, sale, possession and use of dangerous drugs and the diversion of controlled precursors and essential chemicals (for example ephedrine, used to make methamphetamine). It created the Dangerous Drugs Board (policy) and the Philippine Drug Enforcement Agency (PDEA, enforcement and laboratory). Drug testing follows two steps: a screening test, then a confirmatory test for positive screens."),
        S("Effects on the body and interpreting findings",
          "A toxicology result states which substance was found and, when possible, its concentration in blood or urine. Interpretation considers the specimen type, the time since use, tolerance, and whether the level is consistent with use, impairment or a fatal dose. Detection in urine shows past use, not necessarily impairment at the time of an incident. Toxicologists also consider the offender's welfare: findings can support referral to treatment and rehabilitation, which the law provides for drug dependents."),
        S("Alcohol",
          "Ethanol is a central nervous system depressant. It is absorbed mainly from the small intestine and eliminated mostly by the liver at a fairly steady rate. Blood alcohol concentration (BAC) is measured by breath analysers and confirmed by blood analysis, usually by gas chromatography.\n\n"
          "Under R.A. 10586, the Anti-Drunk and Drugged Driving Act of 2013, a driver of a private vehicle is considered under the influence at a BAC of 0.05 percent or higher, while drivers of trucks, buses, motorcycles and public utility vehicles must have no alcohol (any BAC above 0.0 percent)."),
        S("Quick review",
          "• Stimulants: shabu, cocaine. Depressants: alcohol, benzodiazepines. Opioids: heroin, morphine. Hallucinogens: LSD. Cannabis: THC.\n"
          "• R.A. 9165 (2002): Dangerous Drugs Board and PDEA; screening then confirmatory drug tests.\n"
          "• R.A. 10586: BAC 0.05% for private drivers; 0.0% for trucks, buses, motorcycles and PUVs."),
    ], terms=[("Stimulant", "A drug that increases central nervous system activity."),
              ("Depressant", "A drug that slows central nervous system activity."),
              ("Controlled precursor", "A chemical that can be used to manufacture dangerous drugs and is regulated by law."),
              ("BAC", "Blood alcohol concentration, expressed as a percentage."),
              ("PDEA", "Philippine Drug Enforcement Agency, the lead anti-drug law enforcement agency.")]),

    dict(title="Reviewer: Legal aspects and forensic report writing", minutes=8, refs=CHEM_REFS, sections=[
        S("Admissibility of chemical and toxicological evidence",
          "Laboratory findings are admitted through the testimony of a qualified forensic chemist or toxicologist, an expert whose special knowledge, training and experience are shown in court. The court weighs the expert's qualifications, the methods used, and whether proper procedures were followed. The integrity and identity of the evidence must be proven from seizure to court."),
        S("Chain of custody in drug cases",
          "Section 21 of R.A. 9165, as amended by R.A. 10640 (2014), requires that seized drugs be inventoried and photographed immediately after seizure in the presence of the accused (or representative or counsel), an elected public official, and a representative of the National Prosecution Service or the media. The items must be turned over to the PDEA forensic laboratory within 24 hours, and the laboratory must issue its certification of results promptly. Unjustified lapses that cast doubt on the identity and integrity of the drugs can lead to acquittal."),
        S("Interpreting and presenting results",
          "• Report only what the tests show; distinguish presumptive from confirmed results.\n"
          "• State concentrations with units and the specimen type.\n"
          "• Explain limitations, such as possible degradation or contamination.\n"
          "• Avoid opinions outside your expertise; stay objective whichever side called you."),
        S("Parts of a forensic chemistry report",
          "1. Case and request details (requesting officer, date and time received).\n"
          "2. Specimens submitted, with description, weight or volume, and markings.\n"
          "3. Purpose of the examination.\n"
          "4. Methods and tests performed.\n"
          "5. Findings or results.\n"
          "6. Conclusion.\n"
          "7. Remarks and disposition of the specimens.\n"
          "8. Name, signature and position of the examiner, and approval of the head of office.\n"
          "Integrity and accountability matter: never alter results, and correct errors openly."),
        S("Quick review",
          "• Experts must show qualifications; methods must be sound.\n"
          "• R.A. 10640: inventory and photographs with the accused, an elected official, and a prosecution or media representative; laboratory within 24 hours.\n"
          "• Reports separate presumptive from confirmed results and state limitations."),
    ], terms=[("Expert witness", "A witness allowed to give opinions because of special knowledge, skill, training or experience."),
              ("Corpus delicti", "The body of the crime; in drug cases, the seized drug itself."),
              ("Inventory", "The listing and photographing of seized items immediately after seizure."),
              ("Certification of results", "The laboratory's official statement of its findings.")]),
]
