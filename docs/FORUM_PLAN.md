# Forum implementation

Reference: https://forum.cursor.com/ only. Public HTML checked 2026-11-08.

1. Preserve Vue 3 / Spring Boot / MariaDB, session cookies, CSRF and existing data.
2. Replace blog/submission workflow with direct forum publication and editable published topics. Keep private drafts. Existing pending submissions become drafts, not silently published.
3. Add persistent moderation rules, automatic detection, topic reports, review decisions, tags, subcategories and activity. Reuse account permissions and comment moderation.
4. Build Discourse-style topic tables, categories, tags, search, topic discussion, user activity, drafts, preferences and administration. Light/dark/responsive.
5. Run frontend checks, backend tests, isolated database HTTP integration and browser screenshot QA.

Implementation status: all five modules delivered. Flyway migrations extend through V12. Frontend production build and 13 tests pass; the backend has 19 unit/controller tests and 19 database integration tests. Real Chrome runs cover 16 workflow groups, desktop screenshots and 390px mobile layouts. See TESTING.md for commands and VISUAL_REVIEW.md for page comparisons and remaining differences.

Limits: authenticated reference views cannot be inspected without a reference account; those layouts extend the public design. No independent blogs, submission review, boards, collaborative editing or manuscript history.
