-- Retire the built-in cases (Tutorial: Living Room, Practice: Apartment Burglary,
-- Assessment: Office Shooting). Students now learn through Training Parts 1-3 and only
-- simulate cases their professor generated (opened by code or QR).
--
-- The rows are unpublished, not deleted, so past attempts and their scores stay intact.
-- start_simulation only accepts published scenarios, so they can no longer be started.

update public.simulation_scenarios
   set published = false
 where id in ('tutorial_residence', 'practice_apartment', 'assessment_office');
