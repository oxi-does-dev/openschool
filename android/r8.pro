# R8 вместо D8: те же классы, но без мёртвого кода и с короткими именами.
# В приложении нет reflection, поэтому хватает сохранить только те классы,
# которые Android создаёт по имени из манифеста.

-keep class ru.sch.diary.MainActivity { *; }
-keep class ru.sch.diary.NotifyJob { *; }
-keep class ru.sch.diary.SchFilesProvider { *; }

-dontwarn **
