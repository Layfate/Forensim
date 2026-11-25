package com.forensim.app.engine.scene

import kotlin.random.Random

/**
 * Random case briefings for the professor's "New summary" button. A briefing is built from parts
 * (what was reported, a note about the body, how the scene was secured, the task) so many
 * combinations exist and two shuffles rarely match.
 *
 * Every briefing stays accurate to the case:
 *  - it fits the room and who would report there (a clerk in a store, a housekeeper in a hotel room);
 *  - it agrees with the victim choice: with a body in the scene nobody is "taken to hospital"
 *    and nobody says "no one was hurt"; without one, a death means the body has been removed;
 *  - it never names the crime. Types students must tell apart share the same wording
 *    (theft and burglary both read as "items missing"; assault and shooting as "a person hurt").
 */
object CaseBriefings {

    /** Words for one room: how to name it and who would call the police there. */
    private class Place(val room: String, val reporters: List<String>)

    private val places = mapOf(
        "residence" to Place("the living room", listOf("the homeowner", "the occupant", "a family member")),
        "apartment" to Place("the apartment", listOf("the tenant", "a roommate", "the building superintendent")),
        "office" to Place("the office", listOf("an employee", "the office manager", "the cleaner")),
        "store" to Place("the convenience store", listOf("the clerk", "the store manager", "the owner")),
        "warehouse" to Place("the warehouse", listOf("the shift supervisor", "a worker arriving for the early shift", "the security guard")),
        "hotel_room" to Place("the hotel room", listOf("a housekeeper", "the guest", "the night manager")),
        "kitchen" to Place("the kitchen", listOf("the homeowner", "the occupant", "a relative")),
        "garage" to Place("the garage", listOf("the owner", "the homeowner", "a family member")),
        "bar" to Place("the bar", listOf("the bartender", "the bar manager", "a server closing up")),
        "classroom" to Place("the classroom", listOf("a teacher", "the janitor", "the principal")),
    )
    private val fallback = Place("the room", listOf("the occupant", "a witness", "the owner"))

    // {Who}/{who}: the reporter (capitalized or not); {room}: the room.

    /** Theft and burglary: belongings missing. Says nothing about how anyone got in. */
    private val missing = listOf(
        "{Who} found valuables missing from {room} and called the police.",
        "{Who} reported that several items had gone missing from {room} overnight.",
        "Police were called after {who} noticed belongings missing from {room}.",
        "{Who} discovered that money and personal items were gone from {room} and phoned for help.",
        "{Who} found drawers opened and valuables gone from {room}, then called the police.",
    )

    /** Robbery: belongings missing after someone left in a hurry. */
    private val takenInAHurry = listOf(
        "{Who} reported money missing from {room} after a stranger left in a hurry.",
        "Shortly after an unknown person rushed out of {room}, {who} found cash missing and called the police.",
        "{Who} called the police: a visitor hurried out of {room}, and money and belongings were gone.",
        "Witnesses saw someone run from {room}. Moments later {who} reported cash missing.",
    )

    /** Assault and shooting without a body: the person is in hospital. */
    private val hurt = listOf(
        "{Who} found a person injured in {room}. They were taken to hospital.",
        "After reports of a disturbance, officers found a person hurt in {room}. Paramedics took them to hospital.",
        "People nearby heard loud noises, and a person was later found injured in {room} and rushed to hospital.",
        "{Who} called an ambulance after finding someone hurt in {room}. The person is now in hospital.",
    )

    /** Homicide without a body: the body has already been removed. */
    private val deadRemoved = listOf(
        "A person was found dead in {room}. The body has since been removed for examination.",
        "{Who} found a person dead in {room}. The coroner has taken the body; everything else is as it was found.",
        "After reports of a disturbance, a person was found dead in {room}. The body has been removed.",
        "{Who} raised the alarm after finding someone dead in {room}. The body was taken away before you arrived.",
    )

