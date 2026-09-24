# Database migrations

Flyway runs the SQL files in this folder automatically when the Spring Boot
application starts, before Hibernate validates the entity mappings.

## Rules for future schema changes

1. Never edit a migration that has already run in any environment.
2. Add the next migration using the format `V2__short_description.sql`, then
   `V3__short_description.sql`, and so on.
3. Keep schema changes here; do not rely on Hibernate to create production
   tables because `spring.jpa.hibernate.ddl-auto=validate` is intentional.
4. Test each migration against a database restored from the latest approved
   dump before deployment.

`V1__post_dump_20260921_dashboard_data.sql` is the consolidated migration for
all schema changes made after `Dump20260921.sql`. The dump already contains the
email, Gmail synchronization, labels/folders, calendar, call, chat, and core CRM
tables, so those historical scripts are not replayed.
