# R8 rules for the release build.

# WorkManager (daily streak reminder) uses Room, which creates WorkDatabase_Impl by reflection.
# Room 2.6's own rule keeps the class but not its no-arg constructor under R8 full mode (AGP 9),
# which crashes the app at startup ("NoSuchMethodException: WorkDatabase_Impl.<init>").
-keep class * extends androidx.room.RoomDatabase { <init>(); }