    /** Any violent type with the body still in the scene (assault, shooting, homicide). */
    private val bodyAtScene = listOf(
        "{Who} found a person lying motionless in {room} and called emergency services. The person has not been moved.",
        "After reports of a disturbance, officers found a person unresponsive on the floor of {room}. Nobody has moved them.",
        "{Who} discovered a person on the floor of {room} and called the police. The body remains where it was found.",
        "People nearby heard loud noises. Officers then found a person lying on the floor of {room}, and the body has been left in place.",
    )

    /** Drug possession: found during a search, already secured by the officers. */
    private val search = listOf(
        "Officers serving a search warrant secured {room} after noticing suspicious items.",
        "Acting on a tip, police entered {room} with a warrant and found items that need examining.",
        "After complaints about visitors coming and going at all hours, officers obtained a warrant and secured {room}.",
        "During a warranted search, officers stopped and secured {room} when they saw suspicious items in plain view.",
    )

    /** Vandalism: property damaged. */
    private val damaged = listOf(
        "{Who} found property in {room} damaged and called the police.",
        "Police were called after {who} discovered {room} had been damaged overnight.",
        "{Who} arrived to find things broken in {room} and reported it.",
        "{Who} reported damage to {room} that was not there the day before.",
    )

    /** After a search line: the room is held, nothing moved (it already says it was secured). */
    private val searchHeld = listOf(
        "Nothing has been moved since the search stopped.",
        "Officers are holding the room until you arrive.",
        "Entry is now limited to investigators.",
    )

    private val nobodyHurt = listOf("Nobody was hurt.", "No one was injured.", "No one was in the room at the time.")

    /** Added to a non-violent case when the professor puts a body in the scene. */
    private val bodyAlsoFound = listOf(
        "A person was also found lying on the floor and has not been moved.",
        "Officers also found a person on the floor. The body has been left where it lay.",
        "A person lying on the floor was found as well; nobody has moved them.",
    )

    private val secured = listOf(
        "Officers have secured the scene.",
        "The scene is taped off and every entry is logged.",
        "Patrol has secured the area and is waiting for you.",
        "Nothing has been touched since the first officers arrived.",
    )

    private val task = listOf(
        "Process the scene and work out what happened.",
        "Document and collect the evidence, then decide what happened.",
        "Process the scene carefully and determine what took place.",
        "Work the scene and let the evidence show you what happened.",
    )

    /**
     * A random briefing for [type] in [location], matching [victim] (null = no body in the scene).
     * Never returns [avoid] (the briefing on screen) when another option exists.
     */
    fun random(type: CaseType, location: CaseLocation, victim: Victim?, random: Random = Random.Default, avoid: String? = null): String {
        var text = build(type, location, victim, random)
        repeat(12) { if (text != avoid) return text; text = build(type, location, victim, random) }
        return text
    }

    private fun build(type: CaseType, location: CaseLocation, victim: Victim?, random: Random): String {
        val place = places[location.id] ?: fallback
        val who = place.reporters.random(random)
        val body = victim != null
        val violent = type == CaseType.ASSAULT || type == CaseType.SHOOTING || type == CaseType.HOMICIDE
        val event = when {
            violent && body -> bodyAtScene
            type == CaseType.HOMICIDE -> deadRemoved
            violent -> hurt
            type == CaseType.ROBBERY -> takenInAHurry
            type == CaseType.DRUG_POSSESSION -> search
            type == CaseType.VANDALISM -> damaged
            else -> missing // theft, burglary
        }.random(random)
        val parts = buildList {
            add(event)
            when {
                body && !violent -> add(bodyAlsoFound.random(random))
                type == CaseType.VANDALISM -> add(nobodyHurt.random(random))
            }
            // The search lines already say the officers secured the room.
            add((if (type == CaseType.DRUG_POSSESSION) searchHeld else secured).random(random))
            add(task.random(random))
        }
        return parts.joinToString(" ")
            .replace("{Who}", who.replaceFirstChar { it.uppercase() })
            .replace("{who}", who)
            .replace("{room}", place.room)
    }
}
