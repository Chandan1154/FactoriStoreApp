# Dependency scan and remediation summary

## Result

- Initial Spring Boot 3.5.16 backend scan: 22 known advisories across six
  dependency groups (including critical/high findings).
- Remediation: upgraded the backend to Spring Boot 4.1.2 and migrated to the
  supported `spring-boot-starter-webmvc` starter, then reran tests and the
  dependency scan.
- Follow-up scan: zero findings in the scanner's resolved dependency report
  at scan time.
- Validation: `mvn -q -f backend/pom.xml test` passed after the upgrade.

The final machine-readable scan result is [final-dependency-scan.json](./final-dependency-scan.json).
This is point-in-time evidence, not a guarantee against newly disclosed issues.
Rescan dependencies before every release and review transitive dependencies
and container base images separately.
