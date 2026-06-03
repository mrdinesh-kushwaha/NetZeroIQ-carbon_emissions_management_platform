# Render Deployment

Use production profile on Render:

```env
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=jdbc:postgresql://<host>:5432/<database>
DB_USER=<database_user>
DB_PASSWORD=<database_password>
JWT_SECRET=<strong_secret_minimum_32_characters>
CORS_ALLOWED_ORIGINS=https://<your-frontend-domain>
APP_SEED_ENABLED=false
```

Local development uses H2 by default:

```bash
mvn spring-boot:run
```

Production secrets are not hardcoded in the codebase.
